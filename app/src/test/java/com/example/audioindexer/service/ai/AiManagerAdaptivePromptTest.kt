package com.example.audioindexer.service.ai

import com.example.audioindexer.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiManagerAdaptivePromptTest {

    @Test
    fun testAllowedCategories_strictlyEnforced() {
        val allowed = AiManager.ALLOWED_CATEGORIES
        assertEquals(6, allowed.size)
        assertTrue(allowed.contains("Love"))
        assertTrue(allowed.contains("Motivational"))
        assertTrue(allowed.contains("Sad"))
        assertTrue(allowed.contains("High Energy"))
        assertTrue(allowed.contains("Relaxed"))
        assertTrue(allowed.contains("Focus"))
    }

    @Test
    fun testIsLengthyPromptError_identifiesLengthExceptions() {
        // HTTP 413 & typical provider error messages
        assertTrue(AiManager.isLengthyPromptError("HTTP 413 Payload Too Large"))
        assertTrue(AiManager.isLengthyPromptError("Error: too much lengthy prompt received"))
        assertTrue(AiManager.isLengthyPromptError("The request is too long to process"))
        assertTrue(AiManager.isLengthyPromptError("context_length_exceeded: maximum context length is 8192 tokens"))
        assertTrue(AiManager.isLengthyPromptError("Maximum context length exceeded"))
        assertTrue(AiManager.isLengthyPromptError("Requested token count exceeds budget"))
        assertTrue(AiManager.isLengthyPromptError("Token limit exceeded for this model"))
        assertTrue(AiManager.isLengthyPromptError("Prompt tokens exceeds maximum allowed"))

        // Unrelated errors should not match
        assertFalse(AiManager.isLengthyPromptError(null))
        assertFalse(AiManager.isLengthyPromptError("Rate limit reached (429)"))
        assertFalse(AiManager.isLengthyPromptError("Invalid API Key (401)"))
        assertFalse(AiManager.isLengthyPromptError("Internal Server Error (500)"))
    }

    @Test
    fun testParseCategory_canonicalAndSynonyms() {
        // Canonical
        assertEquals("Love", AiManager.parseCategory("Love"))
        assertEquals("Motivational", AiManager.parseCategory("motivational"))
        assertEquals("Sad", AiManager.parseCategory("Sad"))
        assertEquals("High Energy", AiManager.parseCategory("High Energy"))
        assertEquals("Relaxed", AiManager.parseCategory("relaxed"))
        assertEquals("Focus", AiManager.parseCategory("Focus"))

        // Hyphenated & Punctuated
        assertEquals("High Energy", AiManager.parseCategory("High-Energy"))
        assertEquals("High Energy", AiManager.parseCategory("**High-Energy**"))
        assertEquals("Love", AiManager.parseCategory("\"Romantic\""))
        assertEquals("Motivational", AiManager.parseCategory("Uplifting Anthem"))
        assertEquals("Sad", AiManager.parseCategory("Melancholic Sorrow"))
        assertEquals("Relaxed", AiManager.parseCategory("Chill Lo-Fi"))
        assertEquals("Focus", AiManager.parseCategory("[Study / Instrumental]"))

        // Invalid / Hallucinated tags
        assertNull(AiManager.parseCategory("Random Nonsense 1234"))
    }

    @Test
    fun testParsePartResponse_variousFormatsAndMarkdown() {
        val songs = listOf(
            Song(id = 1, filePath = "/path/1.mp3", title = "Midnight Song", artist = "Artist A", album = "Album A"),
            Song(id = 2, filePath = "/path/2.mp3", title = "Power Up", artist = "Artist B", album = "Album B"),
            Song(id = 3, filePath = "/path/3.mp3", title = "Tears in Rain", artist = "Artist C", album = "Album C"),
            Song(id = 4, filePath = "/path/4.mp3", title = "Deep Work", artist = "Artist D", album = "Album D")
        )

        val llmResponse = """
            Here are the categorizations:
            [ID: 1]: Romantic
            **[ID: 2]**: High-Energy
            Song 3: Melancholic
            4. Focus
        """.trimIndent()

        val results = AiManager.parsePartResponse(llmResponse, songs)
        assertEquals(4, results.size)
        assertEquals("Love", results[1L])
        assertEquals("High Energy", results[2L])
        assertEquals("Sad", results[3L])
        assertEquals("Focus", results[4L])
    }

    @Test
    fun testParseCategory_customUserDefinedCategories() {
        val customCategories = listOf("Synthwave", "Gym Workout", "Late Night Drive", "Meditation")

        // Exact and case-insensitive
        assertEquals("Synthwave", AiManager.parseCategory("synthwave", customCategories))
        assertEquals("Gym Workout", AiManager.parseCategory("Gym Workout", customCategories))
        assertEquals("Late Night Drive", AiManager.parseCategory("late night drive", customCategories))
        assertEquals("Meditation", AiManager.parseCategory("meditation", customCategories))

        // Contained in punctuation / text
        assertEquals("Synthwave", AiManager.parseCategory("**Synthwave**", customCategories))
        assertEquals("Gym Workout", AiManager.parseCategory("[Gym Workout / Cardio]", customCategories))

        // When standard categories are not in custom list, they shouldn't match
        assertNull(AiManager.parseCategory("Sad", customCategories))
    }

    @Test
    fun testParsePartResponse_withCustomCategories() {
        val songs = listOf(
            Song(id = 10, filePath = "/path/10.mp3", title = "Retro Cyber", artist = "Artist X", album = "Album X"),
            Song(id = 20, filePath = "/path/20.mp3", title = "Beast Mode", artist = "Artist Y", album = "Album Y")
        )
        val customCategories = listOf("Synthwave", "Gym Workout")
        val response = """
            [ID: 10]: Synthwave
            [ID: 20]: Gym Workout
        """.trimIndent()

        val results = AiManager.parsePartResponse(response, songs, customCategories)
        assertEquals("Synthwave", results[10L])
        assertEquals("Gym Workout", results[20L])
    }
}
