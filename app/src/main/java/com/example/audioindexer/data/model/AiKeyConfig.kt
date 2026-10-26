package com.example.audioindexer.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AiProvider(val displayName: String, val defaultModel: String) {
    GEMINI("Google Gemini", "gemini-1.5-flash"),
    GROQ("Groq", "llama-3.3-70b-versatile"),
    OPENAI("OpenAI", "gpt-4o-mini")
}

@Entity(
    tableName = "ai_keys",
    indices = [
        Index(value = ["provider"]),
        Index(value = ["isActive"])
    ]
)
data class AiKeyConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val provider: String, // "GEMINI", "GROQ", "OPENAI"
    val label: String,
    val apiKey: String,
    val model: String = "",
    val isActive: Boolean = true,
    val isRateLimited: Boolean = false,
    val rateLimitResetTime: Long = 0L,
    val errorCount: Int = 0,
    val lastError: String? = null,
    val lastUsedTime: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
) {
    val maskedKey: String
        get() {
            if (apiKey.length <= 8) return "••••••••"
            return apiKey.take(4) + "••••••••" + apiKey.takeLast(4)
        }
}
