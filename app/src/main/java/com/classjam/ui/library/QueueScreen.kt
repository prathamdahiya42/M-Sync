package com.classjam.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.classjam.core.model.QueueItem
import com.classjam.core.model.Role
import com.classjam.core.model.RolePermissions
import com.classjam.core.model.Track
import com.classjam.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    currentTrack: Track?,
    queue: List<QueueItem>,
    userRole: Role,
    allPhonesReady: Boolean,
    readyPhonesOnlyOverride: Boolean,
    onToggleReadyPhonesOverride: (Boolean) -> Unit,
    onAddMoreMusicClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        containerColor = JamBackground,
        topBar = {
            TopAppBar(
                title = { Text("Playback Queue", color = JamOnSurface, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = JamOnSurface)
                    }
                },
                actions = {
                    if (RolePermissions.canRequestQueue(userRole)) {
                        IconButton(onClick = onAddMoreMusicClick) {
                            Icon(Icons.Default.LibraryMusic, contentDescription = "Browse Library", tint = JamPrimary)
                        }
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // READY State Status Banner
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (allPhonesReady) JamSecondary.copy(alpha = 0.12f) else JamPrimary.copy(alpha = 0.10f)
                ),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (allPhonesReady) JamSecondary.copy(alpha = 0.3f) else JamPrimary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (allPhonesReady) Icons.Default.CheckCircle else Icons.Default.Sync,
                            contentDescription = null,
                            tint = if (allPhonesReady) JamSecondary else JamPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (allPhonesReady) "All Output Phones Ready" else "Phones Syncing Music...",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = JamOnSurface
                            )
                            Text(
                                text = if (allPhonesReady) "Audio is downloaded and verified on all phones."
                                else "Prefetching queued songs over Wi-Fi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = JamOnSurfaceMuted
                            )
                        }
                    }
                }
            }

            // Admin Override Control
            if (userRole == Role.ADMIN) {
                Surface(
                    color = JamSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, JamSurfaceVariant),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Start with ready phones only",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = JamOnSurface
                            )
                            Text(
                                text = "Play immediately without waiting for slow downloads",
                                style = MaterialTheme.typography.bodySmall,
                                color = JamOnSurfaceMuted
                            )
                        }
                        Switch(
                            checked = readyPhonesOnlyOverride,
                            onCheckedChange = onToggleReadyPhonesOverride,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = JamPrimary
                            )
                        )
                    }
                }
            }

            // Now Playing Card
            if (currentTrack != null) {
                Text("Now Playing", style = MaterialTheme.typography.titleSmall, color = JamPrimary, fontWeight = FontWeight.Bold)
                Card(
                    colors = CardDefaults.cardColors(containerColor = JamSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, JamSurfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = JamPrimary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(50.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = JamPrimary, modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = currentTrack.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = JamOnSurface
                            )
                            Text(
                                text = "${currentTrack.artist} • ${currentTrack.formatDuration()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = JamOnSurfaceMuted
                            )
                        }
                    }
                }
            }

            // Upcoming Queue
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Up Next (${queue.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = JamOnSurface,
                    fontWeight = FontWeight.Bold
                )
                if (RolePermissions.canRequestQueue(userRole)) {
                    TextButton(onClick = onAddMoreMusicClick) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = JamPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Song", color = JamPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (queue.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = "The queue is empty. Tap 'Add Song' to choose music!",
                        color = JamOnSurfaceMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(queue, key = { _, item -> item.queueId }) { index, item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = JamSurface),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, JamSurfaceVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = JamOnSurfaceMuted,
                                        modifier = Modifier.width(28.dp)
                                    )
                                    Column {
                                        Text(
                                            text = item.track.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = JamOnSurface,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "${item.track.artist} • Added by ${item.addedByDisplayName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = JamOnSurfaceMuted,
                                            maxLines = 1
                                        )
                                    }
                                }
                                Surface(
                                    color = JamPrimaryVariant.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (index < 2) "PREFETCHED" else "QUEUED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (index < 2) JamSecondary else JamOnSurfaceMuted,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
