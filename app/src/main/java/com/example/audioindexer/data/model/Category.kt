package com.example.audioindexer.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Custom mood/genre category entity.
 * Users can create, edit, customize, or delete categories from scratch.
 */
@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["name"], unique = true)
    ]
)
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val colorHex: String = "#00E5FF",
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
