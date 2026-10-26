package com.example.audioindexer.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(
    tableName = "ai_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["provider"]),
        Index(value = ["status"])
    ]
)
data class AiLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val provider: String, // "GEMINI", "GROQ", "OPENAI"
    val model: String,
    val apiKeyMasked: String,
    val requestPrompt: String,
    val responseBody: String,
    val extractedCategory: String?,
    val status: String, // "SUCCESS", "RATE_LIMITED", "ERROR", "ROTATED_KEY"
    val durationMs: Long,
    val songId: Long? = null,
    val songTitle: String? = null,
    val errorMessage: String? = null
) {
    fun toExportString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        return buildString {
            appendLine("==================================================================")
            appendLine("LOG ID: #$id | DATE: ${sdf.format(Date(timestamp))}")
            appendLine("PROVIDER: $provider | MODEL: $model | STATUS: $status")
            appendLine("KEY: $apiKeyMasked | DURATION: ${durationMs}ms")
            if (songTitle != null) {
                appendLine("SONG: $songTitle (ID: $songId)")
            }
            if (extractedCategory != null) {
                appendLine("EXTRACTED CATEGORY: $extractedCategory")
            }
            if (errorMessage != null) {
                appendLine("ERROR: $errorMessage")
            }
            appendLine("--- REQUEST PROMPT ---")
            appendLine(requestPrompt)
            appendLine("--- RESPONSE BODY ---")
            appendLine(responseBody)
            appendLine("==================================================================")
            appendLine()
        }
    }
}
