package com.example.audioindexer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.audioindexer.data.model.Category
import com.example.audioindexer.data.model.Playlist
import com.example.audioindexer.data.model.Song
import com.example.audioindexer.ui.theme.AccentGreen
import com.example.audioindexer.ui.theme.AccentOrange
import com.example.audioindexer.ui.theme.AccentRed
import com.example.audioindexer.ui.theme.DarkSurfaceBorder
import com.example.audioindexer.ui.theme.PrimaryCyan
import com.example.audioindexer.ui.theme.SecondaryViolet
import com.example.audioindexer.ui.theme.TertiaryAmber

@Composable
fun PlaylistsTab(
    playlists: List<Playlist>,
    allSongs: List<Song>,
    selectedPlaylist: Playlist?,
    selectedPlaylistSongs: List<Song>,
    playlistSongCounts: Map<Long, Int>,
    onSelectPlaylist: (Playlist?) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    onPlaySongFromPlaylist: (Playlist, Song) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onRemoveSongFromPlaylist: (Long, Long) -> Unit,
    onCreateCategoryPlaylist: (name: String, description: String, categories: List<String>, onResult: (Boolean, String) -> Unit) -> Unit,
    onCreateCustomPlaylist: (name: String, description: String, songIds: List<Long>, onResult: (Boolean, String) -> Unit) -> Unit,
    availableCategories: List<Category> = emptyList(),
    onManageCategories: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    if (selectedPlaylist != null) {
        // Detailed view of selected playlist
        PlaylistDetailView(
            playlist = selectedPlaylist,
            songs = selectedPlaylistSongs,
            onBack = { onSelectPlaylist(null) },
            onPlayPlaylist = { onPlayPlaylist(selectedPlaylist) },
            onPlaySong = { onPlaySongFromPlaylist(selectedPlaylist, it) },
            onRemoveSong = { onRemoveSongFromPlaylist(selectedPlaylist.id, it.id) },
            modifier = modifier
        )
    } else {
        // Playlists list view
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header & Create Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Playlists (${playlists.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Custom selections & AI mood categories",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onManageCategories != null) {
                        OutlinedButton(
                            onClick = onManageCategories,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(15.dp), tint = SecondaryViolet)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Categories", fontSize = 11.sp, color = SecondaryViolet)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Button(
                        onClick = { showCreateDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Playlist", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.QueueMusic,
                            contentDescription = null,
                            tint = PrimaryCyan.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Playlists Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Create custom song playlists or auto-populate using AI categories (Love, Focus, High Energy, etc.).",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showCreateDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = Color.Black)
                        ) {
                            Text("Create First Playlist")
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        val count = playlistSongCounts[playlist.id] ?: 0
                        PlaylistCard(
                            playlist = playlist,
                            songCount = count,
                            onClick = { onSelectPlaylist(playlist) },
                            onPlay = { onPlayPlaylist(playlist) },
                            onDelete = { onDeletePlaylist(playlist) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            allSongs = allSongs,
            availableCategories = availableCategories,
            onDismiss = { showCreateDialog = false },
            onCreateCategory = { name, desc, cats, cb ->
                onCreateCategoryPlaylist(name, desc, cats, cb)
                showCreateDialog = false
            },
            onCreateCustom = { name, desc, ids, cb ->
                onCreateCustomPlaylist(name, desc, ids, cb)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun PlaylistCard(
    playlist: Playlist,
    songCount: Int,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(SecondaryViolet.copy(alpha = 0.4f), PrimaryCyan.copy(alpha = 0.3f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = PrimaryCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (playlist.description.isNotBlank()) playlist.description else "$songCount tracks",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = PrimaryCyan)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AccentRed.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
fun PlaylistDetailView(
    playlist: Playlist,
    songs: List<Song>,
    onBack: () -> Unit,
    onPlayPlaylist: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onRemoveSong: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Back Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${songs.size} tracks • ${playlist.description}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (songs.isNotEmpty()) {
                Button(
                    onClick = onPlayPlaylist,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play All", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No songs in this playlist.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(songs, key = { it.id }) { song ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPlaySong(song) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${song.artist} • ${song.album}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { onRemoveSong(song) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = AccentOrange, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreatePlaylistDialog(
    allSongs: List<Song>,
    availableCategories: List<Category> = emptyList(),
    onDismiss: () -> Unit,
    onCreateCategory: (name: String, description: String, categories: List<String>, onResult: (Boolean, String) -> Unit) -> Unit,
    onCreateCustom: (name: String, description: String, songIds: List<Long>, onResult: (Boolean, String) -> Unit) -> Unit
) {
    var mode by remember { mutableStateOf<String>("CATEGORY") } // "CATEGORY" or "CUSTOM"
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val selectedCategories = remember { mutableStateListOf<String>() }
    var searchQuery by remember { mutableStateOf("") }
    var customCategoryFilter by remember { mutableStateOf<String?>(null) }
    val selectedSongIds = remember { mutableStateListOf<Long>() }

    val categoryNames = remember(availableCategories) {
        if (availableCategories.isNotEmpty()) availableCategories.map { it.name }
        else listOf("Love", "Motivational", "Sad", "High Energy", "Relaxed", "Focus")
    }

    LaunchedEffect(categoryNames) {
        if (selectedCategories.isEmpty() && categoryNames.isNotEmpty()) {
            selectedCategories.add(categoryNames.first())
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Create Playlist",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Auto-populate from categories/moods or pick custom songs.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Mode Selector Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceBorder.copy(alpha = 0.5f))
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (mode == "CATEGORY") PrimaryCyan else Color.Transparent)
                            .clickable { mode = "CATEGORY" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Category / Mood Mix",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (mode == "CATEGORY") Color.Black else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (mode == "CUSTOM") PrimaryCyan else Color.Transparent)
                            .clickable { mode = "CUSTOM" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Custom Pick Songs",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (mode == "CUSTOM") Color.Black else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name") },
                    placeholder = {
                        Text(
                            if (mode == "CATEGORY") {
                                if (selectedCategories.isNotEmpty()) "${selectedCategories.joinToString(" & ")} Mix" else "My Mood Mix"
                            } else "My Favorite Mix"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (mode == "CATEGORY") {
                    // Category Selection Chips with real-time song count
                    Text(
                        text = "Select one or more categories:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(categoryNames) { cat ->
                            val isSel = selectedCategories.contains(cat)
                            val count = allSongs.count { it.aiCategory?.trim()?.equals(cat.trim(), ignoreCase = true) == true }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSel) {
                                            if (selectedCategories.size > 1) {
                                                selectedCategories.remove(cat)
                                            }
                                        } else {
                                            selectedCategories.add(cat)
                                        }
                                    },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSel) PrimaryCyan.copy(alpha = 0.18f) else DarkSurfaceBorder.copy(alpha = 0.3f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isSel) 1.5.dp else 1.dp,
                                    color = if (isSel) PrimaryCyan else DarkSurfaceBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = isSel,
                                            onCheckedChange = { checked ->
                                                if (checked) {
                                                    if (!selectedCategories.contains(cat)) selectedCategories.add(cat)
                                                } else {
                                                    if (selectedCategories.size > 1) selectedCategories.remove(cat)
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = PrimaryCyan)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = cat,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSel) PrimaryCyan else MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSel) PrimaryCyan else DarkSurfaceBorder)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "$count tracks",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSel) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val totalSelectedCount = allSongs.count { song ->
                        val songCat = song.aiCategory?.trim()
                        selectedCategories.any { sel -> sel.trim().equals(songCat, ignoreCase = true) }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Total songs in selection: $totalSelectedCount",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen
                    )
                } else {
                    // Custom songs multi-select list with Category Filter
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            val isAll = customCategoryFilter == null
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isAll) PrimaryCyan else DarkSurfaceBorder.copy(alpha = 0.4f))
                                    .clickable { customCategoryFilter = null }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "All (${allSongs.size})",
                                    fontSize = 10.sp,
                                    fontWeight = if (isAll) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isAll) Color.Black else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        items(categoryNames) { cat ->
                            val isSel = customCategoryFilter == cat
                            val count = allSongs.count { it.aiCategory?.trim()?.equals(cat.trim(), ignoreCase = true) == true }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) PrimaryCyan else DarkSurfaceBorder.copy(alpha = 0.4f))
                                    .clickable { customCategoryFilter = cat }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "$cat ($count)",
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) Color.Black else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search Songs") },
                        placeholder = { Text("Filter by title or artist") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Selected ${selectedSongIds.size} songs",
                        fontSize = 11.sp,
                        color = PrimaryCyan,
                        fontWeight = FontWeight.Bold
                    )

                    val filtered = remember(searchQuery, customCategoryFilter, allSongs) {
                        allSongs.filter { song ->
                            val matchesQuery = searchQuery.isBlank() ||
                                    song.title.contains(searchQuery, ignoreCase = true) ||
                                    song.artist.contains(searchQuery, ignoreCase = true)
                            val matchesCategory = customCategoryFilter == null ||
                                    song.aiCategory?.trim()?.equals(customCategoryFilter!!.trim(), ignoreCase = true) == true
                            matchesQuery && matchesCategory
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    ) {
                        items(filtered.take(60), key = { it.id }) { song ->
                            val isChecked = selectedSongIds.contains(song.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) selectedSongIds.remove(song.id)
                                        else selectedSongIds.add(song.id)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedSongIds.add(song.id)
                                        else selectedSongIds.remove(song.id)
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = PrimaryCyan)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = song.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(text = song.artist, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                if (!song.aiCategory.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(SecondaryViolet.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = song.aiCategory!!, fontSize = 9.sp, color = PrimaryCyan)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val playlistTitle = name.ifBlank {
                                if (mode == "CATEGORY") {
                                    if (selectedCategories.isNotEmpty()) "${selectedCategories.joinToString(" & ")} Mix" else "Mood Mix"
                                } else "Custom Mix"
                            }
                            if (mode == "CATEGORY") {
                                onCreateCategory(playlistTitle, description, selectedCategories.toList()) { _, _ -> }
                            } else {
                                onCreateCustom(playlistTitle, description, selectedSongIds.toList()) { _, _ -> }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = Color.Black)
                    ) {
                        Text("Create", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
