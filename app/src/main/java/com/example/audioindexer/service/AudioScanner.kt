package com.example.audioindexer.service

import android.content.Context
import android.provider.MediaStore
import com.example.audioindexer.data.db.AudioDatabase
import com.example.audioindexer.data.model.Song
import com.example.audioindexer.data.model.StagedSong
import com.example.audioindexer.util.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Stack

data class ScanProgress(
    val currentDirectory: String = "",
    val filesScanned: Int = 0,
    val audioFound: Int = 0,
    val newTracksIsolated: Int = 0,
    val isFinished: Boolean = false,
    val message: String = ""
)

object AudioScanner {

    /**
     * Discovers audio files using Android's system MediaStore indexed audio provider.
     * This provides normal music discovery across all shared device storage on Android 10, 11, 12, 13, 14, 15
     * without requiring MANAGE_EXTERNAL_STORAGE.
     */
    fun queryMediaStoreAudio(context: Context): List<File> {
        val audioFiles = mutableListOf<File>()
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DATA
            )
            val selection = "${MediaStore.Audio.Media.DATA} LIKE '%.mp3' OR " +
                    "${MediaStore.Audio.Media.DATA} LIKE '%.wav' OR " +
                    "${MediaStore.Audio.Media.DATA} LIKE '%.m4a'"

            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )

            cursor?.use { c ->
                val dataColumn = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                while (c.moveToNext()) {
                    if (dataColumn != -1) {
                        val path = c.getString(dataColumn)
                        if (!path.isNullOrBlank()) {
                            val file = File(path)
                            if (StorageUtils.isAudioFile(file) && file.exists() && file.canRead()) {
                                audioFiles.add(file)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Graceful fallback for test or restricted environments
        }
        return audioFiles
    }

    /**
     * Discovers audio files (.mp3, .wav, .m4a) across:
     * 1. Android MediaStore indexed audio provider (shared internal & external audio).
     * 2. Iterative filesystem traversal across all accessible storage volumes (including removable SD cards).
     * Duplicates are automatically eliminated.
     */
    fun findAudioFiles(
        context: Context,
        onDirectoryVisited: ((String, Int) -> Unit)? = null
    ): List<File> {
        val seenMap = LinkedHashMap<String, File>()

        // 1. First, harvest all indexed audio from Android MediaStore
        val mediaStoreFiles = queryMediaStoreAudio(context)
        for (f in mediaStoreFiles) {
            val canonicalOrAbs = try { f.canonicalPath } catch (_: Exception) { f.absolutePath }
            seenMap[canonicalOrAbs] = f
        }

        onDirectoryVisited?.invoke("Android MediaStore (Indexed Audio)", seenMap.size)

        // 2. Traversal of accessible storage directories and removable SD cards
        val storageVolumes = StorageUtils.getAllStorageDirectories(context)

        for (volume in storageVolumes) {
            val rootDir = File(volume.path)
            if (!rootDir.exists() || !rootDir.canRead()) continue

            // Use iterative traversal (Stack) to prevent call-stack overflow on deep file hierarchies
            val directoryStack = Stack<File>()
            directoryStack.push(rootDir)

            while (!directoryStack.isEmpty()) {
                val currentDir = directoryStack.pop()
                onDirectoryVisited?.invoke(currentDir.absolutePath, seenMap.size)

                val children = try {
                    currentDir.listFiles()
                } catch (e: Exception) {
                    null
                } ?: continue

                for (child in children) {
                    try {
                        if (child.isDirectory) {
                            val name = child.name
                            // Skip hidden system/cache directories to preserve battery and IO
                            if (!name.startsWith(".") &&
                                !name.equals("Android", ignoreCase = true) &&
                                !name.equals("tmp", ignoreCase = true) &&
                                !name.equals("cache", ignoreCase = true)
                            ) {
                                directoryStack.push(child)
                            }
                        } else if (child.isFile) {
                            if (StorageUtils.isAudioFile(child)) {
                                val pathKey = try { child.canonicalPath } catch (_: Exception) { child.absolutePath }
                                if (!seenMap.containsKey(pathKey)) {
                                    seenMap[pathKey] = child
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Avoid crashing on individual file permission anomalies
                    }
                }
            }
        }

        return seenMap.values.toList()
    }

    /**
     * Performs a full scan and indexes all discovered files directly into the "songs" table.
     */
    suspend fun performFullScan(
        context: Context,
        onProgress: (ScanProgress) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val db = AudioDatabase.getDatabase(context)
        val songDao = db.songDao()

        onProgress(ScanProgress(message = "Discovering storage volumes and scanning audio files..."))

        val audioFiles = findAudioFiles(context) { dir, count ->
            onProgress(
                ScanProgress(
                    currentDirectory = dir,
                    audioFound = count,
                    message = "Scanning directory: ${File(dir).name}"
                )
            )
        }

        val existingPaths = songDao.getAllFilePaths().toHashSet()
        val newAudioFiles = audioFiles.filter { !existingPaths.contains(it.absolutePath) }
        val totalNew = newAudioFiles.size
        val songsBatch = mutableListOf<Song>()
        var newlyIndexedCount = 0

        for ((index, file) in newAudioFiles.withIndex()) {
            val meta = MetadataExtractor.extract(file)
            songsBatch.add(MetadataExtractor.toSong(meta))

            // Batch insert in chunks of 50 for optimal SQLite performance
            if (songsBatch.size >= 50 || index == totalNew - 1) {
                songDao.insertAll(songsBatch)
                newlyIndexedCount += songsBatch.size
                songsBatch.clear()

                onProgress(
                    ScanProgress(
                        currentDirectory = file.parent ?: "",
                        filesScanned = index + 1,
                        audioFound = audioFiles.size,
                        message = "Indexing songs into database: $newlyIndexedCount / $totalNew"
                    )
                )
            }
        }

        onProgress(
            ScanProgress(
                filesScanned = audioFiles.size,
                audioFound = audioFiles.size,
                isFinished = true,
                message = "Scan complete. Found ${audioFiles.size} audio files ($newlyIndexedCount new tracks indexed)."
            )
        )

        newlyIndexedCount
    }

    /**
     * Delta-check system:
     * Compares current storage against existing database index.
     * Isolates only newly discovered tracks and holds them in the "staged_songs" table
     * WITHOUT processing them into the main "songs" table.
     */
    suspend fun performDeltaCheck(
        context: Context,
        onProgress: (ScanProgress) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val db = AudioDatabase.getDatabase(context)
        val songDao = db.songDao()
        val stagedSongDao = db.stagedSongDao()

        onProgress(ScanProgress(message = "Reading indexed tracks and checking disk deltas..."))

        // Existing indexed and staged file paths
        val existingIndexedPaths = songDao.getAllFilePaths().toHashSet()
        val existingStagedPaths = stagedSongDao.getAllStagedFilePaths().toHashSet()

        val diskFiles = findAudioFiles(context) { dir, count ->
            onProgress(
                ScanProgress(
                    currentDirectory = dir,
                    audioFound = count,
                    message = "Checking for new tracks in: ${File(dir).name}"
                )
            )
        }

        // Isolate ONLY the new tracks that are neither indexed nor currently staged
        val newFiles = diskFiles.filter { file ->
            val path = file.absolutePath
            !existingIndexedPaths.contains(path) && !existingStagedPaths.contains(path)
        }

        val newlyStagedCount = newFiles.size
        val stagedBatch = mutableListOf<StagedSong>()

        for ((index, file) in newFiles.withIndex()) {
            val meta = MetadataExtractor.extract(file)
            stagedBatch.add(MetadataExtractor.toStagedSong(meta, source = "DELTA_CHECK"))

            if (stagedBatch.size >= 50 || index == newlyStagedCount - 1) {
                stagedSongDao.insertAll(stagedBatch)
                stagedBatch.clear()

                onProgress(
                    ScanProgress(
                        filesScanned = index + 1,
                        audioFound = diskFiles.size,
                        newTracksIsolated = index + 1,
                        message = "Staging newly detected tracks: ${index + 1} / $newlyStagedCount"
                    )
                )
            }
        }

        onProgress(
            ScanProgress(
                filesScanned = diskFiles.size,
                audioFound = diskFiles.size,
                newTracksIsolated = newlyStagedCount,
                isFinished = true,
                message = if (newlyStagedCount > 0)
                    "Delta check complete: Isolated $newlyStagedCount new tracks in staging table without processing."
                else
                    "Delta check complete: No new audio tracks found on storage."
            )
        )

        newlyStagedCount
    }
}
