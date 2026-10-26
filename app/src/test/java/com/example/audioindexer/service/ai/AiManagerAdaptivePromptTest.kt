package com.example.audioindexer.service.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
