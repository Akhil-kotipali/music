package com.example.audioindexer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.audioindexer.data.dao.AiKeyConfigDao
import com.example.audioindexer.data.dao.AiLogDao
import com.example.audioindexer.data.dao.SongDao
import com.example.audioindexer.data.dao.StagedSongDao
import com.example.audioindexer.data.model.AiKeyConfig
import com.example.audioindexer.data.model.AiLog
import com.example.audioindexer.data.model.Song
import com.example.audioindexer.data.model.StagedSong

@Database(
    entities = [
        Song::class,
        StagedSong::class,
        AiKeyConfig::class,
        AiLog::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AudioDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
    abstract fun stagedSongDao(): StagedSongDao
    abstract fun aiKeyConfigDao(): AiKeyConfigDao
    abstract fun aiLogDao(): AiLogDao

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
