package com.example.audioindexer.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlist_song_map",
    indices = [
        Index(value = ["playlist_id", "song_id"], unique = true),
        Index(value = ["playlist_id"]),
        Index(value = ["song_id"])
    ]
)
data class PlaylistSongMap(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,
    @ColumnInfo(name = "song_id")
    val songId: Long,
    @ColumnInfo(name = "play_count")
    val playCount: Int = 0,
    @ColumnInfo(name = "skip_count")
    val skipCount: Int = 0,
    @ColumnInfo(name = "last_played_at")
    val lastPlayedAt: Long = 0L,
    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
) {
    val totalAppearances: Int
        get() = playCount + skipCount

    val skipRatio: Double
        get() = if (totalAppearances > 0) skipCount.toDouble() / totalAppearances else 0.0

    val isPruneEligible: Boolean
        get() = totalAppearances >= 5 && skipRatio > 0.80
}
