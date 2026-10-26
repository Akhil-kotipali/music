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

        const val MAX_PART_SIZE = 4
        const val MAX_PROMPT_CHARS = 2600

        fun isLengthyPromptError(error: String?): Boolean {
            if (error == null) return false
            val lower = error.lowercase(Locale.ROOT)
            return lower.contains("too long") ||
                    lower.contains("lengthy") ||
                    lower.contains("too much lengthy") ||
                    lower.contains("context_length_exceeded") ||
                    lower.contains("maximum context length") ||
                    lower.contains("payload size exceeds") ||
                    lower.contains("request entity too large") ||
                    lower.contains("413") ||
                    lower.contains("prompt length") ||
                    (lower.contains("token") && (lower.contains("exceed") || lower.contains("limit") || lower.contains("maximum")))
        }
    }

    /**
     * Categorizes a single song by extracting metadata and a 30-second lyric/audio snippet.
     * Automatically handles key rotation, rate limit fallbacks, and prompt length condensation.
     */
    suspend fun categorizeSong(song: Song, maxSnippetChars: Int = 400): String? = withContext(Dispatchers.IO) {
        // Disallow non-music files (voice notes, ringtones) from AI categorization
        if (!song.isMusic) {
            return@withContext null
        }

        val audioFile = File(song.filePath)
        val rawSnippet = if (!song.lyricsSnippet.isNullOrBlank() && !song.lyricsSnippet.startsWith("Placeholder")) {
            song.lyricsSnippet
        } else {
            AudioSnippetExtractor.extractThirtySecondSnippet(audioFile)
        }
        val snippet = if (rawSnippet.length > maxSnippetChars) rawSnippet.take(maxSnippetChars) + "... [truncated]" else rawSnippet

        val prompt = buildString {
            appendLine("Title: ${song.title}")
            appendLine("Artist: ${song.artist}")
            appendLine("Album: ${song.album}")
            appendLine("30-Second Text/Audio Snippet:")
            appendLine(snippet)
            appendLine()
            appendLine("Instruction: $STRICT_SYSTEM_PROMPT Reply ONLY with the chosen tag name.")
        }

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

        for (key in activeKeys) {
            val startTime = System.currentTimeMillis()
            try {
                val responseText = executeAiRequest(key, prompt)
                val duration = System.currentTimeMillis() - startTime
                val parsedCategory = parseCategory(responseText)

                if (parsedCategory != null) {
                    matchedCategory = parsedCategory
                    keyConfigDao.update(
                        key.copy(
                            isRateLimited = false,
                            lastUsedTime = System.currentTimeMillis(),
                            errorCount = 0,
                            lastError = null
                        )
                    )

                    songDao.updateAiCategory(song.id, parsedCategory)

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
            } catch (e: LengthyPromptException) {
                // If single song prompt was flagged as too lengthy, retry once with highly condensed snippet
                if (maxSnippetChars > 120) {
                    return@withContext categorizeSong(song, maxSnippetChars = 100)
                }
                val duration = System.currentTimeMillis() - startTime
                logDao.insert(
                    AiLog(
                        provider = key.provider,
                        model = resolveModel(key),
                        apiKeyMasked = key.maskedKey,
                        requestPrompt = prompt,
                        responseBody = e.message ?: "Prompt too lengthy",
                        extractedCategory = null,
                        status = "LENGTHY_PROMPT",
                        durationMs = duration,
                        songId = song.id,
                        songTitle = song.title,
                        errorMessage = "Prompt too lengthy: ${e.message}"
                    )
                )
            } catch (e: RateLimitException) {
                val duration = System.currentTimeMillis() - startTime
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
                if (isLengthyPromptError(e.message) && maxSnippetChars > 120) {
                    return@withContext categorizeSong(song, maxSnippetChars = 100)
                }
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
     * Batch categorizes multiple songs.
     * If the prompt is too lengthy or the API returns a lengthy prompt error,
     * dynamically breaks the list into parts and sends parts only.
     */
    suspend fun batchCategorize(
        songs: List<Song>,
        onProgress: (current: Int, total: Int, lastResult: CategorizationResult?) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        // Only categorize genuine music tracks; exclude voice notes and ringtones
        val musicSongs = songs.filter { it.isMusic }
        if (musicSongs.isEmpty()) {
            return@withContext 0
        }

        var processedCount = 0
        var totalReported = 0
        val totalSongs = musicSongs.size

        // Break into initial parts
        val initialParts = musicSongs.chunked(MAX_PART_SIZE)

        for (part in initialParts) {
            val partResults = processPartWithAdaptiveSplitting(part)
            for (res in partResults) {
                totalReported++
                if (res != null) {
                    processedCount++
                }
                onProgress(totalReported, totalSongs, res)
            }
        }

        processedCount
    }

    /**
     * Recursively processes a part of songs.
     * If prompt length exceeds budget or the API throws a LengthyPromptException,
     * dynamically breaks the part into halves and sends each part separately.
     */
    private suspend fun processPartWithAdaptiveSplitting(part: List<Song>): List<CategorizationResult?> {
        if (part.isEmpty()) return emptyList()

        if (part.size == 1) {
            val song = part.first()
            val start = System.currentTimeMillis()
            val category = categorizeSong(song)
            val duration = System.currentTimeMillis() - start
            val result = category?.let {
                CategorizationResult(
                    songId = song.id,
                    title = song.title,
                    category = it,
                    provider = "AI",
                    durationMs = duration
                )
            }
            return listOf(result)
        }

        // Build multi-song prompt for the part
        val partPrompt = buildPartPrompt(part)

        // Check if pre-emptive length exceeds safety threshold
        if (partPrompt.length > MAX_PROMPT_CHARS) {
            // Break into smaller parts and send parts only
            val subParts = part.chunked(maxOf(1, part.size / 2))
            logDao.insert(
                AiLog(
                    provider = "SYSTEM",
                    model = "adaptive-chunker",
                    apiKeyMasked = "N/A",
                    requestPrompt = "Part of ${part.size} songs has prompt length ${partPrompt.length} chars (exceeds budget $MAX_PROMPT_CHARS).",
                    responseBody = "Breaking list into ${subParts.size} parts and sending parts only.",
                    extractedCategory = null,
                    status = "PROMPT_SPLIT",
                    durationMs = 0L,
                    errorMessage = "Prompt too lengthy. Dynamically split into sub-parts of ${subParts.first().size} songs."
                )
            )
            val combinedResults = mutableListOf<CategorizationResult?>()
            for (subPart in subParts) {
                combinedResults.addAll(processPartWithAdaptiveSplitting(subPart))
            }
            return combinedResults
        }

        // Attempt sending part to active AI key
        val activeKeys = keyConfigDao.getAllActiveKeys()
        if (activeKeys.isEmpty()) {
            return part.map { null }
        }

        var partSuccess = false
        val results = mutableListOf<CategorizationResult?>()

        for (key in activeKeys) {
            val start = System.currentTimeMillis()
            try {
                val responseText = executeAiRequest(key, partPrompt)
                val duration = System.currentTimeMillis() - start

                val parsedMap = parsePartResponse(responseText, part)
                if (parsedMap.isNotEmpty()) {
                    keyConfigDao.update(
                        key.copy(
                            isRateLimited = false,
                            lastUsedTime = System.currentTimeMillis(),
                            errorCount = 0,
                            lastError = null
                        )
                    )

                    logDao.insert(
                        AiLog(
                            provider = key.provider,
                            model = resolveModel(key),
                            apiKeyMasked = key.maskedKey,
                            requestPrompt = partPrompt,
                            responseBody = responseText,
                            extractedCategory = "${parsedMap.size} songs categorized in part",
                            status = "SUCCESS",
                            durationMs = duration
                        )
                    )

                    for (song in part) {
                        val cat = parsedMap[song.id]
                        if (cat != null) {
                            songDao.updateAiCategory(song.id, cat)
                            results.add(
                                CategorizationResult(
                                    songId = song.id,
                                    title = song.title,
                                    category = cat,
                                    provider = key.provider,
                                    durationMs = duration / part.size
                                )
                            )
                        } else {
                            // If a song wasn't parsed in the batch response, categorize individually
                            val individualCat = categorizeSong(song)
                            results.add(
                                individualCat?.let {
                                    CategorizationResult(song.id, song.title, it, key.provider, 0L)
                                }
                            )
                        }
                    }
                    partSuccess = true
                    break
                }
            } catch (e: LengthyPromptException) {
                // The API specifically stated prompt was too lengthy!
                // Break into smaller parts and send parts only!
                val subParts = part.chunked(maxOf(1, part.size / 2))
                logDao.insert(
                    AiLog(
                        provider = key.provider,
                        model = resolveModel(key),
                        apiKeyMasked = key.maskedKey,
                        requestPrompt = "API returned prompt too lengthy: ${e.message}",
                        responseBody = "Breaking list into ${subParts.size} parts and sending parts only.",
                        extractedCategory = null,
                        status = "PROMPT_SPLIT",
                        durationMs = System.currentTimeMillis() - start,
                        errorMessage = "API key reported prompt is too lengthy. Split into parts of size ${subParts.first().size}."
                    )
                )
                val combined = mutableListOf<CategorizationResult?>()
                for (subPart in subParts) {
                    combined.addAll(processPartWithAdaptiveSplitting(subPart))
                }
                return combined
            } catch (e: RateLimitException) {
                keyConfigDao.updateRateLimit(key.id, true, System.currentTimeMillis() + 60_000L)
            } catch (e: Exception) {
                if (isLengthyPromptError(e.message)) {
                    val subParts = part.chunked(maxOf(1, part.size / 2))
                    val combined = mutableListOf<CategorizationResult?>()
                    for (subPart in subParts) {
                        combined.addAll(processPartWithAdaptiveSplitting(subPart))
                    }
                    return combined
                }
            }
        }

        // If part batch failed across keys, fall back to individual song processing
        if (!partSuccess) {
            return part.map { song ->
                val cat = categorizeSong(song)
                cat?.let { CategorizationResult(song.id, song.title, it, "AI", 0L) }
            }
        }

        return results
    }

    private fun buildPartPrompt(songs: List<Song>): String = buildString {
        appendLine("Instruction: Categorize each numbered song below into exactly one primary mood/genre tag from: Love, Motivational, Sad, High Energy, Relaxed, Focus.")
        appendLine("Format your response strictly line by line with the song ID and chosen tag:")
        appendLine("[ID: <number>]: <Tag>")
        appendLine()
        for (song in songs) {
            val audioFile = File(song.filePath)
            val snippet = if (!song.lyricsSnippet.isNullOrBlank() && !song.lyricsSnippet.startsWith("Placeholder")) {
                song.lyricsSnippet
            } else {
                AudioSnippetExtractor.extractThirtySecondSnippet(audioFile)
            }
            val shortSnippet = if (snippet.length > 150) snippet.take(150) + "..." else snippet
            appendLine("[ID: ${song.id}] Title: \"${song.title}\" | Artist: \"${song.artist}\" | 30s Snippet: $shortSnippet")
        }
    }

    private fun parsePartResponse(responseText: String, songs: List<Song>): Map<Long, String> {
        val results = mutableMapOf<Long, String>()
        val lines = responseText.lines()

        for (song in songs) {
            val idPattern = Regex("(?i)\\[?ID:?\\s*${song.id}\\]?[:\\s-]+([A-Za-z\\s]+)")
            for (line in lines) {
                val match = idPattern.find(line)
                if (match != null) {
                    val candidate = match.groupValues[1].trim()
                    val parsed = parseCategory(candidate)
                    if (parsed != null) {
                        results[song.id] = parsed
                        break
                    }
                }
            }
        }
        return results
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

        if (response.code == 413 || isLengthyPromptError(responseBody)) {
            throw LengthyPromptException("Gemini Prompt Too Lengthy (HTTP ${response.code}): $responseBody")
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
            put("max_tokens", 80)
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
            put("max_tokens", 80)
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

        if (response.code == 413 || isLengthyPromptError(responseBody)) {
            throw LengthyPromptException("$providerName Prompt Too Lengthy (HTTP ${response.code}): $responseBody")
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
class LengthyPromptException(message: String) : Exception(message)
