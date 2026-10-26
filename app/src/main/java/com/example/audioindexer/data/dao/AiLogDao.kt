package com.example.audioindexer.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.audioindexer.data.model.AiLog
import kotlinx.coroutines.flow.Flow

@Dao
interface AiLogDao {

    @Query("SELECT * FROM ai_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<AiLog>>

    @Query("SELECT * FROM ai_logs ORDER BY timestamp DESC")
    suspend fun getAllLogsDirect(): List<AiLog>

    @Query("SELECT * FROM ai_logs WHERE provider = :provider ORDER BY timestamp DESC")
    fun getLogsByProvider(provider: String): Flow<List<AiLog>>

    @Query("SELECT * FROM ai_logs WHERE status = :status ORDER BY timestamp DESC")
    fun getLogsByStatus(status: String): Flow<List<AiLog>>

    @Query("SELECT COUNT(*) FROM ai_logs")
    fun getLogCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AiLog): Long

    @Delete
    suspend fun delete(log: AiLog)

    @Query("DELETE FROM ai_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ai_logs")
    suspend fun clearAll()
}
