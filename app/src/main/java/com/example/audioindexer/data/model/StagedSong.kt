package com.example.audioindexer.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "staged_songs",
    indices = [
        Index(value = ["filePath"], unique = true),
        Index(value = ["detectedAt"]),
        Index(value = ["is_music"]),
        Index(value = ["audio_type"])
    ]
)
data class StagedSong(
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
    val detectedAt: Long = System.currentTimeMillis(),
    val source: String = "DELTA_CHECK",
    val status: String = "STAGED_UNPROCESSED"
)
