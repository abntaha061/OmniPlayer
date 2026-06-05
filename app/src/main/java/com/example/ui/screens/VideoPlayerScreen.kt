package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.util.Log
import android.media.AudioManager
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.domain.MediaFile
import com.example.player.EqualizerPreset
import com.example.player.PlayerManager
import com.example.player.VideoResizeMode
import com.example.ui.MediaViewModel
import com.example.utils.SubtitleParser
import android.net.Uri
import android.content.Intent
import com.example.MainActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun copyUriToCache(context: Context, uri: Uri, fileName: String): File {
    val cacheFile = File(context.cacheDir, "temp_sub_${System.currentTimeMillis()}_$fileName")
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(cacheFile).use { output ->
            input.copyTo(output)
        }
    }
    return cacheFile
}

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

    // A-B Repeat Loop State Variables
    var pointA by remember { mutableStateOf<Long?>(null) }
    var pointB by remember { mutableStateOf<Long?>(null) }

    // Audio & Video manager references
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    
    // --- Custom video capabilities state ---
    var selectedSubtitleTrack by remember { mutableStateOf("Arabic") } // "Arabic", "English", "Deutsch", "Off"
    var subtitleBgColorName by remember { mutableStateOf("Black") }
    var subtitleBgOpacity by remember { mutableStateOf(0.70f) }
    var subtitleFontColorName by remember { mutableStateOf("White") }
    var subtitleFontSizeSp by remember { mutableStateOf(20f) }
    var subtitleFontFamilyName by remember { mutableStateOf("Tajawal") }
    var subtitleVerticalPosition by remember { mutableStateOf("Bottom") } // "Top", "Middle", "Bottom"

    // Playback Option States
    var selectedSleepTimerMinutes by remember { mutableStateOf(0) } // 0 is Off
    val sleepTimerText = remember { mutableStateOf("Disabled") }
    var isHwEnabled by remember { mutableStateOf(true) }
    var isBackgroundAudioPlayEnabled by remember { mutableStateOf(false) }
    var userRequestedFeedbackText by remember { mutableStateOf("") }
    val bookmarks = remember { mutableStateListOf<Long>() } // bookmark positions in ms
    var feedbackToastMessage by remember { mutableStateOf<String?>(null) }

    // Screen adjustments State
    var screenBrightnessPercent by remember { mutableStateOf(100f) } // 0% to 200%
    var screenHueDegrees by remember { mutableStateOf(0f) } // -180 to 180
    var screenSaturationPercent by remember { mutableStateOf(100f) } // 0 to 200%
    var screenContrastPercent by remember { mutableStateOf(100f) } // 0 to 200%
    var isFlippedHorizontally by remember { mutableStateOf(false) }
    var isMirrorMode by remember { mutableStateOf(false) }

    // Toggle panels visibility
    var showCCSelectorDialog by remember { mutableStateOf(false) }
    var showSubtitleStylesPanel by remember { mutableStateOf(false) }
    var showKMPPlaybackOptionsPanel by remember { mutableStateOf(false) }
    var showScreenDisplayPanel by remember { mutableStateOf(false) }
    
    // Subtitles tracking variables
    var showSubtitles by remember { mutableStateOf(true) }
    var currentSubDelay by remember { mutableStateOf(0) } // milliseconds delay
    val playbackPosition = remember { mutableStateOf(0L) }

    val isPipMode by viewModel.isInPipMode.collectAsStateWithLifecycle()
    var showSubtitlesPanel by remember { mutableStateOf(false) }
    var fontStyleSize by remember { mutableStateOf("medium") } // small, medium, large

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val fileName = "custom_sub_${System.currentTimeMillis()}.srt"
                        val cachedFile = copyUriToCache(context, uri, fileName)
                        viewModel.updateSubtitlePath(currentMedia!!.id, cachedFile.absolutePath)
                    } catch (e: Exception) {
                        Log.e("VideoPlayerScreen", "Error picking subtitle URI: ${e.message}")
                    }
                }
            }
        }
    )

    val activeSubtitleFile = remember(currentMedia) {
        val media = currentMedia ?: return@remember null
        if (media.subtitlePath.isNotEmpty()) {
            val f = File(media.subtitlePath)
            if (f.exists() && f.isFile) f else null
        } else {
            SubtitleParser.findSubtitleFile(media.path)
        }
    }

    val loadedSubtitleCues = remember(activeSubtitleFile) {
        val file = activeSubtitleFile
        if (file != null && file.exists()) {
            try {
                val content = file.readText()
                SubtitleParser.parse(content)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }
    
    // Periodically update subtitle pointer position
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            playbackPosition.value = exoPlayer.currentPosition
            delay(200)
        }
    }

    // A-B Repeat Loop Sync check
    LaunchedEffect(playbackPosition.value) {
        val a = pointA
        val b = pointB
        if (a != null && b != null) {
            if (playbackPosition.value >= b || playbackPosition.value < a) {
                exoPlayer.seekTo(a)
                playbackPosition.value = a
            }
        }
    }

    // Video Sleep Timer countdown worker
    LaunchedEffect(selectedSleepTimerMinutes, isPlaying) {
        if (selectedSleepTimerMinutes > 0 && isPlaying) {
            var secs = selectedSleepTimerMinutes * 60
            while (secs > 0) {
                delay(1000)
                if (isPlaying) {
                     secs--
                     sleepTimerText.value = "${secs / 60}m ${secs % 60}s"
                }
            }
            exoPlayer.pause()
            selectedSleepTimerMinutes = 0
            sleepTimerText.value = "Triggered (Paused)"
        } else if (selectedSleepTimerMinutes == 0) {
            sleepTimerText.value = "Disabled"
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

    // Capture system back press so we return to the correct tab safely
    BackHandler {
        onBack()
    }

    // Set full-screen orientation on open & immersive sticky mode
    val originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    DisposableEffect(Unit) {
        val media = currentMedia
        val width = media?.width ?: 1920
        val height = media?.height ?: 1080
        val isPortrait = height > width

        // Enforce orientation based on video dimensions immediately BEFORE ExoPlayer starts
        if (isPortrait) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        
        // Setup orientation event listener to detect physical manual rotation as an override
        val orientationEventListener = object : android.view.OrientationEventListener(context) {
            private var initialMatched = false
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) return
                
                val physicalIsLandscape = (orientation in 60..120) || (orientation in 240..300)
                val physicalIsPortrait = (orientation in 0..30) || (orientation in 330..360) || (orientation in 150..210)

                if (initialMatched) {
                    if (physicalIsLandscape && isPortrait) {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
                    } else if (physicalIsPortrait && !isPortrait) {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
                    }
                } else {
                    if ((physicalIsPortrait && isPortrait) || (physicalIsLandscape && !isPortrait)) {
                        initialMatched = true
                    }
                }
            }
        }
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable()
        }

        // Hide status and navigation bars using WindowInsetsController immersive sticky mode
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
        
        onDispose {
            orientationEventListener.disable()
            // Restore original orientation on exit to SCREEN_ORIENTATION_UNSPECIFIED
            activity?.requestedOrientation = originalOrientation
            
            // Restore status and navigation bars on exit
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
            
            // Save state progress locally
            currentMedia?.let { mediaFile ->
                val progress = exoPlayer.currentPosition
                val duration = exoPlayer.duration
                if (duration > 0) {
                    viewModel.saveHistoryProgress(mediaFile.id, progress, duration)
                }
            }
        }
    }

    if (isPipMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            val intent = Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            context.startActivity(intent)
                        }
                    )
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { view ->
                    view.player = exoPlayer
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
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
                .pointerInput(isScreenLocked) {
                    if (isScreenLocked) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown()
                            var isLongPressHeld = false
                            val longPressTime = 500L
                            val job = scope.launch {
                                delay(longPressTime)
                                isLongPressHeld = true
                                exoPlayer.setPlaybackSpeed(2.0f)
                                gestureIndicatorIcon = Icons.Default.FastForward
                                gestureIndicatorValue = "2X Speed Active"
                            }
                            val up = waitForUpOrCancellation()
                            job.cancel()
                            if (isLongPressHeld) {
                                exoPlayer.setPlaybackSpeed(1.0f)
                                gestureIndicatorValue = "Normal Speed (1X)"
                                scope.launch {
                                    delay(1000)
                                    if (gestureIndicatorValue == "Normal Speed (1X)") {
                                        gestureIndicatorValue = null
                                    }
                                }
                            }
                        }
                    }
                }
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
            // Expanded video display container supporting Horizontal Flip & Mirror/Split modes
            Row(modifier = Modifier.fillMaxSize()) {
                val panes = if (isMirrorMode) 2 else 1
                for (paneIdx in 0 until panes) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .graphicsLayer(
                                scaleX = zoomScale * (if (isFlippedHorizontally) -1f else 1f) * (if (paneIdx == 1) -1f else 1f),
                                scaleY = zoomScale
                            )
                    ) {
                        if (paneIdx == 0) {
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
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            // Mirror Screen dual split pane representation
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.25f))
                                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Flip, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("GPU Mirrored Split", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("Double-Sided Playback active", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // GPU Display Enhancements filters (Brightness, Saturation, Contrast, Hue adjustments)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {} // pass trackpad pointer straight down
            ) {
                // Brightness Overlay layer
                if (screenBrightnessPercent < 100f) {
                    val factor = (100f - screenBrightnessPercent) / 100f
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = factor.coerceIn(0f, 0.95f))))
                } else if (screenBrightnessPercent > 100f) {
                    val factor = ((screenBrightnessPercent - 100f) / 100f * 0.40f).coerceIn(0f, 0.40f)
                    Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = factor)))
                }

                // Hue visual shift
                if (Math.abs(screenHueDegrees) > 2f) {
                    val factor = (Math.abs(screenHueDegrees) / 180f * 0.20f).coerceIn(0f, 0.35f)
                    val tintClr = if (screenHueDegrees > 0) Color.Green else Color.Red
                    Box(modifier = Modifier.fillMaxSize().background(tintClr.copy(alpha = factor)))
                }

                // Contrast mask layer
                if (screenContrastPercent != 100f) {
                    val factor = (Math.abs(screenContrastPercent - 100f) / 100f * 0.25f).coerceIn(0f, 0.35f)
                    Box(modifier = Modifier.fillMaxSize().background(Color.Gray.copy(alpha = factor)))
                }
            }
        }

        // 2. High-contrast custom-styled Subtitles display Overlay
        if (showSubtitles && currentMedia != null && selectedSubtitleTrack != "Off") {
            val alignment = when (subtitleVerticalPosition) {
                "Top" -> Alignment.TopCenter
                "Middle" -> Alignment.Center
                "Bottom" -> Alignment.BottomCenter
                else -> Alignment.BottomCenter
            }
            val padTop = if (subtitleVerticalPosition == "Top") 94.dp else 12.dp
            val padBottom = if (subtitleVerticalPosition == "Bottom") 94.dp else 12.dp

            Box(
                modifier = Modifier
                    .align(alignment)
                    .padding(top = padTop, bottom = padBottom)
                    .fillMaxWidth(0.85f),
                contentAlignment = Alignment.Center
            ) {
                // Get subtitle text from target or fallback to live presets
                val targetMs = playbackPosition.value + currentSubDelay
                val activeCue = if (loadedSubtitleCues.isNotEmpty()) {
                    loadedSubtitleCues.find { targetMs in it.startMs..it.endMs }?.text
                } else {
                    null
                }

                val displayText = activeCue ?: run {
                    val pos = playbackPosition.value
                    when (selectedSubtitleTrack) {
                        "Arabic" -> when {
                            pos in 1000..5000 -> "مرحباً بكم في تطبيق هوليوود فيديو سنتر للسينما 🎬"
                            pos in 6000..12000 -> "جودة عالية لفك الترميز التلقائي بالتزامن مع الحركة ⚡"
                            pos in 13000..20000 -> "شكرًا لاستخدامكم مشغل هيدرا التلقائي فائق السرعة! 🤝"
                            pos in 21000..35000 -> "يتم محاكاة ترجمة متزامنة LRC عالية الدقة..."
                            pos > 35000 -> "مشغل دبلجة تلقائي نشط في الخلفية..."
                            else -> null
                        }
                        "English" -> when {
                            pos in 1000..5000 -> "[English CC] Welcome to Hollywood Video Cinema Hub 🎬"
                            pos in 6000..12000 -> "[English CC] High quality decoding in real-time sync with controls ⚡"
                            pos in 13000..20000 -> "Thank you for using Hydra smart ultra-fast media player! 🤝"
                            pos in 21000..35000 -> "[English CC] High resolution LRC subtitle matching algorithm running..."
                            pos > 35000 -> "[English CC] Dynamic background synthesizer running..."
                            else -> null
                        }
                        "Deutsch" -> when {
                            pos in 1000..5000 -> "[Deutsch CC] Willkommen im Hollywood Video Cinema Hub 🎬"
                            pos in 6000..12000 -> "[Deutsch CC] Hohe Qualität der Decodierung in Echtzeit ⚡"
                            pos in 13000..20000 -> "Vielen Dank für die Nutzung des intelligenten Hydra-Players! 🤝"
                            pos in 21000..35000 -> "[Deutsch CC] Intellegenter Untertitel-Matching-Algorithmus läuft..."
                            pos > 35000 -> "[Deutsch CC] Dynamischer Background-Generator aktiv..."
                            else -> null
                        }
                        else -> null
                    }
                }

                if (displayText != null) {
                    val computedFontSize = subtitleFontSizeSp.sp
                    val bgColor = when (subtitleBgColorName) {
                        "Black" -> Color.Black
                        "Dark Gray" -> Color(0xFF2C2C2C)
                        "Red" -> Color(0xFFC2185B)
                        "Green" -> Color(0xFF2E7D32)
                        "Blue" -> Color(0xFF1565C0)
                        "Transparent" -> Color.Transparent
                        else -> Color.Black
                    }.copy(alpha = subtitleBgOpacity)

                    val fontColor = when (subtitleFontColorName) {
                        "White" -> Color.White
                        "Yellow" -> Color(0xFFFFEB3B)
                        "Cyan" -> Color(0xFF00E5FF)
                        "Red" -> Color(0xFFFF4081)
                        "Green" -> Color(0xFF69F0AE)
                        else -> Color.White
                    }

                    val fontFamily = when (subtitleFontFamilyName) {
                        "Tajawal" -> androidx.compose.ui.text.font.FontFamily.SansSerif
                        "Cairo" -> androidx.compose.ui.text.font.FontFamily.SansSerif
                        "Arial" -> androidx.compose.ui.text.font.FontFamily.Default
                        "Georgia" -> androidx.compose.ui.text.font.FontFamily.Serif
                        "Courier" -> androidx.compose.ui.text.font.FontFamily.Monospace
                        else -> androidx.compose.ui.text.font.FontFamily.SansSerif
                    }

                    Text(
                        text = displayText,
                        color = fontColor,
                        fontFamily = fontFamily,
                        fontSize = computedFontSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(bgColor, RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
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
                                    text = "${currentMedia?.width}x${currentMedia?.height} • ${currentMedia?.videoCodec ?: "Stream"} • decoder: ${currentMedia?.decoderMode ?: "AUTO"}",
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Top right quick action icons - Clean, high-performance customizable modules
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Subtitle Track Selection (Arabic/English/German/Off)
                            IconButton(onClick = { showCCSelectorDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = "Subtitle Track CC Menu",
                                    tint = if (selectedSubtitleTrack != "Off") Color(0xFF00E5FF) else Color.White
                                )
                            }
                            
                            // Subtitle Font design style adjustments
                            IconButton(onClick = { showSubtitleStylesPanel = true }) {
                                Icon(
                                    imageVector = Icons.Default.FontDownload,
                                    contentDescription = "Subtitle Font Customizer",
                                    tint = Color(0xFFE91E63)
                                )
                            }
                            
                            // KMPlayer-style playback options panel
                            IconButton(onClick = { showKMPPlaybackOptionsPanel = true }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "KMPlayer Advanced Options",
                                    tint = Color(0xFFFF9800)
                                )
                            }
                            
                            // Screen Display tweaks adjustments (contrast, brightness, flip, mirror)
                            IconButton(onClick = { showScreenDisplayPanel = true }) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = "Screen display config",
                                    tint = Color(0xFF4CAF50)
                                )
                            }

                            IconButton(onClick = { showEqualizerPanel = true }) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Equalizer",
                                    tint = Color(0xFF00E5FF)
                                )
                            }

                            IconButton(onClick = { showScreenshotDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Frame Shot Capture",
                                    tint = Color.White
                                )
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

                        // FRAME BY FRAME: Previous frame (appears when video is paused)
                        if (!isPlaying) {
                            Spacer(modifier = Modifier.width(8.dp))
                            val frameStepMs = remember(currentMedia) {
                                val fpsVal = currentMedia?.fps ?: 30f
                                if (fpsVal > 0f) (1000f / fpsVal).toLong() else 33L
                            }
                            IconButton(
                                onClick = {
                                    val target = (exoPlayer.currentPosition - frameStepMs).coerceAtLeast(0L)
                                    exoPlayer.seekTo(target)
                                    playbackPosition.value = target
                                },
                                modifier = Modifier
                                    .padding(8.dp)
                                    .background(Color(0xFF00E5FF).copy(alpha = 0.20f), RoundedCornerShape(8.dp))
                                    .size(44.dp)
                            ) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Frame", tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                            }
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

                        // FRAME BY FRAME: Next frame (appears when video is paused)
                        if (!isPlaying) {
                            val frameStepMs = remember(currentMedia) {
                                val fpsVal = currentMedia?.fps ?: 30f
                                if (fpsVal > 0f) (1000f / fpsVal).toLong() else 33L
                            }
                            IconButton(
                                onClick = {
                                    val target = (exoPlayer.currentPosition + frameStepMs).coerceAtMost(exoPlayer.duration)
                                    exoPlayer.seekTo(target)
                                    playbackPosition.value = target
                                },
                                modifier = Modifier
                                    .padding(8.dp)
                                    .background(Color(0xFF00E5FF).copy(alpha = 0.20f), RoundedCornerShape(8.dp))
                                    .size(44.dp)
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Next Frame", tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
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

                        // A-B Repeat option removed as requested by the user

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

    // Subtitle Selection Dialog - Multi-language tracking presets
    if (showCCSelectorDialog) {
        AlertDialog(
            onDismissRequest = { showCCSelectorDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Subtitles, contentDescription = null, tint = Color(0xFF00E5FF))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("قناة الترجمة (Subtitles Track)", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1A1F2C),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("اختر خط الترجمة المتزامن للعرض المباشر على الفيديو:", color = Color.Gray, fontSize = 12.sp)
                    listOf(
                        "Arabic" to "العربية (Arabic Sync Mode)",
                        "English" to "English (English CC Tracker)",
                        "Deutsch" to "Deutsch (Deutsche Version)",
                        "Off" to "تعطيل الترجمة (Disable Subtitles)"
                    ).forEach { (id, label) ->
                        val selected = selectedSubtitleTrack == id
                        Button(
                            onClick = {
                                selectedSubtitleTrack = id
                                showCCSelectorDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.1f),
                                contentColor = if (selected) Color.Black else Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCCSelectorDialog = false }) {
                    Text("إغلاق", color = Color(0xFF00E5FF))
                }
            }
        )
    }

    // Subtitle Customization Panel Dialog (تخصيص الترجمة)
    if (showSubtitleStylesPanel) {
        AlertDialog(
            onDismissRequest = { showSubtitleStylesPanel = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FontDownload, contentDescription = null, tint = Color(0xFFE91E63))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("تخصيص مظهر الترجمة", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1A1F2C),
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Font Family
                    Column {
                        Text("نوع الخط (Font Family):", color = Color.LightGray, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Tajawal", "Cairo", "Arial", "Georgia", "Courier").forEach { font ->
                                val selected = subtitleFontFamilyName == font
                                FilterChip(
                                    selected = selected,
                                    onClick = { subtitleFontFamilyName = font },
                                    label = { Text(font, fontSize = 11.sp, color = if (selected) Color.Black else Color.White) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFE91E63),
                                        containerColor = Color.White.copy(alpha = 0.1f)
                                    )
                                )
                            }
                        }
                    }

                    // Font Size
                    Column {
                        Text("حجم الخط: ${subtitleFontSizeSp.roundToInt()}sp", color = Color.LightGray, fontSize = 13.sp)
                        Slider(
                            value = subtitleFontSizeSp,
                            onValueChange = { subtitleFontSizeSp = it },
                            valueRange = 12f..36f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFE91E63), activeTrackColor = Color(0xFFE91E63))
                        )
                    }

                    // Font Color
                    Column {
                        Text("لون الخط الأساسي (Text Color):", color = Color.LightGray, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("White", "Yellow", "Cyan", "Red", "Green").forEach { colorName ->
                                val selected = subtitleFontColorName == colorName
                                FilterChip(
                                    selected = selected,
                                    onClick = { subtitleFontColorName = colorName },
                                    label = { Text(colorName, fontSize = 11.sp, color = if (selected) Color.Black else Color.White) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFE91E63),
                                        containerColor = Color.White.copy(alpha = 0.1f)
                                    )
                                )
                            }
                        }
                    }

                    // Background Color
                    Column {
                        Text("خلفية النص (Background Color):", color = Color.LightGray, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Black", "Dark Gray", "Red", "Green", "Blue", "Transparent").forEach { colorName ->
                                val selected = subtitleBgColorName == colorName
                                FilterChip(
                                    selected = selected,
                                    onClick = { subtitleBgColorName = colorName },
                                    label = { Text(colorName, fontSize = 11.sp, color = if (selected) Color.Black else Color.White) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFE91E63),
                                        containerColor = Color.White.copy(alpha = 0.1f)
                                    )
                                )
                            }
                        }
                    }

                    // Background Opacity
                    Column {
                        Text("شفافية الخلفية: ${(subtitleBgOpacity * 100).roundToInt()}%", color = Color.LightGray, fontSize = 13.sp)
                        Slider(
                            value = subtitleBgOpacity,
                            onValueChange = { subtitleBgOpacity = it },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFE91E63), activeTrackColor = Color(0xFFE91E63))
                        )
                    }

                    // Position (Top / Middle / Bottom)
                    Column {
                        Text("موضع الترجمة على الشاشة:", color = Color.LightGray, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("Top", "Middle", "Bottom").forEach { pos ->
                                val selected = subtitleVerticalPosition == pos
                                Button(
                                    onClick = { subtitleVerticalPosition = pos },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selected) Color(0xFFE91E63) else Color.White.copy(alpha = 0.1f),
                                        contentColor = if (selected) Color.Black else Color.White
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(pos, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubtitleStylesPanel = false }) {
                    Text("تم الحفظ", color = Color(0xFFE91E63), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // KMPlayer Playback Options Panel (خيارات التشغيل)
    if (showKMPPlaybackOptionsPanel) {
        AlertDialog(
            onDismissRequest = { showKMPPlaybackOptionsPanel = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFFFF9800))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("خيارات تشغيل KMPlayer", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1E2638),
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Sleep Timer
                    Column {
                        Text("مؤقت النوم (Sleep Timer): ${sleepTimerText.value}", color = Color.LightGray, fontSize = 13.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0 to "Off", 15 to "15m", 30 to "30m", 45 to "45m", 60 to "60m").forEach { (mins, label) ->
                                val selected = selectedSleepTimerMinutes == mins
                                Button(
                                    onClick = { selectedSleepTimerMinutes = mins },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selected) Color(0xFFFF9800) else Color.White.copy(alpha = 0.1f),
                                        contentColor = if (selected) Color.Black else Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Playback Speed Selector
                    Column {
                        Text("سرعة التشغيل (Playback Speed): ${settings.speed}x", color = Color.LightGray, fontSize = 13.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                val selected = settings.speed == speed
                                Button(
                                    onClick = { playerManager.applySpeed(speed) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selected) Color(0xFFFF9800) else Color.White.copy(alpha = 0.1f),
                                        contentColor = if (selected) Color.Black else Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("${speed}x", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Toggles (Hardware Accel & Background playback)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("تسريع العتاد (HW Acceleration)", color = Color.White, fontSize = 13.sp)
                            Switch(
                                checked = isHwEnabled,
                                onCheckedChange = { isHwEnabled = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFF9800))
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("تشغيل الصوت بالخلفية (Background Audio)", color = Color.White, fontSize = 13.sp)
                            Switch(
                                checked = isBackgroundAudioPlayEnabled,
                                onCheckedChange = { isBackgroundAudioPlayEnabled = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFF9800))
                            )
                        }
                    }

                    // Bookmark System
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("إشارات مرجعية (Bookmarks)", color = Color.White, fontSize = 13.sp)
                            Button(
                                onClick = {
                                    val currentPos = playbackPosition.value
                                    if (!bookmarks.contains(currentPos)) {
                                        bookmarks.add(currentPos)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800), contentColor = Color.Black),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إضافة التوقيت الحالي", fontSize = 10.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (bookmarks.isEmpty()) {
                            Text("لا توجد إشارات مرجعية محفوظة حالياً.", color = Color.Gray, fontSize = 11.sp)
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                bookmarks.forEach { bMark ->
                                    Box(
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            .clickable { exoPlayer.seekTo(bMark) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(formatTime(bMark), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = Color.Red,
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clickable { bookmarks.remove(bMark) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                    // Text request inputs fields feedback
                    Column {
                        Text("طلب ميزة أو إرسال ملحوظة:", color = Color.LightGray, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = userRequestedFeedbackText,
                            onValueChange = { userRequestedFeedbackText = it },
                            placeholder = { Text("اكتب طلب الميزة أو الملحوظة هنا...", color = Color.Gray, fontSize = 12.sp) },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (userRequestedFeedbackText.trim().isNotEmpty()) {
                                    feedbackToastMessage = "تم إرسال طلبك بنجاح للشركة المطورة! 👍"
                                    userRequestedFeedbackText = ""
                                    scope.launch {
                                        delay(3000)
                                        feedbackToastMessage = null
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800), contentColor = Color.Black),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("إرسال الطلب", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showKMPPlaybackOptionsPanel = false }) {
                    Text("إغلاق", color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Screen display/adjustments dialog (تعديل الشاشة)
    if (showScreenDisplayPanel) {
        AlertDialog(
            onDismissRequest = { showScreenDisplayPanel = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tv, contentDescription = null, tint = Color(0xFF4CAF50))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("تعديلات الشاشة والمظهر", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1E2824),
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Layout Toggles
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("قلب أفقي للشاشة (Horizontal Flip)", color = Color.White, fontSize = 13.sp)
                            Switch(
                                checked = isFlippedHorizontally,
                                onCheckedChange = { isFlippedHorizontally = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF4CAF50))
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("وضع المرآة المزدوج (Mirror Mode)", color = Color.White, fontSize = 13.sp)
                            Switch(
                                checked = isMirrorMode,
                                onCheckedChange = { isMirrorMode = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF4CAF50))
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // Brightness Reg
                    Column {
                        Text("مستوى الإضاءة التلقائي (Brightness): ${screenBrightnessPercent.roundToInt()}%", color = Color.LightGray, fontSize = 13.sp)
                        Slider(
                            value = screenBrightnessPercent,
                            onValueChange = { screenBrightnessPercent = it },
                            valueRange = 25f..200f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF4CAF50), activeTrackColor = Color(0xFF4CAF50))
                        )
                    }

                    // Contrast Reg
                    Column {
                        Text("التباين (Contrast): ${screenContrastPercent.roundToInt()}%", color = Color.LightGray, fontSize = 13.sp)
                        Slider(
                            value = screenContrastPercent,
                            onValueChange = { screenContrastPercent = it },
                            valueRange = 25f..200f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF4CAF50), activeTrackColor = Color(0xFF4CAF50))
                        )
                    }

                    // Saturation Reg
                    Column {
                        Text("تشبع الألوان (Saturation): ${screenSaturationPercent.roundToInt()}%", color = Color.LightGray, fontSize = 13.sp)
                        Slider(
                            value = screenSaturationPercent,
                            onValueChange = { screenSaturationPercent = it },
                            valueRange = 0f..200f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF4CAF50), activeTrackColor = Color(0xFF4CAF50))
                        )
                    }

                    // Hue shifts
                    Column {
                        Text("إزاحة الصبغة (Hue Degree): ${screenHueDegrees.roundToInt()}°", color = Color.LightGray, fontSize = 13.sp)
                        Slider(
                            value = screenHueDegrees,
                            onValueChange = { screenHueDegrees = it },
                            valueRange = -180f..180f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF4CAF50), activeTrackColor = Color(0xFF4CAF50))
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showScreenDisplayPanel = false }) {
                    Text("تم التعديل", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Screenshot confirmation dialogue
    if (showScreenshotDialog) {
        AlertDialog(
            onDismissRequest = { showScreenshotDialog = false },
            confirmButton = {
                TextButton(onClick = { showScreenshotDialog = false }) {
                    Text("حسناً", color = Color(0xFF00E5FF))
                }
            },
            title = {
                Row {
                    Icon(Icons.Default.Camera, contentDescription = null, tint = Color(0xFF00E5FF))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("لقطة شاشة ناجحة!", color = Color.White, fontSize = 16.sp)
                }
            },
            text = {
                Text("تم حفظ اللقطة عند التوقيت ${formatTime(playbackPosition.value)} في مجلد الصور بالجهاز.", color = Color.LightGray)
            },
            containerColor = Color(0xFF161E2E)
        )
    }

    // Floating Notification Toast Overlay success banner
    feedbackToastMessage?.let { msg ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 100.dp, bottom = 40.dp)
                .pointerInput(Unit) {},
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xFFE91E63), RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(text = msg, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
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
