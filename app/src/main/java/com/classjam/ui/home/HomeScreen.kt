package com.classjam.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classjam.core.datastore.AppPreferences
import com.classjam.core.model.NetworkMode
import com.classjam.core.util.BatteryOptimizationHelper
import com.classjam.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    appPreferences: AppPreferences,
    onHostClick: () -> Unit,
    onJoinScanClick: () -> Unit,
    onJoinFallbackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val displayName by appPreferences.displayNameFlow.collectAsState(initial = "")
    val networkMode by appPreferences.networkModeFlow.collectAsState(initial = NetworkMode.IN_APP_HOTSPOT)
    val manualSsid by appPreferences.manualSsidFlow.collectAsState(initial = "")
    val manualPassword by appPreferences.manualPasswordFlow.collectAsState(initial = "")

    var nameInput by remember(displayName) { mutableStateOf(displayName) }
    var ssidInput by remember(manualSsid) { mutableStateOf(manualSsid) }
    var passwordInput by remember(manualPassword) { mutableStateOf(manualPassword) }
    var showManualWifiDialog by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }

    var isBatteryExempt by remember { mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    Scaffold(
        containerColor = JamBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = JamPrimary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = JamPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Class Jam",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = JamOnSurface,
                                    letterSpacing = (-0.5).sp
                                )
                            )
                            Text(
                                text = "Synchronized Local Music & Party Games",
                                style = MaterialTheme.typography.labelSmall.copy(color = JamOnSurfaceMuted)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showPermissionRationale = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Permissions Info", tint = JamPrimary)
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
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Screen-Off Battery Optimization Card
            if (!isBatteryExempt) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = JamSurfaceVariant),
                    border = BorderStroke(1.dp, JamSurfaceBorder),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = JamPrimary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Enable Screen-Off Sync",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = JamOnSurface
                                )
                                Text(
                                    text = "Allow background run so music never stutters when phone is locked.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JamOnSurfaceMuted
                                )
                            }
                        }

                        Button(
                            onClick = {
                                BatteryOptimizationHelper.requestDisableBatteryOptimization(context)
                                isBatteryExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JamPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Fix", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Display Name Card
            Card(
                colors = CardDefaults.cardColors(containerColor = JamSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, JamSurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Your Display Name",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = JamOnSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            coroutineScope.launch { appPreferences.setDisplayName(it) }
                        },
                        placeholder = { Text("e.g. Alex", color = JamOnSurfaceMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JamPrimary,
                            unfocusedBorderColor = JamSurfaceBorder,
                            focusedTextColor = JamOnSurface,
                            unfocusedTextColor = JamOnSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Host Network Mode Card
            Card(
                colors = CardDefaults.cardColors(containerColor = JamSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, JamSurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = JamPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Host Network Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = JamOnSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    NetworkMode.entries.forEach { mode ->
                        val selected = networkMode == mode
                        Surface(
                            onClick = {
                                coroutineScope.launch { appPreferences.setNetworkMode(mode) }
                                if (mode == NetworkMode.MANUAL_HOTSPOT) {
                                    showManualWifiDialog = true
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) JamSurfaceVariant else JamSurface,
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) JamPrimary else JamSurfaceBorder
                            ),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = {
                                        coroutineScope.launch { appPreferences.setNetworkMode(mode) }
                                        if (mode == NetworkMode.MANUAL_HOTSPOT) {
                                            showManualWifiDialog = true
                                        }
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = JamPrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = mode.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = JamOnSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = mode.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = JamOnSurfaceMuted
                                    )
                                    if (mode == NetworkMode.MANUAL_HOTSPOT && manualSsid.isNotBlank()) {
                                        Text(
                                            text = "Configured: SSID \"$manualSsid\"",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = JamSecondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Start Session (Host Button) - Vibrant Cherry Red
            Button(
                onClick = onHostClick,
                colors = ButtonDefaults.buttonColors(containerColor = JamPrimary),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
            ) {
                Icon(Icons.Default.Podcasts, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Start Session as Admin (Host)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                )
            }

            // Join Session (Scan QR Button) - Light Red Surface Button
            Button(
                onClick = onJoinScanClick,
                colors = ButtonDefaults.buttonColors(containerColor = JamSurfaceVariant),
                border = BorderStroke(1.dp, JamPrimary.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = JamPrimary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Join Session (Scan QR)",
                    style = MaterialTheme.typography.titleMedium.copy(color = JamPrimary, fontWeight = FontWeight.Bold)
                )
            }

            // Fallback manual join button
            OutlinedButton(
                onClick = onJoinFallbackClick,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, JamSurfaceBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = JamOnSurfaceMuted),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(
                    text = "Join with 4-Digit Code or Manual IP",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    if (showPermissionRationale) {
        PermissionRationaleDialog(
            onGrantPermissionsClick = {
                showPermissionRationale = false
                val permissions = mutableListOf<String>()
                permissions.add(Manifest.permission.CAMERA)
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
                } else {
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                permissionsLauncher.launch(permissions.toTypedArray())
            },
            onDismiss = { showPermissionRationale = false }
        )
    }

    if (showManualWifiDialog) {
        AlertDialog(
            onDismissRequest = { showManualWifiDialog = false },
            title = { Text("Manual Hotspot / Router Info", color = JamOnSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter your phone's personal hotspot or classroom router details.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JamOnSurfaceMuted
                    )
                    OutlinedTextField(
                        value = ssidInput,
                        onValueChange = { ssidInput = it },
                        label = { Text("Wi-Fi SSID") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password (optional)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            appPreferences.setManualWifiCredentials(ssidInput, passwordInput)
                        }
                        showManualWifiDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JamPrimary)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualWifiDialog = false }) {
                    Text("Cancel", color = JamOnSurfaceMuted)
                }
            },
            containerColor = JamSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
