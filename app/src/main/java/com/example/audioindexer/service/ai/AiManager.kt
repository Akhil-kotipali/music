package com.example.audioindexer.service.ai

import android.content.Context
import com.example.audioindexer.data.dao.AiKeyConfigDao
import com.example.audioindexer.data.dao.AiLogDao
import com.example.audioindexer.data.dao.SongDao
import com.example.audioindexer.data.model.AiKeyConfig
import com.example.audioindexer.data.model.AiLog
import com.example.audioindexer.data.model.AiProvider
import com.example.audioindexer.data.model.Song
import com.example.audioindexer.service.AudioSnippetExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed class KeyHealthResult {
    data class Success(val message: String, val latencyMs: Long) : KeyHealthResult()
    data class RateLimited(val message: String) : KeyHealthResult()
    data class Error(val message: String) : KeyHealthResult()
}

data class CategorizationResult(
    val songId: Long,
    val title: String,
    val category: String,
    val provider: String,
    val durationMs: Long
)

class AiManager(
    private val context: Context,
    private val keyConfigDao: AiKeyConfigDao,
    private val logDao: AiLogDao,
    private val songDao: SongDao
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    companion object {
        val ALLOWED_CATEGORIES = listOf(
            "Love",
            "Motivational",
            "Sad",
            "High Energy",
            "Relaxed",
            "Focus"
        )

        const val STRICT_SYSTEM_PROMPT =
            "Categorize this song into exactly one primary mood/genre tag from this list: Love, Motivational, Sad, High Energy, Relaxed, Focus."
    }

    /**
     * Categorizes a single song by extracting metadata and a 30-second lyric/audio snippet.
     * Automatically handles key rotation and rate limit fallbacks.
     */
    suspend fun categorizeSong(song: Song): String? = withContext(Dispatchers.IO) {
        val audioFile = File(song.filePath)
        val snippet = if (!song.lyricsSnippet.isNullOrBlank() && !song.lyricsSnippet.startsWith("Placeholder")) {
            song.lyricsSnippet
        } else {
            AudioSnippetExtractor.extractThirtySecondSnippet(audioFile)
        }

        val prompt = buildString {
            appendLine("Title: ${song.title}")
            appendLine("Artist: ${song.artist}")
            appendLine("Album: ${song.album}")
            appendLine("30-Second Text/Audio Snippet:")
            appendLine(snippet)
            appendLine()
            appendLine("Instruction: $STRICT_SYSTEM_PROMPT Reply ONLY with the chosen tag name.")
        }

        // Fetch candidate active keys, prioritizing non-rate-limited keys
        val activeKeys = keyConfigDao.getAllActiveKeys()
        if (activeKeys.isEmpty()) {
            logDao.insert(
                AiLog(
                    provider = "NONE",
                    model = "none",
                    apiKeyMasked = "N/A",
                    requestPrompt = prompt,
                    responseBody = "No active AI API keys configured.",
                    extractedCategory = null,
                    status = "ERROR",
                    durationMs = 0L,
                    songId = song.id,
                    songTitle = song.title,
                    errorMessage = "No active API keys found. Please configure Gemini, Groq, or OpenAI keys."
                )
            )
            return@withContext null
        }

        var matchedCategory: String? = null

        // Try keys in sequence; rotate automatically if rate-limited or error
        for (key in activeKeys) {
            val startTime = System.currentTimeMillis()
            try {
                val responseText = executeAiRequest(key, prompt)
                val duration = System.currentTimeMillis() - startTime
                val parsedCategory = parseCategory(responseText)

                if (parsedCategory != null) {
                    matchedCategory = parsedCategory
                    // Update key usage stats
                    keyConfigDao.update(
                        key.copy(
                            isRateLimited = false,
                            lastUsedTime = System.currentTimeMillis(),
                            errorCount = 0,
                            lastError = null
                        )
                    )

                    // Update song table in Room
                    songDao.updateAiCategory(song.id, parsedCategory)

                    // Log success
                    logDao.insert(
                        AiLog(
                            provider = key.provider,
                            model = resolveModel(key),
                            apiKeyMasked = key.maskedKey,
                            requestPrompt = prompt,
                            responseBody = responseText,
                            extractedCategory = parsedCategory,
                            status = "SUCCESS",
                            durationMs = duration,
                            songId = song.id,
                            songTitle = song.title
                        )
                    )
                    break
                } else {
                    // Response did not contain a valid category tag
                    logDao.insert(
                        AiLog(
                            provider = key.provider,
                            model = resolveModel(key),
                            apiKeyMasked = key.maskedKey,
                            requestPrompt = prompt,
                            responseBody = responseText,
                            extractedCategory = null,
                            status = "ERROR",
                            durationMs = duration,
                            songId = song.id,
                            songTitle = song.title,
                            errorMessage = "Response did not match allowed tags: Love, Motivational, Sad, High Energy, Relaxed, Focus."
                        )
                    )
                }
            } catch (e: RateLimitException) {
                val duration = System.currentTimeMillis() - startTime
                // Flag key as rate-limited and rotate to next key
                keyConfigDao.updateRateLimit(key.id, true, System.currentTimeMillis() + 60_000L)
                logDao.insert(
                    AiLog(
                        provider = key.provider,
                        model = resolveModel(key),
                        apiKeyMasked = key.maskedKey,
                        requestPrompt = prompt,
                        responseBody = e.message ?: "Rate limited (HTTP 429)",
                        extractedCategory = null,
                        status = "RATE_LIMITED",
                        durationMs = duration,
                        songId = song.id,
                        songTitle = song.title,
                        errorMessage = "Rate limit reached (429). Rotating to alternative key/provider."
                    )
                )
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                keyConfigDao.update(
                    key.copy(
                        errorCount = key.errorCount + 1,
                        lastError = e.localizedMessage
                    )
                )
                logDao.insert(
                    AiLog(
                        provider = key.provider,
                        model = resolveModel(key),
                        apiKeyMasked = key.maskedKey,
                        requestPrompt = prompt,
                        responseBody = e.localizedMessage ?: "Network or API error",
                        extractedCategory = null,
                        status = "ERROR",
                        durationMs = duration,
                        songId = song.id,
                        songTitle = song.title,
                        errorMessage = e.message
                    )
                )
            }
        }

        matchedCategory
    }

    /**
     * Batch categorizes multiple songs, reporting progress callbacks.
     */
    suspend fun batchCategorize(
        songs: List<Song>,
        onProgress: (current: Int, total: Int, lastResult: CategorizationResult?) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        var processedCount = 0
        for ((index, song) in songs.withIndex()) {
            val startTime = System.currentTimeMillis()
            val category = categorizeSong(song)
            val duration = System.currentTimeMillis() - startTime

            val result = category?.let {
                CategorizationResult(
                    songId = song.id,
                    title = song.title,
                    category = it,
                    provider = "AI",
                    durationMs = duration
                )
            }

            if (category != null) {
                processedCount++
            }
            onProgress(index + 1, songs.size, result)
        }
        processedCount
    }

    /**
     * Tests health and rate-limiting status of an API key
     */
    suspend fun testKeyHealth(keyConfig: AiKeyConfig): KeyHealthResult = withContext(Dispatchers.IO) {
        val testPrompt = "Ping. Answer with exactly one word: OK"
        val start = System.currentTimeMillis()
        try {
            val response = executeAiRequest(keyConfig, testPrompt)
            val duration = System.currentTimeMillis() - start
            keyConfigDao.updateRateLimit(keyConfig.id, false, 0L)
            KeyHealthResult.Success("Key valid. Response: ${response.trim()} (${duration}ms)", duration)
        } catch (e: RateLimitException) {
            keyConfigDao.updateRateLimit(keyConfig.id, true, System.currentTimeMillis() + 60_000L)
            KeyHealthResult.RateLimited("Key rate-limited (429): ${e.message}")
        } catch (e: Exception) {
            KeyHealthResult.Error("Key error: ${e.message}")
        }
    }

    /**
     * Executes REST call against Gemini, Groq, or OpenAI
     */
    private fun executeAiRequest(key: AiKeyConfig, prompt: String): String {
        return when (key.provider.uppercase(Locale.ROOT)) {
            "GEMINI" -> callGemini(key, prompt)
            "GROQ" -> callGroq(key, prompt)
            "OPENAI" -> callOpenAi(key, prompt)
            else -> throw IllegalArgumentException("Unknown AI provider: ${key.provider}")
        }
    }

    private fun callGemini(key: AiKeyConfig, prompt: String): String {
        val model = if (key.model.isNotBlank()) key.model else "gemini-1.5-flash"
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${key.apiKey}"

        val jsonBody = JSONObject().apply {
            val partsArray = JSONArray().apply {
                put(JSONObject().put("text", prompt))
            }
            val contentsArray = JSONArray().apply {
                put(JSONObject().put("parts", partsArray))
            }
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("maxOutputTokens", 50)
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody(mediaType))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()

        if (response.code == 429) {
            throw RateLimitException("Gemini Rate Limit Exceeded (HTTP 429): $responseBody")
        }

        if (!response.isSuccessful) {
            throw Exception("Gemini API Error (HTTP ${response.code}): $responseBody")
        }

        val json = JSONObject(responseBody)
        val candidates = json.optJSONArray("candidates") ?: return ""
        if (candidates.length() > 0) {
            val content = candidates.getJSONObject(0).optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            if (parts != null && parts.length() > 0) {
                return parts.getJSONObject(0).optString("text", "")
            }
        }
        return ""
    }

    private fun callGroq(key: AiKeyConfig, prompt: String): String {
        val model = if (key.model.isNotBlank()) key.model else "llama-3.3-70b-versatile"
        val url = "https://api.groq.com/openai/v1/chat/completions"

        val jsonBody = JSONObject().apply {
            put("model", model)
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", STRICT_SYSTEM_PROMPT)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            put("messages", messages)
            put("temperature", 0.1)
            put("max_tokens", 30)
        }

        return callOpenAiCompatible(url, key.apiKey, jsonBody, "Groq")
    }

    private fun callOpenAi(key: AiKeyConfig, prompt: String): String {
        val model = if (key.model.isNotBlank()) key.model else "gpt-4o-mini"
        val url = "https://api.openai.com/v1/chat/completions"

        val jsonBody = JSONObject().apply {
            put("model", model)
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", STRICT_SYSTEM_PROMPT)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            put("messages", messages)
            put("temperature", 0.1)
            put("max_tokens", 30)
        }

        return callOpenAiCompatible(url, key.apiKey, jsonBody, "OpenAI")
    }

    private fun callOpenAiCompatible(url: String, apiKey: String, body: JSONObject, providerName: String): String {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody(mediaType))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()

        if (response.code == 429) {
            throw RateLimitException("$providerName Rate Limit Exceeded (HTTP 429): $responseBody")
        }

        if (!response.isSuccessful) {
            throw Exception("$providerName API Error (HTTP ${response.code}): $responseBody")
        }

        val json = JSONObject(responseBody)
        val choices = json.optJSONArray("choices") ?: return ""
        if (choices.length() > 0) {
            val message = choices.getJSONObject(0).optJSONObject("message")
            return message?.optString("content", "") ?: ""
        }
        return ""
    }

    /**
     * Parses the strict category response
     */
    private fun parseCategory(rawResponse: String): String? {
        val clean = rawResponse.trim()
            .replace("*", "")
            .replace("#", "")
            .replace("\"", "")
            .replace("'", "")
            .trim()

        for (category in ALLOWED_CATEGORIES) {
            if (clean.equals(category, ignoreCase = true)) {
                return category
            }
        }
        // Substring search fallback
        for (category in ALLOWED_CATEGORIES) {
            if (clean.contains(category, ignoreCase = true)) {
                return category
            }
        }
        return null
    }

    private fun resolveModel(key: AiKeyConfig): String {
        if (key.model.isNotBlank()) return key.model
        return when (key.provider.uppercase()) {
            "GEMINI" -> "gemini-1.5-flash"
            "GROQ" -> "llama-3.3-70b-versatile"
            "OPENAI" -> "gpt-4o-mini"
            else -> "default"
        }
    }

    /**
     * Exports all AI logs into a text file and returns the File object.
     */
    suspend fun exportLogsToTextFile(): File = withContext(Dispatchers.IO) {
        val logs = logDao.getAllLogsDirect()
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val filename = "ai_categorization_logs_${sdf.format(Date())}.txt"

        val exportDir = File(context.getExternalFilesDir(null), "ai_logs")
        if (!exportDir.exists()) exportDir.mkdirs()

        val file = File(exportDir, filename)
        FileOutputStream(file).use { fos ->
            val header = buildString {
                appendLine("==================================================================")
                appendLine("AUDIO INDEXER - AI INTERACTION AUDIT LOGS")
                appendLine("GENERATED AT: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                appendLine("TOTAL LOG ENTRIES: ${logs.size}")
                appendLine("ALLOWED TAGS: ${ALLOWED_CATEGORIES.joinToString(", ")}")
                appendLine("==================================================================")
                appendLine()
            }
            fos.write(header.toByteArray(Charsets.UTF_8))

            for (log in logs) {
                fos.write(log.toExportString().toByteArray(Charsets.UTF_8))
            }
        }
        file
    }
}

class RateLimitException(message: String) : Exception(message)
