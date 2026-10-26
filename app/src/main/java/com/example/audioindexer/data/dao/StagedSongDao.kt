package com.example.audioindexer.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.audioindexer.data.model.StagedSong
import kotlinx.coroutines.flow.Flow

@Dao
interface StagedSongDao {

    @Query("SELECT * FROM staged_songs ORDER BY detectedAt DESC")
    fun getAllStagedSongs(): Flow<List<StagedSong>>

    @Query("SELECT * FROM staged_songs ORDER BY detectedAt DESC")
    suspend fun getAllStagedSongsDirect(): List<StagedSong>

    @Query("SELECT * FROM staged_songs WHERE filePath = :filePath LIMIT 1")
    suspend fun getStagedSongByPath(filePath: String): StagedSong?

    @Query("SELECT filePath FROM staged_songs")
    suspend fun getAllStagedFilePaths(): List<String>

    @Query("SELECT COUNT(*) FROM staged_songs")
    fun getStagedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM staged_songs")
    suspend fun getStagedCountDirect(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(stagedSong: StagedSong): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(stagedSongs: List<StagedSong>): List<Long>

    @Update
    suspend fun update(stagedSong: StagedSong)

    @Delete
    suspend fun delete(stagedSong: StagedSong)

    @Query("DELETE FROM staged_songs WHERE filePath = :filePath")
    suspend fun deleteByPath(filePath: String): Int

    @Query("DELETE FROM staged_songs")
    suspend fun deleteAll()
}
