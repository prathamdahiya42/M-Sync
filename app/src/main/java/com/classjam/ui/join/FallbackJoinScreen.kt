package com.classjam.ui.join

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classjam.core.model.QrPayload
import com.classjam.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FallbackJoinScreen(
    onConnectManual: (QrPayload) -> Unit,
    onBackClick: () -> Unit
) {
    var ipInput by remember { mutableStateOf("") }
    var portInput by remember { mutableStateOf("8080") }
    var roomCodeInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = JamBackground,
        topBar = {
            TopAppBar(
                title = { Text("Manual Session Join", color = JamOnSurface) },
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
                                Icon(Icons.Default.Lan, contentDescription = null, tint = JamPrimary, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Enter Host Details",
                            style = MaterialTheme.typography.titleMedium,
                            color = JamOnSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "If you can't scan the QR code, ask the host for their IP address and 4-digit room code shown on their screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JamOnSurfaceMuted
                    )

                    OutlinedTextField(
                        value = roomCodeInput,
                        onValueChange = {
                            if (it.length <= 4) roomCodeInput = it
                        },
                        label = { Text("4-Digit Room Code") },
                        placeholder = { Text("e.g. 5231") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JamPrimary,
                            focusedLabelColor = JamPrimary,
                            unfocusedBorderColor = JamSurfaceVariant
                        )
                    )

                    OutlinedTextField(
                        value = ipInput,
                        onValueChange = { ipInput = it },
                        label = { Text("Host IP Address") },
                        placeholder = { Text("e.g. 192.168.1.50 or 192.168.43.1") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JamPrimary,
                            focusedLabelColor = JamPrimary,
                            unfocusedBorderColor = JamSurfaceVariant
                        )
                    )

                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { portInput = it },
                        label = { Text("Host Port") },
                        placeholder = { Text("8080") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JamPrimary,
                            focusedLabelColor = JamPrimary,
                            unfocusedBorderColor = JamSurfaceVariant
                        )
                    )
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = JamError,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = {
                    val cleanIp = ipInput.trim()
                    val port = portInput.toIntOrNull() ?: 8080
                    if (cleanIp.isBlank()) {
                        errorMessage = "Please enter the Host IP address"
                        return@Button
                    }
                    val payload = QrPayload(
                        v = 1,
                        ssid = null,
                        password = null,
                        ip = cleanIp,
                        port = port,
                        token = "", // Fallback will authenticate via roomCode
                        roomName = "Manual Session",
                        roomCode = roomCodeInput.trim()
                    )
                    onConnectManual(payload)
                },
                colors = ButtonDefaults.buttonColors(containerColor = JamPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Connect to Session", fontWeight = FontWeight.Bold)
            }
        }
    }
}
