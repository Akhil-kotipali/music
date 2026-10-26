package com.example.audioindexer.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.audioindexer.AudioIndexerApp
import com.example.audioindexer.MainActivity
import com.example.audioindexer.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import java.io.File

data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queueStats: FatigueQueueStats? = null,
    val engineStatus: SkipEngineStatus? = null,
    val playbackSpeed: Float = 1.0f,
    val isBeatModeEnabled: Boolean = false,
    val currentSoundPreset: String = "Mega Bass",
    val bassStrength: Int = 850
)

class AudioPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    lateinit var player: ExoPlayer
        private set

    lateinit var audioEffectsManager: AudioEffectsManager
        private set
    lateinit var beatDetectorManager: BeatDetectorManager
        private set

    val fatigueQueue = FatiguePreventionQueue()
    lateinit var skipRecommendationEngine: SkipRecommendationEngine
        private set

    private var currentPlaylistId: Long? = 1L // Default playlist ID

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val binder = PlaybackBinder()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var isReceiverRegistered = false
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.d("AudioPlaybackService", "Audio becoming noisy (headphones/BT disconnected) -> pausing")
                pause()
            }
        }
    }

    private var consecutiveMissingFileSkips = 0

    // Earbud media button multi-tap detection (Single: Play/Pause, Double: Next, Triple: Prev)
    private var mediaButtonClickCount = 0
    private val mediaButtonHandler = Handler(Looper.getMainLooper())
    private val mediaButtonRunnable = Runnable {
        when (mediaButtonClickCount) {
            1 -> togglePlayPause()
            2 -> playNext()
            3 -> playPrevious()
            else -> if (mediaButtonClickCount > 3) playPrevious()
        }
        mediaButtonClickCount = 0
    }

    inner class PlaybackBinder : Binder() {
        fun getService(): AudioPlaybackService = this@AudioPlaybackService
    }

    override fun onBind(intent: Intent?): IBinder? {
        val superBinder = super.onBind(intent)
        return if (intent?.action == "androidx.media3.session.MediaSessionService") {
            superBinder
        } else {
            binder
        }
    }

    override fun onCreate() {
        super.onCreate()

        val app = application as AudioIndexerApp
        skipRecommendationEngine = SkipRecommendationEngine(
            context = applicationContext,
            playlistDao = app.database.playlistDao(),
            scope = serviceScope
        )

        audioEffectsManager = AudioEffectsManager(applicationContext)
        beatDetectorManager = BeatDetectorManager(applicationContext)

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // handleAudioFocus = true
            )
            .build()

        val initialSessionId = player.audioSessionId
        if (initialSessionId != C.AUDIO_SESSION_ID_UNSET) {
            audioEffectsManager.attachToAudioSession(initialSessionId)
            beatDetectorManager.attachToAudioSession(initialSessionId)
        }

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    audioEffectsManager.attachToAudioSession(audioSessionId)
                    beatDetectorManager.attachToAudioSession(audioSessionId)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateState()
                if (playbackState == Player.STATE_READY) {
                    val sid = player.audioSessionId
                    if (sid != C.AUDIO_SESSION_ID_UNSET && sid > 0) {
                        audioEffectsManager.attachToAudioSession(sid)
                        beatDetectorManager.attachToAudioSession(sid)
                    }
                }
                if (playbackState == Player.STATE_ENDED) {
                    fatigueQueue.currentSong?.let {
                        skipRecommendationEngine.onSongCompleted(it, currentPlaylistId)
                    }
                    playNextInternal(isUserSkip = false)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("AudioPlaybackService", "ExoPlayer playback error: ${error.message}", error)
                // Automatically recover and advance to next track in queue rather than freezing
                playNextInternal(isUserSkip = false)
            }
        })

        // Collect engine status to update playbackState flow
        serviceScope.launch {
            skipRecommendationEngine.engineStatus.collect {
                updateState()
            }
        }

        // Periodic ticker for current position & rhythmic haptics when playing
        serviceScope.launch {
            while (isActive) {
                if (::player.isInitialized && player.isPlaying) {
                    updateState()
                    if (::beatDetectorManager.isInitialized) {
                        beatDetectorManager.onPlaybackTick(player.currentPosition)
                    }
                }
                delay(300L)
            }
        }

        // Register Audio Becoming Noisy BroadcastReceiver
        try {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            registerReceiver(becomingNoisyReceiver, filter)
            isReceiverRegistered = true
        } catch (e: Exception) {
            Log.w("AudioPlaybackService", "Failed to register becoming noisy receiver: ${e.message}")
        }

        val sessionActivityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(object : MediaSession.Callback {
                override fun onMediaButtonEvent(
                    session: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                    intent: Intent
                ): Boolean {
                    val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }

                    if (keyEvent != null) {
                        if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                            when (keyEvent.keyCode) {
                                KeyEvent.KEYCODE_HEADSETHOOK,
                                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                                KeyEvent.KEYCODE_MEDIA_PLAY,
                                KeyEvent.KEYCODE_MEDIA_PAUSE,
                                KeyEvent.KEYCODE_MEDIA_NEXT,
                                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> return true
                            }
                        } else if (keyEvent.action == KeyEvent.ACTION_UP) {
                            when (keyEvent.keyCode) {
                                KeyEvent.KEYCODE_HEADSETHOOK,
                                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                    mediaButtonHandler.removeCallbacks(mediaButtonRunnable)
                                    mediaButtonClickCount++
                                    if (mediaButtonClickCount >= 3) {
                                        mediaButtonHandler.post(mediaButtonRunnable)
                                    } else {
                                        mediaButtonHandler.postDelayed(mediaButtonRunnable, 350L)
                                    }
                                    return true
                                }
                                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                    if (!player.isPlaying) togglePlayPause()
                                    return true
                                }
                                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                    if (player.isPlaying) pause()
                                    return true
                                }
                                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                    playNext()
                                    return true
                                }
                                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                    playPrevious()
                                    return true
                                }
                            }
                        }
                    }
                    return super.onMediaButtonEvent(session, controllerInfo, intent)
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    fun playSongFromPlaylist(playlist: List<Song>, song: Song, playlistId: Long? = 1L) {
        currentPlaylistId = playlistId
        fatigueQueue.setPlaylist(playlist, song.id)
        consecutiveMissingFileSkips = 0
        loadAndPlay(song)
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                fatigueQueue.currentSong?.let { loadAndPlay(it) }
            } else {
                player.play()
            }
        }
        updateState()
    }

    fun pause() {
        player.pause()
        updateState()
    }

    fun playNext() {
        playNextInternal(isUserSkip = true)
    }

    private fun playNextInternal(isUserSkip: Boolean) {
        val current = fatigueQueue.currentSong
        if (isUserSkip && current != null) {
            // Register skip in SkipRecommendationEngine
            val reordered = skipRecommendationEngine.onSkipClicked(
                currentPositionMs = player.currentPosition,
                currentSong = current,
                playlistId = currentPlaylistId,
                upcomingQueue = fatigueQueue.getRemainingInCurrentCycle()
            )
            // Apply Short-Term Pivot or Contrasted Filter to remaining cycle queue
            fatigueQueue.reorderRemaining(reordered)
        }

        val nextSong = fatigueQueue.next()
        if (nextSong != null) {
            loadAndPlay(nextSong)
        } else {
            player.stop()
            updateState()
        }
    }

    fun playPrevious() {
        if (player.currentPosition > 3000L) {
            player.seekTo(0)
            updateState()
            return
        }
        val prevSong = fatigueQueue.previous()
        if (prevSong != null) {
            loadAndPlay(prevSong)
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        updateState()
    }

    private fun loadAndPlay(song: Song) {
        val file = File(song.filePath)
        if (!file.exists() || !file.canRead()) {
            Log.w("AudioPlaybackService", "Audio file missing or unreadable: ${song.filePath}. Skipping to next track.")
            consecutiveMissingFileSkips++
            val total = fatigueQueue.getStats().totalSongsInPlaylist
            if (consecutiveMissingFileSkips >= maxOf(1, total)) {
                Log.e("AudioPlaybackService", "All tracks in playlist are unreadable or missing. Halting playback loop safely.")
                player.stop()
                consecutiveMissingFileSkips = 0
                updateState()
                return
            }
            playNextInternal(isUserSkip = false)
            return
        }

        consecutiveMissingFileSkips = 0

        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setMediaId(song.id.toString())
            .setMediaMetadata(metadata)
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        val sid = player.audioSessionId
        if (sid != C.AUDIO_SESSION_ID_UNSET && sid > 0) {
            audioEffectsManager.attachToAudioSession(sid)
            beatDetectorManager.attachToAudioSession(sid)
        }
        player.play()
        updateState()
    }

    fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        updateState()
    }

    fun setSoundPreset(presetName: String) {
        if (::audioEffectsManager.isInitialized) {
            audioEffectsManager.applyPreset(presetName)
            updateState()
        }
    }

    fun setBassBoostStrength(strength: Int) {
        if (::audioEffectsManager.isInitialized) {
            audioEffectsManager.setBassStrength(strength)
            updateState()
        }
    }

    fun setBeatModeEnabled(enabled: Boolean) {
        if (::beatDetectorManager.isInitialized) {
            if (::player.isInitialized) {
                val sid = player.audioSessionId
                if (sid != C.AUDIO_SESSION_ID_UNSET && sid > 0) {
                    beatDetectorManager.attachToAudioSession(sid)
                }
            }
            beatDetectorManager.setBeatModeEnabled(enabled)
            updateState()
        }
    }

    fun updateState() {
        _playbackState.value = PlaybackState(
            currentSong = fatigueQueue.currentSong,
            isPlaying = player.isPlaying,
            currentPositionMs = player.currentPosition,
            durationMs = if (player.duration > 0) player.duration else (fatigueQueue.currentSong?.durationMs ?: 0L),
            queueStats = fatigueQueue.getStats(),
            engineStatus = skipRecommendationEngine.engineStatus.value,
            playbackSpeed = player.playbackParameters.speed,
            isBeatModeEnabled = if (::beatDetectorManager.isInitialized) beatDetectorManager.isBeatModeEnabled else false,
            currentSoundPreset = if (::audioEffectsManager.isInitialized) audioEffectsManager.currentPreset else "Mega Bass",
            bassStrength = if (::audioEffectsManager.isInitialized) audioEffectsManager.bassStrength else 850
        )
    }

    override fun onDestroy() {
        mediaButtonHandler.removeCallbacksAndMessages(null)
        if (::beatDetectorManager.isInitialized) {
            beatDetectorManager.release()
        }
        if (::audioEffectsManager.isInitialized) {
            audioEffectsManager.release()
        }
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(becomingNoisyReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }
}
