package com.example.audioindexer.performance

import com.example.audioindexer.data.model.Song
import com.example.audioindexer.playback.EngineStateMode
import com.example.audioindexer.playback.FatiguePreventionQueue
import com.example.audioindexer.playback.SkipRecommendationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

/**
 * LargeLibraryPerformanceTest
 *
 * Validates performance, memory stability, and concurrency invariants
 * when operating on a large music library containing 10,000+ tracks.
 */
class LargeLibraryPerformanceTest {

    private val categories = listOf("Love", "Motivational", "Sad", "High Energy", "Relaxed", "Focus")

    private fun generateLargeLibrary(size: Int = 10_000): List<Song> {
        return (1..size).map { i ->
            Song(
                id = i.toLong(),
                filePath = "/storage/emulated/0/Music/track_$i.mp3",
                title = "Track #$i",
                artist = "Artist ${i % 150}",
                album = "Album ${i % 50}",
                lyricsSnippet = "Sample lyrics snippet for track $i",
                aiCategory = categories[i % categories.size],
                isMusic = true,
                audioType = "MUSIC",
                durationMs = 180_000L + (i % 60) * 1000L,
                fileSize = 4_000_000L + (i % 500) * 1024L,
                extension = "mp3"
            )
        }
    }

    @Test
    fun testQueueInitializationPerformanceWith10000Tracks() {
        val largeLibrary = generateLargeLibrary(10_000)
        val fatigueQueue = FatiguePreventionQueue()

        val initDurationMs = measureTimeMillis {
            fatigueQueue.setPlaylist(largeLibrary, initialSongId = 1L)
        }

        println("FatiguePreventionQueue initialization for 10,000 songs took: ${initDurationMs}ms")
        // Initialization should be sub-second (< 500ms even on CI)
        assertTrue("Queue init took too long: ${initDurationMs}ms", initDurationMs < 500)

        val stats = fatigueQueue.getStats()
        assertEquals(10_000, stats.totalSongsInPlaylist)
        assertEquals(1, stats.playedInCurrentCycle)
        assertEquals(9_999, stats.remainingInCurrentCycle)
        assertEquals(1L, fatigueQueue.currentSong?.id)
    }

    @Test
    fun testShufflePlaybackLatencyAcross1000PicksIn10000TrackLibrary() {
        val largeLibrary = generateLargeLibrary(10_000)
        val fatigueQueue = FatiguePreventionQueue()
        fatigueQueue.setPlaylist(largeLibrary, initialSongId = 1L)

        var lastTrackId = 1L
        val pickCount = 1_000
        val durations = mutableListOf<Long>()

        for (i in 1..pickCount) {
            val start = System.nanoTime()
            val nextTrack = fatigueQueue.next()
            val elapsedNs = System.nanoTime() - start
            durations.add(elapsedNs)

            assertNotNull("Next track must not be null in 10,000 song library", nextTrack)
            assertNotEquals("Immediate repeat invariant violated at pick $i", lastTrackId, nextTrack!!.id)
            lastTrackId = nextTrack.id
        }

        val avgDurationUs = durations.average() / 1_000.0
        val maxDurationMs = (durations.maxOrNull() ?: 0L) / 1_000_000.0
        println("1,000 picks from 10k library: Avg=${avgDurationUs}µs, Max=${maxDurationMs}ms")

        // Average pick should take less than 1 millisecond (1000 microseconds)
        assertTrue("Average pick latency too high: ${avgDurationUs}µs", avgDurationUs < 1_000.0)
    }

    @Test
    fun testFatigueZeroRepeatInvariantAcrossEntire10000TrackCycle() {
        val largeLibrary = generateLargeLibrary(10_000)
        val fatigueQueue = FatiguePreventionQueue()
        fatigueQueue.setPlaylist(largeLibrary, initialSongId = 1L)

        val playedSet = mutableSetOf<Long>()
        playedSet.add(fatigueQueue.currentSong!!.id)

        // Consume remaining 9,999 tracks in cycle 1
        for (i in 2..10_000) {
            val track = fatigueQueue.next()
            assertNotNull(track)
            val added = playedSet.add(track!!.id)
            assertTrue("Song ${track.id} repeated before all 10,000 songs played!", added)
        }

        assertEquals(10_000, playedSet.size)
        assertEquals(1, fatigueQueue.cycleNumber)

        val lastSongOfCycle1 = fatigueQueue.currentSong!!.id

        // Advance to cycle 2
        val cycle2First = fatigueQueue.next()
        assertNotNull(cycle2First)
        assertEquals(2, fatigueQueue.cycleNumber)
        // Ensure first track of cycle 2 is not the last track of cycle 1
        assertNotEquals(lastSongOfCycle1, cycle2First!!.id)
    }

    @Test
    fun testConcurrentQueueAccessThreadSafety() {
        val largeLibrary = generateLargeLibrary(5_000)
        val fatigueQueue = FatiguePreventionQueue()
        fatigueQueue.setPlaylist(largeLibrary, initialSongId = 1L)

        val threadCount = 6
        val iterationsPerThread = 200
        val latch = CountDownLatch(threadCount)
        val successfulPicks = AtomicInteger(0)
        val errors = AtomicInteger(0)

        for (t in 0 until threadCount) {
            Thread {
                try {
                    for (i in 0 until iterationsPerThread) {
                        val track = fatigueQueue.next()
                        if (track != null) {
                            successfulPicks.incrementAndGet()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        val finished = latch.await(10, TimeUnit.SECONDS)
        assertTrue("Concurrent queue operations timed out", finished)
        assertEquals("Encountered errors during concurrent queue access", 0, errors.get())
        assertEquals(threadCount * iterationsPerThread, successfulPicks.get())
    }

    @Test
    fun testCategoryPivotFilteringPerformance() {
        val largeLibrary = generateLargeLibrary(10_000)
        val engine = SkipRecommendationEngine()

        // Simulate 2 rapid hard skips to trigger CONTRASTED_FILTER
        val song1 = largeLibrary[0] // Love
        val song2 = largeLibrary[1] // Motivational

        engine.onSkipClicked(
            currentPositionMs = 2_000L,
            currentSong = song1,
            playlistId = 1L,
            upcomingQueue = largeLibrary.drop(1)
        )

        engine.onSkipClicked(
            currentPositionMs = 3_000L,
            currentSong = song2,
            playlistId = 1L,
            upcomingQueue = largeLibrary.drop(2)
        )

        assertEquals(EngineStateMode.CONTRASTED_FILTER, engine.engineStatus.value.mode)

        // Measure filtering and reordering 10,000 upcoming songs
        val filterDurationMs = measureTimeMillis {
            val reordered = engine.onSkipClicked(
                currentPositionMs = 3_000L,
                currentSong = largeLibrary[2],
                playlistId = 1L,
                upcomingQueue = largeLibrary
            )
            assertTrue("Reordered pool must be non-empty", reordered.isNotEmpty())
        }

        println("Contrasted filter across 10,000 songs took: ${filterDurationMs}ms")
        assertTrue("Filtering 10,000 songs took too long: ${filterDurationMs}ms", filterDurationMs < 100)
    }
}
