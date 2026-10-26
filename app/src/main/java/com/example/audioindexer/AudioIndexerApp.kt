package com.example.audioindexer

import android.app.Application
import com.example.audioindexer.data.db.AudioDatabase
import com.example.audioindexer.data.repository.AudioRepository

class AudioIndexerApp : Application() {

    val database: AudioDatabase by lazy {
        AudioDatabase.getDatabase(this)
    }

    val repository: AudioRepository by lazy {
        AudioRepository(
            songDao = database.songDao(),
            stagedSongDao = database.stagedSongDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: AudioIndexerApp
            private set
    }
}
