package com.example.audioindexer.playback

import android.content.Context
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlin.math.hypot

/**
 * BeatDetectorManager
 *
 * Implements "Beat Mode": Real-time beat & bass detection via Android Visualizer.
 * Analyzes audio FFT energy in the bass frequency band (40Hz - 160Hz) and high-transient band,
 * and triggers synchronized rhythmic haptic feedback on the user's device.
 */
class BeatDetectorManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("beat_mode_prefs", Context.MODE_PRIVATE)

    var isBeatModeEnabled: Boolean = prefs.getBoolean("beat_mode_enabled", false)
        private set

    private var visualizer: Visualizer? = null
    private var currentSessionId: Int = 0

    // Energy tracking for dynamic beat detection
    private var runningBassAverage = 0f
    private var runningVariance = 0f
    private var lastBeatTimestampMs = 0L

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
        if (audioSessionId <= 0 || audioSessionId == currentSessionId) return
        releaseVisualizer()
        currentSessionId = audioSessionId

        if (!isBeatModeEnabled) return

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
                            processFftBeat(fft)
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    false,
                    true
                )
                enabled = true
            }
        } catch (e: Exception) {
            Log.w("BeatDetectorManager", "Visualizer attachment failed: ${e.message}")
        }
    }

    /**
     * Toggles Beat Mode on or off and persists the preference.
     */
    fun setBeatModeEnabled(enabled: Boolean) {
        isBeatModeEnabled = enabled
        prefs.edit().putBoolean("beat_mode_enabled", enabled).apply()

        if (enabled) {
            if (currentSessionId > 0 && visualizer == null) {
                attachToAudioSession(currentSessionId)
            } else {
                try {
                    visualizer?.enabled = true
                } catch (_: Exception) {}
            }
        } else {
            try {
                visualizer?.enabled = false
            } catch (_: Exception) {}
        }
    }

    private fun processFftBeat(fft: ByteArray) {
        // FFT components: byte[0]=DC, byte[1]=Nyquist
        // Real: fft[2*i], Imag: fft[2*i + 1]
        // Frequency bins: bin 1 to bin 5 correspond to low sub-bass & bass kicks
        var bassEnergy = 0f
        val numBassBins = (fft.size / 2).coerceAtMost(6)

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

        // Dynamic beat threshold: average energy + multiplier of variance (with a minimum energy floor)
        val threshold = runningBassAverage + 1.25f * runningVariance
        val minFloor = 35f // Minimum noise floor to prevent false triggers during silent parts

        // Debounce: Minimum 150ms between beat haptics (supports up to ~180 BPM music)
        if (bassEnergy > threshold && bassEnergy > minFloor && (now - lastBeatTimestampMs >= 150L)) {
            lastBeatTimestampMs = now
            triggerHapticPulse()
        }
    }

    private fun triggerHapticPulse() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Predefined crisp click for tactile beat precision
                val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                vib.vibrate(effect)
            } else {
                val effect = VibrationEffect.createOneShot(32L, VibrationEffect.DEFAULT_AMPLITUDE)
                vib.vibrate(effect)
            }
        } catch (_: Exception) {
            try {
                @Suppress("DEPRECATION")
                vib.vibrate(28L)
            } catch (_: Exception) {}
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
