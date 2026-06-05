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
import com.example.domain.NetworkStream
import com.example.ui.MediaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamingScreen(
    viewModel: MediaViewModel,
    onSelectMedia: (MediaFile) -> Unit,
    modifier: Modifier = Modifier
) {
    val streams by viewModel.networkStreams.collectAsStateWithLifecycle()

    var streamUrlString by remember { mutableStateOf("") }
    var streamTitleString by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .testTag("streaming_screen_column")
    ) {
        Text(
            text = "Network Direct Streams",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Stream URL input Box card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Open Network stream link",
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                OutlinedTextField(
                    value = streamTitleString,
                    onValueChange = { streamTitleString = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                    placeholder = { Text("Stream Label/Title (e.g. Astro-Stream Live)") },
                    modifier = Modifier.fillMaxWidth().testTag("stream_title_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF)
                    )
                )

                OutlinedTextField(
                    value = streamUrlString,
                    onValueChange = { streamUrlString = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                    placeholder = { Text("HTTPS / HLS / DASH link (.m3u8, .mpd, .mp4)") },
                    modifier = Modifier.fillMaxWidth().testTag("stream_url_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF)
                    )
                )

                Button(
                    onClick = {
                        if (streamUrlString.isNotEmpty()) {
                            val title = if (streamTitleString.isEmpty()) "Online Stream Feed" else streamTitleString
                            viewModel.addNetworkStream(title, streamUrlString)
                            
                            // Play directly! Create a simulated media item
                            val media = MediaFile(
                                id = System.currentTimeMillis(),
                                path = streamUrlString,
                                name = title,
                                displayName = title,
                                folderPath = "Network Stream Links",
                                size = 0,
                                duration = 0
                            )
                            onSelectMedia(media)

                            streamUrlString = ""
                            streamTitleString = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    modifier = Modifier.fillMaxWidth().testTag("stream_launch_button")
                ) {
                    Icon(Icons.Default.CloudQueue, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stream Instant Live", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 10.dp))

        Text(
            text = "Saved Streams History",
            color = Color.LightGray,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (streams.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("No parsed stream URLs yet. Test standard livestream parameters.", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(streams) { stream ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable {
                                val media = MediaFile(
                                    id = stream.id,
                                    path = stream.url,
                                    name = stream.title,
                                    displayName = stream.title,
                                    folderPath = "Network Stream Links",
                                    size = 0,
                                    duration = 0
                                )
                                onSelectMedia(media)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFF00E5FF).copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF00E5FF))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = stream.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(text = stream.url, color = Color.LightGray, fontSize = 11.sp, maxLines = 1)
                            }
                        }

                        IconButton(onClick = { viewModel.deleteNetworkStream(stream.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Stream", tint = Color.Red.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}
