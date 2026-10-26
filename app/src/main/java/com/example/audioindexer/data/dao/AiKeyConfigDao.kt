package com.example.audioindexer.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.audioindexer.data.model.AiKeyConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface AiKeyConfigDao {

    @Query("SELECT * FROM ai_keys ORDER BY createdAt DESC")
    fun getAllKeys(): Flow<List<AiKeyConfig>>

    @Query("SELECT * FROM ai_keys ORDER BY createdAt DESC")
    suspend fun getAllKeysDirect(): List<AiKeyConfig>

    @Query("SELECT * FROM ai_keys WHERE provider = :provider AND isActive = 1 ORDER BY isRateLimited ASC, lastUsedTime ASC")
    suspend fun getAvailableKeysForProvider(provider: String): List<AiKeyConfig>

    @Query("SELECT * FROM ai_keys WHERE isActive = 1 ORDER BY isRateLimited ASC, lastUsedTime ASC")
    suspend fun getAllActiveKeys(): List<AiKeyConfig>

    @Query("SELECT * FROM ai_keys WHERE id = :id LIMIT 1")
    suspend fun getKeyById(id: Long): AiKeyConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(keyConfig: AiKeyConfig): Long

    @Update
    suspend fun update(keyConfig: AiKeyConfig)

    @Delete
    suspend fun delete(keyConfig: AiKeyConfig)

    @Query("UPDATE ai_keys SET isRateLimited = :isRateLimited, rateLimitResetTime = :resetTime WHERE id = :id")
    suspend fun updateRateLimit(id: Long, isRateLimited: Boolean, resetTime: Long)

    @Query("UPDATE ai_keys SET isActive = :isActive WHERE id = :id")
    suspend fun updateActiveStatus(id: Long, isActive: Boolean)

    @Query("DELETE FROM ai_keys WHERE id = :id")
    suspend fun deleteById(id: Long)
}
