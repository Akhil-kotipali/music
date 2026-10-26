package com.example.audioindexer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.audioindexer.data.model.Song
import com.example.audioindexer.playback.FatigueQueueStats
import com.example.audioindexer.playback.PlaybackState
import com.example.audioindexer.ui.theme.AccentGreen
import com.example.audioindexer.ui.theme.DarkSurfaceBorder
import com.example.audioindexer.ui.theme.PrimaryCyan
import com.example.audioindexer.ui.theme.SecondaryViolet
import com.example.audioindexer.ui.theme.TertiaryAmber
import com.example.audioindexer.util.StorageUtils

@Composable
fun MiniPlaybackBar(
    playbackState: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song = playbackState.currentSong ?: return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .border(1.dp, PrimaryCyan.copy(alpha = 0.35f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            // Seek progress line
            val progress = if (playbackState.durationMs > 0)
                (playbackState.currentPositionMs.toFloat() / playbackState.durationMs).coerceIn(0f, 1f)
            else 0f

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = PrimaryCyan,
                trackColor = DarkSurfaceBorder
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Icon
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SecondaryViolet.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = PrimaryCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title, Artist, & Fatigue Cycle Badge
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = song.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (!song.aiCategory.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(PrimaryCyan.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = song.aiCategory,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryCyan
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = song.artist,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        playbackState.queueStats?.let { stats ->
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• Fatigue Cycle ${stats.cycleNumber} (${stats.playedInCurrentCycle}/${stats.totalSongsInPlaylist})",
                                fontSize = 10.sp,
                                color = AccentGreen
                            )
                        }
                    }
                }

                // Controls: Previous, Play/Pause, Next
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onSkipPrevious, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = MaterialTheme.colorScheme.onSurface)
                    }

                    FilledIconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(38.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = PrimaryCyan, contentColor = Color.Black)
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play"
                        )
                    }

                    IconButton(onClick = onSkipNext, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
fun FullPlayerDialog(
    playbackState: PlaybackState,
    remainingQueue: List<Song>,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffleRemaining: () -> Unit,
    onDismiss: () -> Unit
) {
    val song = playbackState.currentSong ?: return
    var sliderPosition by remember(playbackState.currentPositionMs) {
        mutableFloatStateOf(playbackState.currentPositionMs.toFloat())
    }
    var isUserSeeking by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ExoPlayer Media Session",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Big Art Visualizer Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(SecondaryViolet.copy(alpha = 0.4f), PrimaryCyan.copy(alpha = 0.2f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = PrimaryCyan,
                        modifier = Modifier.size(64.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Song Title & Artist
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${song.artist} • ${song.album}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // AI Category Pill
                if (!song.aiCategory.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(PrimaryCyan.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "AI Mood: ${song.aiCategory}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Seek Slider
                val duration = if (playbackState.durationMs > 0) playbackState.durationMs.toFloat() else 1f
                Slider(
                    value = if (isUserSeeking) sliderPosition else playbackState.currentPositionMs.toFloat().coerceIn(0f, duration),
                    onValueChange = {
                        isUserSeeking = true
                        sliderPosition = it
                    },
                    onValueChangeFinished = {
                        isUserSeeking = false
                        onSeek(sliderPosition.toLong())
                    },
                    valueRange = 0f..duration,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryCyan,
                        activeTrackColor = PrimaryCyan,
                        inactiveTrackColor = DarkSurfaceBorder
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = StorageUtils.formatDuration(playbackState.currentPositionMs),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = StorageUtils.formatDuration(playbackState.durationMs),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Standard Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onShuffleRemaining) {
                        Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = MaterialTheme.colorScheme.onSurface)
                    }

                    IconButton(onClick = onSkipPrevious, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp))
                    }

                    FilledIconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = PrimaryCyan, contentColor = Color.Black)
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    IconButton(onClick = onSkipNext, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp))
                    }

                    IconButton(onClick = { /* loop mode */ }) {
                        Icon(Icons.Default.Repeat, contentDescription = "Repeat", tint = AccentGreen)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Fatigue Prevention Queue Overview
                playbackState.queueStats?.let { stats ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceBorder.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Fatigue Prevention Queue",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGreen
                                )
                                Text(
                                    text = "Cycle ${stats.cycleNumber}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryCyan
                                )
                            }
                            Text(
                                text = "Zero repeats until all ${stats.totalSongsInPlaylist} songs played (${stats.playedInCurrentCycle} played, ${stats.remainingInCurrentCycle} remaining).",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (remainingQueue.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Up next: ${remainingQueue.take(3).joinToString { it.title }}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
