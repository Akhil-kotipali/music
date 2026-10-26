package com.example.audioindexer.data.repository

import androidx.room.withTransaction
import com.example.audioindexer.data.dao.SongDao
import com.example.audioindexer.data.dao.StagedSongDao
import com.example.audioindexer.data.db.AudioDatabase
import com.example.audioindexer.data.model.Song
import com.example.audioindexer.data.model.StagedSong
import com.example.audioindexer.util.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AudioRepository(
    private val database: AudioDatabase? = null,
    private val songDao: SongDao,
    private val stagedSongDao: StagedSongDao
) {
    val allSongs: Flow<List<Song>> = songDao.getAllSongs()
    val allStagedSongs: Flow<List<StagedSong>> = stagedSongDao.getAllStagedSongs()
    val songCount: Flow<Int> = songDao.getSongCount()
    val stagedCount: Flow<Int> = stagedSongDao.getStagedCount()

    fun searchSongs(query: String): Flow<List<Song>> {
        return songDao.searchSongs(query)
    }

    suspend fun getAllIndexedPaths(): Set<String> = withContext(Dispatchers.IO) {
        songDao.getAllFilePaths().toSet()
    }

    suspend fun getAllStagedPaths(): Set<String> = withContext(Dispatchers.IO) {
        stagedSongDao.getAllStagedFilePaths().toSet()
    }

    suspend fun insertSong(song: Song): Long = withContext(Dispatchers.IO) {
        songDao.insert(song)
    }

    suspend fun insertSongs(songs: List<Song>): List<Long> = withContext(Dispatchers.IO) {
        songDao.insertAll(songs)
    }

    suspend fun stageSong(stagedSong: StagedSong): Long = withContext(Dispatchers.IO) {
        stagedSongDao.insert(stagedSong)
    }

    suspend fun stageSongs(stagedSongs: List<StagedSong>): List<Long> = withContext(Dispatchers.IO) {
        stagedSongDao.insertAll(stagedSongs)
    }

    /**
     * Atomically promotes a staged track into the main songs index.
     * Inserts into songs and deletes from staged_songs within an atomic Room transaction
     * to eliminate state discrepancies and orphan records.
     */
    suspend fun promoteStagedSong(stagedSong: StagedSong): Boolean = withContext(Dispatchers.IO) {
        val canonicalPath = StorageUtils.getCanonicalPath(stagedSong.filePath)
        val song = Song(
            filePath = canonicalPath,
            title = stagedSong.title,
            artist = stagedSong.artist,
            album = stagedSong.album,
            lyricsSnippet = stagedSong.lyricsSnippet ?: "Lyrics snippet: [${stagedSong.title}] - not yet parsed.",
            aiCategory = stagedSong.aiCategory,
            isMusic = stagedSong.isMusic,
            audioType = stagedSong.audioType,
            durationMs = stagedSong.durationMs,
            fileSize = stagedSong.fileSize,
            extension = stagedSong.extension,
            dateAdded = System.currentTimeMillis()
        )

        if (database != null) {
            database.withTransaction {
                val existing = songDao.getSongByPath(canonicalPath)
                if (existing != null) {
                    if (existing.aiCategory.isNullOrBlank() && !song.aiCategory.isNullOrBlank()) {
                        songDao.updateAiCategory(existing.id, song.aiCategory)
                    }
                } else {
                    songDao.insert(song)
                }
                stagedSongDao.delete(stagedSong)
            }
        } else {
            val existing = songDao.getSongByPath(canonicalPath)
            if (existing != null) {
                if (existing.aiCategory.isNullOrBlank() && !song.aiCategory.isNullOrBlank()) {
                    songDao.updateAiCategory(existing.id, song.aiCategory)
                }
            } else {
                songDao.insert(song)
            }
            stagedSongDao.delete(stagedSong)
        }
        true
    }

    /**
     * Atomically batch promotes all staged songs into the main library.
     * Enclosed in an atomic transaction to ensure all-or-nothing consistency.
     */
    suspend fun promoteAllStaged(): Int = withContext(Dispatchers.IO) {
        val stagedList = stagedSongDao.getAllStagedFilePaths()
        var promotedCount = 0

        if (database != null) {
            database.withTransaction {
                for (path in stagedList) {
                    val staged = stagedSongDao.getStagedSongByPath(path)
                    if (staged != null) {
                        val canonicalPath = StorageUtils.getCanonicalPath(staged.filePath)
                        val existing = songDao.getSongByPath(canonicalPath)
                        if (existing != null) {
                            if (existing.aiCategory.isNullOrBlank() && !staged.aiCategory.isNullOrBlank()) {
                                songDao.updateAiCategory(existing.id, staged.aiCategory)
                            }
                        } else {
                            val song = Song(
                                filePath = canonicalPath,
                                title = staged.title,
                                artist = staged.artist,
                                album = staged.album,
                                lyricsSnippet = staged.lyricsSnippet ?: "Lyrics snippet: [${staged.title}] - not yet parsed.",
                                aiCategory = staged.aiCategory,
                                isMusic = staged.isMusic,
                                audioType = staged.audioType,
                                durationMs = staged.durationMs,
                                fileSize = staged.fileSize,
                                extension = staged.extension,
                                dateAdded = System.currentTimeMillis()
                            )
                            songDao.insert(song)
                        }
                        stagedSongDao.delete(staged)
                        promotedCount++
                    }
                }
            }
        } else {
            for (path in stagedList) {
                val staged = stagedSongDao.getStagedSongByPath(path)
                if (staged != null) {
                    promoteStagedSong(staged)
                    promotedCount++
                }
            }
        }
        promotedCount
    }

    /**
     * Scans all records in the "songs" and "staged_songs" tables to clean up duplicate records
     * caused by storage path aliases (e.g. /sdcard vs /storage/emulated/0).
     * If one row has an ai_category and another does not, preserves the ai_category!
     */
    suspend fun deduplicateDatabaseSongs(): Int = withContext(Dispatchers.IO) {
        val allSongs = songDao.getAllSongsDirect()
        val seenCanonical = mutableMapOf<String, Song>()
        var duplicatesRemoved = 0

        for (song in allSongs) {
            val canon = StorageUtils.getCanonicalPath(song.filePath)
            val existing = seenCanonical[canon]
            if (existing != null) {
                // Preserve existing AI classification if the duplicate has one
                if (existing.aiCategory.isNullOrBlank() && !song.aiCategory.isNullOrBlank()) {
                    songDao.updateAiCategory(existing.id, song.aiCategory)
                    seenCanonical[canon] = existing.copy(aiCategory = song.aiCategory)
                }
                songDao.delete(song)
                duplicatesRemoved++
            } else {
                if (song.filePath != canon) {
                    val normalizedSong = song.copy(filePath = canon)
                    songDao.update(normalizedSong)
                    seenCanonical[canon] = normalizedSong
                } else {
                    seenCanonical[canon] = song
                }
            }
        }

        // Clean up staged songs table against canonical main library paths
        try {
            val allStaged = stagedSongDao.getAllStagedSongsDirect()
            val seenStaged = mutableMapOf<String, StagedSong>()
            for (staged in allStaged) {
                val canon = StorageUtils.getCanonicalPath(staged.filePath)
                if (seenCanonical.containsKey(canon)) {
                    // Already in main library
                    stagedSongDao.delete(staged)
                    duplicatesRemoved++
                    continue
                }
                val existing = seenStaged[canon]
                if (existing != null) {
                    stagedSongDao.delete(staged)
                    duplicatesRemoved++
                } else {
                    if (staged.filePath != canon) {
                        val normalizedStaged = staged.copy(filePath = canon)
                        stagedSongDao.update(normalizedStaged)
                        seenStaged[canon] = normalizedStaged
                    } else {
                        seenStaged[canon] = staged
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore staging deduplication error if empty
        }

        duplicatesRemoved
    }

    suspend fun dismissStagedSong(stagedSong: StagedSong) = withContext(Dispatchers.IO) {
        stagedSongDao.delete(stagedSong)
    }

    suspend fun clearAllStaged() = withContext(Dispatchers.IO) {
        stagedSongDao.deleteAll()
    }

    suspend fun clearAllSongs() = withContext(Dispatchers.IO) {
        songDao.deleteAll()
    }
}
