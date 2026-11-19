package com.classjam.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.classjam.games.engine.GameCoordinator
import com.classjam.network.protocol.GameType
import com.classjam.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameHostControlsScreen(
    gameCoordinator: GameCoordinator,
    onBackClick: () -> Unit
) {
    val activeGameType by gameCoordinator.activeGameType.collectAsState()
    val scoreboard by gameCoordinator.scoreboardState.collectAsState()

    Scaffold(
        containerColor = JamBackground,
        topBar = {
            TopAppBar(
                title = { Text("Party Games Hub", color = JamOnSurface, fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Active Game Status Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (activeGameType != GameType.NONE) JamPrimary.copy(alpha = 0.08f) else JamSurface
                ),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (activeGameType != GameType.NONE) JamPrimary.copy(alpha = 0.3f) else JamSurfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (activeGameType != GameType.NONE) "ACTIVE: ${activeGameType.title.uppercase()}" else "NO GAME ACTIVE",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (activeGameType != GameType.NONE) JamPrimary else JamOnSurfaceMuted
                        )
                        if (activeGameType != GameType.NONE) {
                            Button(
                                onClick = { gameCoordinator.stopGame() },
                                colors = ButtonDefaults.buttonColors(containerColor = JamError),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Stop Game")
                            }
                        }
                    }

                    Text(
                        text = scoreboard.latestEventSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = JamOnSurface
                    )
                }
            }

            // Launch Games List
            Text("Select Party Game", style = MaterialTheme.typography.titleSmall, color = JamOnSurface, fontWeight = FontWeight.Bold)

            GameType.entries.filter { it != GameType.NONE }.forEach { gameType ->
                val isSelected = activeGameType == gameType
                Card(
                    colors = CardDefaults.cardColors(containerColor = JamSurface),
                    shape = RoundedCornerShape(14.dp),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, JamPrimary) else androidx.compose.foundation.BorderStroke(1.dp, JamSurfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = gameType.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = JamOnSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = gameType.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = JamOnSurfaceMuted
                            )
                        }

                        Button(
                            onClick = { gameCoordinator.startGame(gameType) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) JamSecondary else JamPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (isSelected) "Restart" else "Start")
                        }
                    }
                }
            }

            // Scoreboard Card
            Card(
                colors = CardDefaults.cardColors(containerColor = JamSurface),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, JamSurfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Scoreboard (Round ${scoreboard.roundNumber})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = JamOnSurface
                        )
                        TextButton(onClick = { gameCoordinator.resetScores() }) {
                            Text("Reset Scores", color = JamError)
                        }
                    }

                    if (scoreboard.scores.isEmpty()) {
                        Text("No scores recorded yet.", color = JamOnSurfaceMuted, style = MaterialTheme.typography.bodySmall)
                    } else {
                        scoreboard.scores.forEachIndexed { index, s ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "#${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (index == 0) JamAccent else JamOnSurfaceMuted,
                                        modifier = Modifier.width(28.dp)
                                    )
                                    Text(s.displayName, style = MaterialTheme.typography.bodyMedium, color = JamOnSurface)
                                }
                                Text(
                                    text = "${s.score} pts",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (s.score >= 0) JamSecondary else JamError
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
