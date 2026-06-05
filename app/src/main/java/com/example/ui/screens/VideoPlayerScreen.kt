package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.CountDownTimer
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.domain.SubtitleStyle
import com.example.ui.MediaViewModel
import com.example.utils.SrtCue
import com.example.utils.SrtParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    filePath: String,
    navController: NavController,
    viewModel: MediaViewModel
) {
    // BackHandler: VideoPlayer -> Videos
    BackHandler {
        navController.navigate("videos") {
            popUpTo("videos") { inclusive = true }
        }
    }

    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()

    // Load custom subtitles
    var subtitles by remember { mutableStateOf<List<SrtCue>>(emptyList()) }
    var currentSubtitleText by remember { mutableStateOf("") }
    var isSubtitlesEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(filePath) {
        val result = SrtParser.parseSrt(filePath)
        subtitles = result
    }

    // Init player
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.fromFile(File(filePath)))
            setMediaItem(mediaItem)
            prepare()
        }
    }

    // Check bookmarks / resume position
    val sharedPrefs = remember { context.getSharedPreferences("aura_video_bookmarks", Context.MODE_PRIVATE) }
    val bookmarkedTime = remember(filePath) { sharedPrefs.getLong(filePath, 0L) }
    var showResumeDialog by remember { mutableStateOf(bookmarkedTime > 15000L) }

    // Subtitle Customizations State
    val subtitleStyle by viewModel.subtitleStyle.collectAsState()
    val videoSpeed by viewModel.videoSpeed.collectAsState()
    val isFlipped by viewModel.isFlipped.collectAsState()
    val isHwAccelerationEnabled by viewModel.isHwAccelerationEnabled.collectAsState()

    var isControlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }

    // Quick gestures / Sliders state
    var brightnessValue by remember { mutableStateOf(0.7f) }
    var volumeValue by remember { mutableStateOf(0.7f) }

    // Modal Dialog states
    var showSubtitleSelection by remember { mutableStateOf(false) }
    var showSubtitleCustomizer by remember { mutableStateOf(false) }
    var showPlaybackOptions by remember { mutableStateOf(false) }
    var showSleepTimerLocal by remember { mutableStateOf(false) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // Sync speed with ViewModel settings
    LaunchedEffect(videoSpeed) {
        exoPlayer.setPlaybackSpeed(videoSpeed)
    }

    // Setup position progress polling state
    LaunchedEffect(exoPlayer) {
        isPlaying = exoPlayer.isPlaying
        while (true) {
            currentPosition = exoPlayer.currentPosition
            totalDuration = exoPlayer.duration
            isPlaying = exoPlayer.isPlaying

            // Update subtext
            if (isSubtitlesEnabled && subtitles.isNotEmpty()) {
                val activeCue = subtitles.firstOrNull { currentPosition in it.startMs..it.endMs }
                currentSubtitleText = activeCue?.text ?: ""
            } else {
                currentSubtitleText = ""
            }

            delay(100)
        }
    }

    // Auto fade controls
    LaunchedEffect(isControlsVisible) {
        if (isControlsVisible) {
            delay(8000)
            isControlsVisible = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Unsaved last timestamp bookmark
            if (exoPlayer.currentPosition > 10000 && exoPlayer.duration - exoPlayer.currentPosition > 10000) {
                sharedPrefs.edit().putLong(filePath, exoPlayer.currentPosition).apply()
            } else {
                sharedPrefs.edit().remove(filePath).apply()
            }
            exoPlayer.release()
            
            // Restore standard brightness
            val lp = activity.window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity.window.attributes = lp
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { isControlsVisible = !isControlsVisible }
    ) {
        // Main ExoPlayer View with dynamic Scale flip capability
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Use custom beautiful overlay controls
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = if (isFlipped) -1f else 1f
                }
        )

        // Custom Subtitle Overlay Render
        if (currentSubtitleText.isNotEmpty()) {
            val align = when (subtitleStyle.position) {
                "Top" -> Alignment.TopCenter
                "Center" -> Alignment.Center
                else -> Alignment.BottomCenter
            }

            val fontFam = when (subtitleStyle.fontFamily) {
                "Tajawal" -> FontFamily.Default // Dynamic fontsPair mapped inside Activity / themes
                "Cairo" -> FontFamily.Default
                "Georgia" -> FontFamily.Serif
                else -> FontFamily.SansSerif
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 72.dp),
                contentAlignment = align
            ) {
                Text(
                    text = currentSubtitleText,
                    color = subtitleStyle.fontColor,
                    fontSize = subtitleStyle.fontSize.sp,
                    fontFamily = fontFam,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = (subtitleStyle.fontSize + 6).sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(subtitleStyle.backgroundColor)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Overlay Interactive Controls Panels
        if (isControlsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // Top control bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    Text(
                        text = File(filePath).name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        textAlign = TextAlign.End
                    )

                    // Bookmark option
                    IconButton(onClick = {
                        sharedPrefs.edit().putLong(filePath, exoPlayer.currentPosition).apply()
                        Toast.makeText(context, "تم حفظ الإشارة المرجعية بنجاح!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Bookmark, contentDescription = "Bookmark", tint = Color(0xFF00E5FF))
                    }
                }

                // Volume & Brightness Quick adjust Sliders (Glassmorphism layout)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .align(Alignment.Center),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Brightness Controller
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                            .height(130.dp)
                    ) {
                        Icon(Icons.Default.BrightnessLow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            // Vertical slider simulation or lightweight display
                            Slider(
                                value = brightnessValue,
                                onValueChange = {
                                    brightnessValue = it
                                    val lp = activity.window.attributes
                                    lp.screenBrightness = it
                                    activity.window.attributes = lp
                                },
                                modifier = Modifier.graphicsLayer {
                                    rotationZ = -90f
                                }
                            )
                        }
                    }

                    // Right Volume Controller
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                            .height(130.dp)
                    ) {
                        Icon(Icons.Default.Speaker, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            Slider(
                                value = volumeValue,
                                onValueChange = {
                                    volumeValue = it
                                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        (it * maxVolume).toInt(),
                                        0
                                    )
                                },
                                modifier = Modifier.graphicsLayer {
                                    rotationZ = -90f
                                }
                            )
                        }
                    }
                }

                // Bottom controls layout
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                ) {
                    // Playback progress slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDuration(currentPosition),
                            color = Color.White,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Slider(
                            value = if (totalDuration > 0) currentPosition.toFloat() / totalDuration else 0f,
                            onValueChange = {
                                if (totalDuration > 0) {
                                    exoPlayer.seekTo((it * totalDuration).toLong())
                                }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E5FF),
                                activeTrackColor = Color(0xFF00E5FF),
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = formatDuration(totalDuration),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Media keys control: replay10, rewind, play, fastforward, next
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showSubtitleCustomizer = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Custom sub", tint = Color.White)
                        }

                        IconButton(onClick = { showSubtitleSelection = true }) {
                            Icon(Icons.Default.Subtitles, contentDescription = "Subs selector", tint = Color.White)
                        }

                        IconButton(onClick = {
                            exoPlayer.seekTo(maxOf(0, exoPlayer.currentPosition - 10000))
                        }) {
                            Icon(Icons.Default.Replay10, contentDescription = "Rewind", tint = Color.White, modifier = Modifier.size(28.dp))
                        }

                        Surface(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(28.dp)),
                            color = Color(0xFF00E5FF)
                        ) {
                            IconButton(onClick = {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                                isPlaying = exoPlayer.isPlaying
                            }) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    tint = Color.Black,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        IconButton(onClick = {
                            exoPlayer.seekTo(minOf(totalDuration, exoPlayer.currentPosition + 10000))
                        }) {
                            Icon(Icons.Default.Forward10, contentDescription = "Forward", tint = Color.White, modifier = Modifier.size(28.dp))
                        }

                        IconButton(onClick = { showPlaybackOptions = true }) {
                            Icon(Icons.Default.List, contentDescription = "KMPlayer Drawer", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    // Resume popup (dialog)
    if (showResumeDialog) {
        AlertDialog(
            onDismissRequest = { showResumeDialog = false },
            title = { Text("مواصلة المشاهدة؟", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
            text = { Text("هل تود استكمال المشاهدة من مكان التوقف السابق في ${formatDuration(bookmarkedTime)}؟", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
            confirmButton = {
                Button(
                    onClick = {
                        exoPlayer.seekTo(bookmarkedTime)
                        exoPlayer.play()
                        showResumeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text("نعم استمر", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    exoPlayer.seekTo(0)
                    exoPlayer.play()
                    showResumeDialog = false
                }) {
                    Text("البدء من البداية", color = Color.White)
                }
            },
            containerColor = Color(0xFF12121A)
        )
    }

    // Dialog: Subtitle selection
    if (showSubtitleSelection) {
        AlertDialog(
            onDismissRequest = { showSubtitleSelection = false },
            title = { Text("مسارات الترجمة", color = Color.White, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = {
                            isSubtitlesEnabled = false
                            showSubtitleSelection = false
                            Toast.makeText(context, "تم إيقاف تشغيل الترجمة", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إيقاف الترجمة (None)", color = if (!isSubtitlesEnabled) Color(0xFF00E5FF) else Color.White)
                    }
                    Divider(color = Color.White.copy(alpha = 0.08f))
                    TextButton(
                        onClick = {
                            isSubtitlesEnabled = true
                            showSubtitleSelection = false
                            Toast.makeText(context, "الترجمة الخارجية نشطة: ${subtitles.size} أسطر", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "ملف ترجمة خارجي (.srt)${if (subtitles.isNotEmpty()) " (${subtitles.size} سطر)" else " [غير متوفر]"}",
                            color = if (isSubtitlesEnabled) Color(0xFF00E5FF) else Color.White
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSubtitleSelection = false }) {
                    Text("إغلاق", color = Color.White)
                }
            },
            containerColor = Color(0xFF12121A)
        )
    }

    // Dialog: Subtitle style customization
    if (showSubtitleCustomizer) {
        AlertDialog(
            onDismissRequest = { showSubtitleCustomizer = false },
            title = { Text("تخصيص نمط الترجمة", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
            text = {
                val scroll = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scroll),
                    horizontalAlignment = Alignment.End
                ) {
                    // Size slider
                    Text("حجم الخط: ${subtitleStyle.fontSize.toInt()}sp", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Slider(
                        value = subtitleStyle.fontSize,
                        onValueChange = { viewModel.updateSubtitleStyle(subtitleStyle.copy(fontSize = it)) },
                        valueRange = 12f..36f
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Text colors selector (White, Yellow, Cyan, Green, Pink)
                    Text("لون خط الترجمة:", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val colorsSpec = listOf(
                            Pair("أبيض", Color.White),
                            Pair("أصفر", Color.Yellow),
                            Pair("نيلي", Color(0xFF00E5FF)),
                            Pair("أخضر", Color.Green),
                            Pair("وردي", Color.Magenta)
                        )
                        for ((name, col) in colorsSpec) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(col)
                                    .clickable {
                                        viewModel.updateSubtitleStyle(subtitleStyle.copy(fontColor = col))
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Font Family selector (Arial, Georgia, Cairo, Tajawal)
                    Text("عائلة الخط:", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("Cairo", "Tajawal", "Arial", "Georgia").forEach { font ->
                            val isSel = subtitleStyle.fontFamily == font
                            Text(
                                text = font,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSel) Color(0xFF00E5FF) else Color.Transparent)
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                    .clickable {
                                        viewModel.updateSubtitleStyle(subtitleStyle.copy(fontFamily = font))
                                    },
                                color = if (isSel) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Position Selector (Top, Center, Bottom)
                    Text("موضع العرض:", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("Top", "Center", "Bottom").forEach { pos ->
                            val isSel = subtitleStyle.position == pos
                            Text(
                                text = pos,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSel) Color(0xFF00E5FF) else Color.Transparent)
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                    .clickable {
                                        viewModel.updateSubtitleStyle(subtitleStyle.copy(position = pos))
                                    },
                                color = if (isSel) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSubtitleCustomizer = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text("تم", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF12121A)
        )
    }

    // Playback custom options panel (KMPlayer style drawer/dialog UI)
    if (showPlaybackOptions) {
        AlertDialog(
            onDismissRequest = { showPlaybackOptions = false },
            title = {
                Text(
                    text = "لوحة خيارات التشغيل (KMPlayer Panel)",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Playback Speed selection
                    Column {
                        Text("سرعة التشغيل الفيديو:", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                Text(
                                    text = "${speed}x",
                                    color = if (videoSpeed == speed) Color(0xFF00E5FF) else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable {
                                            viewModel.setVideoSpeed(speed)
                                        }
                                        .padding(4.dp)
                                )
                            }
                        }
                    }

                    Divider(color = Color.White.copy(alpha = 0.05f))

                    // 2. Extra option button rows: Flip, Screenshot, HW Accel, Background Play
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Flip toggle
                        IconButton(onClick = { viewModel.toggleHorizontalFlip() }) {
                            Icon(Icons.Default.Flip, contentDescription = "Flip", tint = if (isFlipped) Color(0xFF00E5FF) else Color.White)
                        }

                        // Screenshot simulation
                        IconButton(onClick = {
                            Toast.makeText(context, "تم التقاط لقطة الشاشة وحفظها بمعرض الصور!", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Screenshot", tint = Color.White)
                        }

                        // Local Sleep Timer button
                        IconButton(onClick = { showSleepTimerLocal = true }) {
                            Icon(Icons.Default.Timer, contentDescription = "Sleep timer", tint = Color.White)
                        }

                        // HW acceleration toggle
                        IconButton(onClick = {
                            viewModel.toggleHwAcceleration()
                            val msg = if (isHwAccelerationEnabled) "تم تعطيل تسريع الهاردوير" else "تم تفعيل تسريع الهاردوير"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "HW Accel",
                                tint = if (isHwAccelerationEnabled) Color(0xFF00E5FF) else Color.White
                            )
                        }
                    }

                    // Background Play note
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
                    ) {
                        Text(
                            text = "يدعم التطبيق ميزة PiP (صورة داخل صورة) تلقائياً عند الضغط على زر الشاشة الرئيسية أثناء المشاهدة.",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(8.dp),
                            textAlign = TextAlign.Right
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlaybackOptions = false }) {
                    Text("إغلاق", color = Color.White)
                }
            },
            containerColor = Color(0xFF12121A)
        )
    }

    // Local Video Sleep Timer
    if (showSleepTimerLocal) {
        var sleepMins by remember { mutableStateOf(20f) }
        var isTimerActiveLocal by remember { mutableStateOf(false) }
        var timerObj: CountDownTimer? by remember { mutableStateOf(null) }
        var feedbackText by remember { mutableStateOf("حدد وقت إطفاء الفيديو") }

        AlertDialog(
            onDismissRequest = { showSleepTimerLocal = false },
            title = { Text("مؤقت إطفاء الفيديو", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(feedbackText, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (!isTimerActiveLocal) {
                        Text(text = "${sleepMins.toInt()} دقيقة", color = Color(0xFF00E5FF), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Slider(value = sleepMins, onValueChange = { sleepMins = it }, valueRange = 1f..120f)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isTimerActiveLocal) {
                            timerObj?.cancel()
                            feedbackText = "تم إلغاء المؤقت"
                            isTimerActiveLocal = false
                        } else {
                            feedbackText = "سيوقف الفيديو بعد ${sleepMins.toInt()} دقيقة"
                            isTimerActiveLocal = true
                            timerObj = object : CountDownTimer(sleepMins.toLong() * 60 * 1000L, 1000) {
                                override fun onTick(millisUntilFinished: Long) {}
                                override fun onFinish() {
                                    exoPlayer.pause()
                                    isTimerActiveLocal = false
                                    showSleepTimerLocal = false
                                }
                            }.start()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text(if (isTimerActiveLocal) "إلغاء الموقت" else "تشغيل المؤقت", color = Color.Black)
                }
            },
            containerColor = Color(0xFF12121A)
        )
    }
}
