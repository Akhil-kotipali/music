package com.example.audioindexer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AudioClassifierTest {

    @Test
    fun testWhatsAppVoiceNotes_detectedAsVoiceNote() {
        val file1 = File("/storage/emulated/0/WhatsApp/Media/WhatsApp Audio/AUD-20230501-WA0001.mp3")
        val res1 = AudioClassifier.classify(file1, durationMs = 12000L, fileSize = 80000L, title = null, artist = null, album = null)

        assertFalse(res1.isSong)
        assertEquals(AudioClassification.VOICE_NOTE, res1.type)

        val file2 = File("/storage/emulated/0/Downloads/PTT-20240115-WA0009.opus")
        val res2 = AudioClassifier.classify(file2, durationMs = 8000L, fileSize = 45000L, title = null, artist = null, album = null)

        assertFalse(res2.isSong)
        assertEquals(AudioClassification.VOICE_NOTE, res2.type)
    }

    @Test
    fun testCallRecordings_detectedAsCallRecording() {
        val file = File("/storage/emulated/0/CallRecordings/rec_20230910_112233.m4a")
        val res = AudioClassifier.classify(file, durationMs = 180000L, fileSize = 2000000L, title = null, artist = null, album = null)

        assertFalse(res.isSong)
        assertEquals(AudioClassification.CALL_RECORDING, res.type)
    }

    @Test
    fun testRingtonesAndAlarms_detectedAsRingtone() {
        val file = File("/system/media/audio/ringtones/ClassicBell.ogg")
        val res = AudioClassifier.classify(file, durationMs = 15000L, fileSize = 250000L, title = "Classic Bell", artist = null, album = null)

        assertFalse(res.isSong)
        assertEquals(AudioClassification.RINGTONE_ALARM, res.type)
    }

    @Test
    fun testShortSfxWithoutTags_detectedAsShortSfx() {
        val file = File("/storage/emulated/0/Downloads/click.wav")
        val res = AudioClassifier.classify(file, durationMs = 3000L, fileSize = 35000L, title = "click", artist = "Unknown Artist", album = null)

        assertFalse(res.isSong)
        assertEquals(AudioClassification.SHORT_SFX, res.type)
    }

    @Test
    fun testLongPodcastWithoutArtist_detectedAsPodcast() {
        val file = File("/storage/emulated/0/Downloads/Lecture_Episode_45.mp3")
        val res = AudioClassifier.classify(file, durationMs = 3600000L, fileSize = 80000000L, title = "Lecture 45", artist = null, album = null)

        assertFalse(res.isSong)
        assertEquals(AudioClassification.PODCAST_SPEECH, res.type)
    }

    @Test
    fun testGenuineMusicalTrack_detectedAsMusic() {
        val file = File("/storage/emulated/0/Music/Coldplay - Yellow.mp3")
        val res = AudioClassifier.classify(
            file = file,
            durationMs = 269000L,
            fileSize = 8500000L,
            title = "Yellow",
            artist = "Coldplay",
            album = "Parachutes"
        )

        assertTrue(res.isSong)
        assertEquals(AudioClassification.MUSIC, res.type)
    }
}
