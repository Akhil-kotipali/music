package com.example.audioindexer.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.audioindexer.data.model.Playlist
import com.example.audioindexer.data.model.PlaylistSongMap
import com.example.audioindexer.data.model.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: Long): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    // Playlist Song Map & Pruning queries
    @Query("SELECT * FROM playlist_song_map WHERE playlist_id = :playlistId AND song_id = :songId LIMIT 1")
    suspend fun getSongMap(playlistId: Long, songId: Long): PlaylistSongMap?

    @Query("SELECT * FROM playlist_song_map WHERE playlist_id = :playlistId")
    fun getMapsForPlaylist(playlistId: Long): Flow<List<PlaylistSongMap>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMap(map: PlaylistSongMap): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllMaps(maps: List<PlaylistSongMap>): List<Long>

    @Update
    suspend fun updateMap(map: PlaylistSongMap)

    @Query("UPDATE playlist_song_map SET play_count = play_count + 1, last_played_at = :timestamp WHERE playlist_id = :playlistId AND song_id = :songId")
    suspend fun incrementPlayCount(playlistId: Long, songId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE playlist_song_map SET skip_count = skip_count + 1, last_played_at = :timestamp WHERE playlist_id = :playlistId AND song_id = :songId")
    suspend fun incrementSkipCount(playlistId: Long, songId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM playlist_song_map WHERE playlist_id = :playlistId AND song_id = :songId")
    suspend fun deleteSongFromPlaylist(playlistId: Long, songId: Long): Int

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN playlist_song_map psm ON s.id = psm.song_id
        WHERE psm.playlist_id = :playlistId
        ORDER BY s.title ASC
    """)
    fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>>

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN playlist_song_map psm ON s.id = psm.song_id
        WHERE psm.playlist_id = :playlistId
        ORDER BY s.title ASC
    """)
    suspend fun getSongsForPlaylistDirect(playlistId: Long): List<Song>
}
