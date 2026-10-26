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

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

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
        viewModelScope.launch {
            val uncategorized = database.songDao().getSongsWithoutAiCategory()
            if (uncategorized.isEmpty()) return@launch

            isAiCategorizing.value = true
            aiProgressCurrent.value = 0
            aiProgressTotal.value = uncategorized.size
            lastCategoryResult.value = null

            aiManager.batchCategorize(uncategorized) { current, total, result ->
                aiProgressCurrent.value = current
                aiProgressTotal.value = total
                result?.let {
                    lastCategoryResult.value = "${it.title} -> ${it.category}"
                }
            }

            isAiCategorizing.value = false
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
