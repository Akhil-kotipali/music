package com.example.audioindexer

import android.app.Application
import com.example.audioindexer.data.db.AudioDatabase
import com.example.audioindexer.data.model.Playlist
import com.example.audioindexer.data.repository.AudioRepository
import com.example.audioindexer.service.ai.AiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

    val aiManager: AiManager by lazy {
        AiManager(
            context = this,
            keyConfigDao = database.aiKeyConfigDao(),
            logDao = database.aiLogDao(),
            songDao = database.songDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        CoroutineScope(Dispatchers.IO).launch {
            if (database.playlistDao().getPlaylistById(1L) == null) {
                database.playlistDao().insertPlaylist(
                    Playlist(
                        id = 1L,
                        name = "Daily Mix",
                        description = "Smart queue with skip intelligence and auto-pruning"
                    )
                )
            }
        }
    }

    companion object {
        lateinit var instance: AudioIndexerApp
            private set
    }
}
