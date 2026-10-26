package com.example.audioindexer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.audioindexer.MainActivity
import com.example.audioindexer.data.db.AudioDatabase
import com.example.audioindexer.data.repository.AudioRepository
import com.example.audioindexer.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ServiceStatusState {
    IDLE,
    SCANNING,
    DELTA_CHECKING,
    ERROR
}

data class ServiceStatus(
    val state: ServiceStatusState = ServiceStatusState.IDLE,
    val isObserverRunning: Boolean = false,
    val message: String = "Service ready",
    val watchedDirectoryCount: Int = 0
)

class AudioScannerService : Service() {

    private val binder = LocalBinder()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var activeScanJob: Job? = null
    private var observerManager: AudioFileObserverManager? = null

    private val _serviceStatus = MutableStateFlow(ServiceStatus())
    val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()

    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    private val _isObserverActive = MutableStateFlow(false)
    val isObserverActive: StateFlow<Boolean> = _isObserverActive.asStateFlow()

    companion object {
        const val CHANNEL_ID = "audio_scanner_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_FULL_SCAN = "com.example.audioindexer.START_FULL_SCAN"
        const val ACTION_RUN_DELTA_CHECK = "com.example.audioindexer.RUN_DELTA_CHECK"
        const val ACTION_START_OBSERVER = "com.example.audioindexer.START_OBSERVER"
        const val ACTION_STOP_SERVICE = "com.example.audioindexer.STOP_SERVICE"

        fun startFullScan(context: Context) {
            val intent = Intent(context, AudioScannerService::class.java).apply {
                action = ACTION_START_FULL_SCAN
            }
            startServiceCompat(context, intent)
        }

        fun startDeltaCheck(context: Context) {
            val intent = Intent(context, AudioScannerService::class.java).apply {
                action = ACTION_RUN_DELTA_CHECK
            }
            startServiceCompat(context, intent)
        }

        fun startObserver(context: Context) {
            val intent = Intent(context, AudioScannerService::class.java).apply {
                action = ACTION_START_OBSERVER
            }
            startServiceCompat(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AudioScannerService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        private fun startServiceCompat(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioScannerService = this@AudioScannerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundWithNotification("Audio Scanner Service active")

        observerManager = AudioFileObserverManager(
            context = applicationContext,
            scope = serviceScope,
            onNewAudioDetected = { path ->
                updateNotification("New audio file detected: $path")
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FULL_SCAN -> triggerFullScan()
            ACTION_RUN_DELTA_CHECK -> triggerDeltaCheck()
            ACTION_START_OBSERVER -> enableObserver(true)
            ACTION_STOP_SERVICE -> stopSelfGracefully()
        }
        return START_STICKY
    }

    fun triggerFullScan() {
        if (!checkPermissions()) return

        activeScanJob?.cancel()
        activeScanJob = serviceScope.launch {
            _serviceStatus.value = _serviceStatus.value.copy(
                state = ServiceStatusState.SCANNING,
                message = "Scanning internal storage and SD cards..."
            )
            updateNotification("Scanning storage for audio files (.mp3, .wav, .m4a)...")

            try {
                val count = AudioScanner.performFullScan(applicationContext) { progress ->
                    _scanProgress.value = progress
                    updateNotification("Indexed ${progress.filesScanned} files (${progress.audioFound} audio found)")
                }
                val repo = AudioRepository(AudioDatabase.getDatabase(applicationContext))
                repo.deduplicateDatabaseSongs()

                _serviceStatus.value = _serviceStatus.value.copy(
                    state = ServiceStatusState.IDLE,
                    message = "Full scan complete: $count songs indexed."
                )
                updateNotification("Scan complete: $count audio files indexed.")
            } catch (e: Exception) {
                _serviceStatus.value = _serviceStatus.value.copy(
                    state = ServiceStatusState.ERROR,
                    message = "Scan failed: ${e.message}"
                )
                updateNotification("Scan error: ${e.message}")
            }
        }
    }

    fun triggerDeltaCheck() {
        if (!checkPermissions()) return

        activeScanJob?.cancel()
        activeScanJob = serviceScope.launch {
            _serviceStatus.value = _serviceStatus.value.copy(
                state = ServiceStatusState.DELTA_CHECKING,
                message = "Running delta check for new audio tracks..."
            )
            updateNotification("Running delta check across storage volumes...")

            try {
                val newTracksCount = AudioScanner.performDeltaCheck(applicationContext) { progress ->
                    _scanProgress.value = progress
                    updateNotification(progress.message)
                }
                val repo = AudioRepository(AudioDatabase.getDatabase(applicationContext))
                repo.deduplicateDatabaseSongs()

                _serviceStatus.value = _serviceStatus.value.copy(
                    state = ServiceStatusState.IDLE,
                    message = "Delta check complete: $newTracksCount new tracks isolated in staging."
                )
                updateNotification("Delta check complete: $newTracksCount new tracks isolated.")
            } catch (e: Exception) {
                _serviceStatus.value = _serviceStatus.value.copy(
                    state = ServiceStatusState.ERROR,
                    message = "Delta check failed: ${e.message}"
                )
                updateNotification("Delta check error: ${e.message}")
            }
        }
    }

    fun enableObserver(enable: Boolean) {
        if (enable) {
            if (!checkPermissions()) return
            observerManager?.startWatching()
            val count = observerManager?.getWatchedCount() ?: 0
            _isObserverActive.value = true
            _serviceStatus.value = _serviceStatus.value.copy(
                isObserverRunning = true,
                watchedDirectoryCount = count,
                message = "File observer active on $count directories"
            )
            updateNotification("Monitoring $count directories for new audio files")
        } else {
            observerManager?.stopWatching()
            _isObserverActive.value = false
            _serviceStatus.value = _serviceStatus.value.copy(
                isObserverRunning = false,
                watchedDirectoryCount = 0,
                message = "File observer paused"
            )
            updateNotification("File observer paused")
        }
    }

    private fun checkPermissions(): Boolean {
        val hasManage = PermissionUtils.hasManageExternalStoragePermission()
        val hasRead = PermissionUtils.hasReadStoragePermission(applicationContext)
        if (!hasManage && !hasRead) {
            _serviceStatus.value = _serviceStatus.value.copy(
                state = ServiceStatusState.ERROR,
                message = "Permissions missing: Please grant READ and MANAGE_EXTERNAL_STORAGE."
            )
            updateNotification("Storage permissions required for scanning.")
            return false
        }
        return true
    }

    private fun startForegroundWithNotification(contentText: String) {
        val notification = buildNotification(contentText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Audio Storage Indexer")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Scanner Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifies when storage is being scanned or monitored for audio tracks"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun stopSelfGracefully() {
        observerManager?.stopWatching()
        activeScanJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        observerManager?.stopWatching()
        serviceScope.cancel()
    }
}
