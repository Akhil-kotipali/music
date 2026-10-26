package com.example.audioindexer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyEncryptionHelperTest {

    @Test
    fun testEncryptionAndDecryptionRoundTrip() {
        val originalKey = "sk-proj-testKey123456789ABCDEF"
        val encrypted = KeyEncryptionHelper.encrypt(originalKey)

        assertTrue("Encrypted key must start with prefix", encrypted.startsWith(KeyEncryptionHelper.ENCRYPTED_PREFIX))
        assertNotEquals("Ciphertext must not match raw key", originalKey, encrypted)

        val decrypted = KeyEncryptionHelper.decrypt(encrypted)
        assertEquals("Decrypted key must match original plaintext", originalKey, decrypted)
    }

    @Test
    fun testLegacyUnencryptedKeyIsPreserved() {
        // Test backward compatibility: existing database keys without enc prefix return as is
        val legacyKey = "AIzaSyLegacyKeyWithoutEncryption987"
        val result = KeyEncryptionHelper.decrypt(legacyKey)
        assertEquals("Legacy plaintext key must pass through unmodified", legacyKey, result)
    }

    @Test
    fun testBlankAndEmptyKeyHandling() {
        assertEquals("", KeyEncryptionHelper.encrypt(""))
        assertEquals("   ", KeyEncryptionHelper.encrypt("   "))
        assertEquals("", KeyEncryptionHelper.decrypt(""))
    }
}
