package com.example.audioindexer.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Main indexed song entity stored in the Room database table "songs".
 *
 * Fields required:
 * - filePath: Absolute file path (unique index)
 * - title: Audio track title
 * - artist: Artist name
 * - album: Album name
 * - lyricsSnippet: Placeholder text field for lyrics preview/snippet
 */
@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["filePath"], unique = true),
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["album"])
    ]
)
data class Song(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val title: String,
    val artist: String,
    val album: String,
    val lyricsSnippet: String? = null,
    val durationMs: Long = 0L,
    val fileSize: Long = 0L,
    val extension: String = "",
    val dateAdded: Long = System.currentTimeMillis(),
    val dateModified: Long = 0L
)
