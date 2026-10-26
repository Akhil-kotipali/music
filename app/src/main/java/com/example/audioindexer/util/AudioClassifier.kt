package com.example.audioindexer.util

import java.io.File
import java.util.Locale

enum class AudioClassification {
    MUSIC,           // Genuine musical track / song
    VOICE_NOTE,      // Voice note / voice memo (WhatsApp, Telegram, etc.)
    RINGTONE_ALARM,  // Ringtone, notification tone, alarm sound
    CALL_RECORDING,  // Phone call recording
    PODCAST_SPEECH,  // Long podcast, audiobook, lecture
    SHORT_SFX        // Short sound effect, UI click, system tone
}

data class AudioClassificationResult(
    val type: AudioClassification,
    val isSong: Boolean,
    val label: String,
    val reason: String
)

/**
 * AudioClassifier
 *
 * Intelligently analyzes an audio file's directory path, filename patterns, duration,
 * file size, and embedded ID3 tags to distinguish genuine songs/musical tracks from:
 * - WhatsApp & Telegram voice notes
 * - Call recordings & sound memos
 * - System ringtones, notification tones, and alarms
 * - Short sound effects and UI clicks
 * - Lengthy podcasts, lectures, and audiobooks
 */
object AudioClassifier {

    // Non-music directory keywords (case-insensitive substring checks)
    private val VOICE_NOTE_DIR_KEYWORDS = listOf(
        "whatsapp voice notes", "whatsapp audio", "telegram audio", "telegram voice",
        "voice notes", "voicenotes", "voice memos", "voicememos", "voice recorder",
        "voicerecorder", "sound recorder", "soundrecorder"
    )

    private val CALL_RECORDING_DIR_KEYWORDS = listOf(
        "call recordings", "call recording", "callrecorder", "call_rec", "calls", "phonerecord"
    )

    private val RINGTONE_ALARM_DIR_KEYWORDS = listOf(
        "ringtones", "ringtone", "notifications", "notification", "alarms", "alarm",
        "system/media", "ui/audio", "sound effects", "sfx", "tones"
    )

    private val PODCAST_DIR_KEYWORDS = listOf(
        "podcasts", "podcast", "audiobooks", "audiobook", "lectures", "lecture"
    )

    // Regex patterns for filenames
    private val WHATSAPP_VOICE_REGEX = Regex("^(PTT|AUD)-\\d{8}-WA\\d+.*", RegexOption.IGNORE_CASE)
    private val RECORDING_NAME_REGEX = Regex("^(rec|recording|call|voice|memo)[-_\\s]?\\d+.*", RegexOption.IGNORE_CASE)

    /**
     * Intelligently classifies an audio file.
     *
     * @return AudioClassificationResult with classification type and isSong boolean.
     */
    fun classify(
        file: File,
        durationMs: Long,
        fileSize: Long,
        title: String?,
        artist: String?,
        album: String?
    ): AudioClassificationResult {
        val pathLower = file.absolutePath.lowercase(Locale.ROOT)
        val nameWithoutExt = file.nameWithoutExtension.lowercase(Locale.ROOT)

        // 1. Directory Path Checks
        for (kw in VOICE_NOTE_DIR_KEYWORDS) {
            if (pathLower.contains(kw)) {
                return AudioClassificationResult(
                    type = AudioClassification.VOICE_NOTE,
                    isSong = false,
                    label = "Voice Note",
                    reason = "Found in voice notes directory: $kw"
                )
            }
        }

        for (kw in CALL_RECORDING_DIR_KEYWORDS) {
            if (pathLower.contains(kw)) {
                return AudioClassificationResult(
                    type = AudioClassification.CALL_RECORDING,
                    isSong = false,
                    label = "Call Recording",
                    reason = "Found in call recordings directory: $kw"
                )
            }
        }

        for (kw in RINGTONE_ALARM_DIR_KEYWORDS) {
            if (pathLower.contains(kw)) {
                return AudioClassificationResult(
                    type = AudioClassification.RINGTONE_ALARM,
                    isSong = false,
                    label = "Ringtone / Tone",
                    reason = "Found in ringtones/alarms directory: $kw"
                )
            }
        }

        for (kw in PODCAST_DIR_KEYWORDS) {
            if (pathLower.contains(kw)) {
                return AudioClassificationResult(
                    type = AudioClassification.PODCAST_SPEECH,
                    isSong = false,
                    label = "Podcast / Speech",
                    reason = "Found in podcast/audiobook directory: $kw"
                )
            }
        }

        // 2. Filename Pattern Checks
        if (WHATSAPP_VOICE_REGEX.matches(file.nameWithoutExtension)) {
            return AudioClassificationResult(
                type = AudioClassification.VOICE_NOTE,
                isSong = false,
                label = "Voice Note",
                reason = "Filename matches WhatsApp voice note format"
            )
        }

        if (RECORDING_NAME_REGEX.matches(nameWithoutExt)) {
            val hasExplicitArtist = !artist.isNullOrBlank() && !artist.equals("Unknown Artist", ignoreCase = true)
            if (!hasExplicitArtist) {
                return AudioClassificationResult(
                    type = AudioClassification.CALL_RECORDING,
                    isSong = false,
                    label = "Recording",
                    reason = "Recording filename prefix with no artist tag"
                )
            }
        }

        // 3. Duration & Size Thresholds
        // Very short audio (< 25s) without explicit music metadata is almost certainly a ringtone, notification, or sound effect
        if (durationMs in 1..24_999L) {
            val hasExplicitSongTags = !artist.isNullOrBlank() &&
                    !artist.equals("Unknown Artist", ignoreCase = true) &&
                    !album.isNullOrBlank() &&
                    !album.equals("Unknown Album", ignoreCase = true)
            if (!hasExplicitSongTags) {
                return AudioClassificationResult(
                    type = AudioClassification.SHORT_SFX,
                    isSong = false,
                    label = "Tone / SFX",
                    reason = "Duration < 25s (${durationMs / 1000}s) without distinct music metadata"
                )
            }
        }

        // Very long audio (> 30 minutes) without artist metadata is typical of podcasts/lectures
        if (durationMs > 1_800_000L) {
            val hasExplicitArtist = !artist.isNullOrBlank() && !artist.equals("Unknown Artist", ignoreCase = true)
            if (!hasExplicitArtist) {
                return AudioClassificationResult(
                    type = AudioClassification.PODCAST_SPEECH,
                    isSong = false,
                    label = "Podcast / Speech",
                    reason = "Duration > 30 mins (${durationMs / 60000}m) without music artist tags"
                )
            }
        }

        // Very small files (< 150 KB) without duration or tags are sound clips
        if (fileSize < 150 * 1024L && (durationMs <= 0 || durationMs < 30_000L)) {
            return AudioClassificationResult(
                type = AudioClassification.SHORT_SFX,
                isSong = false,
                label = "Short Sound Clip",
                reason = "File size < 150KB (${fileSize / 1024}KB) without music tags"
            )
        }

        // 4. Genuine Song / Musical Track
        return AudioClassificationResult(
            type = AudioClassification.MUSIC,
            isSong = true,
            label = "Music Track",
            reason = "Standard musical track criteria verified"
        )
    }
}
