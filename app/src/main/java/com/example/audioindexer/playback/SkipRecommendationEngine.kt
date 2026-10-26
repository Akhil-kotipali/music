package com.example.audioindexer.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.audioindexer.data.dao.PlaylistDao
import com.example.audioindexer.data.model.PlaylistSongMap
import com.example.audioindexer.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EngineStateMode {
    NORMAL,
    SHORT_TERM_PIVOT,
    CONTRASTED_FILTER,
    MOOD_MISMATCH,
    MOOD_ALIGNMENT,
    HUNTING_MODE
}

data class SkipEngineStatus(
    val mode: EngineStateMode = EngineStateMode.NORMAL,
    val consecutiveHardSkips: Int = 0,
    val isHuntingMode: Boolean = false,
    val lastSkippedCategory: String? = null,
    val lastActionDescription: String = "Monitoring playback behavior"
)

/**
 * SkipRecommendationEngine
 *
 * Real-time skip intelligence engine:
 * 1. Hard Skip detection: Clicks 'Next' within first 15 seconds (< 15,000ms).
 * 2. Mood Mismatch & Alignment:
 *    - Recognizes that consecutive skips often mean a mood is currently irrelevant, NOT hunting mode.
 *    - Pivots queue away from the rejected mood to align with user's desired state.
 * 3. Short-Term Pivot & Contrasting:
 *    - Narrows queue pool dynamically while preserving playlist intent.
 *    - Distinguishes rapid song searching (Hunting Mode across 5+ diverse tracks) from mood mismatch.
 * 4. Long-Term Pruning:
 *    - Tracks skip_count and play_count per playlist in the database.
 *    - If skip-to-play ratio > 80% after appearing >= 5 times, automatically removes the song from that playlist map.
 */
class SkipRecommendationEngine(
    private val context: Context? = null,
    private val playlistDao: PlaylistDao? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    private val onToastNotification: ((String) -> Unit)? = null
) {
    companion object {
        const val HARD_SKIP_THRESHOLD_MS = 15_000L // 15 seconds
        const val RAPID_SKIP_WINDOW_MS = 15_000L   // 15-second rolling window
        const val PRUNE_MIN_APPEARANCES = 5        // Minimum 5 appearances
        const val PRUNE_SKIP_RATIO = 0.80          // 80% skip-to-play ratio threshold

        // Contrast matrix for mood/genre tags
        val CONTRASTED_CATEGORIES = mapOf(
            "Sad" to listOf("High Energy", "Motivational", "Relaxed"),
            "Relaxed" to listOf("High Energy", "Focus", "Motivational"),
            "High Energy" to listOf("Relaxed", "Sad", "Focus"),
            "Motivational" to listOf("Relaxed", "Sad", "Focus"),
            "Love" to listOf("Focus", "High Energy", "Motivational"),
            "Focus" to listOf("High Energy", "Love", "Motivational")
        )
    }

    private var consecutiveHardSkips = 0
    private val rapidSkipTimestamps = mutableListOf<Long>()
    private val recentSkippedCategories = mutableListOf<String>()
    private var isHuntingMode = false
    private var lastSkippedCategory: String? = null

    private val _engineStatus = MutableStateFlow(SkipEngineStatus())
    val engineStatus: StateFlow<SkipEngineStatus> = _engineStatus.asStateFlow()

    /**
     * Called whenever user clicks 'Next'.
     * Analyzes playback duration to determine if it is a Hard Skip (<15s) and applies
     * Mood Alignment, Short-Term Pivot, Hunting Mode, or Long-Term Pruning.
     *
     * Returns the reordered/filtered upcoming queue.
     */
    fun onSkipClicked(
        currentPositionMs: Long,
        currentSong: Song,
        playlistId: Long?,
        upcomingQueue: List<Song>
    ): List<Song> {
        val now = System.currentTimeMillis()
        val isHardSkip = currentPositionMs < HARD_SKIP_THRESHOLD_MS
        val skippedCategory = currentSong.aiCategory

        // Track rolling window for rapid skipping
        rapidSkipTimestamps.add(now)
        rapidSkipTimestamps.removeAll { now - it > RAPID_SKIP_WINDOW_MS }

        if (!skippedCategory.isNullOrBlank()) {
            recentSkippedCategories.add(skippedCategory)
            if (recentSkippedCategories.size > 6) {
                recentSkippedCategories.removeAt(0)
            }
        }

        // Check if skips indicate Mood Irrelevance (same mood skipped 2+ times)
        val isMoodMismatch = !skippedCategory.isNullOrBlank() &&
                recentSkippedCategories.takeLast(3).count { it.equals(skippedCategory, ignoreCase = true) } >= 2

        // True Hunting Mode: 5+ rapid skips across diverse categories in under 15 seconds
        if (rapidSkipTimestamps.size >= 5 && !isMoodMismatch) {
            isHuntingMode = true
            _engineStatus.value = SkipEngineStatus(
                mode = EngineStateMode.HUNTING_MODE,
                consecutiveHardSkips = consecutiveHardSkips,
                isHuntingMode = true,
                lastSkippedCategory = lastSkippedCategory,
                lastActionDescription = "Hunting Mode: User rapidly seeking track. Category penalties suspended."
            )
            if (playlistId != null) {
                recordSkipAndCheckPrune(playlistId, currentSong)
            }
            return upcomingQueue
        }

        if (isHardSkip) {
            consecutiveHardSkips++
            lastSkippedCategory = skippedCategory

            // Long-term DB Pruning tracking
            if (playlistId != null) {
                recordSkipAndCheckPrune(playlistId, currentSong)
            }

            // 1. Mood Mismatch handling: User is rejecting this specific mood, not hunting
            if (isMoodMismatch && !skippedCategory.isNullOrBlank()) {
                val reordered = applyPivotAway(upcomingQueue, skippedCategory)
                _engineStatus.value = SkipEngineStatus(
                    mode = EngineStateMode.MOOD_MISMATCH,
                    consecutiveHardSkips = consecutiveHardSkips,
                    isHuntingMode = false,
                    lastSkippedCategory = skippedCategory,
                    lastActionDescription = "Mood Adjustment: Skipped '$skippedCategory' (irrelevant to mood). Reprioritizing alternative moods."
                )
                return reordered
            }

            // 2. Short-Term Pivot Behaviors
            return if (consecutiveHardSkips >= 2 && !skippedCategory.isNullOrBlank()) {
                // Consecutive hard skip >= 2: Contrast
                val contrasted = CONTRASTED_CATEGORIES[skippedCategory] ?: emptyList()
                val reordered = applyContrastedFilter(upcomingQueue, contrasted, skippedCategory)

                _engineStatus.value = SkipEngineStatus(
                    mode = EngineStateMode.CONTRASTED_FILTER,
                    consecutiveHardSkips = consecutiveHardSkips,
                    isHuntingMode = false,
                    lastSkippedCategory = skippedCategory,
                    lastActionDescription = "Contrasted Filter: Hard skipped '$skippedCategory'. Prioritizing: ${contrasted.joinToString()}."
                )
                reordered
            } else if (consecutiveHardSkips == 1 && !skippedCategory.isNullOrBlank()) {
                // Hard skip = 1: Pivot away from this category
                val reordered = applyPivotAway(upcomingQueue, skippedCategory)

                _engineStatus.value = SkipEngineStatus(
                    mode = EngineStateMode.SHORT_TERM_PIVOT,
                    consecutiveHardSkips = consecutiveHardSkips,
                    isHuntingMode = false,
                    lastSkippedCategory = skippedCategory,
                    lastActionDescription = "Short-Term Pivot: Hard skipped '$skippedCategory'. Pivoting upcoming queue to alternative categories."
                )
                reordered
            } else {
                upcomingQueue
            }
        } else {
            // Soft skip (>15s): Normal queue progression
            consecutiveHardSkips = 0
            isHuntingMode = false
            _engineStatus.value = SkipEngineStatus(
                mode = EngineStateMode.NORMAL,
                consecutiveHardSkips = 0,
                isHuntingMode = false,
                lastActionDescription = "Soft skip (>15s). Normal queue progression."
            )
            return upcomingQueue
        }
    }

    /**
     * Called when a track finishes playing naturally or user listens past the hard-skip window.
     */
    fun onSongCompleted(song: Song, playlistId: Long?) {
        consecutiveHardSkips = 0
        isHuntingMode = false
        rapidSkipTimestamps.clear()
        recentSkippedCategories.clear()

        _engineStatus.value = SkipEngineStatus(
            mode = EngineStateMode.NORMAL,
            consecutiveHardSkips = 0,
            isHuntingMode = false,
            lastSkippedCategory = null,
            lastActionDescription = "Track completed normally. Play metrics logged."
        )

        if (playlistId != null) {
            recordPlay(playlistId, song)
        }
    }

    private fun recordPlay(playlistId: Long, song: Song) {
        scope.launch {
            val dao = playlistDao ?: return@launch
            val existing = dao.getSongMap(playlistId, song.id)
            if (existing != null) {
                dao.updateMap(
                    existing.copy(
                        playCount = existing.playCount + 1,
                        lastPlayedAt = System.currentTimeMillis()
                    )
                )
            } else {
                dao.insertMap(
                    PlaylistSongMap(
                        playlistId = playlistId,
                        songId = song.id,
                        playCount = 1,
                        skipCount = 0,
                        lastPlayedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    /**
     * Behavior 1 (Single Hard Skip): Pivots away from skippedCategory
     */
    private fun applyPivotAway(queue: List<Song>, avoidedCategory: String): List<Song> {
        val nonMatching = queue.filter { it.aiCategory != avoidedCategory }
        val matching = queue.filter { it.aiCategory == avoidedCategory }
        return nonMatching + matching
    }

    /**
     * Behavior 1 (Double Hard Skip): Narrows pool aggressively to highly contrasted categories
     */
    private fun applyContrastedFilter(
        queue: List<Song>,
        contrastedCategories: List<String>,
        avoidedCategory: String
    ): List<Song> {
        val contrastedSongs = queue.filter { it.aiCategory != null && contrastedCategories.contains(it.aiCategory) }
        val otherNonMatching = queue.filter { it.aiCategory != avoidedCategory && !contrastedCategories.contains(it.aiCategory ?: "") }
        val matchingAvoided = queue.filter { it.aiCategory == avoidedCategory }

        return contrastedSongs + otherNonMatching + matchingAvoided
    }

    /**
     * Behavior 2: Long-Term Pruning
     * Tracks skip_count and play_count per playlist. If skip ratio > 80% after appearing >= 5 times,
     * deletes song from playlist map and notifies user via Toast.
     */
    private fun recordSkipAndCheckPrune(playlistId: Long, song: Song) {
        scope.launch {
            val dao = playlistDao ?: return@launch
            val existing = dao.getSongMap(playlistId, song.id)
            val currentPlays = existing?.playCount ?: 0
            val currentSkips = (existing?.skipCount ?: 0) + 1
            val totalAppearances = currentPlays + currentSkips
            val skipRatio = currentSkips.toDouble() / totalAppearances

            if (totalAppearances >= PRUNE_MIN_APPEARANCES && skipRatio > PRUNE_SKIP_RATIO) {
                // Delete from playlist map
                dao.deleteSongFromPlaylist(playlistId, song.id)
                val playlist = dao.getPlaylistById(playlistId)
                val playlistName = playlist?.name ?: "Playlist"

                val percentage = (skipRatio * 100).toInt()
                showToast("Auto-pruned '${song.title}' from $playlistName ($percentage% skip rate)")
            } else {
                if (existing != null) {
                    dao.updateMap(
                        existing.copy(
                            skipCount = currentSkips,
                            lastPlayedAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    dao.insertMap(
                        PlaylistSongMap(
                            playlistId = playlistId,
                            songId = song.id,
                            playCount = 0,
                            skipCount = 1,
                            lastPlayedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    private fun showToast(message: String) {
        onToastNotification?.invoke(message)
        if (context != null) {
            try {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Throwable) {
                // Ignore in headless/test environments
            }
        }
    }
}
