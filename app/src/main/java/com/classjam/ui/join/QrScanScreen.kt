package com.classjam.ui.join

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.classjam.core.model.QrPayload
import com.classjam.qr.CameraScannerView
import com.classjam.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(
    onPayloadScanned: (QrPayload) -> Unit,
    onFallbackClick: () -> Unit,
    onBackClick: () -> Unit
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Scan Host QR Code", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            CameraScannerView(
                onQrPayloadScanned = onPayloadScanned,
                onError = { errorMessage = it }
            )

            // Top Instructions Banner
            Surface(
                color = Color(0xCC241618),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Point camera at host's QR code to join automatically",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Error display if camera fails
            if (errorMessage != null) {
                Surface(
                    color = JamError,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color.White,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Bottom Manual Fallback Button
            Button(
                onClick = onFallbackClick,
                colors = ButtonDefaults.buttonColors(containerColor = JamSurface),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(24.dp)
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Keyboard, contentDescription = null, tint = JamPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Enter 4-Digit Code or IP Manually",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = JamOnSurface)
                )
            }
        }
    }
}
