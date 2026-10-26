package com.example.audioindexer.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.audioindexer.data.model.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY name ASC")
    suspend fun getAllCategoriesDirect(): List<Category>

    @Query("SELECT name FROM categories ORDER BY name ASC")
    suspend fun getAllCategoryNamesDirect(): List<String>

    @Query("SELECT * FROM categories WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun getCategoryByName(name: String): Category?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoryCountDirect(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<Category>): List<Long>

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("DELETE FROM categories WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name))")
    suspend fun deleteByName(name: String): Int

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}
