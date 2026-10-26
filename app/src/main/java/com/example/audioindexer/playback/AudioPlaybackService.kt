package com.example.audioindexer.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.audioindexer.MainActivity
import com.example.audioindexer.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queueStats: FatigueQueueStats? = null
)

class AudioPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    lateinit var player: ExoPlayer
        private set

    val fatigueQueue = FatiguePreventionQueue()

    private val binder = PlaybackBinder()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

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

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // handleAudioFocus = true
            )
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateState()
                if (playbackState == Player.STATE_ENDED) {
                    playNext()
                }
            }
        })

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
                // Default Media3 session behaviors
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    fun playSongFromPlaylist(playlist: List<Song>, song: Song) {
        fatigueQueue.setPlaylist(playlist, song.id)
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

    fun playNext() {
        val nextSong = fatigueQueue.next()
        if (nextSong != null) {
            loadAndPlay(nextSong)
        } else {
            player.stop()
            updateState()
        }
    }

    fun playPrevious() {
        // If current song played more than 3 seconds, replay it; else go back
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
        if (!file.exists()) return

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
        player.play()
        updateState()
    }

    fun updateState() {
        _playbackState.value = PlaybackState(
            currentSong = fatigueQueue.currentSong,
            isPlaying = player.isPlaying,
            currentPositionMs = player.currentPosition,
            durationMs = if (player.duration > 0) player.duration else (fatigueQueue.currentSong?.durationMs ?: 0L),
            queueStats = fatigueQueue.getStats()
        )
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
