package com.classjam.ui.games

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classjam.core.model.Device
import com.classjam.network.protocol.GameEventPayload
import com.classjam.network.protocol.GameType
import com.classjam.network.protocol.ScoreboardPayload
import com.classjam.sync.ClockSyncEngine
import com.classjam.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamePlayerScreen(
    activeGameType: GameType,
    latestEvent: GameEventPayload?,
    scoreboard: ScoreboardPayload?,
    connectedDevices: List<Device>,
    selfDeviceId: String,
    clockSyncEngine: ClockSyncEngine,
    onSendEvent: (GameEventPayload) -> Unit,
    onBackClick: () -> Unit
) {
    var hasSatDown by remember(latestEvent) { mutableStateOf(false) }
    var sitReactionMs by remember { mutableLongStateOf(0L) }

    Scaffold(
        containerColor = JamBackground,
        topBar = {
            TopAppBar(
                title = { Text(activeGameType.title, color = JamOnSurface, fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            when (activeGameType) {
                GameType.MUSICAL_CHAIRS -> {
                    val isMusicStopped = latestEvent?.event == "MUSIC_STOPPED"
                    val isRoundOver = latestEvent?.event == "ROUND_OVER"

                    if (isRoundOver) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = JamSurface),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Round Finished!", style = MaterialTheme.typography.titleLarge, color = JamAccent, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(latestEvent?.extraData ?: "", style = MaterialTheme.typography.bodyMedium, color = JamOnSurface)
                            }
                        }
                    } else if (isMusicStopped) {
                        if (!hasSatDown) {
                            Button(
                                onClick = {
                                    val clientTapNs = SystemClock.elapsedRealtimeNanos()
                                    val correctedTapNs = clockSyncEngine.toHostTimeNs(clientTapNs)
                                    val stopNs = latestEvent?.hostTimestampNs ?: clientTapNs
                                    sitReactionMs = ((correctedTapNs - stopNs) / 1_000_000L).coerceAtLeast(0L)
                                    hasSatDown = true

                                    onSendEvent(
                                        GameEventPayload(
                                            gameType = GameType.MUSICAL_CHAIRS,
                                            event = "SIT_TAP",
                                            senderDeviceId = selfDeviceId,
                                            correctedTimestampNs = correctedTapNs
                                        )
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = JamError),
                                shape = CircleShape,
                                modifier = Modifier.size(220.dp)
                            ) {
                                Text(
                                    text = "SIT!",
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontSize = 44.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                )
                            }
                        } else {
                            Surface(
                                color = JamSecondary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = JamSecondary, modifier = Modifier.size(54.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("You sat down!", style = MaterialTheme.typography.titleLarge, color = JamSecondary, fontWeight = FontWeight.Bold)
                                    Text("Reaction time: ${sitReactionMs}ms", style = MaterialTheme.typography.bodyMedium, color = JamOnSurface)
                                }
                            }
                        }
                    } else {
                        // Music playing - waiting to sit
                        Card(
                            colors = CardDefaults.cardColors(containerColor = JamSurface),
                            shape = RoundedCornerShape(18.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, JamSurfaceVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    color = JamPrimary.copy(alpha = 0.12f),
                                    shape = CircleShape,
                                    modifier = Modifier.size(90.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = JamPrimary, modifier = Modifier.size(50.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Music is Playing!", style = MaterialTheme.typography.titleLarge, color = JamOnSurface, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Listen closely and get ready to tap SIT when it cuts out!", color = JamOnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                GameType.HOT_POTATO -> {
                    val isHolding = latestEvent?.targetDeviceId == selfDeviceId
                    val isExploded = latestEvent?.event == "POTATO_EXPLODED"

                    if (isExploded) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = JamError.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = JamError, modifier = Modifier.size(60.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("BOOM! Potato Exploded!", style = MaterialTheme.typography.titleLarge, color = JamError, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(latestEvent?.extraData ?: "", color = JamOnSurface, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else if (isHolding) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = JamError),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Whatshot, contentDescription = null, tint = JamAccent, modifier = Modifier.size(60.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "YOU HAVE THE POTATO!",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = Color.White)
                                )
                                Text("Tap a friend below immediately to toss it!", color = Color.White, style = MaterialTheme.typography.bodyMedium)

                                Spacer(modifier = Modifier.height(16.dp))

                                val otherPlayers = connectedDevices.filter { it.deviceId != selfDeviceId && it.role != com.classjam.core.model.Role.ADMIN }
                                otherPlayers.forEach { player ->
                                    Button(
                                        onClick = {
                                            onSendEvent(
                                                GameEventPayload(
                                                    gameType = GameType.HOT_POTATO,
                                                    event = "PASS_POTATO",
                                                    senderDeviceId = selfDeviceId,
                                                    targetDeviceId = player.deviceId
                                                )
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(48.dp)
                                    ) {
                                        Text("Pass to ${player.displayName}", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = JamSurface),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = JamSecondary, modifier = Modifier.size(54.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("You are Safe!", style = MaterialTheme.typography.titleLarge, color = JamSecondary, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(latestEvent?.extraData ?: "Watch out, someone might pass to you!", color = JamOnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                GameType.BUZZER_GUESS -> {
                    val isLocked = latestEvent?.event == "BUZZER_LOCKED"
                    val iWonBuzzer = isLocked && latestEvent?.senderDeviceId == selfDeviceId

                    if (isLocked) {
                        Surface(
                            color = if (iWonBuzzer) JamAccent.copy(alpha = 0.25f) else JamSurfaceVariant,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (iWonBuzzer) Icons.Default.EmojiEvents else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (iWonBuzzer) JamAccent else JamOnSurfaceMuted,
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (iWonBuzzer) "YOU BUZZED IN FIRST!" else "BUZZER LOCKED",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (iWonBuzzer) JamAccent else JamOnSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(latestEvent?.extraData ?: "", color = JamOnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                val clientTapNs = SystemClock.elapsedRealtimeNanos()
                                val correctedTapNs = clockSyncEngine.toHostTimeNs(clientTapNs)
                                onSendEvent(
                                    GameEventPayload(
                                        gameType = GameType.BUZZER_GUESS,
                                        event = "BUZZ_IN",
                                        senderDeviceId = selfDeviceId,
                                        correctedTimestampNs = correctedTapNs
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JamPrimary),
                            shape = CircleShape,
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                            modifier = Modifier.size(220.dp)
                        ) {
                            Text(
                                text = "BUZZ IN!",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            )
                        }
                    }
                }

                GameType.NONE -> {
                    Text("Free DJ Mode Active", style = MaterialTheme.typography.titleMedium, color = JamOnSurface)
                }
            }

            // Scoreboard Summary
            if (scoreboard != null && scoreboard.scores.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = JamSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, JamSurfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Scoreboard", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = JamOnSurface)
                        scoreboard.scores.forEachIndexed { idx, s ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("#${idx + 1} ${s.displayName}", color = JamOnSurface, style = MaterialTheme.typography.bodyMedium)
                                Text("${s.score} pts", fontWeight = FontWeight.Bold, color = JamSecondary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
