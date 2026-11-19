package com.classjam.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.classjam.core.datastore.AppPreferences
import com.classjam.core.model.NetworkMode
import com.classjam.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appPreferences: AppPreferences,
    isHost: Boolean,
    onOpenCalibrationClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    val networkMode by appPreferences.networkModeFlow.collectAsState(initial = NetworkMode.IN_APP_HOTSPOT)
    val maxClients by appPreferences.maxClientsFlow.collectAsState(initial = 10)
    val serverPort by appPreferences.serverPortFlow.collectAsState(initial = 8080)
    val keepSongs by appPreferences.keepDownloadedSongsFlow.collectAsState(initial = false)
    val manualOffsetMs by appPreferences.manualOffsetMsFlow.collectAsState(initial = 0L)

    var maxClientsInput by remember(maxClients) { mutableIntStateOf(maxClients) }
    var portInput by remember(serverPort) { mutableStateOf(serverPort.toString()) }

    Scaffold(
        containerColor = JamBackground,
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = JamOnSurface, fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Audio Calibration Section
            Card(
                colors = CardDefaults.cardColors(containerColor = JamSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, JamSurfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = JamPrimary.copy(alpha = 0.12f),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = JamPrimary, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Audio Latency & Calibration",
                            style = MaterialTheme.typography.titleMedium,
                            color = JamOnSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Current Offset: %+d ms".format(manualOffsetMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = JamPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tune audio sync by ear using our interactive metronome click track. Indispensable for Bluetooth speakers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JamOnSurfaceMuted
                    )
                    Button(
                        onClick = onOpenCalibrationClick,
                        colors = ButtonDefaults.buttonColors(containerColor = JamPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.Hearing, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Calibration Mode", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Cache & Storage Section
            Card(
                colors = CardDefaults.cardColors(containerColor = JamSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, JamSurfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Keep Downloaded Songs",
                            style = MaterialTheme.typography.titleSmall,
                            color = JamOnSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Preserve audio files in phone cache when leaving a session (default is auto-delete).",
                            style = MaterialTheme.typography.bodySmall,
                            color = JamOnSurfaceMuted
                        )
                    }
                    Switch(
                        checked = keepSongs,
                        onCheckedChange = {
                            coroutineScope.launch { appPreferences.setKeepDownloadedSongs(it) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                            checkedTrackColor = JamPrimary
                        )
                    )
                }
            }

            // Host-specific Network Configuration
            if (isHost) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = JamSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, JamSurfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = JamPrimary.copy(alpha = 0.12f),
                                shape = androidx.compose.foundation.shape.CircleShape,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Router, contentDescription = null, tint = JamPrimary, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Host Room Settings",
                                style = MaterialTheme.typography.titleMedium,
                                color = JamOnSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Max Clients Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Max Connected Clients", style = MaterialTheme.typography.bodyMedium, color = JamOnSurface)
                                Text("$maxClientsInput clients", style = MaterialTheme.typography.bodyMedium, color = JamPrimary, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = maxClientsInput.toFloat(),
                                onValueChange = {
                                    maxClientsInput = it.toInt()
                                    coroutineScope.launch { appPreferences.setMaxClients(it.toInt()) }
                                },
                                valueRange = 2f..30f,
                                steps = 27,
                                colors = SliderDefaults.colors(
                                    thumbColor = JamPrimary,
                                    activeTrackColor = JamPrimary,
                                    inactiveTrackColor = JamSurfaceVariant
                                )
                            )
                        }

                        // Server Port Input
                        OutlinedTextField(
                            value = portInput,
                            onValueChange = {
                                portInput = it
                                it.toIntOrNull()?.let { p ->
                                    coroutineScope.launch { appPreferences.setServerPort(p) }
                                }
                            },
                            label = { Text("Server Port") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JamPrimary,
                                unfocusedBorderColor = JamSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    }
}
