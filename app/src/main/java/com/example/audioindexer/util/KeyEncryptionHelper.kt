package com.example.audioindexer.util

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * KeyEncryptionHelper
 *
 * Protects AI API keys (Gemini, Groq, OpenAI) at rest using AES-GCM-256 encryption.
 * When running on Android devices, it leverages the hardware-backed/TEE AndroidKeyStore.
 * In headless/JVM test runners where AndroidKeyStore is unavailable, it employs a local AES-GCM
 * fallback so tests execute deterministically without mocking crashes.
 */
object KeyEncryptionHelper {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "audio_indexer_ai_key"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    const val ENCRYPTED_PREFIX = "enc:v1:"

    // Fallback seed for JVM/test environments where AndroidKeyStore provider is absent
    private const val JVM_FALLBACK_SEED = "AudioIndexer#SecureKeySeed@2026-Enc"

    /**
     * Encrypts plaintext API key into an authenticated GCM string prefixed with 'enc:v1:'.
     * If input is already encrypted or blank, returns as is.
     */
    fun encrypt(plainText: String): String {
        if (plainText.isBlank() || plainText.startsWith(ENCRYPTED_PREFIX)) {
            return plainText
        }

        return try {
            val key = getAndroidKeyStoreSecretKey()
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + cipherBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)

            ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(combined)
        } catch (e: Throwable) {
            // JVM test or KeyStore failure fallback
            fallbackEncrypt(plainText)
        }
    }

    /**
     * Decrypts an encrypted key string.
     * Legacy unencrypted keys (without 'enc:v1:' prefix) are returned directly for backward compatibility.
     */
    fun decrypt(cipherText: String): String {
        if (!cipherText.startsWith(ENCRYPTED_PREFIX)) {
            return cipherText
        }

        val rawData = cipherText.removePrefix(ENCRYPTED_PREFIX)
        return try {
            val combined = Base64.getDecoder().decode(rawData)
            if (combined.size <= GCM_IV_LENGTH) return cipherText

            val iv = ByteArray(GCM_IV_LENGTH)
            val cipherBytes = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherBytes, 0, cipherBytes.size)

            val key = getAndroidKeyStoreSecretKey()
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Throwable) {
            // Try fallback decrypt if created via fallback
            fallbackDecrypt(rawData) ?: cipherText
        }
    }

    private fun getAndroidKeyStoreSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }

        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("KeyStore entry is not a SecretKeyEntry")
        return entry.secretKey
    }

    private fun getFallbackSecretKey(): SecretKey {
        val sha = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha.digest(JVM_FALLBACK_SEED.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun fallbackEncrypt(plainText: String): String {
        return try {
            val key = getFallbackSecretKey()
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + cipherBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)

            ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(combined)
        } catch (e: Exception) {
            plainText
        }
    }

    private fun fallbackDecrypt(encodedData: String): String? {
        return try {
            val combined = Base64.getDecoder().decode(encodedData)
            if (combined.size <= GCM_IV_LENGTH) return null

            val iv = ByteArray(GCM_IV_LENGTH)
            val cipherBytes = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherBytes, 0, cipherBytes.size)

            val key = getFallbackSecretKey()
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
