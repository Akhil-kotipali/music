package com.example.audioindexer.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Main indexed song entity stored in the Room database table "songs".
 *
 * Fields:
 * - filePath: Absolute file path (unique index)
 * - title: Audio track title
 * - artist: Artist name
 * - album: Album name
 * - lyricsSnippet: Text field for lyrics preview/snippet
 * - ai_category: Mood/genre tag from AI (Love, Motivational, Sad, High Energy, Relaxed, Focus)
 */
@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["filePath"], unique = true),
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["album"]),
        Index(value = ["ai_category"]),
        Index(value = ["is_music"]),
        Index(value = ["audio_type"])
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
    @ColumnInfo(name = "ai_category")
    val aiCategory: String? = null,
    @ColumnInfo(name = "is_music", defaultValue = "1")
    val isMusic: Boolean = true,
    @ColumnInfo(name = "audio_type", defaultValue = "'MUSIC'")
    val audioType: String = "MUSIC",
    val durationMs: Long = 0L,
    val fileSize: Long = 0L,
    val extension: String = "",
    val dateAdded: Long = System.currentTimeMillis(),
    val dateModified: Long = 0L
)
