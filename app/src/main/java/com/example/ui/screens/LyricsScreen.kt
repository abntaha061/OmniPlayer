package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.domain.Song
import com.example.ui.MediaViewModel
import com.example.ui.components.AuroraBackground
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun LyricsScreen(
    navController: NavController,
    viewModel: MediaViewModel,
    onClose: () -> Unit
) {
    // BackHandler: Lyrics screen -> Music Tracklist
    BackHandler {
        navController.navigate("music") {
            popUpTo("music") { inclusive = true }
        }
    }

    val song by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlayingAudio.collectAsState()
    val currentTimeMs by viewModel.audioPositionMs.collectAsState()
    val progress by viewModel.audioProgress.collectAsState()
    val lrcLines by viewModel.lrcLines.collectAsState()

    if (song == null) return

    val currentSongValue = song!!

    var showControls by remember { mutableStateOf(true) }

    // Auto-hide controls after 7 seconds
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(7000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { showControls = true }
    ) {
        // Aurora Background with dynamic album colors
        AuroraBackground(color1 = currentSongValue.color1, color2 = currentSongValue.color2)

        // Album art as blurred background (15% opacity)
        currentSongValue.albumArt?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(20.dp),
                alpha = 0.15f,
                contentScale = ContentScale.Crop
            )
        }

        // Header: Back Action & Song Detail
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    tint = Color.White,
                    contentDescription = "Close",
                    modifier = Modifier.size(32.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = currentSongValue.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
                Text(
                    text = currentSongValue.artist,
                    color = Color(0xFF00E5FF),
                    fontSize = 13.sp,
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
            }
        }

        // Synced lyrics list
        val currentIndex = remember(lrcLines, currentTimeMs) {
            lrcLines.indexOfLast { it.timeMs <= currentTimeMs }
        }
        val listState = rememberLazyListState()

        LaunchedEffect(currentIndex) {
            if (currentIndex >= 0 && lrcLines.isNotEmpty()) {
                listState.animateScrollToItem(maxOf(0, currentIndex - 2))
            }
        }

        if (lrcLines.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "كلمات الأغنية غير متوفرة لـ هذا الملف.\nضع ملف .lrc بنفس الاسم بجانب الأغنية للرؤية المتزامنة.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 100.dp, bottom = 160.dp),
                verticalArrangement = Arrangement.Center
            ) {
                itemsIndexed(lrcLines) { index, line ->
                    val isCurrent = index == currentIndex
                    Text(
                        text = line.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        fontSize = if (isCurrent) 22.sp else 16.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) Color(0xFF00E5FF) else Color.White.copy(
                            alpha = when {
                                abs(index - currentIndex) <= 1 -> 0.8f
                                abs(index - currentIndex) <= 3 -> 0.45f
                                else -> 0.18f
                            }
                        )
                    )
                }
            }
        }

        // Controls (auto-hide after 7s)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp, top = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Progress slider bar
                Slider(
                    value = progress,
                    onValueChange = { viewModel.seekAudio(it) },
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00E5FF),
                        activeTrackColor = Color(0xFF00E5FF),
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Playback speed/buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.playPrevious() }) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                            contentDescription = "Previous"
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(32.dp)),
                        color = Color(0xFF00E5FF)
                    ) {
                        IconButton(
                            onClick = { viewModel.togglePlayPauseAudio() },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp),
                                contentDescription = "Play/Pause"
                            )
                        }
                    }

                    IconButton(onClick = { viewModel.playNext() }) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                            contentDescription = "Next"
                        )
                    }
                }
            }
        }
    }
}
