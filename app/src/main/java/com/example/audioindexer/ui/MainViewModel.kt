package com.example.audioindexer.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audioindexer.AudioIndexerApp
import com.example.audioindexer.data.model.AiKeyConfig
import com.example.audioindexer.data.model.AiLog
import com.example.audioindexer.data.model.Song
import com.example.audioindexer.data.model.StagedSong
import com.example.audioindexer.data.repository.AudioRepository
import com.example.audioindexer.playback.AudioPlaybackService
import com.example.audioindexer.playback.PlaybackState
import com.example.audioindexer.service.AudioScannerService
import com.example.audioindexer.service.ScanProgress
import com.example.audioindexer.service.ServiceStatus
import com.example.audioindexer.service.ai.AiManager
import com.example.audioindexer.service.ai.KeyHealthResult
import com.example.audioindexer.util.PermissionUtils
import com.example.audioindexer.util.StorageUtils
import com.example.audioindexer.util.StorageVolumeInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import com.example.audioindexer.data.model.Playlist
import com.example.audioindexer.data.model.PlaylistSongMap

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: AudioRepository,
    private val aiManager: AiManager,
    private val database: com.example.audioindexer.data.db.AudioDatabase
) : ViewModel() {

    val searchQuery = MutableStateFlow("")
    val filterMusicOnly = MutableStateFlow(true)

    val songs: StateFlow<List<Song>> = combine(
        searchQuery,
        filterMusicOnly
    ) { query, musicOnly ->
        Pair(query, musicOnly)
    }.flatMapLatest { (query, musicOnly) ->
        val baseFlow = if (musicOnly) {
            database.songDao().getMusicSongs()
        } else {
            repository.allSongs
        }
        if (query.isBlank()) {
            baseFlow
        } else {
            repository.searchSongs(query).map { list ->
                if (musicOnly) list.filter { it.isMusic } else list
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stagedSongs: StateFlow<List<StagedSong>> = repository.allStagedSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val songCount: StateFlow<Int> = repository.songCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val musicSongCount: StateFlow<Int> = database.songDao().getMusicSongCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val nonMusicCount: StateFlow<Int> = database.songDao().getNonMusicCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val stagedCount: StateFlow<Int> = repository.stagedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val uncategorizedCount: StateFlow<Int> = database.songDao().getUncategorizedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Playlists
    val playlists: StateFlow<List<Playlist>> = database.playlistDao().getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylistSongs = MutableStateFlow<List<Song>>(emptyList())
    val playlistSongCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())

    // Real-Time Live Model Discovery
    val liveModels = MutableStateFlow<List<String>>(emptyList())
    val isFetchingModels = MutableStateFlow(false)
    val modelFetchError = MutableStateFlow<String?>(null)

    // User Notification
    val userNotification = MutableStateFlow<String?>(null)

    // Sleep Timer
    val sleepTimerSecondsRemaining = MutableStateFlow<Int?>(null)
    private var sleepTimerJob: Job? = null

    // Audio Preset
    val currentSoundPreset = MutableStateFlow("Balanced")

    // AI Keys & Logs
    val aiKeys: StateFlow<List<AiKeyConfig>> = database.aiKeyConfigDao().getAllKeys()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aiLogs: StateFlow<List<AiLog>> = database.aiLogDao().getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // AI Categorization Prompt & Progress
    val showAiPrompt = MutableStateFlow(true)
    val isAiCategorizing = MutableStateFlow(false)
    val aiProgressCurrent = MutableStateFlow(0)
    val aiProgressTotal = MutableStateFlow(0)
    val lastCategoryResult = MutableStateFlow<String?>(null)

    // Key Testing State
    val keyTestingState = MutableStateFlow<Map<Long, String>>(emptyMap())

    // Storage Volumes
    private val _storageVolumes = MutableStateFlow<List<StorageVolumeInfo>>(emptyList())
    val storageVolumes: StateFlow<List<StorageVolumeInfo>> = _storageVolumes.asStateFlow()

    // Permissions
    private val _hasManageStorage = MutableStateFlow(false)
    val hasManageStorage: StateFlow<Boolean> = _hasManageStorage.asStateFlow()

    private val _hasReadStorage = MutableStateFlow(false)
    val hasReadStorage: StateFlow<Boolean> = _hasReadStorage.asStateFlow()

    // Scanner Service State
    private val _serviceStatus = MutableStateFlow(ServiceStatus())
    val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()

    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    private val _isObserverActive = MutableStateFlow(false)
    val isObserverActive: StateFlow<Boolean> = _isObserverActive.asStateFlow()

    // Playback Service State
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    val showFullPlayer = MutableStateFlow(false)
    val remainingQueue = MutableStateFlow<List<Song>>(emptyList())

    // Selected song for details modal
    val selectedSong = MutableStateFlow<Song?>(null)
    val selectedStagedSong = MutableStateFlow<StagedSong?>(null)

    private var scannerService: AudioScannerService? = null
    private var isScannerBound = false

    private var playbackService: AudioPlaybackService? = null
    private var isPlaybackBound = false

    private val scannerConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? AudioScannerService.LocalBinder
            scannerService = binder?.getService()
            isScannerBound = true

            scannerService?.let { s ->
                viewModelScope.launch { s.serviceStatus.collect { _serviceStatus.value = it } }
                viewModelScope.launch { s.scanProgress.collect { _scanProgress.value = it } }
                viewModelScope.launch { s.isObserverActive.collect { _isObserverActive.value = it } }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            scannerService = null
            isScannerBound = false
        }
    }

    private val playbackConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? AudioPlaybackService.PlaybackBinder
            playbackService = binder?.getService()
            isPlaybackBound = true

            playbackService?.let { ps ->
                viewModelScope.launch {
                    ps.playbackState.collect {
                        _playbackState.value = it
                        remainingQueue.value = ps.fatigueQueue.getRemainingInCurrentCycle()
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
            isPlaybackBound = false
        }
    }

    fun bindServices(context: Context) {
        if (!isScannerBound) {
            val scannerIntent = Intent(context, AudioScannerService::class.java)
            context.bindService(scannerIntent, scannerConnection, Context.BIND_AUTO_CREATE)
        }
        if (!isPlaybackBound) {
            val playbackIntent = Intent(context, AudioPlaybackService::class.java)
            context.bindService(playbackIntent, playbackConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindServices(context: Context) {
        if (isScannerBound) {
            try { context.unbindService(scannerConnection) } catch (ignored: Exception) {}
            isScannerBound = false
            scannerService = null
        }
        if (isPlaybackBound) {
            try { context.unbindService(playbackConnection) } catch (ignored: Exception) {}
            isPlaybackBound = false
            playbackService = null
        }
    }

    fun refreshState(context: Context) {
        _hasManageStorage.value = PermissionUtils.hasManageExternalStoragePermission()
        _hasReadStorage.value = PermissionUtils.hasReadStoragePermission(context)
        _storageVolumes.value = StorageUtils.getAllStorageDirectories(context)
    }

    // --- AI Integration Methods ---

    fun addOrUpdateKey(provider: String, label: String, apiKey: String, model: String, existingId: Long? = null) {
        viewModelScope.launch {
            val config = AiKeyConfig(
                id = existingId ?: 0,
                provider = provider,
                label = label,
                apiKey = apiKey,
                model = model,
                isActive = true
            )
            database.aiKeyConfigDao().insert(config)
        }
    }

    fun toggleKeyActive(id: Long, isActive: Boolean) {
        viewModelScope.launch {
            database.aiKeyConfigDao().updateActiveStatus(id, isActive)
        }
    }

    fun deleteKey(id: Long) {
        viewModelScope.launch {
            database.aiKeyConfigDao().deleteById(id)
        }
    }

    fun testKeyHealth(keyConfig: AiKeyConfig) {
        viewModelScope.launch {
            val res = aiManager.testKeyHealth(keyConfig)
            val msg = when (res) {
                is KeyHealthResult.Success -> res.message
                is KeyHealthResult.RateLimited -> res.message
                is KeyHealthResult.Error -> res.message
            }
            keyTestingState.value = keyTestingState.value + (keyConfig.id to msg)
        }
    }

    fun startAiCategorization() {
        if (isAiCategorizing.value) return // Prevent parallel duplicate AI submissions
        viewModelScope.launch {
            val uncategorized = database.songDao().getSongsWithoutAiCategory()
            if (uncategorized.isEmpty()) return@launch

            isAiCategorizing.value = true
            aiProgressCurrent.value = 0
            aiProgressTotal.value = uncategorized.size
            lastCategoryResult.value = null

            try {
                aiManager.batchCategorize(uncategorized) { current, total, result ->
                    aiProgressCurrent.value = current
                    aiProgressTotal.value = total
                    result?.let {
                        lastCategoryResult.value = "${it.title} -> ${it.category}"
                    }
                }
            } finally {
                isAiCategorizing.value = false
            }
        }
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch {
            database.aiLogDao().deleteById(id)
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            database.aiLogDao().clearAll()
        }
    }

    fun exportLogs(onComplete: (File) -> Unit) {
        viewModelScope.launch {
            val file = aiManager.exportLogsToTextFile()
            onComplete(file)
        }
    }

    // --- Audio Playback Methods ---

    fun playSong(song: Song) {
        val currentPlaylist = songs.value
        playbackService?.playSongFromPlaylist(currentPlaylist, song)
    }

    fun togglePlayPause() {
        playbackService?.togglePlayPause()
    }

    fun skipNext() {
        playbackService?.playNext()
    }

    fun skipPrevious() {
        playbackService?.playPrevious()
    }

    fun seekTo(positionMs: Long) {
        playbackService?.seekTo(positionMs)
    }

    fun shuffleRemainingQueue() {
        playbackService?.fatigueQueue?.shuffleRemaining()
        remainingQueue.value = playbackService?.fatigueQueue?.getRemainingInCurrentCycle() ?: emptyList()
    }

    // --- Scanner Methods ---

    fun triggerFullScan(context: Context) {
        if (scannerService != null) {
            scannerService?.triggerFullScan()
        } else {
            AudioScannerService.startFullScan(context)
        }
    }

    fun triggerDeltaCheck(context: Context) {
        if (scannerService != null) {
            scannerService?.triggerDeltaCheck()
        } else {
            AudioScannerService.startDeltaCheck(context)
        }
    }

    fun toggleObserver(context: Context, enable: Boolean) {
        if (scannerService != null) {
            scannerService?.enableObserver(enable)
        } else {
            if (enable) AudioScannerService.startObserver(context)
        }
    }

    fun promoteStagedSong(stagedSong: StagedSong) {
        viewModelScope.launch {
            repository.promoteStagedSong(stagedSong)
            if (selectedStagedSong.value?.id == stagedSong.id) {
                selectedStagedSong.value = null
            }
        }
    }

    fun promoteAllStaged() {
        viewModelScope.launch {
            repository.promoteAllStaged()
        }
    }

    fun dismissStagedSong(stagedSong: StagedSong) {
        viewModelScope.launch {
            repository.dismissStagedSong(stagedSong)
            if (selectedStagedSong.value?.id == stagedSong.id) {
                selectedStagedSong.value = null
            }
        }
    }

    fun clearAllStaged() {
        viewModelScope.launch {
            repository.clearAllStaged()
        }
    }

    fun clearAllIndexedSongs() {
        viewModelScope.launch {
            repository.clearAllSongs()
        }
    }

    // --- Playlist Methods ---

    fun refreshPlaylistSongCounts() {
        viewModelScope.launch {
            val counts = mutableMapOf<Long, Int>()
            playlists.value.forEach { pl ->
                counts[pl.id] = database.playlistDao().getSongCountForPlaylistDirect(pl.id)
            }
            playlistSongCounts.value = counts
        }
    }

    fun selectPlaylist(playlist: Playlist?) {
        selectedPlaylist.value = playlist
        if (playlist != null) {
            viewModelScope.launch {
                val songs = database.playlistDao().getSongsForPlaylistDirect(playlist.id)
                selectedPlaylistSongs.value = songs
            }
        } else {
            selectedPlaylistSongs.value = emptyList()
        }
    }

    fun playPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val songs = database.playlistDao().getSongsForPlaylistDirect(playlist.id)
            if (songs.isNotEmpty()) {
                playbackService?.playSongFromPlaylist(songs, songs.first(), playlist.id)
                userNotification.value = "Playing playlist '${playlist.name}' (${songs.size} tracks)"
            } else {
                userNotification.value = "Playlist '${playlist.name}' has no songs yet"
            }
        }
    }

    fun playSongFromPlaylist(playlist: Playlist, song: Song) {
        viewModelScope.launch {
            val songs = database.playlistDao().getSongsForPlaylistDirect(playlist.id)
            if (songs.isNotEmpty()) {
                playbackService?.playSongFromPlaylist(songs, song, playlist.id)
            }
        }
    }

    fun createCategoryPlaylist(name: String, description: String, category: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val matching = database.songDao().getSongsByCategoryDirect(category)
                if (matching.isEmpty()) {
                    onComplete(false, "No songs categorized under '$category'")
                    return@launch
                }
                val desc = description.ifBlank { "Auto-populated $category songs (${matching.size} tracks)" }
                val playlist = Playlist(name = name.trim(), description = desc)
                val playlistId = database.playlistDao().insertPlaylist(playlist)
                val maps = matching.map { song ->
                    PlaylistSongMap(
                        playlistId = playlistId,
                        songId = song.id,
                        addedAt = System.currentTimeMillis()
                    )
                }
                database.playlistDao().insertAllMaps(maps)
                refreshPlaylistSongCounts()
                onComplete(true, "Playlist '${playlist.name}' created with ${matching.size} songs!")
            } catch (e: Exception) {
                onComplete(false, e.message ?: "Failed to create playlist")
            }
        }
    }

    fun createCustomPlaylist(name: String, description: String, songIds: List<Long>, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                if (songIds.isEmpty()) {
                    onComplete(false, "Please select at least 1 song")
                    return@launch
                }
                val desc = description.ifBlank { "Custom playlist (${songIds.size} tracks)" }
                val playlist = Playlist(name = name.trim(), description = desc)
                val playlistId = database.playlistDao().insertPlaylist(playlist)
                val maps = songIds.map { id ->
                    PlaylistSongMap(
                        playlistId = playlistId,
                        songId = id,
                        addedAt = System.currentTimeMillis()
                    )
                }
                database.playlistDao().insertAllMaps(maps)
                refreshPlaylistSongCounts()
                onComplete(true, "Playlist '${playlist.name}' created with ${songIds.size} songs!")
            } catch (e: Exception) {
                onComplete(false, e.message ?: "Failed to create playlist")
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            database.playlistDao().clearPlaylistSongs(playlist.id)
            database.playlistDao().deletePlaylist(playlist)
            if (selectedPlaylist.value?.id == playlist.id) {
                selectedPlaylist.value = null
                selectedPlaylistSongs.value = emptyList()
            }
            refreshPlaylistSongCounts()
            userNotification.value = "Playlist '${playlist.name}' deleted"
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            database.playlistDao().deleteSongFromPlaylist(playlistId, songId)
            selectedPlaylist.value?.let { pl ->
                if (pl.id == playlistId) {
                    selectedPlaylistSongs.value = database.playlistDao().getSongsForPlaylistDirect(playlistId)
                }
            }
            refreshPlaylistSongCounts()
        }
    }

    // --- Live Model Discovery ---

    fun fetchLiveModels(provider: String, apiKey: String) {
        if (apiKey.isBlank()) {
            modelFetchError.value = "Please enter an API key first"
            return
        }
        viewModelScope.launch {
            isFetchingModels.value = true
            modelFetchError.value = null
            liveModels.value = emptyList()
            try {
                val list = aiManager.fetchRealTimeModels(provider, apiKey)
                liveModels.value = list
                if (list.isEmpty()) {
                    modelFetchError.value = "No active models returned for this key"
                }
            } catch (e: Exception) {
                modelFetchError.value = e.message ?: "Failed to query provider models"
            } finally {
                isFetchingModels.value = false
            }
        }
    }

    fun clearLiveModels() {
        liveModels.value = emptyList()
        modelFetchError.value = null
    }

    // --- Listening Experience & Sound Controls ---

    fun setPlaybackSpeed(speed: Float) {
        playbackService?.setPlaybackSpeed(speed)
    }

    fun setSoundPreset(preset: String) {
        currentSoundPreset.value = preset
        userNotification.value = "Audio preset applied: $preset"
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        val totalSec = minutes * 60
        sleepTimerSecondsRemaining.value = totalSec
        userNotification.value = "Sleep timer set for $minutes minutes"
        sleepTimerJob = viewModelScope.launch {
            var remaining = totalSec
            while (remaining > 0) {
                delay(1000L)
                remaining--
                sleepTimerSecondsRemaining.value = remaining
            }
            playbackService?.pause()
            sleepTimerSecondsRemaining.value = null
            userNotification.value = "Sleep timer expired. Playback paused."
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerSecondsRemaining.value = null
        userNotification.value = "Sleep timer cancelled"
    }

    fun clearNotification() {
        userNotification.value = null
    }
}

class MainViewModelFactory(
    private val app: AudioIndexerApp
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(app.repository, app.aiManager, app.database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
