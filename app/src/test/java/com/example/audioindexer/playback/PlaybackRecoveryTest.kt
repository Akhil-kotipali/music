package com.example.audioindexer.playback

import com.example.audioindexer.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * PlaybackRecoveryTest
 *
 * Validates playback error recovery, missing file loop protection,
 * and queue resilience against corrupted, missing, or unreadable files.
 */
class PlaybackRecoveryTest {

    private lateinit var queue: FatiguePreventionQueue

    private fun createSong(id: Long, path: String, title: String): Song = Song(
        id = id,
        filePath = path,
        title = title,
        artist = "Artist $id",
        album = "Album $id",
        lyricsSnippet = "Snippet $id",
        aiCategory = "Motivational",
        isMusic = true,
        audioType = "MUSIC",
        durationMs = 200_000L,
        fileSize = 4_000_000L,
        extension = "mp3"
    )

    @Before
    fun setUp() {
        queue = FatiguePreventionQueue()
    }

    @Test
    fun testMissingFilesLoopGuard_terminatesWhenAllTracksMissing() {
        // Simulate a playlist of 5 songs whose files do not exist on disk
        val songs = (1..5).map {
            createSong(it.toLong(), "/non/existent/path/song_$it.mp3", "Track $it")
        }
        queue.setPlaylist(songs, initialSongId = 1L)

        // Emulate AudioPlaybackService missing file skip loop guard logic
        var consecutiveMissingSkips = 0
        var playbackHaltedSafely = false
        val total = queue.getStats().totalSongsInPlaylist

        var currentSong = queue.currentSong
        while (currentSong != null) {
            val file = File(currentSong.filePath)
            if (!file.exists() || !file.canRead()) {
                consecutiveMissingSkips++
                if (consecutiveMissingSkips >= maxOf(1, total)) {
                    playbackHaltedSafely = true
                    currentSong = null
                    break
                }
                currentSong = queue.next()
            } else {
                consecutiveMissingSkips = 0
                break
            }
        }

        assertTrue("Playback loop guard must halt safely when all files are missing", playbackHaltedSafely)
        assertEquals("Consecutive missing skips must reach total playlist count (5)", 5, consecutiveMissingSkips)
        assertNull("Current playing track must be null after safe halt", currentSong)
    }

    @Test
    fun testPartiallyMissingPlaylist_skipsMissingAndPlaysValidTrack() {
        // Create 1 valid temp file and 2 missing files
        val validTempFile = File.createTempFile("test_song", ".mp3").apply {
            deleteOnExit()
        }

        val song1 = createSong(1L, "/invalid/path/1.mp3", "Missing Song 1")
        val song2 = createSong(2L, validTempFile.absolutePath, "Valid Song 2")
        val song3 = createSong(3L, "/invalid/path/3.mp3", "Missing Song 3")

        val songs = listOf(song1, song2, song3)
        queue.setPlaylist(songs, initialSongId = 1L)

        var consecutiveMissingSkips = 0
        var playedSong: Song? = null
        val total = queue.getStats().totalSongsInPlaylist

        var candidate = queue.currentSong
        while (candidate != null) {
            val file = File(candidate.filePath)
            if (!file.exists() || !file.canRead()) {
                consecutiveMissingSkips++
                if (consecutiveMissingSkips >= maxOf(1, total)) {
                    break
                }
                candidate = queue.next()
            } else {
                consecutiveMissingSkips = 0
                playedSong = candidate
                break
            }
        }

        assertNotNull("Must successfully recover and find the valid track", playedSong)
        assertEquals("Valid Song 2", playedSong?.title)
        assertEquals(0, consecutiveMissingSkips)
    }

    @Test
    fun testReorderingPreservesCycleIntegrityWithoutLoss() {
        val songs = (1..6).map { createSong(it.toLong(), "/path/$it.mp3", "Track $it") }
        queue.setPlaylist(songs, initialSongId = 1L)

        val remainingBefore = queue.getRemainingInCurrentCycle()
        assertEquals(5, remainingBefore.size)

        // Reverse the remaining queue
        val reversed = remainingBefore.reversed()
        queue.reorderRemaining(reversed)

        val remainingAfter = queue.getRemainingInCurrentCycle()
        assertEquals(5, remainingAfter.size)
        assertEquals(reversed.map { it.id }, remainingAfter.map { it.id })

        // Consume remaining items and ensure all distinct IDs are played
        val playedIds = mutableSetOf<Long>()
        playedIds.add(queue.currentSong!!.id)

        for (i in 1..5) {
            val next = queue.next()
            assertNotNull(next)
            playedIds.add(next!!.id)
        }

        assertEquals(6, playedIds.size)
    }
}
