package com.classjam.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classjam.core.model.Role
import com.classjam.core.model.RolePermissions
import com.classjam.core.model.Track
import com.classjam.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    currentTrack: Track?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    userRole: Role,
    masterVolume: Float,
    localVolume: Float,
    isMuted: Boolean,
    outputGroupCount: Int,
    clockOffsetNs: Long,
    averageRttNs: Long,
    onPlayPauseClick: () -> Unit,
    onSkipClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onMasterVolumeChange: (Float) -> Unit,
    onLocalVolumeChange: (Float) -> Unit,
    onViewQueueClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val canControl = RolePermissions.canControlPlayback(userRole)
    val isAdmin = userRole == Role.ADMIN

    var seekingPosition by remember { mutableStateOf<Long?>(null) }
    val displayPosition = seekingPosition ?: currentPositionMs
    val totalDuration = if (durationMs > 0) durationMs else (currentTrack?.durationMs ?: 1L)

    val offsetMs = clockOffsetNs / 1_000_000.0
    val rttMs = averageRttNs / 1_000_000.0

    Scaffold(
        containerColor = JamBackground,
        topBar = {
            TopAppBar(
                title = { Text("Now Playing", color = JamOnSurface, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = JamOnSurface)
                    }
                },
                actions = {
                    IconButton(onClick = onViewQueueClick) {
                        Icon(Icons.Default.QueueMusic, contentDescription = "Queue", tint = JamAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = JamBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Album Art Glow Box
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFFFFCCD5), JamSurfaceVariant),
                            radius = 450f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.85f),
                    shape = CircleShape,
                    modifier = Modifier.size(110.dp),
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = JamPrimary,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }
            }

            // Song Title and Artist
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentTrack?.title ?: "No Song Selected",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = JamOnSurface
                    ),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentTrack?.artist ?: "Tap Queue to select music",
                    style = MaterialTheme.typography.bodyLarge,
                    color = JamOnSurfaceMuted,
                    maxLines = 1
                )
            }

            // Output Group & Sync Status Pill
            Surface(
                color = JamSurfaceVariant,
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCCD5))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SpeakerGroup,
                        contentDescription = null,
                        tint = JamPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Synced on $outputGroupCount phones • Offset: %.1f ms".format(offsetMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = JamOnSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Timeline Scrub Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = displayPosition.toFloat(),
                    onValueChange = { if (canControl) seekingPosition = it.toLong() },
                    onValueChangeFinished = {
                        seekingPosition?.let { onSeek(it) }
                        seekingPosition = null
                    },
                    valueRange = 0f..totalDuration.toFloat(),
                    enabled = canControl,
                    colors = SliderDefaults.colors(
                        thumbColor = JamPrimary,
                        activeTrackColor = JamPrimary,
                        inactiveTrackColor = JamSurfaceVariant
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTimeMs(displayPosition),
                        style = MaterialTheme.typography.labelSmall,
                        color = JamOnSurfaceMuted
                    )
                    Text(
                        text = formatTimeMs(totalDuration),
                        style = MaterialTheme.typography.labelSmall,
                        color = JamOnSurfaceMuted
                    )
                }
            }

            // Playback Controls (Big Touch Targets)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play / Pause Big Center Button
                FilledIconButton(
                    onClick = onPlayPauseClick,
                    enabled = canControl,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (canControl) JamPrimary else JamSurfaceVariant
                    ),
                    modifier = Modifier.size(76.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.width(28.dp))

                // Skip Next Button
                IconButton(
                    onClick = onSkipClick,
                    enabled = canControl,
                    modifier = Modifier
                        .size(54.dp)
                        .background(JamSurfaceVariant, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Skip Next",
                        tint = if (canControl) JamOnSurface else JamOnSurfaceMuted,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            if (!canControl) {
                Text(
                    text = "The DJ or Admin controls the music for the room.",
                    style = MaterialTheme.typography.bodySmall,
                    color = JamOnSurfaceMuted
                )
            }

            // Volume Section
            Card(
                colors = CardDefaults.cardColors(containerColor = JamSurface),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, JamSurfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Local Phone Volume
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = if (isMuted) JamError else JamPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isMuted) "This Phone (MUTED BY HOST)" else "This Phone Volume",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = JamOnSurface
                        )
                    }
                    Slider(
                        value = localVolume,
                        onValueChange = onLocalVolumeChange,
                        enabled = !isMuted,
                        colors = SliderDefaults.colors(
                            thumbColor = JamPrimary,
                            activeTrackColor = JamPrimary,
                            inactiveTrackColor = JamSurfaceVariant
                        )
                    )

                    // Master Volume (Admin only)
                    if (isAdmin) {
                        HorizontalDivider(color = JamSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speaker, contentDescription = null, tint = JamAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Master Volume (All Phones)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = JamOnSurface
                            )
                        }
                        Slider(
                            value = masterVolume,
                            onValueChange = onMasterVolumeChange,
                            colors = SliderDefaults.colors(
                                thumbColor = JamAccent,
                                activeTrackColor = JamAccent,
                                inactiveTrackColor = JamSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimeMs(timeMs: Long): String {
    val totalSec = timeMs / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
