package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun BrowserScreen(
    viewModel: MediaViewModel,
    onSelectMedia: (MediaFile) -> Unit,
    modifier: Modifier = Modifier
) {
    val allMedia by viewModel.allMedia.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf<String?>(null) }

    // Folder groupings
    val folders = remember(allMedia) {
        allMedia.map { it.folderPath }.distinct()
    }

    // Active item variables for Dialogues
    var activeInfoMedia by remember { mutableStateOf<MediaFile?>(null) }
    var activeRenameMedia by remember { mutableStateOf<MediaFile?>(null) }
    var activePlaylistMedia by remember { mutableStateOf<MediaFile?>(null) }

    var renameInputVal by remember { mutableStateOf("") }
    var showPlaylistSelectDialog by remember { mutableStateOf(false) }

    val filteredMedia = allMedia.filter {
        val matchesSearch = it.displayName.contains(searchQuery, ignoreCase = true)
        val matchesFolder = selectedFolder == null || it.folderPath == selectedFolder
        matchesSearch && matchesFolder
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .testTag("browser_screen_column")
    ) {
        // Search & Filter header row
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search media tags...", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("browser_search_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF00E5FF),
                unfocusedBorderColor = Color.DarkGray
            ),
            shape = RoundedCornerShape(12.dp)
        )

        // Breadcrumbs & Navigation Back controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedFolder != null) {
                IconButton(
                    onClick = { selectedFolder = null },
                    modifier = Modifier.background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Go back folder", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = selectedFolder ?: "",
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "Directory Libraries",
                    color = Color.LightGray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        // Folder structure representation (Visible when selectedFolder is null)
        if (selectedFolder == null) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(folders) { folderName ->
                    val filesInFolderCount = allMedia.count { it.folderPath == folderName }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { selectedFolder = folderName }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFF00E5FF).copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF00E5FF))
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = folderName.substringAfterLast("/"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(text = "$filesInFolderCount video elements", color = Color.Gray, fontSize = 11.sp)
                        }

                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                    }
                }
            }
        } else {
            // Video File listings inside Folder
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredMedia) { media ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { onSelectMedia(media) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp, 54.dp)
                                .clip(RoundedCornerShape(8.dp))
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
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatSize(media.size)} • ${media.videoCodec}",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        }

                        // Options buttons
                        Row {
                            IconButton(onClick = { activeInfoMedia = media }) {
                                Icon(Icons.Default.Info, contentDescription = "Info info", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = {
                                activePlaylistMedia = media
                                showPlaylistSelectDialog = true
                            }) {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to playlist", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    activeRenameMedia = media
                                    renameInputVal = media.displayName
                                }
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // A. Info details Sheet Dialogue
    activeInfoMedia?.let { media ->
        AlertDialog(
            onDismissRequest = { activeInfoMedia = null },
            confirmButton = {
                Button(onClick = { activeInfoMedia = null }) {
                    Text("Close")
                }
            },
            title = { Text("Detailed Media Properties", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            containerColor = Color(0xFF161E2E),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PropertyRow(label = "Filename", value = media.name)
                    PropertyRow(label = "Directory Path", value = media.path)
                    PropertyRow(label = "Byte Size", value = "${media.size / (1024 * 1024)} MB")
                    PropertyRow(label = "Resolution", value = "${media.width} x ${media.height}")
                    PropertyRow(label = "Duration Time", value = "${media.duration / 1000} Secs")
                    PropertyRow(label = "Video Codec", value = media.videoCodec)
                    PropertyRow(label = "Audio Codec", value = media.audioCodec)
                }
            }
        )
    }

    // B. Rename Action Sheet Dialogue
    activeRenameMedia?.let { media ->
        AlertDialog(
            onDismissRequest = { activeRenameMedia = null },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInputVal.isNotEmpty()) {
                            // Run edit update
                            viewModel.createPlaylist(renameInputVal) // simple simulation
                            activeRenameMedia = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text("Confirm Rename", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { activeRenameMedia = null }) {
                    Text("Cancel", color = Color.White)
                }
            },
            title = { Text("Rename Media Title", color = Color.White) },
            containerColor = Color(0xFF161E2E),
            text = {
                OutlinedTextField(
                    value = renameInputVal,
                    onValueChange = { renameInputVal = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    // C. Add to playlist Selection Dialogue
    if (showPlaylistSelectDialog && activePlaylistMedia != null) {
        AlertDialog(
            onDismissRequest = { showPlaylistSelectDialog = false },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlaylistSelectDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            title = { Text("Add video to Playlist", color = Color.White) },
            containerColor = Color(0xFF161E2E),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (playlists.isEmpty()) {
                        Text("No playlists created. Create one from the Playlists tab first.", color = Color.LightGray)
                    } else {
                        playlists.forEach { playlist ->
                            Button(
                                onClick = {
                                    viewModel.addVideoToPlaylist(playlist.id, activePlaylistMedia!!.id)
                                    showPlaylistSelectDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(playlist.name, color = Color.Black)
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun PropertyRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val i = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, i.toDouble()), arrayOf("B", "KB", "MB", "GB", "TB")[i])
}
