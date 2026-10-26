package com.example.audioindexer.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.audioindexer.data.model.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM songs ORDER BY dateAdded DESC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE is_music = 1 ORDER BY dateAdded DESC")
    fun getMusicSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs ORDER BY dateAdded DESC")
    suspend fun getAllSongsDirect(): List<Song>

    @Query("SELECT * FROM songs WHERE is_music = 1 ORDER BY dateAdded DESC")
    suspend fun getMusicSongsDirect(): List<Song>

    @Query("SELECT * FROM songs WHERE filePath = :filePath LIMIT 1")
    suspend fun getSongByPath(filePath: String): Song?

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getSongById(id: Long): Song?

    @Query("SELECT filePath FROM songs")
    suspend fun getAllFilePaths(): List<String>

    @Query("SELECT COUNT(*) FROM songs")
    fun getSongCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM songs WHERE is_music = 1")
    fun getMusicSongCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM songs WHERE is_music = 0")
    fun getNonMusicCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getSongCountDirect(): Int

    @Query("SELECT * FROM songs WHERE is_music = 1 AND (ai_category IS NULL OR ai_category = '')")
    suspend fun getSongsWithoutAiCategory(): List<Song>

    @Query("SELECT COUNT(*) FROM songs WHERE is_music = 1 AND (ai_category IS NULL OR ai_category = '')")
    fun getUncategorizedCount(): Flow<Int>

    @Query("SELECT * FROM songs WHERE ai_category = :category ORDER BY title ASC")
    fun getSongsByCategory(category: String): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE ai_category = :category AND is_music = 1 ORDER BY title ASC")
    suspend fun getSongsByCategoryDirect(category: String): List<Song>

    @Query("UPDATE songs SET ai_category = :category WHERE id = :id")
    suspend fun updateAiCategory(id: Long, category: String)

    @Query("""
        SELECT * FROM songs 
        WHERE title LIKE '%' || :query || '%' 
           OR artist LIKE '%' || :query || '%' 
           OR album LIKE '%' || :query || '%' 
           OR filePath LIKE '%' || :query || '%'
           OR ai_category LIKE '%' || :query || '%'
        ORDER BY title ASC
    """)
    fun searchSongs(query: String): Flow<List<Song>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(song: Song): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(songs: List<Song>): List<Long>

    @Update
    suspend fun update(song: Song)

    @Delete
    suspend fun delete(song: Song)

    @Query("DELETE FROM songs WHERE filePath = :filePath")
    suspend fun deleteByPath(filePath: String): Int

    @Query("DELETE FROM songs")
    suspend fun deleteAll()
}
