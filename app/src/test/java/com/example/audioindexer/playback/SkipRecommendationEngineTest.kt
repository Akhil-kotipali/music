package com.example.audioindexer.playback

import com.example.audioindexer.data.dao.PlaylistDao
import com.example.audioindexer.data.model.Playlist
import com.example.audioindexer.data.model.PlaylistSongMap
import com.example.audioindexer.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipRecommendationEngineTest {

    private fun createSong(id: Long, title: String, category: String?): Song {
        return Song(
            id = id,
            filePath = "/storage/emulated/0/Music/$title.mp3",
            title = title,
            artist = "Artist $id",
            album = "Album",
            lyricsSnippet = "Snippet",
            aiCategory = category,
            isMusic = true,
            audioType = "MUSIC",
            durationMs = 200_000L,
            fileSize = 4_000_000L,
            extension = "mp3"
        )
    }

    private class MockPlaylistDao : PlaylistDao {
        val maps = mutableMapOf<Pair<Long, Long>, PlaylistSongMap>()
        val deletedFromPlaylist = mutableListOf<Pair<Long, Long>>()

        override fun getAllPlaylists(): Flow<List<Playlist>> = emptyFlow()
        override suspend fun getPlaylistById(id: Long): Playlist = Playlist(id = id, name = "Workout Hits")
        override suspend fun insertPlaylist(playlist: Playlist): Long = playlist.id
        override suspend fun deletePlaylist(playlist: Playlist) {}

        override suspend fun getSongMap(playlistId: Long, songId: Long): PlaylistSongMap? {
            return maps[Pair(playlistId, songId)]
        }
        override fun getMapsForPlaylist(playlistId: Long): Flow<List<PlaylistSongMap>> = emptyFlow()
        override suspend fun insertMap(map: PlaylistSongMap): Long {
            maps[Pair(map.playlistId, map.songId)] = map
            return 1L
        }
        override suspend fun insertAllMaps(maps: List<PlaylistSongMap>): List<Long> = emptyList()
        override suspend fun updateMap(map: PlaylistSongMap) {
            maps[Pair(map.playlistId, map.songId)] = map
        }
        override suspend fun incrementPlayCount(playlistId: Long, songId: Long, timestamp: Long) {}
        override suspend fun incrementSkipCount(playlistId: Long, songId: Long, timestamp: Long) {}
        override suspend fun deleteSongFromPlaylist(playlistId: Long, songId: Long): Int {
            maps.remove(Pair(playlistId, songId))
            deletedFromPlaylist.add(Pair(playlistId, songId))
            return 1
        }
        override fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>> = emptyFlow()
        override suspend fun getSongsForPlaylistDirect(playlistId: Long): List<Song> = emptyList()
        override fun getSongCountForPlaylist(playlistId: Long): Flow<Int> = emptyFlow()
        override suspend fun getSongCountForPlaylistDirect(playlistId: Long): Int = maps.size
        override suspend fun clearPlaylistSongs(playlistId: Long) {}
        override fun getPlaylistCount(): Flow<Int> = emptyFlow()
    }

    @Test
    fun testFirstHardSkipTriggersShortTermPivot() {
        val mockDao = MockPlaylistDao()
        val engine = SkipRecommendationEngine(
            playlistDao = mockDao,
            scope = CoroutineScope(Dispatchers.Unconfined)
        )

        val currentSong = createSong(1, "Sad Track 1", "Sad")
        val queue = listOf(
            createSong(2, "Sad Track 2", "Sad"),
            createSong(3, "Motivational Track", "Motivational"),
            createSong(4, "High Energy Track", "High Energy")
        )

        // Hard skip (<15s)
        val resultQueue = engine.onSkipClicked(
            currentPositionMs = 5000L,
            currentSong = currentSong,
            playlistId = null,
            upcomingQueue = queue
        )

        val status = engine.engineStatus.value
        assertEquals(EngineStateMode.SHORT_TERM_PIVOT, status.mode)
        assertEquals(1, status.consecutiveHardSkips)
        assertFalse(status.isHuntingMode)
        assertEquals("Sad", status.lastSkippedCategory)

        // Non-matching categories must precede "Sad"
        assertEquals("Motivational", resultQueue[0].aiCategory)
        assertEquals("High Energy", resultQueue[1].aiCategory)
        assertEquals("Sad", resultQueue[2].aiCategory)
    }

    @Test
    fun testSecondConsecutiveHardSkipTriggersContrastedFilter() {
        val mockDao = MockPlaylistDao()
        val engine = SkipRecommendationEngine(
            playlistDao = mockDao,
            scope = CoroutineScope(Dispatchers.Unconfined)
        )

        val queue = listOf(
            createSong(2, "Sad Track 2", "Sad"),
            createSong(3, "Focus Track", "Focus"),
            createSong(4, "High Energy Track", "High Energy")
        )

        // First hard skip on Sad
        engine.onSkipClicked(
            currentPositionMs = 4000L,
            currentSong = createSong(1, "Sad Track 1", "Sad"),
            playlistId = null,
            upcomingQueue = queue
        )

        // Second hard skip on Sad
        val resultQueue = engine.onSkipClicked(
            currentPositionMs = 3000L,
            currentSong = createSong(10, "Sad Track Another", "Sad"),
            playlistId = null,
            upcomingQueue = queue
        )

        val status = engine.engineStatus.value
        assertEquals(EngineStateMode.CONTRASTED_FILTER, status.mode)
        assertEquals(2, status.consecutiveHardSkips)

        // Contrasted categories for "Sad" are High Energy, Motivational, Relaxed
        assertEquals("High Energy", resultQueue[0].aiCategory)
        assertEquals("Focus", resultQueue[1].aiCategory)
        assertEquals("Sad", resultQueue[2].aiCategory)
    }

    @Test
    fun testSoftSkipResetsPenalties() {
        val mockDao = MockPlaylistDao()
        val engine = SkipRecommendationEngine(
            playlistDao = mockDao,
            scope = CoroutineScope(Dispatchers.Unconfined)
        )

        val queue = listOf(createSong(2, "Track 2", "Sad"))

        // Hard skip
        engine.onSkipClicked(5000L, createSong(1, "Track 1", "Sad"), null, queue)
        assertEquals(1, engine.engineStatus.value.consecutiveHardSkips)

        // Soft skip (after 45s of listening, > 15s)
        val result = engine.onSkipClicked(45000L, createSong(1, "Track 1", "Sad"), null, queue)
        assertEquals(EngineStateMode.NORMAL, engine.engineStatus.value.mode)
        assertEquals(0, engine.engineStatus.value.consecutiveHardSkips)
        assertEquals(queue, result)
    }

    @Test
    fun testHuntingModeSuspendsCategoryPenalties() {
        val mockDao = MockPlaylistDao()
        val engine = SkipRecommendationEngine(
            playlistDao = mockDao,
            scope = CoroutineScope(Dispatchers.Unconfined)
        )

        val queue = listOf(
            createSong(10, "Song 10", "Sad"),
            createSong(11, "Song 11", "Motivational")
        )

        // 4 rapid skips within rolling 20s window
        engine.onSkipClicked(2000L, createSong(1, "Song 1", "Sad"), null, queue)
        engine.onSkipClicked(1000L, createSong(2, "Song 2", "Sad"), null, queue)
        engine.onSkipClicked(1500L, createSong(3, "Song 3", "Sad"), null, queue)
        val result = engine.onSkipClicked(500L, createSong(4, "Song 4", "Sad"), null, queue)

        val status = engine.engineStatus.value
        assertEquals(EngineStateMode.HUNTING_MODE, status.mode)
        assertTrue(status.isHuntingMode)
        // In Hunting Mode, queue is not rearranged
        assertEquals(queue, result)
    }

    @Test
    fun testNaturalSongCompletionResetsMode() {
        val mockDao = MockPlaylistDao()
        val engine = SkipRecommendationEngine(
            playlistDao = mockDao,
            scope = CoroutineScope(Dispatchers.Unconfined)
        )

        val queue = listOf(createSong(1, "Song 1", "Sad"))
        engine.onSkipClicked(2000L, queue.first(), null, queue)
        assertEquals(1, engine.engineStatus.value.consecutiveHardSkips)

        // Natural completion
        engine.onSongCompleted(queue.first(), null)
        assertEquals(EngineStateMode.NORMAL, engine.engineStatus.value.mode)
        assertEquals(0, engine.engineStatus.value.consecutiveHardSkips)
    }

    @Test
    fun testAutoPruningThresholdWhenSkipRatioExceeds80Percent() = runBlocking {
        val mockDao = MockPlaylistDao()
        val notifications = mutableListOf<String>()
        val engine = SkipRecommendationEngine(
            playlistDao = mockDao,
            scope = CoroutineScope(Dispatchers.Unconfined),
            onToastNotification = { notifications.add(it) }
        )

        val song = createSong(99, "Annoying Track", "Sad")
        val playlistId = 77L

        // Pre-populate with 4 skips and 0 plays (appearances = 4 < 5, so not pruned yet)
        mockDao.insertMap(
            PlaylistSongMap(
                playlistId = playlistId,
                songId = song.id,
                playCount = 0,
                skipCount = 4
            )
        )

        // 5th skip: total appearances = 5, skips = 5, ratio = 100% (>80%)
        engine.onSkipClicked(
            currentPositionMs = 3000L,
            currentSong = song,
            playlistId = playlistId,
            upcomingQueue = emptyList()
        )

        // Verify song was pruned from the playlist map
        assertTrue(mockDao.deletedFromPlaylist.contains(Pair(playlistId, song.id)))
        assertTrue(notifications.any { it.contains("Auto-pruned 'Annoying Track'") })
    }
}
