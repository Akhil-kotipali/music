package com.example.audioindexer.service

import android.content.Context
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
     * Recursively collects all audio files (.mp3, .wav, .m4a) from all detected storage volumes.
     */
    fun findAudioFiles(
        context: Context,
        onDirectoryVisited: ((String, Int) -> Unit)? = null
    ): List<File> {
        val audioFiles = mutableListOf<File>()
        val storageVolumes = StorageUtils.getAllStorageDirectories(context)
        var totalAudioFound = 0

        for (volume in storageVolumes) {
            val rootDir = File(volume.path)
            if (!rootDir.exists() || !rootDir.canRead()) continue

            // Use iterative traversal (Stack) to prevent call-stack overflow on deep file hierarchies
            val directoryStack = Stack<File>()
            directoryStack.push(rootDir)

            while (!directoryStack.isEmpty()) {
                val currentDir = directoryStack.pop()
                onDirectoryVisited?.invoke(currentDir.absolutePath, totalAudioFound)

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
                                audioFiles.add(child)
                                totalAudioFound++
                            }
                        }
                    } catch (e: Exception) {
                        // Avoid crashing on individual file permission anomalies
                    }
                }
            }
        }

        return audioFiles
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

        indexedCount
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
