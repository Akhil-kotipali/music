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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
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
import com.example.audioindexer.playback.EngineStateMode
import com.example.audioindexer.playback.FatigueQueueStats
import com.example.audioindexer.playback.PlaybackState
import com.example.audioindexer.playback.SkipEngineStatus
import com.example.audioindexer.ui.theme.AccentGreen
import com.example.audioindexer.ui.theme.AccentOrange
import com.example.audioindexer.ui.theme.AccentRed
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

                // Title, Artist, & Engine Status Badge
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

                        // Skip Engine badge in mini player
                        playbackState.engineStatus?.let { eng ->
                            if (eng.mode != EngineStateMode.NORMAL) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            when (eng.mode) {
                                                EngineStateMode.HUNTING_MODE -> PrimaryCyan.copy(alpha = 0.2f)
                                                EngineStateMode.CONTRASTED_FILTER -> SecondaryViolet.copy(alpha = 0.2f)
                                                EngineStateMode.SHORT_TERM_PIVOT -> AccentOrange.copy(alpha = 0.2f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                    Text(
                                        text = when (eng.mode) {
                                            EngineStateMode.HUNTING_MODE -> "HUNTING"
                                            EngineStateMode.CONTRASTED_FILTER -> "CONTRAST"
                                            EngineStateMode.SHORT_TERM_PIVOT -> "PIVOT"
                                            else -> ""
                                        },
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (eng.mode) {
                                            EngineStateMode.HUNTING_MODE -> PrimaryCyan
                                            EngineStateMode.CONTRASTED_FILTER -> SecondaryViolet
                                            EngineStateMode.SHORT_TERM_PIVOT -> AccentOrange
                                            else -> Color.White
                                        }
                                    )
                                }
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
                                text = "• Zero-Repeat (${stats.playedInCurrentCycle}/${stats.totalSongsInPlaylist})",
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
fun AnimatedEqualizerBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    val h1 by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(310, easing = LinearEasing), RepeatMode.Reverse), label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(540, easing = LinearEasing), RepeatMode.Reverse), label = "h3"
    )
    val h4 by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(370, easing = LinearEasing), RepeatMode.Reverse), label = "h4"
    )
    val h5 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(480, easing = LinearEasing), RepeatMode.Reverse), label = "h5"
    )
    val h6 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(390, easing = LinearEasing), RepeatMode.Reverse), label = "h6"
    )

    val list = listOf(h1, h2, h3, h4, h5, h6)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        list.forEach { heightRatio ->
            val scale = if (isPlaying) heightRatio else 0.2f
            Box(
                modifier = Modifier
                    .width(7.dp)
                    .height((46 * scale).coerceAtLeast(8f).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(PrimaryCyan)
            )
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
    onDismiss: () -> Unit,
    playbackSpeed: Float = 1.0f,
    onSetPlaybackSpeed: (Float) -> Unit = {},
    sleepTimerRemainingSec: Int? = null,
    onStartSleepTimer: (Int) -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    currentPreset: String = "Balanced",
    onSetPreset: (String) -> Unit = {}
) {
    val song = playbackState.currentSong ?: return
    var sliderPosition by remember(playbackState.currentPositionMs) {
        mutableFloatStateOf(playbackState.currentPositionMs.toFloat())
    }
    var isUserSeeking by remember { mutableStateOf(false) }
    var showSleepTimerMenu by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
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

                Spacer(modifier = Modifier.height(12.dp))

                // Big Art Visualizer Box with Animated Waves
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(SecondaryViolet.copy(alpha = 0.45f), PrimaryCyan.copy(alpha = 0.2f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AnimatedEqualizerBars(
                            isPlaying = playbackState.isPlaying,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text(
                            text = if (playbackState.isPlaying) "Playing • Hi-Fi Audio Stream" else "Paused",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = PrimaryCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Song Title & Artist
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${song.artist} • ${song.album}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Skip Engine Status Bar
                playbackState.engineStatus?.let { engine ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when (engine.mode) {
                                    EngineStateMode.HUNTING_MODE -> PrimaryCyan.copy(alpha = 0.15f)
                                    EngineStateMode.CONTRASTED_FILTER -> SecondaryViolet.copy(alpha = 0.15f)
                                    EngineStateMode.SHORT_TERM_PIVOT -> AccentOrange.copy(alpha = 0.15f)
                                    EngineStateMode.NORMAL -> DarkSurfaceBorder.copy(alpha = 0.4f)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (engine.mode) {
                                    EngineStateMode.HUNTING_MODE -> Icons.Default.Search
                                    EngineStateMode.CONTRASTED_FILTER -> Icons.Default.ElectricBolt
                                    EngineStateMode.SHORT_TERM_PIVOT -> Icons.Default.Speed
                                    EngineStateMode.NORMAL -> Icons.Default.MusicNote
                                },
                                contentDescription = null,
                                tint = when (engine.mode) {
                                    EngineStateMode.HUNTING_MODE -> PrimaryCyan
                                    EngineStateMode.CONTRASTED_FILTER -> SecondaryViolet
                                    EngineStateMode.SHORT_TERM_PIVOT -> AccentOrange
                                    EngineStateMode.NORMAL -> AccentGreen
                                },
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = engine.lastActionDescription,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

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

                // Standard Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onShuffleRemaining) {
                        Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = MaterialTheme.colorScheme.onSurface)
                    }

                    IconButton(onClick = onSkipPrevious, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(30.dp))
                    }

                    FilledIconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = PrimaryCyan, contentColor = Color.Black)
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    IconButton(onClick = onSkipNext, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(30.dp))
                    }

                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Repeat, contentDescription = "Repeat", tint = AccentGreen)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Playback Speed Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Speed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                            val isSelected = (playbackSpeed == spd)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) PrimaryCyan else DarkSurfaceBorder.copy(alpha = 0.4f))
                                    .clickable { onSetPlaybackSpeed(spd) }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "${spd}x",
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Sleep Timer & Audio Preset Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sleep Timer Chip
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sleepTimerRemainingSec != null) AccentOrange.copy(alpha = 0.2f) else DarkSurfaceBorder.copy(alpha = 0.4f))
                            .border(1.dp, if (sleepTimerRemainingSec != null) AccentOrange else DarkSurfaceBorder, RoundedCornerShape(8.dp))
                            .clickable { showSleepTimerMenu = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = "Timer",
                                tint = if (sleepTimerRemainingSec != null) AccentOrange else PrimaryCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (sleepTimerRemainingSec != null) {
                                    val m = sleepTimerRemainingSec / 60
                                    val s = sleepTimerRemainingSec % 60
                                    "${m}:${s.toString().padStart(2, '0')}"
                                } else "Sleep Timer",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }

                    // Sound EQ Preset Chip (cycle presets on click)
                    val presets = listOf("Balanced", "Bass Boost", "Vocal Clear", "Treble Boost")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SecondaryViolet.copy(alpha = 0.2f))
                            .border(1.dp, SecondaryViolet.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable {
                                val nextIdx = (presets.indexOf(currentPreset) + 1) % presets.size
                                onSetPreset(presets[nextIdx])
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = "EQ", tint = SecondaryViolet, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = currentPreset,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Sleep Timer Selector Popup
                if (showSleepTimerMenu) {
                    Dialog(onDismissRequest = { showSleepTimerMenu = false }) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Set Sleep Timer",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                listOf(15, 30, 45, 60).forEach { mins ->
                                    Button(
                                        onClick = {
                                            onStartSleepTimer(mins)
                                            showSleepTimerMenu = false
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("$mins minutes")
                                    }
                                }
                                if (sleepTimerRemainingSec != null) {
                                    OutlinedButton(
                                        onClick = {
                                            onCancelSleepTimer()
                                            showSleepTimerMenu = false
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Turn Off Sleep Timer", color = AccentOrange)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Fatigue Prevention Queue Overview
                playbackState.queueStats?.let { stats ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceBorder.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Fatigue Prevention Queue",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGreen
                                )
                                Text(
                                    text = "Cycle ${stats.cycleNumber}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryCyan
                                )
                            }
                            Text(
                                text = "Zero repeats until all ${stats.totalSongsInPlaylist} tracks played (${stats.playedInCurrentCycle} played, ${stats.remainingInCurrentCycle} remaining).",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (remainingQueue.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
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
