package com.example.audioindexer.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Staging table entity stored in the Room database table "staged_songs".
 *
 * When new audio files are added to storage, the file observer or delta-check system
 * isolates only the new tracks and holds them in this staging table WITHOUT processing
 * them into the main "songs" table.
 */
@Entity(
    tableName = "staged_songs",
    indices = [
        Index(value = ["filePath"], unique = true),
        Index(value = ["detectedAt"])
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
    val durationMs: Long = 0L,
    val fileSize: Long = 0L,
    val extension: String = "",
    val detectedAt: Long = System.currentTimeMillis(),
    val source: String = "DELTA_CHECK", // "FILE_OBSERVER" or "DELTA_CHECK"
    val status: String = "STAGED_UNPROCESSED"
)
