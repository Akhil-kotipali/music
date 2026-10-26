package com.example.audioindexer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.audioindexer.data.dao.SongDao
import com.example.audioindexer.data.dao.StagedSongDao
import com.example.audioindexer.data.model.Song
import com.example.audioindexer.data.model.StagedSong

@Database(
    entities = [
        Song::class,
        StagedSong::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AudioDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
    abstract fun stagedSongDao(): StagedSongDao

    companion object {
        @Volatile
        private var INSTANCE: AudioDatabase? = null

        fun getDatabase(context: Context): AudioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AudioDatabase::class.java,
                    "audio_indexer.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
