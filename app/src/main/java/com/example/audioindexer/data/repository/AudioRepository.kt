package com.example.audioindexer.data.repository

import com.example.audioindexer.data.dao.SongDao
import com.example.audioindexer.data.dao.StagedSongDao
import com.example.audioindexer.data.model.Song
import com.example.audioindexer.data.model.StagedSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AudioRepository(
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
     * Promotes a staged track into the main songs index.
     * Extracts full processing, saves to songs table, and cleans up staging entry.
     */
    suspend fun promoteStagedSong(stagedSong: StagedSong): Boolean = withContext(Dispatchers.IO) {
        val song = Song(
            filePath = stagedSong.filePath,
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
        songDao.insert(song)
        stagedSongDao.delete(stagedSong)
        true
    }

    /**
     * Batch promotes all staged songs into the main library.
     */
    suspend fun promoteAllStaged(): Int = withContext(Dispatchers.IO) {
        val stagedList = stagedSongDao.getAllStagedFilePaths()
        var promotedCount = 0
        for (path in stagedList) {
            val staged = stagedSongDao.getStagedSongByPath(path)
            if (staged != null) {
                promoteStagedSong(staged)
                promotedCount++
            }
        }
        promotedCount
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
