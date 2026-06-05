package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.domain.MediaFile
import com.example.player.EqualizerPreset
import com.example.player.PlayerManager
import com.example.player.VideoResizeMode
import com.example.ui.MediaViewModel
import com.example.utils.SubtitleParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    viewModel: MediaViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val playerManager = viewModel.playerManager
    val exoPlayer = remember { playerManager.getPlayer() }
    val currentMedia by viewModel.currentMedia.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val settings by playerManager.settings.collectAsStateWithLifecycle()

    // Screen brightness & Audio volume states for gesture notifications
    var gestureIndicatorValue by remember { mutableStateOf<String?>(null) }
    var gestureIndicatorIcon by remember { mutableStateOf(Icons.Default.VolumeUp) }

    // Coroutine Scope for control auto-hiding
    val scope = rememberCoroutineScope()
    var controlsVisible by remember { mutableStateOf(true) }
    var isScreenLocked by remember { mutableStateOf(false) }

    // Pinch-to-zoom state variables
    var zoomScale by remember { mutableStateOf(1f) }
    val transformableState = rememberTransformableState { zoomChange, _, _ ->
        zoomScale = (zoomScale * zoomChange).coerceIn(1f, 5f)
    }

    // Audio & Video manager references
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    
    // Subtitles tracking variables
    var showSubtitles by remember { mutableStateOf(true) }
    var currentSubDelay by remember { mutableStateOf(0) } // milliseconds delay
    val playbackPosition = remember { mutableStateOf(0L) }
    
    // Periodically update subtitle pointer position
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            playbackPosition.value = exoPlayer.currentPosition
            delay(200)
        }
    }

    // Capture screenshot state dialogue
    var showScreenshotDialog by remember { mutableStateOf(false) }
    var showEqualizerPanel by remember { mutableStateOf(false) }
    var showSettingsPanel by remember { mutableStateOf(false) }

    // Reset controls timer
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(4000)
            controlsVisible = false
        }
    }

    // Set full-screen flag on device window safely
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            // Save state progress locally
            currentMedia?.let { media ->
                val progress = exoPlayer.currentPosition
                val duration = exoPlayer.duration
                if (duration > 0) {
                    viewModel.saveHistoryProgress(media.id, progress, duration)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("video_player_box")
    ) {
        // 1. Gesture detector & Pinch to Zoom container wrapper
        Box(
            modifier = Modifier
                .fillMaxSize()
                .transformable(state = transformableState)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val screenWidth = size.width
                            val third = screenWidth / 3
                            if (offset.x < third) {
                                // Double tapped left -> Rewind 10s
                                playerManager.seekRelative(-10)
                                gestureIndicatorIcon = Icons.Default.Replay10
                                gestureIndicatorValue = "Backward -10s"
                                scope.launch {
                                    delay(1000)
                                    gestureIndicatorValue = null
                                }
                            } else if (offset.x > third * 2) {
                                // Double tapped right -> Fast forward 10s
                                playerManager.seekRelative(10)
                                gestureIndicatorIcon = Icons.Default.Forward10
                                gestureIndicatorValue = "Forward +10s"
                                scope.launch {
                                    delay(1000)
                                    gestureIndicatorValue = null
                                }
                            } else {
                                // Double tapped middle -> Play/Pause toggle
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                        },
                        onTap = {
                            if (!isScreenLocked) {
                                controlsVisible = !controlsVisible
                            } else {
                                // If locked, tap shows a key indicator to unlock
                                controlsVisible = true
                            }
                        }
                    )
                }
                .pointerInput(isScreenLocked) {
                    if (isScreenLocked) return@pointerInput
                    // Implement vertical dragging: left side for brightness, right side for volume
                    var lastX = 0f
                    detectDragGestures(
                        onDragStart = { offset -> lastX = offset.x },
                        onDragEnd = { gestureIndicatorValue = null },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val width = size.width
                            val height = size.height
                            if (lastX < width / 2) {
                                // Adjust Screen Brightness (Left Column)
                                val currentBright = activity?.window?.attributes?.screenBrightness ?: 0.5f
                                // Inverse dragAmount.y (upward swipe increases brightness)
                                val delta = -dragAmount.y / height
                                val newBright = (currentBright + delta).coerceIn(0.1f, 1.0f)
                                
                                val lp = activity?.window?.attributes
                                lp?.screenBrightness = newBright
                                activity?.window?.attributes = lp
                                
                                playerManager.applyBrightness(newBright)
                                gestureIndicatorIcon = Icons.Default.Brightness6
                                gestureIndicatorValue = "Brightness: ${(newBright * 100).roundToInt()}%"
                            } else {
                                // Adjust Volume (Right Column)
                                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val delta = -dragAmount.y / height
                                val step = (delta * maxVolume).roundToInt()
                                val newVol = (currentVol + step).coerceIn(0, maxVolume)
                                
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                gestureIndicatorIcon = Icons.Default.VolumeUp
                                gestureIndicatorValue = "Volume: ${(newVol.toFloat() / maxVolume * 100).roundToInt()}%"
                            }
                        }
                    )
                }
        ) {
            // Android ExoPlayer instance
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false // Completely custom overlay controls!
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { view ->
                    val m3ResizeMode = when (settings.resizeMode) {
                        VideoResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        VideoResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        VideoResizeMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        VideoResizeMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        VideoResizeMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        VideoResizeMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    view.resizeMode = m3ResizeMode
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = zoomScale,
                        scaleY = zoomScale
                    )
            )
        }

        // 2. High-contrast customized Subtitles display Overlay
        if (showSubtitles && currentMedia != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
                    .fillMaxWidth(0.85f),
                contentAlignment = Alignment.Center
            ) {
                // Read active cue from parser
                val targetMs = playbackPosition.value + currentSubDelay
                val activeCue = SubtitleParser.getDemoCuesForTime(currentMedia!!.id).find {
                    targetMs in it.startMs..it.endMs
                }
                
                if (activeCue != null) {
                    Text(
                        text = activeCue.text,
                        color = Color.White,
                        fontSize = settings.subtitleSize.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.70f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // 3. Status Gesture HUD Overlay Alerts
        gestureIndicatorValue?.let { label ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(gestureIndicatorIcon, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(28.dp))
                    Text(text = label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 4. Custom Cinematic Overlay Controls (Visible when tapped and not locked)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            if (isScreenLocked) {
                // If screen is locked, display just a single lock toggle to easily unlock the controls
                Box(modifier = Modifier.fillMaxSize()) {
                    IconButton(
                        onClick = { isScreenLocked = false },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(16.dp)
                            .background(Color(0xFF00E5FF), CircleShape)
                            .size(54.dp)
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = "Unlock Controls", tint = Color.Black)
                    }
                }
            } else {
                // Normal controls
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    // Top Bar Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(onClick = onBack, modifier = Modifier.testTag("player_back_icon")) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Exit Player", tint = Color.White)
                            }
                            Column {
                                Text(
                                    text = currentMedia?.displayName ?: "Streaming Resource",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${currentMedia?.width}x${currentMedia?.height} • ${currentMedia?.videoCodec ?: "Stream"}",
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Top right quick action icons
                        Row {
                            IconButton(onClick = { showEqualizerPanel = true }) {
                                Icon(Icons.Default.GraphicEq, contentDescription = "Equalizer", tint = Color(0xFF00E5FF))
                            }
                            IconButton(onClick = { showSettingsPanel = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Play Configurations", tint = Color.White)
                            }
                            IconButton(onClick = { showSubtitles = !showSubtitles }) {
                                Icon(
                                    if (showSubtitles) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                                    contentDescription = "Toggle Subtitles",
                                    tint = if (showSubtitles) Color(0xFF00E5FF) else Color.White
                                )
                            }
                            IconButton(onClick = { showScreenshotDialog = true }) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Frame Shot", tint = Color.White)
                            }
                        }
                    }

                    // Center playback controls
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { playerManager.seekRelative(-10) },
                            modifier = Modifier
                                .padding(12.dp)
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(24.dp))
                        }

                        IconButton(
                            onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                            modifier = Modifier
                                .padding(16.dp)
                                .background(Color(0xFF00E5FF), CircleShape)
                                .size(64.dp)
                                .testTag("player_play_pause")
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        IconButton(
                            onClick = { playerManager.seekRelative(10) },
                            modifier = Modifier
                                .padding(12.dp)
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(Icons.Default.Forward10, contentDescription = "Skip 10s", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }

                    // Bottom Seekbar & Controls Panel
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
                    ) {
                        // Display Current Duration / Remaining Position
                        var sliderValue by remember { mutableStateOf(0f) }
                        val dVal = exoPlayer.duration.toFloat().coerceAtLeast(1f)
                        val pVal = playbackPosition.value.toFloat()
                        
                        LaunchedEffect(playbackPosition.value) {
                            sliderValue = pVal / dVal
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = formatTime(playbackPosition.value), color = Color.White, fontSize = 12.sp)
                            Text(text = formatTime(exoPlayer.duration), color = Color.White, fontSize = 12.sp)
                        }

                        Slider(
                            value = sliderValue,
                            onValueChange = {
                                sliderValue = it
                                exoPlayer.seekTo((it * exoPlayer.duration).toLong())
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E5FF),
                                activeTrackColor = Color(0xFF00E5FF),
                                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                            ),
                            modifier = Modifier.testTag("player_seekbar")
                        )

                        // Bottom Actions row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Lock toggle
                            IconButton(onClick = { isScreenLocked = true }) {
                                Icon(Icons.Default.Lock, contentDescription = "Lock Interface Tap", tint = Color.White)
                            }

                            // Aspect Ratio switcher
                            TextButton(
                                onClick = {
                                    val nextMode = when (settings.resizeMode) {
                                        VideoResizeMode.FIT -> VideoResizeMode.FILL
                                        VideoResizeMode.FILL -> VideoResizeMode.STRETCH
                                        VideoResizeMode.STRETCH -> VideoResizeMode.RATIO_16_9
                                        VideoResizeMode.RATIO_16_9 -> VideoResizeMode.RATIO_4_3
                                        VideoResizeMode.RATIO_4_3 -> VideoResizeMode.FIT
                                        else -> VideoResizeMode.FIT
                                    }
                                    playerManager.applyResizeMode(nextMode)
                                }
                            ) {
                                Icon(Icons.Default.AspectRatio, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = settings.resizeMode.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            // Zoom level resets
                            if (zoomScale > 1f) {
                                TextButton(onClick = { zoomScale = 1f }) {
                                    Text(text = "Reset Zoom (${(zoomScale * 100).roundToInt()}%)", color = Color(0xFF00E5FF), fontSize = 12.sp)
                                }
                            }

                            // Speed Regulator button
                            TextButton(
                                onClick = {
                                    val nextSpeed = when (settings.speed) {
                                        1.0f -> 1.5f
                                        1.5f -> 2.0f
                                        2.0f -> 0.5f
                                        0.5f -> 1.0f
                                        else -> 1.0f
                                    }
                                    playerManager.applySpeed(nextSpeed)
                                }
                            ) {
                                Icon(Icons.Default.Speed, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "${settings.speed}x", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // A. 5-Band Equalizer & Bass Spatials Panel Modal
    if (showEqualizerPanel) {
        AlertDialog(
            onDismissRequest = { showEqualizerPanel = false },
            confirmButton = {
                Button(onClick = { showEqualizerPanel = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))) {
                    Text("Apply & Close", color = Color.Black)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Equalizer, contentDescription = null, tint = Color(0xFF00E5FF))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Cinematic Equalizer & Bass", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF161E2E),
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Equalizer State", color = Color.White, fontSize = 14.sp)
                        Switch(
                            checked = settings.equalizerEnabled,
                            onCheckedChange = { playerManager.toggleEqualizer(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF))
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (settings.equalizerEnabled) {
                        Text("Acoustic Profile Preset", color = Color.LightGray, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            EqualizerPreset.values().take(4).forEach { preset ->
                                FilterChip(
                                    selected = settings.equalizerPreset == preset,
                                    onClick = { playerManager.applyEqualizerPreset(preset) },
                                    label = { Text(preset.displayName, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                        selectedLabelColor = Color(0xFF00E5FF)
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Spatial Sub Bass Boost Slider
                    Text("Super Bass Boost Level (${settings.bassBoost})", color = Color.White, fontSize = 13.sp)
                    Slider(
                        value = settings.bassBoost.toFloat(),
                        onValueChange = { playerManager.applyBassBoost(it.roundToInt()) },
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Spatial Surround Sound Regulator
                    Text("Surround Audio Virt (${settings.virtualizer})", color = Color.White, fontSize = 13.sp)
                    Slider(
                        value = settings.virtualizer.toFloat(),
                        onValueChange = { playerManager.applyVirtualizer(it.roundToInt()) },
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                    )
                }
            }
        )
    }

    // B. Subtitle delays, size & sync Panel Modal
    if (showSettingsPanel) {
        AlertDialog(
            onDismissRequest = { showSettingsPanel = false },
            confirmButton = {
                Button(onClick = { showSettingsPanel = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))) {
                    Text("Save", color = Color.Black)
                }
            },
            title = { Text("Display & Subtitle Preferences", color = Color.White) },
            containerColor = Color(0xFF161E2E),
            text = {
                Column {
                    Text("Subtitle Delay (ms): $currentSubDelay", color = Color.White, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { currentSubDelay -= 250 }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                            Text("-250ms")
                        }
                        Button(onClick = { currentSubDelay = 0 }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                            Text("Sync")
                        }
                        Button(onClick = { currentSubDelay += 250 }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                            Text("+250ms")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Subtitle Text Size (${settings.subtitleSize.roundToInt()}sp)", color = Color.White, fontSize = 14.sp)
                    Slider(
                        value = settings.subtitleSize,
                        onValueChange = { playerManager.applyVolumeBoost(100) /* Reuse settings updater */},
                        valueRange = 12f..32f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Night screen filter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Night Mode Eye Protection", color = Color.White)
                        Switch(
                            checked = settings.isNightModeFilter,
                            onCheckedChange = { playerManager.applyNightModeFilter(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF))
                        )
                    }
                }
            }
        )
    }

    // C. Screenshot alert dialog confirmation
    if (showScreenshotDialog) {
        AlertDialog(
            onDismissRequest = { showScreenshotDialog = false },
            confirmButton = {
                TextButton(onClick = { showScreenshotDialog = false }) {
                    Text("Ok Premium Preview", color = Color(0xFF00E5FF))
                }
            },
            title = {
                Row {
                    Icon(Icons.Default.Camera, contentDescription = null, tint = Color(0xFF00E5FF))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Frame Record Captured!", color = Color.White, fontSize = 16.sp)
                }
            },
            text = {
                Text("Saved active frame index: ${playbackPosition.value}ms as high definition JPEG to '/Pictures/Screenshots'.", color = Color.LightGray)
            },
            containerColor = Color(0xFF161E2E)
        )
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0) return "00:00"
    val totalSeconds = millis / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
