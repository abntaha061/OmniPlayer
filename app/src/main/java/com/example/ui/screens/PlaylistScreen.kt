package com.example.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.MediaFile
import com.example.domain.Playlist
import com.example.ui.MediaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    viewModel: MediaViewModel,
    onSelectMedia: (MediaFile) -> Unit,
    modifier: Modifier = Modifier
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistInputString by remember { mutableStateOf("") }
    
    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
    val playlistMediaList = remember { mutableStateOf<List<MediaFile>>(emptyList()) }
    val selectedPlaylistName = playlists.find { it.id == selectedPlaylistId }?.name ?: ""

    // Coroutine loader for selected playlist
    LaunchedEffect(selectedPlaylistId) {
        selectedPlaylistId?.let { id ->
            viewModel.getPlaylistMedia(id).collect {
                playlistMediaList.value = it
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .testTag("playlist_screen_column")
    ) {
        // Heading with list additions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedPlaylistId != null) {
                IconButton(onClick = { selectedPlaylistId = null }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Go back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = selectedPlaylistName,
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            } else {
                Text(
                    text = "Smart Playlists",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Button(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    modifier = Modifier.testTag("create_playlist_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New List", color = Color.Black, fontSize = 12.sp)
                }
            }
        }

        if (selectedPlaylistId == null) {
            // General Playlists display list
            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(54.dp))
                        Text(text = "No custom playlists created yet.", color = Color.LightGray, fontSize = 14.sp)
                        Text(text = "Unify favorite streams, folders and cinematic demo clips.", color = Color.Gray, fontSize = 11.sp)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(playlists) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { selectedPlaylistId = playlist.id }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(Color(0xFFD500F9).copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.QueueMusic, contentDescription = null, tint = Color(0xFFD500F9))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = playlist.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            IconButton(onClick = { viewModel.deletePlaylist(playlist.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        } else {
            // Viewing active loaded list media files
            if (playlistMediaList.value.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("This playlist is empty. Add videos from the File Browser tab.", color = Color.LightGray)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(playlistMediaList.value) { media ->
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
                                        .size(64.dp, 44.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.DarkGray),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Text(
                                text = media.displayName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(60.dp))
    }

    // Naming alert creation dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        if (playlistInputString.isNotEmpty()) {
                            viewModel.createPlaylist(playlistInputString)
                            playlistInputString = ""
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    modifier = Modifier.testTag("playlist_confirm_button")
                ) {
                    Text("Create", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            title = { Text("Generate Playlist Title", color = Color.White) },
            containerColor = Color(0xFF161E2E),
            text = {
                OutlinedTextField(
                    value = playlistInputString,
                    onValueChange = { playlistInputString = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                    placeholder = { Text("List Name (e.g. Cinema Favourites)...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF)
                    ),
                    modifier = Modifier.testTag("playlist_input_text")
                )
            }
        )
    }
}
