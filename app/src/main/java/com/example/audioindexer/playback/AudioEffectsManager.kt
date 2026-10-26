package com.example.audioindexer.playback

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log

/**
 * AudioEffectsManager
 *
 * Direct interface to Android's native audio effects pipeline on ExoPlayer's audioSessionId.
 * Provides high-impact Bass Boost (up to 1000 strength) and a 5-band Equalizer with deep bass presets.
 */
class AudioEffectsManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("audio_effects_prefs", Context.MODE_PRIVATE)

    private var bassBoost: BassBoost? = null
    private var equalizer: Equalizer? = null
    private var currentSessionId: Int = 0

    var currentPreset: String = prefs.getString("sound_preset", "Mega Bass") ?: "Mega Bass"
        private set

    var bassStrength: Int = prefs.getInt("bass_strength", 850) // 0 to 1000
        private set

    var isBassBoostEnabled: Boolean = prefs.getBoolean("bass_boost_enabled", true)
        private set

    companion object {
        const val PRESET_MEGA_BASS = "Mega Bass"
        const val PRESET_DEEP_BASS = "Deep Bass"
        const val PRESET_BASS_BOOST = "Bass Boost"
        const val PRESET_ELECTRONIC = "Electronic"
        const val PRESET_ROCK = "Rock"
        const val PRESET_VOCAL = "Vocal"
        const val PRESET_BALANCED = "Balanced"

        val AVAILABLE_PRESETS = listOf(
            PRESET_MEGA_BASS,
            PRESET_DEEP_BASS,
            PRESET_BASS_BOOST,
            PRESET_ELECTRONIC,
            PRESET_ROCK,
            PRESET_VOCAL,
            PRESET_BALANCED
        )
    }

    /**
     * Attaches effects to the given ExoPlayer audio session ID.
     */
    fun attachToAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == currentSessionId) return
        release()
        currentSessionId = audioSessionId

        try {
            // 1. Initialize BassBoost
            bassBoost = BassBoost(0, audioSessionId).apply {
                if (strengthSupported) {
                    setStrength(bassStrength.toShort())
                }
                enabled = isBassBoostEnabled
            }
        } catch (e: Exception) {
            Log.w("AudioEffectsManager", "BassBoost initialization error: ${e.message}")
        }

        try {
            // 2. Initialize Equalizer
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
            applyPresetInternal(currentPreset)
        } catch (e: Exception) {
            Log.w("AudioEffectsManager", "Equalizer initialization error: ${e.message}")
        }
    }

    /**
     * Sets Bass Boost strength (0 to 1000).
     */
    fun setBassBoostStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        bassStrength = clamped
        prefs.edit().putInt("bass_strength", clamped).apply()

        try {
            bassBoost?.let {
                if (it.strengthSupported) {
                    it.setStrength(clamped.toShort())
                }
                it.enabled = clamped > 0 && isBassBoostEnabled
            }
        } catch (e: Exception) {
            Log.w("AudioEffectsManager", "Error setting bass strength: ${e.message}")
        }
    }

    /**
     * Alias for setBassBoostStrength
     */
    fun setBassStrength(strength: Int) = setBassBoostStrength(strength)

    /**
     * Enables or disables Bass Boost.
     */
    fun toggleBassBoost(enable: Boolean) {
        isBassBoostEnabled = enable
        prefs.edit().putBoolean("bass_boost_enabled", enable).apply()

        try {
            bassBoost?.enabled = enable && bassStrength > 0
        } catch (e: Exception) {
            Log.w("AudioEffectsManager", "Error toggling bass boost: ${e.message}")
        }
    }

    /**
     * Applies a named equalizer preset with deep bass tuning.
     */
    fun applyPreset(presetName: String) {
        currentPreset = presetName
        prefs.edit().putString("sound_preset", presetName).apply()
        applyPresetInternal(presetName)
    }

    private fun applyPresetInternal(presetName: String) {
        val eq = equalizer ?: return
        val numBands = eq.numberOfBands.toInt()
        val minLevel = eq.bandLevelRange[0]
        val maxLevel = eq.bandLevelRange[1]

        fun boostBand(bandIndex: Int, ratio: Float) {
            if (bandIndex < numBands) {
                val level = if (ratio >= 0) {
                    (maxLevel * ratio).toInt().toShort()
                } else {
                    (minLevel.unaryMinus() * ratio).toInt().toShort()
                }
                eq.setBandLevel(bandIndex.toShort(), level.coerceIn(minLevel, maxLevel))
            }
        }

        try {
            when (presetName) {
                PRESET_MEGA_BASS -> {
                    // Maximum low-end impact: +100% on lowest band, +75% on second band
                    boostBand(0, 1.0f)
                    boostBand(1, 0.75f)
                    boostBand(2, 0.0f)
                    boostBand(3, 0.20f)
                    boostBand(4, 0.35f)
                    setBassBoostStrength(1000)
                }
                PRESET_DEEP_BASS -> {
                    boostBand(0, 0.85f)
                    boostBand(1, 0.60f)
                    boostBand(2, -0.10f)
                    boostBand(3, 0.15f)
                    boostBand(4, 0.25f)
                    setBassBoostStrength(800)
                }
                PRESET_BASS_BOOST -> {
                    boostBand(0, 0.65f)
                    boostBand(1, 0.45f)
                    boostBand(2, 0.0f)
                    boostBand(3, 0.10f)
                    boostBand(4, 0.20f)
                    setBassBoostStrength(600)
                }
                PRESET_ELECTRONIC -> {
                    boostBand(0, 0.70f)
                    boostBand(1, 0.40f)
                    boostBand(2, 0.10f)
                    boostBand(3, 0.35f)
                    boostBand(4, 0.50f)
                    setBassBoostStrength(700)
                }
                PRESET_ROCK -> {
                    boostBand(0, 0.50f)
                    boostBand(1, 0.30f)
                    boostBand(2, -0.10f)
                    boostBand(3, 0.25f)
                    boostBand(4, 0.45f)
                    setBassBoostStrength(500)
                }
                PRESET_VOCAL -> {
                    boostBand(0, -0.20f)
                    boostBand(1, 0.10f)
                    boostBand(2, 0.50f)
                    boostBand(3, 0.35f)
                    boostBand(4, 0.20f)
                    setBassBoostStrength(200)
                }
                else -> { // PRESET_BALANCED
                    for (i in 0 until numBands) {
                        boostBand(i, 0.0f)
                    }
                    setBassBoostStrength(0)
                }
            }
        } catch (e: Exception) {
            Log.w("AudioEffectsManager", "Error applying preset $presetName: ${e.message}")
        }
    }

    fun release() {
        try {
            bassBoost?.release()
            bassBoost = null
        } catch (_: Exception) {}
        try {
            equalizer?.release()
            equalizer = null
        } catch (_: Exception) {}
        currentSessionId = 0
    }
}
