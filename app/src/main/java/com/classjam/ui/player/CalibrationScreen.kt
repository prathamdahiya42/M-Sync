package com.classjam.ui.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classjam.sync.DriftCorrectionState
import com.classjam.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    currentOffsetMs: Long,
    currentDriftMs: Long,
    playbackSpeed: Float,
    correctionState: DriftCorrectionState,
    isClickTrackPlaying: Boolean,
    onOffsetChange: (Long) -> Unit,
    onToggleClickTrack: () -> Unit,
    onBackClick: () -> Unit
) {
    var sliderValue by remember(currentOffsetMs) { mutableFloatStateOf(currentOffsetMs.toFloat()) }

    // Pulsing metronome visualizer
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Scaffold(
        containerColor = JamBackground,
        topBar = {
            TopAppBar(
                title = { Text("Audio Sync Calibration", color = JamOnSurface, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = JamOnSurface)
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
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Pulsing Metronome Circle
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(if (isClickTrackPlaying) pulseScale else 1.0f)
                    .background(
                        color = if (isClickTrackPlaying) Color(0xFFFFCCD5) else JamSurfaceVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Hearing,
                    contentDescription = null,
                    tint = if (isClickTrackPlaying) JamPrimary else JamOnSurfaceMuted,
                    modifier = Modifier.size(64.dp)
                )
            }

            // Click Track Status & Toggle
            Button(
                onClick = onToggleClickTrack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isClickTrackPlaying) JamError else JamPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(
                    imageVector = if (isClickTrackPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isClickTrackPlaying) "Stop Click Track" else "Play Metronome Click Track",
                    fontWeight = FontWeight.Bold
                )
            }

            // Real-Time Drift & Speed Badge
            Card(
                colors = CardDefaults.cardColors(containerColor = JamSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, JamSurfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Active Drift", style = MaterialTheme.typography.bodySmall, color = JamOnSurfaceMuted)
                        Text(
                            text = "%+d ms".format(currentDriftMs),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (kotlin.math.abs(currentDriftMs) < 20) JamSecondary else JamPrimary
                            )
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("Playback Speed", style = MaterialTheme.typography.bodySmall, color = JamOnSurfaceMuted)
                        Text(
                            text = "%.2fx".format(playbackSpeed),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (playbackSpeed == 1.0f) JamOnSurface else JamSecondary
                            )
                        )
                    }
                }
            }

            // Manual Offset Slider Card
            Card(
                colors = CardDefaults.cardColors(containerColor = JamSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, JamSurfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Manual Offset (Bluetooth / Speaker)",
                            style = MaterialTheme.typography.titleSmall,
                            color = JamOnSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "%+d ms".format(sliderValue.toLong()),
                            style = MaterialTheme.typography.titleMedium,
                            color = JamPrimary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Text(
                        text = "Bluetooth speakers typically add 100-250ms delay. Adjust until your phone's click fuses into the host's click with zero echo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JamOnSurfaceMuted
                    )

                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            sliderValue = it
                            onOffsetChange(it.toLong())
                        },
                        valueRange = -300f..300f,
                        steps = 119, // 5ms steps
                        colors = SliderDefaults.colors(
                            thumbColor = JamPrimary,
                            activeTrackColor = JamPrimary,
                            inactiveTrackColor = JamSurfaceVariant
                        )
                    )

                    // Micro-adjustment buttons (-5ms / +5ms)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(
                            onClick = {
                                val next = (sliderValue - 5).coerceAtLeast(-300f)
                                sliderValue = next
                                onOffsetChange(next.toLong())
                            },
                            border = androidx.compose.foundation.BorderStroke(1.dp, JamPrimary.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("-5 ms", color = JamPrimary, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                sliderValue = 0f
                                onOffsetChange(0L)
                            },
                            border = androidx.compose.foundation.BorderStroke(1.dp, JamSurfaceVariant),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Reset (0)", color = JamOnSurfaceMuted)
                        }

                        OutlinedButton(
                            onClick = {
                                val next = (sliderValue + 5).coerceAtMost(300f)
                                sliderValue = next
                                onOffsetChange(next.toLong())
                            },
                            border = androidx.compose.foundation.BorderStroke(1.dp, JamPrimary.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("+5 ms", color = JamPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    }
                }
            }
        }
    }
}
