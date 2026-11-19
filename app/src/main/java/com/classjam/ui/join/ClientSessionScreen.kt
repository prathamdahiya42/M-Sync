package com.classjam.ui.join

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.classjam.core.model.ConnectionState
import com.classjam.core.model.Device
import com.classjam.core.model.Role
import com.classjam.core.model.Track
import com.classjam.network.client.KtorClientSocket
import com.classjam.ui.host.RoleBadge
import com.classjam.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientSessionScreen(
    clientSocket: KtorClientSocket,
    currentTrack: Track?,
    isPlaying: Boolean,
    onNowPlayingClick: () -> Unit,
    onBrowseMusicClick: () -> Unit,
    onViewQueueClick: () -> Unit,
    onLeaveClick: () -> Unit
) {
    val connectionState by clientSocket.connectionState.collectAsState()
    val currentRole by clientSocket.currentRole.collectAsState()
    val deviceList by clientSocket.deviceList.collectAsState()

    var roomName by remember { mutableStateOf("Class Jam") }
    var kickedReason by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(connectionState) {
        when (val state = connectionState) {
            is ConnectionState.Connected -> {
                roomName = state.roomName
            }
            is ConnectionState.Disconnected -> {
                kickedReason = state.reason
            }
            else -> {}
        }
    }

    if (kickedReason != null) {
        AlertDialog(
            onDismissRequest = onLeaveClick,
            title = { Text("Session Ended", color = JamError, fontWeight = FontWeight.Bold) },
            text = { Text(kickedReason ?: "Disconnected from host", color = JamOnSurface) },
            confirmButton = {
                TextButton(onClick = onLeaveClick) {
                    Text("OK", color = JamPrimary)
                }
            },
            containerColor = JamSurface
        )
    }

    Scaffold(
        containerColor = JamBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = roomName,
                            style = MaterialTheme.typography.titleMedium.copy(color = JamOnSurface, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Connected to Class Jam",
                            style = MaterialTheme.typography.labelSmall.copy(color = JamSecondary)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            clientSocket.leave()
                            onLeaveClick()
                        }
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null, tint = JamError)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Leave", color = JamError, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = JamBackground)
            )
        },
        bottomBar = {
            if (currentTrack != null) {
                Surface(
                    color = JamSurface,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    border = BorderStroke(1.dp, JamSurfaceVariant),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNowPlayingClick() }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                color = JamPrimary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.MusicNote else Icons.Default.Pause,
                                        contentDescription = null,
                                        tint = JamPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = currentTrack.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = JamOnSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = currentTrack.artist,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JamOnSurfaceMuted,
                                    maxLines = 1
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Open Player",
                            tint = JamPrimary
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Role Announcement Card
            Card(
                colors = CardDefaults.cardColors(containerColor = JamSurface),
                shape = RoundedCornerShape(20.dp),
                border = if (currentRole == Role.DJ) BorderStroke(2.dp, JamAccent) else BorderStroke(1.dp, JamSurfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RoleBadge(role = currentRole)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (currentRole == Role.DJ) "YOU ARE THE DJ!" else "YOU ARE A PLAYER",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = if (currentRole == Role.DJ) JamAccent else JamOnSurface
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (currentRole == Role.DJ) {
                            "The host granted you DJ privileges! Browse music and add to the queue."
                        } else {
                            "Synced music is playing in perfect time with your group!"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = JamOnSurfaceMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    if (currentRole == Role.DJ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = onBrowseMusicClick,
                                colors = ButtonDefaults.buttonColors(containerColor = JamPrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pick Music", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = onViewQueueClick,
                                border = BorderStroke(1.dp, JamPrimary.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("View Queue", color = JamPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Quick Now Playing / Queue Shortcut
            Card(
                colors = CardDefaults.cardColors(containerColor = JamSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, JamSurfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNowPlayingClick() }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = JamPrimary.copy(alpha = 0.12f),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = JamPrimary, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Open Synced Player", color = JamOnSurface, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = JamPrimary)
                }
            }

            // Friends List
            Text(
                text = "Friends in Room (${deviceList.size})",
                style = MaterialTheme.typography.titleSmall,
                color = JamOnSurface,
                fontWeight = FontWeight.Bold
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(deviceList, key = { it.deviceId }) { device ->
                    val isSelf = device.deviceId == clientSocket.deviceId
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isSelf) JamSurfaceVariant else JamSurface),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (isSelf) JamPrimary.copy(alpha = 0.4f) else JamSurfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (device.role == Role.ADMIN) Icons.Default.Shield
                                    else if (device.role == Role.DJ) Icons.Default.Headphones
                                    else Icons.Default.Smartphone,
                                    contentDescription = null,
                                    tint = if (device.role == Role.ADMIN) JamPrimary
                                    else if (device.role == Role.DJ) JamAccent
                                    else JamOnSurfaceMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = device.displayName + if (isSelf) " (You)" else "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelf) FontWeight.Bold else FontWeight.Normal,
                                        color = JamOnSurface
                                    )
                                    Text(
                                        text = "${device.latencyMs}ms latency",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = JamOnSurfaceMuted
                                    )
                                }
                            }
                            RoleBadge(role = device.role)
                        }
                    }
                }
            }
        }
    }
}
