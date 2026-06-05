package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.domain.MediaFile
import com.example.ui.MediaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MediaViewModel,
    onSelectMedia: (MediaFile) -> Unit,
    modifier: Modifier = Modifier
) {
    val allMedia by viewModel.allMedia.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val watchHistory by viewModel.watchHistory.collectAsStateWithLifecycle()
    val sleepTimeRemaining by viewModel.sleepTimeRemaining.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()

    var showTimerDialog by remember { mutableStateOf(false) }

    val continueWatching = allMedia.filter { it.resumePosition > 0 }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Dynamic Live Banner Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF00E5FF),
                                Color(0xFFD500F9)
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "AURA VIDEO PLAYER PRO",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Cinematic Streaming & Professional Controls",
                        color = Color.Black,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Experience 4K hardware decoders, spatial sound spatializers, and gesture controllers.",
                        color = Color.Black.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 2
                    )
                }
            }
        }

        // Live Scanning Loader
        if (isScanning) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.DarkGray.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Incremental library delta scan in progress...", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        // Quick Sleep-Timer Widget HUD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color(0xFF00E5FF).copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Snooze,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Cinema Sleep Timer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            val statusText = if (sleepTimeRemaining > 0) {
                                val mins = sleepTimeRemaining / 60
                                val secs = sleepTimeRemaining % 60
                                "Pausing in: ${String.format("%02d:%02d", mins, secs)}"
                            } else {
                                "Inactive"
                            }
                            Text(statusText, color = if (sleepTimeRemaining > 0) Color(0xFFD500F9) else Color.Gray, fontSize = 12.sp)
                        }
                    }

                    Row {
                        if (sleepTimeRemaining > 0) {
                            IconButton(onClick = { viewModel.cancelSleepTimer() }) {
                                Icon(Icons.Default.Stop, contentDescription = "Close Timer", tint = Color.Red)
                            }
                        }
                        Button(
                            onClick = { showTimerDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                        ) {
                            Text("Set", color = Color.Black)
                        }
                    }
                }
            }
        }

        // 1. Continue Watching Section
        if (continueWatching.isNotEmpty()) {
            item {
                Text(
                    text = "Continue Watching",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(continueWatching) { media ->
                        Card(
                            onClick = { onSelectMedia(media) },
                            modifier = Modifier
                                .width(220.dp)
                                .testTag("continue_card_${media.id}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .background(Color.DarkGray)
                                ) {
                                    if (media.thumbnailUri.isNotEmpty()) {
                                        AsyncImage(
                                            model = media.thumbnailUri,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.PlayCircleFilled,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(36.dp),
                                            tint = Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                    
                                    // Progress bar
                                    val percent = if (media.duration > 0) media.resumePosition.toFloat() / media.duration else 0f
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .align(Alignment.BottomStart)
                                            .background(Color.White.copy(alpha = 0.4f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(percent)
                                                .background(Color(0xFF00E5FF))
                                        )
                                    }
                                }
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = media.displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        color = Color.White,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Left at ${formatDuration(media.resumePosition)}",
                                        fontSize = 11.sp,
                                        color = Color.LightGray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Favorites Panel
        if (favorites.isNotEmpty()) {
            item {
                Text(
                    text = "Favorites Playlist",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(favorites) { media ->
                        Card(
                            onClick = { onSelectMedia(media) },
                            modifier = Modifier.width(160.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(90.dp)
                                        .background(Color.DarkGray)
                                ) {
                                    if (media.thumbnailUri.isNotEmpty()) {
                                        AsyncImage(
                                            model = media.thumbnailUri,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.VideoFile,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(28.dp),
                                            tint = Color.White.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                                Text(
                                    text = media.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    color = Color.White,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Recently Added Videos list
        item {
            Text(
                text = "Recently Added Videos",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        val displayList = allMedia.take(10)
        if (displayList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No local media scanned. Pull down to refresh.", color = Color.Gray, fontSize = 13.sp)
                }
            }
        } else {
            items(displayList) { media ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onSelectMedia(media) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp, 48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.DarkGray)
                    ) {
                        if (media.thumbnailUri.isNotEmpty()) {
                            AsyncImage(
                                model = media.thumbnailUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.align(Alignment.Center),
                                tint = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = media.displayName,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${formatDuration(media.duration)} • ${media.videoCodec}",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }

                    IconButton(onClick = { viewModel.toggleFavorite(media.id, !media.isFavorite) }) {
                        Icon(
                            imageVector = if (media.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (media.isFavorite) Color(0xFFD500F9) else Color.LightGray
                        )
                    }
                }
            }
        }

        // Quick bottom spacing
        item { Spacer(modifier = Modifier.height(60.dp)) }
    }

    // Timer Dialog Set Overlay
    if (showTimerDialog) {
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTimerDialog = false }) {
                    Text("Cancel", color = Color.LightGray)
                }
            },
            title = { Text("Set Sleep Count", color = Color.White) },
            containerColor = Color(0xFF161E2E),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30, 60, 90).forEach { mins ->
                        Button(
                            onClick = {
                                viewModel.startSleepTimer(mins)
                                showTimerDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${mins} Minutes", color = Color.Black)
                        }
                    }
                }
            }
        )
    }
}

private fun formatDuration(millis: Long): String {
    val totalSecs = millis / 1000
    val secs = totalSecs % 60
    val mins = totalSecs / 60
    return String.format("%02d:%02d", mins, secs)
}
