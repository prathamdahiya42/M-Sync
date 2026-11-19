package com.classjam.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classjam.ui.theme.*

@Composable
fun PermissionRationaleDialog(
    onGrantPermissionsClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = JamPrimary.copy(alpha = 0.12f),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = JamPrimary, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text("Permissions Needed", color = JamOnSurface, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PermissionItem(
                    icon = Icons.Default.QrCodeScanner,
                    title = "Camera",
                    description = "Used exclusively to scan the host phone's QR code so you can join in one scan with no typing."
                )

                PermissionItem(
                    icon = Icons.Default.Wifi,
                    title = "Nearby Wi-Fi / Local Network",
                    description = "Allows the app to automatically join the host's local hotspot without leaving the app."
                )

                PermissionItem(
                    icon = Icons.Default.Notifications,
                    title = "Persistent Notification",
                    description = "Shows an ongoing 'Connected to Class Jam' status in your notification shade with an instant 1-tap Leave button."
                )

                PermissionItem(
                    icon = Icons.Default.LibraryMusic,
                    title = "Audio Storage (Host only)",
                    description = "Used on the host phone to select and distribute music tracks to everyone in the room."
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onGrantPermissionsClick,
                colors = ButtonDefaults.buttonColors(containerColor = JamPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Grant Permissions", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later", color = JamOnSurfaceMuted)
            }
        },
        containerColor = JamSurface,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            color = JamPrimary.copy(alpha = 0.12f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = JamPrimary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = JamOnSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = JamOnSurfaceMuted)
        }
    }
}
