package com.classjam.ui.join

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.classjam.core.model.ConnectionState
import com.classjam.core.model.QrPayload
import com.classjam.network.client.KtorClientSocket
import com.classjam.network.hotspot.WifiConnectManager
import com.classjam.network.hotspot.WifiJoinState
import com.classjam.ui.theme.*

@Composable
fun ConnectingScreen(
    payload: QrPayload,
    wifiConnectManager: WifiConnectManager,
    clientSocket: KtorClientSocket,
    onConnected: () -> Unit,
    onCancel: () -> Unit
) {
    val wifiState by wifiConnectManager.state.collectAsState()
    val socketState by clientSocket.connectionState.collectAsState()

    var statusMessage by remember { mutableStateOf("Initializing connection...") }
    var currentStep by remember { mutableIntStateOf(1) }
    var failureReason by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(payload) {
        if (!payload.ssid.isNullOrBlank()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                statusMessage = "Connecting to Host Wi-Fi: ${payload.ssid}"
                currentStep = 1
                wifiConnectManager.connectToWifi(payload.ssid, payload.password)
            } else {
                // Pre-Android 10
                clientSocket.connect(payload.ip, payload.port, payload.token, payload.roomCode)
            }
        } else {
            // Already on same Wi-Fi
            currentStep = 2
            statusMessage = "Connecting to Host at ${payload.ip}:${payload.port}"
            clientSocket.connect(payload.ip, payload.port, payload.token, payload.roomCode)
        }
    }

    LaunchedEffect(wifiState) {
        when (val state = wifiState) {
            is WifiJoinState.Connected -> {
                currentStep = 2
                statusMessage = "Connected to Wi-Fi. Establishing secure socket to host..."
                clientSocket.connect(payload.ip, payload.port, payload.token, payload.roomCode)
            }
            is WifiJoinState.Failed -> {
                failureReason = "Wi-Fi Join Failed: ${state.reason}"
            }
            else -> {}
        }
    }

    LaunchedEffect(socketState) {
        when (val state = socketState) {
            is ConnectionState.Connected -> {
                currentStep = 3
                statusMessage = "Authenticated! Joining party..."
                onConnected()
            }
            is ConnectionState.Error -> {
                failureReason = state.message
            }
            is ConnectionState.Disconnected -> {
                failureReason = state.reason
            }
            else -> {}
        }
    }

    Scaffold(
        containerColor = JamBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (failureReason == null) {
                CircularProgressIndicator(
                    color = JamPrimary,
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(64.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Joining ${payload.roomName}",
                    style = MaterialTheme.typography.titleLarge,
                    color = JamOnSurface,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JamOnSurfaceMuted
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Progress Step Indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StepDot(active = currentStep >= 1, label = "Wi-Fi")
                    HorizontalDivider(modifier = Modifier.width(20.dp), color = JamSurfaceVariant)
                    StepDot(active = currentStep >= 2, label = "Socket")
                    HorizontalDivider(modifier = Modifier.width(20.dp), color = JamSurfaceVariant)
                    StepDot(active = currentStep >= 3, label = "Ready")
                }

                Spacer(modifier = Modifier.height(48.dp))

                OutlinedButton(
                    onClick = {
                        wifiConnectManager.disconnectAndUnbind()
                        clientSocket.disconnect()
                        onCancel()
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Cancel", color = JamOnSurfaceMuted)
                }
            } else {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = JamError,
                    modifier = Modifier.size(64.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Connection Failed",
                    style = MaterialTheme.typography.titleLarge,
                    color = JamError,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = failureReason ?: "Unknown connection error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JamOnSurface
                )

                Spacer(modifier = Modifier.height(32.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = {
                            failureReason = null
                            currentStep = 1
                            if (!payload.ssid.isNullOrBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                wifiConnectManager.connectToWifi(payload.ssid, payload.password)
                            } else {
                                clientSocket.connect(payload.ip, payload.port, payload.token, payload.roomCode)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = JamPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Retry")
                    }

                    OutlinedButton(
                        onClick = {
                            wifiConnectManager.disconnectAndUnbind()
                            clientSocket.disconnect()
                            onCancel()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Go Back", color = JamOnSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepDot(active: Boolean, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(
                    color = if (active) JamPrimary else JamSurfaceVariant,
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) JamPrimary else JamOnSurfaceMuted
        )
    }
}
