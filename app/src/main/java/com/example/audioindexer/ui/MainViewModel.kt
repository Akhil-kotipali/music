package com.example.audioindexer.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audioindexer.data.model.Song
import com.example.audioindexer.data.model.StagedSong
import com.example.audioindexer.data.repository.AudioRepository
import com.example.audioindexer.service.AudioScannerService
import com.example.audioindexer.service.ScanProgress
import com.example.audioindexer.service.ServiceStatus
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

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: AudioRepository
) : ViewModel() {

    // Search query
    val searchQuery = MutableStateFlow("")

    // Filtered songs
    val songs: StateFlow<List<Song>> = searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            repository.allSongs
        } else {
            repository.searchSongs(query)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Staged songs (isolated new tracks held without processing)
    val stagedSongs: StateFlow<List<StagedSong>> = repository.allStagedSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val songCount: StateFlow<Int> = repository.songCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val stagedCount: StateFlow<Int> = repository.stagedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Storage volumes
    private val _storageVolumes = MutableStateFlow<List<StorageVolumeInfo>>(emptyList())
    val storageVolumes: StateFlow<List<StorageVolumeInfo>> = _storageVolumes.asStateFlow()

    // Permissions
    private val _hasManageStorage = MutableStateFlow(false)
    val hasManageStorage: StateFlow<Boolean> = _hasManageStorage.asStateFlow()

    private val _hasReadStorage = MutableStateFlow(false)
    val hasReadStorage: StateFlow<Boolean> = _hasReadStorage.asStateFlow()

    // Service state
    private val _serviceStatus = MutableStateFlow(ServiceStatus())
    val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()

    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    private val _isObserverActive = MutableStateFlow(false)
    val isObserverActive: StateFlow<Boolean> = _isObserverActive.asStateFlow()

    // Selected song for details modal
    val selectedSong = MutableStateFlow<Song?>(null)
    val selectedStagedSong = MutableStateFlow<StagedSong?>(null)

    private var scannerService: AudioScannerService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? AudioScannerService.LocalBinder
            scannerService = binder?.getService()
            isBound = true

            scannerService?.let { s ->
                viewModelScope.launch {
                    s.serviceStatus.collect { _serviceStatus.value = it }
                }
                viewModelScope.launch {
                    s.scanProgress.collect { _scanProgress.value = it }
                }
                viewModelScope.launch {
                    s.isObserverActive.collect { _isObserverActive.value = it }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            scannerService = null
            isBound = false
        }
    }

    fun bindService(context: Context) {
        if (!isBound) {
            val intent = Intent(context, AudioScannerService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindService(context: Context) {
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (ignored: Exception) {}
            isBound = false
            scannerService = null
        }
    }

    fun refreshState(context: Context) {
        _hasManageStorage.value = PermissionUtils.hasManageExternalStoragePermission()
        _hasReadStorage.value = PermissionUtils.hasReadStoragePermission(context)
        _storageVolumes.value = StorageUtils.getAllStorageDirectories(context)
    }

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

    override fun onCleared() {
        super.onCleared()
    }
}

class MainViewModelFactory(
    private val repository: AudioRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
