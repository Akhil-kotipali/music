package com.example.audioindexer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import android.widget.Toast
import com.example.audioindexer.ui.components.PlaylistsTab
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audioindexer.data.model.Song
import com.example.audioindexer.ui.components.AiCategorizationPrompt
import com.example.audioindexer.ui.components.AiManagerTab
import com.example.audioindexer.ui.components.CategoryManagementDialog
import com.example.audioindexer.ui.components.FullPlayerDialog
import com.example.audioindexer.ui.components.MiniPlaybackBar
import com.example.audioindexer.ui.components.PermissionCard
import com.example.audioindexer.ui.components.ScanControlsCard
import com.example.audioindexer.ui.components.SongDetailDialog
import com.example.audioindexer.ui.components.SongItemCard
import com.example.audioindexer.ui.components.StagedSongItemCard
import com.example.audioindexer.ui.components.StorageVolumesCard
import com.example.audioindexer.ui.theme.DarkSurfaceBorder
import com.example.audioindexer.ui.theme.PrimaryCyan
import com.example.audioindexer.ui.theme.SecondaryViolet
import com.example.audioindexer.ui.theme.TertiaryAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestStandardPermissions: () -> Unit,
    onRequestManageStorage: () -> Unit
) {
    val context = LocalContext.current
    val songs by viewModel.songs.collectAsState()
    val stagedSongs by viewModel.stagedSongs.collectAsState()
    val songCount by viewModel.songCount.collectAsState()
    val stagedCount by viewModel.stagedCount.collectAsState()
    val uncategorizedCount by viewModel.uncategorizedCount.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val storageVolumes by viewModel.storageVolumes.collectAsState()
    val hasManageStorage by viewModel.hasManageStorage.collectAsState()
    val hasReadStorage by viewModel.hasReadStorage.collectAsState()
    val serviceStatus by viewModel.serviceStatus.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val isObserverActive by viewModel.isObserverActive.collectAsState()

    // AI States
    val aiKeys by viewModel.aiKeys.collectAsState()
    val aiLogs by viewModel.aiLogs.collectAsState()
    val showAiPrompt by viewModel.showAiPrompt.collectAsState()
    val isAiCategorizing by viewModel.isAiCategorizing.collectAsState()
    val aiProgressCurrent by viewModel.aiProgressCurrent.collectAsState()
    val aiProgressTotal by viewModel.aiProgressTotal.collectAsState()
    val lastCategoryResult by viewModel.lastCategoryResult.collectAsState()
    val keyTestingState by viewModel.keyTestingState.collectAsState()

    // Playback States
    val playbackState by viewModel.playbackState.collectAsState()
    val showFullPlayer by viewModel.showFullPlayer.collectAsState()
    val remainingQueue by viewModel.remainingQueue.collectAsState()

    // Selected song for details modal
    val selectedSong by viewModel.selectedSong.collectAsState()
    val selectedStagedSong by viewModel.selectedStagedSong.collectAsState()

    val filterMusicOnly by viewModel.filterMusicOnly.collectAsState()
    val musicSongCount by viewModel.musicSongCount.collectAsState()
    val nonMusicCount by viewModel.nonMusicCount.collectAsState()

    // Playlist States
    val playlists by viewModel.playlists.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val selectedPlaylistSongs by viewModel.selectedPlaylistSongs.collectAsState()
    val playlistSongCounts by viewModel.playlistSongCounts.collectAsState()

    // Live Models & UX States
    val liveModels by viewModel.liveModels.collectAsState()
    val isFetchingModels by viewModel.isFetchingModels.collectAsState()
    val modelFetchError by viewModel.modelFetchError.collectAsState()
    val sleepTimerRemainingSec by viewModel.sleepTimerSecondsRemaining.collectAsState()
    val currentSoundPreset by viewModel.currentSoundPreset.collectAsState()
    val userNotification by viewModel.userNotification.collectAsState()

    // Categories
    val categories by viewModel.categories.collectAsState()
    var showCategoryDialog by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(userNotification) {
        userNotification?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearNotification()
        }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.LibraryMusic,
                                contentDescription = null,
                                tint = PrimaryCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Audio Indexer & Player",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "AI Categorization • Zero-Repeat Queue",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showCategoryDialog = true }) {
                        Icon(
                            Icons.Default.Category,
                            contentDescription = "Categories",
                            tint = PrimaryCyan
                        )
                    }
                    IconButton(onClick = { viewModel.refreshState(context) }) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (playbackState.currentSong != null) {
                MiniPlaybackBar(
                    playbackState = playbackState,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onSkipNext = { viewModel.skipNext() },
                    onSkipPrevious = { viewModel.skipPrevious() },
                    onClick = { viewModel.showFullPlayer.value = true }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Permission banner if not granted
            if (!hasManageStorage || !hasReadStorage) {
                PermissionCard(
                    hasManageStorage = hasManageStorage,
                    hasReadStorage = hasReadStorage,
                    onRequestStandardPermissions = onRequestStandardPermissions,
                    onRequestManageStorage = onRequestManageStorage,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // Scrollable Tab Row with 5 feature tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = PrimaryCyan,
                edgePadding = 16.dp,
                divider = { Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DarkSurfaceBorder)) }
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Library ($songCount)")
                        }
                    }
                )

                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.QueueMusic, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Playlists (${playlists.size})")
                        }
                    }
                )

                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HourglassBottom, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Staging")
                            if (stagedCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(TertiaryAmber)
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "$stagedCount",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                )

                Tab(
                    selected = selectedTabIndex == 3,
                    onClick = { selectedTabIndex = 3 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("AI Manager (${aiKeys.size})")
                        }
                    }
                )

                Tab(
                    selected = selectedTabIndex == 4,
                    onClick = { selectedTabIndex = 4 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scanner")
                        }
                    }
                )
            }

            // Tab Content
            when (selectedTabIndex) {
                0 -> LibraryTab(
                    songs = songs,
                    searchQuery = searchQuery,
                    filterMusicOnly = filterMusicOnly,
                    musicSongCount = musicSongCount,
                    nonMusicCount = nonMusicCount,
                    totalAudioCount = songCount,
                    uncategorizedCount = uncategorizedCount,
                    showAiPrompt = showAiPrompt,
                    isAiCategorizing = isAiCategorizing,
                    aiProgressCurrent = aiProgressCurrent,
                    aiProgressTotal = aiProgressTotal,
                    lastCategoryResult = lastCategoryResult,
                    onApproveAi = { viewModel.startAiCategorization() },
                    onDismissAiPrompt = { viewModel.showAiPrompt.value = false },
                    onSearchChange = { viewModel.searchQuery.value = it },
                    onToggleMusicFilter = { viewModel.filterMusicOnly.value = it },
                    onSongClick = { viewModel.selectedSong.value = it },
                    onPlaySong = { viewModel.playSong(it) },
                    onStartScanClick = { viewModel.triggerFullScan(context) }
                )
                1 -> PlaylistsTab(
                    playlists = playlists,
                    allSongs = songs,
                    selectedPlaylist = selectedPlaylist,
                    selectedPlaylistSongs = selectedPlaylistSongs,
                    playlistSongCounts = playlistSongCounts,
                    onSelectPlaylist = { viewModel.selectPlaylist(it) },
                    onPlayPlaylist = { viewModel.playPlaylist(it) },
                    onPlaySongFromPlaylist = { pl, s -> viewModel.playSongFromPlaylist(pl, s) },
                    onDeletePlaylist = { viewModel.deletePlaylist(it) },
                    onRemoveSongFromPlaylist = { plId, sId -> viewModel.removeSongFromPlaylist(plId, sId) },
                    onCreateCategoryPlaylist = { n, d, c, cb -> viewModel.createCategoryPlaylist(n, d, c, cb) },
                    onCreateCustomPlaylist = { n, d, ids, cb -> viewModel.createCustomPlaylist(n, d, ids, cb) },
                    availableCategories = categories,
                    onManageCategories = { showCategoryDialog = true }
                )
                2 -> StagingTab(
                    stagedSongs = stagedSongs,
                    onPromote = { viewModel.promoteStagedSong(it) },
                    onDismiss = { viewModel.dismissStagedSong(it) },
                    onPromoteAll = { viewModel.promoteAllStaged() },
                    onClearAll = { viewModel.clearAllStaged() },
                    onTriggerDeltaCheck = { viewModel.triggerDeltaCheck(context) },
                    onSongClick = { viewModel.selectedStagedSong.value = it }
                )
                3 -> AiManagerTab(
                    aiKeys = aiKeys,
                    aiLogs = aiLogs,
                    keyTestingState = keyTestingState,
                    onAddKey = { p, l, k, m -> viewModel.addOrUpdateKey(p, l, k, m) },
                    onReplaceKey = { id, p, l, k, m -> viewModel.addOrUpdateKey(p, l, k, m, id) },
                    onToggleKeyActive = { id, active -> viewModel.toggleKeyActive(id, active) },
                    onTestKeyHealth = { viewModel.testKeyHealth(it) },
                    onDeleteKey = { viewModel.deleteKey(it) },
                    onDeleteLog = { viewModel.deleteLog(it) },
                    onClearAllLogs = { viewModel.clearAllLogs() },
                    onExportLogs = { onDone -> viewModel.exportLogs(onDone) },
                    onFetchLiveModels = { p, k -> viewModel.fetchLiveModels(p, k) },
                    liveModels = liveModels,
                    isFetchingModels = isFetchingModels,
                    modelFetchError = modelFetchError
                )
                4 -> ScannerTab(
                    serviceStatus = serviceStatus,
                    scanProgress = scanProgress,
                    isObserverActive = isObserverActive,
                    storageVolumes = storageVolumes,
                    hasManageStorage = hasManageStorage,
                    hasReadStorage = hasReadStorage,
                    onStartFullScan = { viewModel.triggerFullScan(context) },
                    onStartDeltaCheck = { viewModel.triggerDeltaCheck(context) },
                    onToggleObserver = { viewModel.toggleObserver(context, it) },
                    onRequestStandardPermissions = onRequestStandardPermissions,
                    onRequestManageStorage = onRequestManageStorage,
                    onClearDatabase = { viewModel.clearAllIndexedSongs() },
                    isBeatModeEnabled = playbackState.isBeatModeEnabled,
                    onToggleBeatMode = { viewModel.toggleBeatMode(it) },
                    bassStrength = playbackState.bassStrength,
                    onSetBassStrength = { viewModel.setBassBoostStrength(it) },
                    currentPreset = playbackState.currentSoundPreset,
                    onSetPreset = { viewModel.setSoundPreset(it) },
                    onManageCategories = { showCategoryDialog = true }
                )
            }
        }
    }

    // Modal Details Dialog
    SongDetailDialog(
        song = selectedSong,
        stagedSong = selectedStagedSong,
        onDismiss = {
            viewModel.selectedSong.value = null
            viewModel.selectedStagedSong.value = null
        },
        onPlay = { viewModel.playSong(it) },
        onPromoteStaged = { viewModel.promoteStagedSong(it) },
        availableCategories = categories,
        onUpdateCategory = { songId, newCat -> viewModel.updateSongCategory(songId, newCat) }
    )

    // Full Player Modal with Fatigue Prevention Queue & Enhanced UX
    if (showFullPlayer) {
        FullPlayerDialog(
            playbackState = playbackState,
            remainingQueue = remainingQueue,
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onSkipNext = { viewModel.skipNext() },
            onSkipPrevious = { viewModel.skipPrevious() },
            onSeek = { viewModel.seekTo(it) },
            onShuffleRemaining = { viewModel.shuffleRemainingQueue() },
            onDismiss = { viewModel.showFullPlayer.value = false },
            playbackSpeed = playbackState.playbackSpeed,
            onSetPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
            sleepTimerRemainingSec = sleepTimerRemainingSec,
            onStartSleepTimer = { viewModel.startSleepTimer(it) },
            onCancelSleepTimer = { viewModel.cancelSleepTimer() },
            currentPreset = playbackState.currentSoundPreset,
            onSetPreset = { viewModel.setSoundPreset(it) },
            bassStrength = playbackState.bassStrength,
            onSetBassStrength = { viewModel.setBassBoostStrength(it) },
            isBeatModeEnabled = playbackState.isBeatModeEnabled,
            onToggleBeatMode = { viewModel.toggleBeatMode(it) }
        )
    }

    // Category Management Dialog (define from scratch, add, delete, reset)
    if (showCategoryDialog) {
        CategoryManagementDialog(
            categories = categories,
            allSongs = songs,
            onDismiss = { showCategoryDialog = false },
            onAddCategory = { n, d, c -> viewModel.addCategory(n, d, c) },
            onDeleteCategory = { cat -> viewModel.deleteCategory(cat) },
            onResetToDefaults = { viewModel.resetCategoriesToDefault() },
            onClearAllCategories = { viewModel.clearAllCategories() }
        )
    }
}

@Composable
private fun LibraryTab(
    songs: List<Song>,
    searchQuery: String,
    filterMusicOnly: Boolean,
    musicSongCount: Int,
    nonMusicCount: Int,
    totalAudioCount: Int,
    uncategorizedCount: Int,
    showAiPrompt: Boolean,
    isAiCategorizing: Boolean,
    aiProgressCurrent: Int,
    aiProgressTotal: Int,
    lastCategoryResult: String?,
    onApproveAi: () -> Unit,
    onDismissAiPrompt: () -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleMusicFilter: (Boolean) -> Unit,
    onSongClick: (Song) -> Unit,
    onPlaySong: (Song) -> Unit,
    onStartScanClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(10.dp))

        // AI Categorization Prompt: "New songs detected. Allow AI categorisation?"
        if (showAiPrompt && uncategorizedCount > 0 || isAiCategorizing) {
            AiCategorizationPrompt(
                uncategorizedCount = uncategorizedCount,
                isCategorizing = isAiCategorizing,
                progressCurrent = aiProgressCurrent,
                progressTotal = aiProgressTotal,
                lastCategoryResult = lastCategoryResult,
                onApprove = onApproveAi,
                onDismiss = onDismissAiPrompt,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search songs, artists, albums, AI mood tags...", fontSize = 13.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryCyan,
                unfocusedBorderColor = DarkSurfaceBorder
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Intelligent Classification Filter Chips: Music Tracks vs All Audio
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Music Tracks Chip
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleMusicFilter(true) },
                color = if (filterMusicOnly) PrimaryCyan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (filterMusicOnly) PrimaryCyan else DarkSurfaceBorder
                )
            ) {
                Text(
                    text = "🎵 Songs ($musicSongCount)",
                    fontSize = 12.sp,
                    fontWeight = if (filterMusicOnly) FontWeight.Bold else FontWeight.Normal,
                    color = if (filterMusicOnly) PrimaryCyan else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // All Audio Chip
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleMusicFilter(false) },
                color = if (!filterMusicOnly) SecondaryViolet.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (!filterMusicOnly) SecondaryViolet else DarkSurfaceBorder
                )
            ) {
                Text(
                    text = "📁 All Audio ($totalAudioCount)",
                    fontSize = 12.sp,
                    fontWeight = if (!filterMusicOnly) FontWeight.Bold else FontWeight.Normal,
                    color = if (!filterMusicOnly) SecondaryViolet else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            if (filterMusicOnly && nonMusicCount > 0) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "$nonMusicCount non-songs filtered",
                    fontSize = 10.sp,
                    color = TertiaryAmber,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (songs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.Album,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No songs match your search" else "No audio files indexed yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (searchQuery.isNotBlank())
                            "Try searching with different keywords"
                        else
                            "Run a storage scan to discover and index all .mp3, .wav, and .m4a tracks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (searchQuery.isBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onStartScanClick,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Storage Scan", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(songs, key = { it.id }) { song ->
                    SongItemCard(
                        song = song,
                        onClick = { onSongClick(song) },
                        onPlayClick = { onPlaySong(song) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StagingTab(
    stagedSongs: List<com.example.audioindexer.data.model.StagedSong>,
    onPromote: (com.example.audioindexer.data.model.StagedSong) -> Unit,
    onDismiss: (com.example.audioindexer.data.model.StagedSong) -> Unit,
    onPromoteAll: () -> Unit,
    onClearAll: () -> Unit,
    onTriggerDeltaCheck: () -> Unit,
    onSongClick: (com.example.audioindexer.data.model.StagedSong) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(12.dp))

        // Staging Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = TertiaryAmber.copy(alpha = 0.12f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, TertiaryAmber.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Track Staging Isolation Area",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TertiaryAmber
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "New audio files identified by the FileObserver or Delta-Check system are isolated here and held in the staging table without processing.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (stagedSongs.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${stagedSongs.size} tracks held in staging",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onClearAll,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Clear All", fontSize = 11.sp)
                    }

                    Button(
                        onClick = onPromoteAll,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = Color.Black)
                    ) {
                        Text("Process All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(stagedSongs, key = { it.id }) { staged ->
                    StagedSongItemCard(
                        stagedSong = staged,
                        onPromote = { onPromote(staged) },
                        onDismiss = { onDismiss(staged) },
                        onClick = { onSongClick(staged) }
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.HourglassBottom,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Staging Area Empty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "No newly added tracks are currently isolated in staging. Trigger a delta check or enable the FileObserver to track new audio arrivals.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onTriggerDeltaCheck,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Run Delta Check Now")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerTab(
    serviceStatus: com.example.audioindexer.service.ServiceStatus,
    scanProgress: com.example.audioindexer.service.ScanProgress,
    isObserverActive: Boolean,
    storageVolumes: List<com.example.audioindexer.util.StorageVolumeInfo>,
    hasManageStorage: Boolean,
    hasReadStorage: Boolean,
    onStartFullScan: () -> Unit,
    onStartDeltaCheck: () -> Unit,
    onToggleObserver: (Boolean) -> Unit,
    onRequestStandardPermissions: () -> Unit,
    onRequestManageStorage: () -> Unit,
    onClearDatabase: () -> Unit,
    isBeatModeEnabled: Boolean,
    onToggleBeatMode: (Boolean) -> Unit,
    bassStrength: Int,
    onSetBassStrength: (Int) -> Unit,
    currentPreset: String,
    onSetPreset: (String) -> Unit,
    onManageCategories: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Sound, Equalizer & Beat Mode Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sound, Equalizer & Beat Mode",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Beat Mode Haptics Toggle
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isBeatModeEnabled) SecondaryViolet.copy(alpha = 0.25f) else DarkSurfaceBorder.copy(alpha = 0.3f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isBeatModeEnabled) SecondaryViolet else DarkSurfaceBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    Icons.Default.Vibration,
                                    contentDescription = "Beat Mode",
                                    tint = if (isBeatModeEnabled) SecondaryViolet else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Beat Mode (Haptic Feedback)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isBeatModeEnabled) SecondaryViolet else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Detects highs, beats & bass in real-time and pulses haptic vibrations",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = isBeatModeEnabled,
                                onCheckedChange = onToggleBeatMode,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = PrimaryCyan,
                                    checkedTrackColor = SecondaryViolet.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Deep Bass Boost Slider
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceBorder.copy(alpha = 0.35f))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Mega Bass Boost Strength", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
                            }
                            Text(
                                text = "${(bassStrength / 10)}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryCyan
                            )
                        }

                        Slider(
                            value = bassStrength.toFloat(),
                            onValueChange = { onSetBassStrength(it.toInt()) },
                            valueRange = 0f..1000f,
                            steps = 19,
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryCyan,
                                activeTrackColor = PrimaryCyan,
                                inactiveTrackColor = DarkSurfaceBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Categories Management Entry Button
                    OutlinedButton(
                        onClick = onManageCategories,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Define & Manage Categories from Scratch", fontSize = 12.sp, color = PrimaryCyan)
                    }
                }
            }
        }

        item {
            ScanControlsCard(
                serviceStatus = serviceStatus,
                scanProgress = scanProgress,
                isObserverActive = isObserverActive,
                onStartFullScan = onStartFullScan,
                onStartDeltaCheck = onStartDeltaCheck,
                onToggleObserver = onToggleObserver
            )
        }

        item {
            StorageVolumesCard(volumes = storageVolumes)
        }

        item {
            PermissionCard(
                hasManageStorage = hasManageStorage,
                hasReadStorage = hasReadStorage,
                onRequestStandardPermissions = onRequestStandardPermissions,
                onRequestManageStorage = onRequestManageStorage
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Database Management",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Clear Room database index to test fresh scanning or delta-check isolation.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onClearDatabase,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear Main Songs Index", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
