package com.classjam.ui.host

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classjam.core.model.Device
import com.classjam.core.model.NetworkMode
import com.classjam.core.model.QrPayload
import com.classjam.core.model.Role
import com.classjam.core.util.ForceMaxBrightness
import com.classjam.network.hotspot.HotspotState
import com.classjam.network.server.KtorHostServer
import com.classjam.qr.QrCodeGenerator
import com.classjam.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostLobbyScreen(
    server: KtorHostServer,
    networkMode: NetworkMode,
    hotspotState: HotspotState,
    localIp: String,
    port: Int,
    roomCode: String,
    roomToken: String,
    roomName: String,
    manualSsid: String?,
    manualPassword: String?,
    onOpenPlayerClick: () -> Unit,
    onOpenQueueClick: () -> Unit,
    onOpenLibraryClick: () -> Unit,
    onLeaveSession: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedDeviceForAction by remember { mutableStateOf<Device?>(null) }

    val devicesMap by server.connectedDevices.collectAsState()
    val allDevices = devicesMap.values.toList()
    val nonHostClients = allDevices.filter { it.role != Role.ADMIN }
    val currentTrack by server.currentTrack.collectAsState()
    val isPlaying by server.isPlaying.collectAsState()

    if (selectedTab == 0) {
        ForceMaxBrightness()
    }

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var effectiveSsid by remember { mutableStateOf<String?>(null) }
    var effectivePassword by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(networkMode, hotspotState, localIp) {
        when (networkMode) {
            NetworkMode.IN_APP_HOTSPOT -> {
                if (hotspotState is HotspotState.Active) {
                    effectiveSsid = hotspotState.ssid
                    effectivePassword = hotspotState.password
                }
            }
            NetworkMode.MANUAL_HOTSPOT -> {
                effectiveSsid = manualSsid
                effectivePassword = manualPassword
            }
            NetworkMode.SAME_WIFI -> {
                effectiveSsid = null
                effectivePassword = null
            }
        }

        val payload = QrPayload(
            v = 1,
            ssid = effectiveSsid,
            password = effectivePassword,
            ip = localIp,
            port = port,
            token = roomToken,
            roomName = roomName,
            roomCode = roomCode
        )
        qrBitmap = QrCodeGenerator.generateQrBitmap(payload.toJson(), sizePx = 640)
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
                            text = "${allDevices.size} phones connected",
                            style = MaterialTheme.typography.labelSmall.copy(color = JamPrimary, fontWeight = FontWeight.Bold)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenPlayerClick) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Now Playing",
                            tint = JamPrimary
                        )
                    }
                    IconButton(onClick = onLeaveSession) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "End Session",
                            tint = JamError
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = JamBackground)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = JamSurface, tonalElevation = 6.dp) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.QrCode, contentDescription = null) },
                    label = { Text("QR Code") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = JamPrimary,
                        selectedTextColor = JamPrimary,
                        indicatorColor = JamSurfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        BadgedBox(badge = {
                            if (nonHostClients.isNotEmpty()) {
                                Badge(containerColor = JamPrimary) {
                                    Text(nonHostClients.size.toString(), color = Color.White)
                                }
                            }
                        }) {
                            Icon(Icons.Default.Group, contentDescription = null)
                        }
                    },
                    label = { Text("Friends & Roles") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = JamPrimary,
                        selectedTextColor = JamPrimary,
                        indicatorColor = JamSurfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.QueueMusic, contentDescription = null) },
                    label = { Text("Music Hub") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = JamPrimary,
                        selectedTextColor = JamPrimary,
                        indicatorColor = JamSurfaceVariant
                    )
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> {
                    // TAB 0: QR Code
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(2.dp, JamPrimary.copy(alpha = 0.2f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            modifier = Modifier
                                .size(280.dp)
                                .padding(8.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (qrBitmap != null) {
                                    Image(
                                        bitmap = qrBitmap!!.asImageBitmap(),
                                        contentDescription = "Host QR Code",
                                        modifier = Modifier.fillMaxSize().padding(12.dp)
                                    )
                                } else {
                                    CircularProgressIndicator(color = JamPrimary)
                                }
                            }
                        }

                        // 4-Digit Room Code Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = JamSurface),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, JamSurfaceBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Room Code (Manual)", style = MaterialTheme.typography.bodySmall, color = JamOnSurfaceMuted)
                                    Text(
                                        text = roomCode,
                                        style = MaterialTheme.typography.headlineLarge.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = JamPrimary,
                                            letterSpacing = 4.sp
                                        )
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Host Address", style = MaterialTheme.typography.bodySmall, color = JamOnSurfaceMuted)
                                    Text(
                                        text = "$localIp:$port",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = JamOnSurface)
                                    )
                                }
                            }
                        }

                        // Network Info Badge
                        Surface(
                            color = JamSurfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, JamSurfaceBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WifiTethering, contentDescription = null, tint = JamPrimary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = when (networkMode) {
                                        NetworkMode.IN_APP_HOTSPOT -> "Mode: In-App Hotspot" +
                                                if (effectiveSsid != null) " ($effectiveSsid)" else " (Starting...)"
                                        NetworkMode.MANUAL_HOTSPOT -> "Mode: Manual Hotspot ($effectiveSsid)"
                                        NetworkMode.SAME_WIFI -> "Mode: Same Wi-Fi ($localIp)"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = JamOnSurface
                                )
                            }
                        }

                        Button(
                            onClick = onLeaveSession,
                            colors = ButtonDefaults.buttonColors(containerColor = JamError),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("End Session", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                1 -> {
                    // TAB 1: Device Tiles Grid
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Connected Friends (${allDevices.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = JamOnSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(allDevices, key = { it.deviceId }) { device ->
                                DeviceTileItem(
                                    device = device,
                                    isHost = device.deviceId == server.hostDeviceId,
                                    onClick = {
                                        if (device.deviceId != server.hostDeviceId) {
                                            selectedDeviceForAction = device
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                2 -> {
                    // TAB 2: Music Hub & Controls
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = JamSurface),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, JamSurfaceBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenPlayerClick() }
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = JamSurfaceVariant,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.size(54.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.MusicNote else Icons.Default.Pause,
                                            contentDescription = null,
                                            tint = JamPrimary,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentTrack?.title ?: "No Song Playing",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = JamOnSurface,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = currentTrack?.artist ?: "Tap to pick music",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = JamOnSurfaceMuted,
                                        maxLines = 1
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = JamPrimary)
                            }
                        }

                        Button(
                            onClick = onOpenLibraryClick,
                            colors = ButtonDefaults.buttonColors(containerColor = JamPrimary),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                        ) {
                            Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Browse Music Library (Host Phone)", fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Button(
                            onClick = onOpenQueueClick,
                            colors = ButtonDefaults.buttonColors(containerColor = JamSurface),
                            border = BorderStroke(1.dp, JamSurfaceBorder),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                        ) {
                            Icon(Icons.Default.QueueMusic, contentDescription = null, tint = JamPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("View Upcoming Queue", color = JamOnSurface, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onOpenPlayerClick,
                            colors = ButtonDefaults.buttonColors(containerColor = JamSurfaceVariant),
                            border = BorderStroke(1.dp, JamPrimary.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = JamPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Full Synced Player", color = JamPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    selectedDeviceForAction?.let { device ->
        ModalBottomSheet(
            onDismissRequest = { selectedDeviceForAction = null },
            containerColor = JamSurface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = device.displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            color = JamOnSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Latency: ${device.latencyMs}ms | Current Role: ${device.role.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = JamOnSurfaceMuted
                        )
                    }
                    RoleBadge(role = device.role)
                }

                HorizontalDivider(color = JamSurfaceBorder, modifier = Modifier.padding(vertical = 4.dp))

                if (device.role == Role.DJ) {
                    Button(
                        onClick = {
                            server.revokeDj(device.deviceId)
                            selectedDeviceForAction = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = JamSurfaceVariant),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, tint = JamPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Revoke DJ Access", color = JamOnSurface, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            server.grantDj(device.deviceId)
                            selectedDeviceForAction = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = JamPrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Default.QueueMusic, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Grant DJ (Music Controller)", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                OutlinedButton(
                    onClick = {
                        server.toggleMute(device.deviceId)
                        selectedDeviceForAction = null
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, JamSurfaceBorder),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(
                        imageVector = if (device.isMuted) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = null,
                        tint = if (device.isMuted) JamSecondary else JamError
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (device.isMuted) "Unmute Audio" else "Mute Audio on this Phone",
                        color = JamOnSurface
                    )
                }

                OutlinedButton(
                    onClick = {
                        server.toggleOutputGroup(device.deviceId)
                        selectedDeviceForAction = null
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, JamSurfaceBorder),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(
                        imageVector = if (device.inOutputGroup) Icons.Default.SpeakerGroup else Icons.Default.SpeakerNotesOff,
                        contentDescription = null,
                        tint = JamPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (device.inOutputGroup) "Exclude from Output Group" else "Include in Output Group",
                        color = JamOnSurface
                    )
                }

                Button(
                    onClick = {
                        server.kickDevice(device.deviceId, "Removed from room by host")
                        selectedDeviceForAction = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JamError),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.PersonRemove, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Kick from Party", fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun DeviceTileItem(
    device: Device,
    isHost: Boolean,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = JamSurface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = if (device.role == Role.DJ || isHost) 1.5.dp else 1.dp,
            color = if (device.role == Role.DJ) JamAccent else if (isHost) JamPrimary else JamSurfaceBorder
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isHost) { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoleBadge(role = device.role)
                Text(
                    text = "${device.latencyMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (device.latencyMs < 30) JamSecondary else JamPrimary
                )
            }

            Text(
                text = device.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = JamOnSurface,
                maxLines = 1
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (device.isMuted) {
                    Surface(color = JamError.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = "MUTED",
                            style = MaterialTheme.typography.labelSmall,
                            color = JamError,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (!device.inOutputGroup) {
                    Surface(color = JamSurfaceVariant, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = "NO AUDIO",
                            style = MaterialTheme.typography.labelSmall,
                            color = JamOnSurfaceMuted,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (device.isReady) {
                    Surface(color = JamSecondary.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = "READY",
                            style = MaterialTheme.typography.labelSmall,
                            color = JamSecondary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (!isHost) {
                Text(
                    text = "Tap to manage",
                    style = MaterialTheme.typography.labelSmall,
                    color = JamPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun RoleBadge(role: Role) {
    val bgColor = when (role) {
        Role.ADMIN -> JamPrimary.copy(alpha = 0.15f)
        Role.DJ -> JamAccent.copy(alpha = 0.15f)
        Role.PLAYER -> JamSurfaceVariant
    }
    val textColor = when (role) {
        Role.ADMIN -> JamPrimary
        Role.DJ -> JamAccent
        Role.PLAYER -> JamOnSurfaceMuted
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = role.name,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
