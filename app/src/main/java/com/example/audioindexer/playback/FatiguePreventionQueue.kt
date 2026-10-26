package com.example.audioindexer.playback

import com.example.audioindexer.data.model.Song

data class FatigueQueueStats(
    val cycleNumber: Int,
    val totalSongsInPlaylist: Int,
    val playedInCurrentCycle: Int,
    val remainingInCurrentCycle: Int,
    val totalHistoricalPlays: Int,
    val isCycleComplete: Boolean
)

/**
 * FatiguePreventionQueue
 *
 * Shuffles a playlist dynamically and ensures:
 * 1. A historical log of played track IDs is maintained.
 * 2. NO song can be repeated or re-queued until EVERY SINGLE song in the current
 *    playlist has been played exactly once.
 * 3. Once all songs have been played, a new cycle is started with a fresh dynamic shuffle,
 *    preventing the first song of the new cycle from being identical to the last played song.
 */
class FatiguePreventionQueue {

    private val masterPlaylist = mutableListOf<Song>()
    private val masterMap = mutableMapOf<Long, Song>()

    // Global historical log of played track IDs
    val playedHistoryIds = mutableListOf<Long>()

    // Track IDs played in the current fatigue cycle
    private val currentCyclePlayedIds = mutableSetOf<Long>()

    // Unplayed songs remaining in the current cycle (dynamically shuffled)
    private val currentCycleQueue = mutableListOf<Song>()

    var currentSong: Song? = null
        private set

    var cycleNumber: Int = 1
        private set

    @Synchronized
    fun setPlaylist(songs: List<Song>, initialSongId: Long? = null) {
        masterPlaylist.clear()
        masterMap.clear()
        playedHistoryIds.clear()
        currentCyclePlayedIds.clear()
        currentCycleQueue.clear()
        cycleNumber = 1
        currentSong = null

        if (songs.isEmpty()) return

        masterPlaylist.addAll(songs)
        for (song in songs) {
            masterMap[song.id] = song
        }

        // Initialize first fatigue cycle
        val shuffled = masterPlaylist.shuffled().toMutableList()

        if (initialSongId != null) {
            val initialSong = masterMap[initialSongId]
            if (initialSong != null) {
                shuffled.remove(initialSong)
                currentSong = initialSong
                currentCyclePlayedIds.add(initialSong.id)
                playedHistoryIds.add(initialSong.id)
            }
        }

        currentCycleQueue.addAll(shuffled)

        if (currentSong == null && currentCycleQueue.isNotEmpty()) {
            val first = currentCycleQueue.removeAt(0)
            currentSong = first
            currentCyclePlayedIds.add(first.id)
            playedHistoryIds.add(first.id)
        }
    }

    /**
     * Advances to next track in the queue adhering strictly to the zero-repeat fatigue rule.
     */
    @Synchronized
    fun next(): Song? {
        if (masterPlaylist.isEmpty()) return null

        // If all songs in current cycle have been played, start a new fatigue cycle!
        if (currentCycleQueue.isEmpty()) {
            cycleNumber++
            currentCyclePlayedIds.clear()

            // Fresh dynamic shuffle for new cycle
            val freshShuffle = masterPlaylist.shuffled().toMutableList()

            // Ensure first song of new cycle is NOT the song that just finished playing
            val lastPlayedId = currentSong?.id
            if (lastPlayedId != null && freshShuffle.size > 1 && freshShuffle.first().id == lastPlayedId) {
                // Swap first item with last item to prevent immediate back-to-back repeat
                val temp = freshShuffle.removeAt(0)
                freshShuffle.add(temp)
            }

            currentCycleQueue.addAll(freshShuffle)
        }

        val nextSong = currentCycleQueue.removeAt(0)
        currentSong = nextSong
        currentCyclePlayedIds.add(nextSong.id)
        playedHistoryIds.add(nextSong.id)
        return nextSong
    }

    /**
     * Moves to previously played song from the historical log.
     */
    @Synchronized
    fun previous(): Song? {
        if (playedHistoryIds.size <= 1) {
            return currentSong
        }

        // Pop current and get preceding song from historical log
        val currentIndex = playedHistoryIds.size - 1
        val prevSongId = playedHistoryIds[currentIndex - 1]
        val prevSong = masterMap[prevSongId]

        if (prevSong != null) {
            // Put current song back into currentCycleQueue front if desired
            currentSong?.let { currentCycleQueue.add(0, it) }
            playedHistoryIds.removeAt(currentIndex)
            currentSong = prevSong
            return prevSong
        }

        return currentSong
    }

    /**
     * Reshuffles remaining songs in current cycle dynamically
     */
    @Synchronized
    fun shuffleRemaining() {
        val remaining = currentCycleQueue.shuffled()
        currentCycleQueue.clear()
        currentCycleQueue.addAll(remaining)
    }

    @Synchronized
    fun getRemainingInCurrentCycle(): List<Song> = currentCycleQueue.toList()

    @Synchronized
    fun getPlayedInCurrentCycle(): List<Song> {
        return currentCyclePlayedIds.mapNotNull { masterMap[it] }
    }

    @Synchronized
    fun getStats(): FatigueQueueStats {
        return FatigueQueueStats(
            cycleNumber = cycleNumber,
            totalSongsInPlaylist = masterPlaylist.size,
            playedInCurrentCycle = currentCyclePlayedIds.size,
            remainingInCurrentCycle = currentCycleQueue.size,
            totalHistoricalPlays = playedHistoryIds.size,
            isCycleComplete = currentCycleQueue.isEmpty()
        )
    }
}
