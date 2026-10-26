package com.example.audioindexer.playback

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.hypot

/**
 * BeatDetectorManager
 *
 * Implements "Beat Mode": Real-time beat & bass detection via Android Visualizer.
 * Analyzes audio FFT energy in the bass frequency band (40Hz - 160Hz) and triggers
 * synchronized rhythmic tactile haptic feedback on the user's device.
 * Also includes rhythmic fallback if Visualizer is unavailable on certain hardware/OEMs.
 */
class BeatDetectorManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("beat_mode_prefs", Context.MODE_PRIVATE)

    var isBeatModeEnabled: Boolean = prefs.getBoolean("beat_mode_enabled", false)
        private set

    private var visualizer: Visualizer? = null
    var currentSessionId: Int = 0
        private set

    // Energy tracking for dynamic beat detection
    private var runningBassAverage = 0f
    private var runningVariance = 0f
    private var lastBeatTimestampMs = 0L
    private var lastFftReceivedTimestampMs = 0L

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Attaches visualizer to the active ExoPlayer audio session ID.
     */
    fun attachToAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (audioSessionId != currentSessionId) {
            releaseVisualizer()
            currentSessionId = audioSessionId
        }

        if (!isBeatModeEnabled) return
        if (visualizer != null) return

        startVisualizer(currentSessionId)
    }

    private fun startVisualizer(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        releaseVisualizer()

        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasAudioPermission) {
            Log.d("BeatDetectorManager", "RECORD_AUDIO not granted yet; will rely on fallback rhythm haptics.")
            return
        }

        try {
            val captureRange = Visualizer.getCaptureSizeRange()
            val size = captureRange[0].coerceAtLeast(128)

            visualizer = Visualizer(audioSessionId).apply {
                captureSize = size
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}

                        override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                            if (fft == null || !isBeatModeEnabled) return
                            lastFftReceivedTimestampMs = System.currentTimeMillis()
                            processFftBeat(fft)
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    false,
                    true
                )
                enabled = true
            }
            Log.d("BeatDetectorManager", "Visualizer attached successfully to audio session: $audioSessionId")
        } catch (e: Exception) {
            Log.w("BeatDetectorManager", "Visualizer attachment failed (using rhythmic fallback): ${e.message}")
        }
    }

    /**
     * Toggles Beat Mode on or off, persists the preference, and triggers immediate tactile feedback.
     */
    fun setBeatModeEnabled(enabled: Boolean) {
        isBeatModeEnabled = enabled
        prefs.edit().putBoolean("beat_mode_enabled", enabled).apply()

        if (enabled) {
            // Immediate physical confirmation buzz so user knows haptics are active
            triggerHapticPulse(forceStrong = true)

            if (currentSessionId > 0) {
                startVisualizer(currentSessionId)
            }
        } else {
            releaseVisualizer()
        }
    }

    /**
     * Called periodically during active playback. If Visualizer is not receiving FFT data
     * (e.g., emulator or OEM restriction), pulses rhythmic haptics on beat intervals.
     */
    fun onPlaybackTick(currentPositionMs: Long) {
        if (!isBeatModeEnabled) return
        val now = System.currentTimeMillis()

        // If Visualizer hasn't provided FFT data in the last 1000ms, use rhythmic tempo pulses (~120 BPM = 500ms)
        if (now - lastFftReceivedTimestampMs > 1000L) {
            if (now - lastBeatTimestampMs >= 480L) {
                lastBeatTimestampMs = now
                triggerHapticPulse(forceStrong = false)
            }
        }
    }

    private fun processFftBeat(fft: ByteArray) {
        // FFT components: byte[0]=DC, byte[1]=Nyquist
        // Frequency bins: bin 1 to bin 6 correspond to low sub-bass & bass kicks
        var bassEnergy = 0f
        val numBassBins = (fft.size / 2).coerceAtMost(8)

        for (i in 1 until numBassBins) {
            val real = fft[2 * i].toFloat()
            val imag = fft[2 * i + 1].toFloat()
            bassEnergy += hypot(real, imag)
        }

        val now = System.currentTimeMillis()

        // Dynamic exponential moving average
        if (runningBassAverage == 0f) {
            runningBassAverage = bassEnergy
            return
        }

        val alpha = 0.08f
        val diff = kotlin.math.abs(bassEnergy - runningBassAverage)
        runningBassAverage = (1f - alpha) * runningBassAverage + alpha * bassEnergy
        runningVariance = (1f - alpha) * runningVariance + alpha * diff

        // Dynamic beat threshold with sensitive floor for wide dynamic range
        val threshold = runningBassAverage + 1.15f * runningVariance
        val minFloor = 14f

        // Debounce: Minimum 140ms between beat haptics (supports up to ~180 BPM music)
        if (bassEnergy > threshold && bassEnergy > minFloor && (now - lastBeatTimestampMs >= 140L)) {
            lastBeatTimestampMs = now
            triggerHapticPulse()
        }
    }

    /**
     * Triggers tactile physical haptic pulse across Android versions.
     */
    fun triggerHapticPulse(forceStrong: Boolean = false) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        val duration = if (forceStrong) 48L else 30L
        val amplitude = if (forceStrong) 255 else 200

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(duration, amplitude)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val attrs = VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_MEDIA)
                        .build()
                    vib.vibrate(effect, attrs)
                } else {
                    val audioAttrs = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                    vib.vibrate(effect, audioAttrs)
                }
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(duration)
            }
        } catch (_: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                    vib.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(duration)
                }
            } catch (_: Exception) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun releaseVisualizer() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Exception) {}
        visualizer = null
    }

    fun release() {
        releaseVisualizer()
        currentSessionId = 0
    }
}
