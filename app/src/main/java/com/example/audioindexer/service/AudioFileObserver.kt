package com.example.audioindexer.service

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.util.Log
import com.example.audioindexer.data.db.AudioDatabase
import com.example.audioindexer.util.StorageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * FileObserver manager that monitors directories on internal storage and SD cards.
 * When a new audio file (.mp3, .wav, .m4a) is created or moved into an observed directory,
 * it isolates the track and holds it in the "staged_songs" table WITHOUT processing.
 */
class AudioFileObserverManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onNewAudioDetected: ((String) -> Unit)? = null
) {
    private val observers = mutableListOf<FileObserver>()
    private val db = AudioDatabase.getDatabase(context)
    private val songDao = db.songDao()
    private val stagedDao = db.stagedSongDao()

    companion object {
        private const val TAG = "AudioFileObserver"
        // Inotify flags to track new file arrivals once writing is complete
        private const val WATCH_FLAGS = FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE
    }

    /**
     * Starts watching all prominent audio and download directories on internal storage and SD cards.
     */
    fun startWatching() {
        stopWatching()

        val candidateDirs = mutableListOf<File>()

        // 1. Standard public directories on internal storage
        val publicDirTypes = arrayOf(
            Environment.DIRECTORY_MUSIC,
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_PODCASTS,
            Environment.DIRECTORY_DOCUMENTS
        )

        for (type in publicDirTypes) {
            val dir = Environment.getExternalStoragePublicDirectory(type)
            if (dir != null && dir.exists()) {
                candidateDirs.add(dir)
                collectSubdirectories(dir, candidateDirs, maxDepth = 3)
            }
        }

        // 2. Discover candidate directories on SD cards and other storage roots
        val allVolumes = StorageUtils.getAllStorageDirectories(context)
        for (volume in allVolumes) {
            val volRoot = File(volume.path)
            if (volRoot.exists() && volRoot.canRead()) {
                val musicDir = File(volRoot, "Music")
                if (musicDir.exists()) {
                    candidateDirs.add(musicDir)
                    collectSubdirectories(musicDir, candidateDirs, maxDepth = 2)
                }
                val downloadDir = File(volRoot, "Download")
                if (downloadDir.exists()) {
                    candidateDirs.add(downloadDir)
                    collectSubdirectories(downloadDir, candidateDirs, maxDepth = 2)
                }
            }
        }

        // Register observers for each target directory
        for (dir in candidateDirs.distinct()) {
            try {
                val observer = createObserver(dir)
                observer.startWatching()
                observers.add(observer)
            } catch (e: Exception) {
                Log.w(TAG, "Could not start FileObserver for ${dir.absolutePath}: ${e.message}")
            }
        }

        Log.d(TAG, "Started monitoring ${observers.size} directories for audio additions.")
    }

    private fun collectSubdirectories(parent: File, result: MutableList<File>, maxDepth: Int, currentDepth: Int = 0) {
        if (currentDepth >= maxDepth) return
        val children = parent.listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: return
        for (child in children) {
            result.add(child)
            collectSubdirectories(child, result, maxDepth, currentDepth + 1)
        }
    }

    private fun createObserver(directory: File): FileObserver {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(directory, WATCH_FLAGS) {
                override fun onEvent(event: Int, path: String?) {
                    handleEvent(directory, event, path)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(directory.absolutePath, WATCH_FLAGS) {
                override fun onEvent(event: Int, path: String?) {
                    handleEvent(directory, event, path)
                }
            }
        }
    }

    private fun handleEvent(parentDir: File, event: Int, relativePath: String?) {
        if (relativePath == null) return
        val file = File(parentDir, relativePath)

        // Only react to audio files
        if (!StorageUtils.isAudioFile(file)) return

        scope.launch(Dispatchers.IO) {
            try {
                val canon = StorageUtils.getCanonicalPath(file)

                // Check if already in main songs or already in staging table
                val existingSong = songDao.getSongByPath(canon)
                val existingStaged = stagedDao.getStagedSongByPath(canon)

                if (existingSong == null && existingStaged == null) {
                    Log.i(TAG, "New audio file isolated: $canon. Holding in staging table without processing.")

                    val metadata = MetadataExtractor.extract(file)
                    val staged = MetadataExtractor.toStagedSong(metadata, source = "FILE_OBSERVER")

                    stagedDao.insert(staged)
                    onNewAudioDetected?.invoke(canon)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling file observer event for ${file.absolutePath}", e)
            }
        }
    }

    fun stopWatching() {
        for (observer in observers) {
            try {
                observer.stopWatching()
            } catch (ignored: Exception) {}
        }
        observers.clear()
        Log.d(TAG, "Stopped all directory FileObservers.")
    }

    fun isWatching(): Boolean = observers.isNotEmpty()

    fun getWatchedCount(): Int = observers.size
}
