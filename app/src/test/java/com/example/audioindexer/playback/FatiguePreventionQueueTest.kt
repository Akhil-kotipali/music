package com.example.audioindexer.playback

import com.example.audioindexer.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FatiguePreventionQueueTest {

    private lateinit var queue: FatiguePreventionQueue

    private fun createSong(id: Long, title: String, category: String? = null): Song {
        return Song(
            id = id,
            filePath = "/storage/emulated/0/Music/$title.mp3",
            title = title,
            artist = "Artist $id",
            album = "Album $id",
            lyricsSnippet = "Snippet $id",
            aiCategory = category
        )
    }

    @Before
    fun setUp() {
        queue = FatiguePreventionQueue()
    }

    @Test
    fun testEmptyPlaylist_returnsNull() {
        queue.setPlaylist(emptyList())
        assertNull(queue.currentSong)
        assertNull(queue.next())
        val stats = queue.getStats()
        assertEquals(0, stats.totalSongsInPlaylist)
    }

    @Test
    fun testSingleSongPlaylist() {
        val song = createSong(1L, "Solo Song")
        queue.setPlaylist(listOf(song))

        assertEquals("Solo Song", queue.currentSong?.title)
        val next = queue.next()
        assertEquals("Solo Song", next?.title)
        assertTrue(queue.cycleNumber >= 2)
    }

    @Test
    fun testZeroRepeatInvariant_acrossCycleBoundaries() {
        // Required invariant: When at least two distinct eligible songs exist,
        // the next song should not equal the currently playing song.
        val song1 = createSong(1L, "Song A")
        val song2 = createSong(2L, "Song B")
        val songs = listOf(song1, song2)

        queue.setPlaylist(songs, initialSongId = 1L)
        assertEquals(1L, queue.currentSong?.id)

        // First cycle play through
        val next1 = queue.next()
        assertNotNull(next1)
        assertEquals(2L, next1?.id) // Must be Song B to avoid repeating Song A

        // Transition from Cycle 1 -> Cycle 2:
        // The first song of cycle 2 MUST NOT be the same as the last song played in cycle 1 (Song B)
        val nextCycleFirst = queue.next()
        assertNotNull(nextCycleFirst)
        assertEquals(1L, nextCycleFirst?.id) // Must be Song A
    }

    @Test
    fun testLargePlaylist_allSongsPlayedBeforeRepeat() {
        val totalSongs = 20
        val songs = (1..totalSongs).map { createSong(it.toLong(), "Track $it") }

        queue.setPlaylist(songs)
        val playedInCycle1 = mutableSetOf<Long>()
        queue.currentSong?.let { playedInCycle1.add(it.id) }

        // Advance through the remaining (totalSongs - 1) songs in cycle 1
        for (i in 2..totalSongs) {
            val song = queue.next()
            assertNotNull(song)
            val isUnique = playedInCycle1.add(song!!.id)
            assertTrue("Song ${song.id} was repeated before cycle 1 ended!", isUnique)
        }

        assertEquals("All $totalSongs distinct songs must be played in cycle 1", totalSongs, playedInCycle1.size)
        assertEquals(1, queue.cycleNumber)

        // Now next() must trigger Cycle 2!
        val cycle2First = queue.next()
        assertNotNull(cycle2First)
        assertEquals(2, queue.cycleNumber)
    }

    @Test
    fun testPrevious_navigatesHistoricalLog() {
        val songs = (1..5).map { createSong(it.toLong(), "Song $it") }
        queue.setPlaylist(songs, initialSongId = 1L)

        val s2 = queue.next()
        val s3 = queue.next()

        assertEquals(s3?.id, queue.currentSong?.id)

        // Previous should go back to s2
        val prev1 = queue.previous()
        assertEquals(s2?.id, prev1?.id)

        // Previous should go back to s1
        val prev2 = queue.previous()
        assertEquals(1L, prev2?.id)
    }

    @Test
    fun testShuffleRemaining_preservesUnplayedCount() {
        val songs = (1..10).map { createSong(it.toLong(), "Song $it") }
        queue.setPlaylist(songs)

        val initialRemaining = queue.getRemainingInCurrentCycle().size
        queue.shuffleRemaining()
        val afterShuffleRemaining = queue.getRemainingInCurrentCycle().size

        assertEquals(initialRemaining, afterShuffleRemaining)
    }
}
