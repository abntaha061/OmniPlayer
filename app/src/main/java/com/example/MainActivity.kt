package com.example

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import androidx.annotation.RequiresApi
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MediaViewModel
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.*
import androidx.compose.material.icons.filled.*
import coil.compose.AsyncImage
import com.example.domain.MediaFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var mediaViewModel: MediaViewModel? = null

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.ACTION_PLAY_PAUSE") {
                mediaViewModel?.let { vm ->
                    val player = vm.playerManager.getPlayer()
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        updatePiPParams(player.isPlaying)
                    }
                }
            } else if (intent?.action == "com.example.ACTION_CLOSE") {
                mediaViewModel?.let { vm ->
                    val current = vm.currentMedia.value
                    if (current != null) {
                        vm.playerManager.stopAndSaveProgress { progress, duration ->
                            vm.saveHistoryProgress(current.id, progress, duration)
                        }
                    }
                    vm.playerManager.release()
                    vm.playerManager.setCurrentMedia(null)
                }
                finish()
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        Log.d("MainActivity", "Permissions status mapped: $granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Trigger storage permission requests for Android <= 12 and >= 13 (READ_MEDIA_VIDEO)
        requestPermissionsIfNeeded()

        val filter = IntentFilter().apply {
            addAction("com.example.ACTION_PLAY_PAUSE")
            addAction("com.example.ACTION_CLOSE")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pipReceiver, filter)
        }

        setContent {
            MyApplicationTheme {
                val viewModel: MediaViewModel = viewModel()
                mediaViewModel = viewModel
                val currentMedia by viewModel.currentMedia.collectAsStateWithLifecycle()
                val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
                val isPipMode by viewModel.isInPipMode.collectAsStateWithLifecycle()

                val navController = rememberNavController()

                LaunchedEffect(isPlaying, isPipMode) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPipMode) {
                        updatePiPParams(isPlaying)
                    }
                }

                LaunchedEffect(currentMedia) {
                    val media = currentMedia
                    if (media != null) {
                        val extension = media.path.substringAfterLast(".").lowercase()
                        val isAudio = media.videoCodec == "Audio Only" || 
                                listOf("mp3", "flac", "aac", "m4a", "ogg", "wav", "opus", "wma", "alac").contains(extension)
                        if (isAudio) {
                            viewModel.activeTab.value = "music"
                        } else {
                            navController.navigate("player") {
                                launchSingleTop = true
                            }
                        }
                    }
                }
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = "main"
                    ) {
                        composable("main") {
                            MainTabbedShell(viewModel = viewModel)
                        }
                        composable("player") {
                            if (currentMedia != null) {
                                VideoPlayerScreen(
                                    viewModel = viewModel,
                                    onBack = {
                                        viewModel.playerManager.stopAndSaveProgress { progress, duration ->
                                            viewModel.saveHistoryProgress(currentMedia!!.id, progress, duration)
                                        }
                                        viewModel.playerManager.release()
                                        viewModel.playerManager.setCurrentMedia(null)
                                        navController.popBackStack("main", false)
                                    }
                                )
                            } else {
                                LaunchedEffect(Unit) {
                                    navController.popBackStack("main", false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        triggerPictureInPicture()
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            // Already in PiP mode
        } else {
            triggerPictureInPicture()
        }
    }

    override fun onStop() {
        super.onStop()
        mediaViewModel?.lockPrivateFolder()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(pipReceiver)
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        mediaViewModel?.setInPipMode(isInPictureInPictureMode)
    }

    private fun isPiPSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    private fun triggerPictureInPicture() {
        if (isPiPSupported()) {
            val vm = mediaViewModel ?: return
            val current = vm.currentMedia.value
            if (current != null && vm.playerManager.isPlaying.value) {
                val isPlaying = vm.playerManager.isPlaying.value
                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val playPauseIntent = Intent("com.example.ACTION_PLAY_PAUSE")
                    val playPausePendingIntent = PendingIntent.getBroadcast(
                        this,
                        1,
                        playPauseIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    
                    val playPauseIconRes = if (isPlaying) {
                        android.R.drawable.ic_media_pause
                    } else {
                        android.R.drawable.ic_media_play
                    }
                    val playPauseAction = RemoteAction(
                        Icon.createWithResource(this, playPauseIconRes),
                        if (isPlaying) "Pause" else "Play",
                        if (isPlaying) "Pause video" else "Play video",
                        playPausePendingIntent
                    )

                    val closeIntent = Intent("com.example.ACTION_CLOSE")
                    val closePendingIntent = PendingIntent.getBroadcast(
                        this,
                        2,
                        closeIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val closeAction = RemoteAction(
                        Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                        "Close",
                        "Close video player",
                        closePendingIntent
                    )

                    builder.setActions(listOf(playPauseAction, closeAction))
                }
                enterPictureInPictureMode(builder.build())
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updatePiPParams(isPlaying: Boolean) {
        val playPauseIntent = Intent("com.example.ACTION_PLAY_PAUSE")
        val playPausePendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val playPauseIconRes = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseAction = RemoteAction(
            Icon.createWithResource(this, playPauseIconRes),
            if (isPlaying) "Pause" else "Play",
            if (isPlaying) "Pause video" else "Play video",
            playPausePendingIntent
        )

        val closeIntent = Intent("com.example.ACTION_CLOSE")
        val closePendingIntent = PendingIntent.getBroadcast(
            this,
            2,
            closeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val closeAction = RemoteAction(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Close",
            "Close video player",
            closePendingIntent
        )

        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(listOf(playPauseAction, closeAction))
        
        setPictureInPictureParams(builder.build())
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!allGranted) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
}

@Composable
fun MainTabbedShell(viewModel: MediaViewModel) {
    val selectedScreen by viewModel.activeTab.collectAsStateWithLifecycle()
    val showFullScreenLyrics by viewModel.showFullScreenLyrics.collectAsStateWithLifecycle()
    val currentMedia by viewModel.currentMedia.collectAsStateWithLifecycle()

    val colors = remember(currentMedia) {
        val media = currentMedia
        if (media != null && media.videoCodec == "Audio Only") {
            getAuroraColorsForTrack(media)
        } else {
            // Default elegant deep slate background colors
            Pair(Color(0xFF0D0E15), Color(0xFF151922))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MovingAuroraBackground(color1 = colors.first, color2 = colors.second)
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Column {
                    // Mini Player (Only displayed above BottomNav if playing an audio / song)
                    MiniPlayerComponent(viewModel = viewModel) {
                        viewModel.showFullScreenLyrics.value = true
                    }
                    NavigationBar(
                        containerColor = Color.Transparent,
                        modifier = Modifier.testTag("bottom_nav_bar")
                    ) {
                        NavigationBarItem(
                            selected = selectedScreen == "videos",
                            onClick = { viewModel.activeTab.value = "videos" },
                            icon = { Icon(Icons.Default.PlayCircle, contentDescription = "Videos") },
                            label = { Text("Videos") },
                            modifier = Modifier.testTag("nav_item_videos")
                        )
                        NavigationBarItem(
                            selected = selectedScreen == "music",
                            onClick = { viewModel.activeTab.value = "music" },
                            icon = { Icon(Icons.Default.MusicNote, contentDescription = "Music tracks") },
                            label = { Text("Music") },
                            modifier = Modifier.testTag("nav_item_music")
                        )
                        NavigationBarItem(
                            selected = selectedScreen == "me",
                            onClick = { viewModel.activeTab.value = "me" },
                            icon = { Icon(Icons.Default.Person, contentDescription = "Stats, Settings and secure Vault") },
                            label = { Text("Me") },
                            modifier = Modifier.testTag("nav_item_me")
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            when (selectedScreen) {
                "videos" -> BrowserScreen(
                    viewModel = viewModel,
                    onSelectMedia = { viewModel.selectMedia(it) },
                    modifier = Modifier.padding(innerPadding)
                )
                "music" -> MusicScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
                "me" -> MeScreen(
                    viewModel = viewModel,
                    onSelectMedia = { viewModel.selectMedia(it) },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }

        // Full Screen LRC Lyrics Popup Screen
        AnimatedVisibility(
            visible = showFullScreenLyrics,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.fillMaxSize()
        ) {
            FullLyricsPopupScreen(viewModel = viewModel) {
                viewModel.showFullScreenLyrics.value = false
            }
        }
    }
}

@Composable
fun MovingAuroraBackground(
    color1: Color,
    color2: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "aurora")
    
    val animatedColor1 by animateColorAsState(
        targetValue = color1,
        animationSpec = tween(1500),
        label = "color1_anim"
    )
    val animatedColor2 by animateColorAsState(
        targetValue = color2,
        animationSpec = tween(1500),
        label = "color2_anim"
    )
    val animatedColor3 by animateColorAsState(
        targetValue = Color(
            red = ((color1.red + color2.red) / 2f).coerceIn(0f, 1f),
            green = ((color1.green + color2.green) / 2f).coerceIn(0f, 1f),
            blue = ((color1.blue + color2.blue) / 2f).coerceIn(0f, 1f),
            alpha = ((color1.alpha + color2.alpha) / 2f).coerceIn(0f, 1f)
        ),
        animationSpec = tween(1500),
        label = "color3_anim"
    )

    val tx1 by infiniteTransition.animateFloat(
        initialValue = -0.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "x1"
    )
    val ty1 by infiniteTransition.animateFloat(
        initialValue = 1.2f,
        targetValue = -0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "y1"
    )
    
    val tx2 by infiniteTransition.animateFloat(
        initialValue = 1.2f,
        targetValue = -0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "x2"
    )
    val ty2 by infiniteTransition.animateFloat(
        initialValue = -0.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(13000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "y2"
    )

    val tx3 by infiniteTransition.animateFloat(
        initialValue = -0.1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "x3"
    )
    val ty3 by infiniteTransition.animateFloat(
        initialValue = -0.1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "y3"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070A13)) // Deep black-blue aesthetic
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(90.dp)
                .alpha(0.40f)
        ) {
            val width = size.width
            val height = size.height
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(animatedColor1, Color.Transparent),
                    center = Offset(tx1 * width, ty1 * height),
                    radius = width * 0.85f
                ),
                radius = width * 0.85f,
                center = Offset(tx1 * width, ty1 * height)
            )
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(animatedColor2, Color.Transparent),
                    center = Offset(tx2 * width, ty2 * height),
                    radius = width * 0.85f
                ),
                radius = width * 0.85f,
                center = Offset(tx2 * width, ty2 * height)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(animatedColor3, Color.Transparent),
                    center = Offset(tx3 * width, ty3 * height),
                    radius = width * 0.70f
                ),
                radius = width * 0.70f,
                center = Offset(tx3 * width, ty3 * height)
            )
        }
    }
}

fun getAuroraColorsForTrack(track: MediaFile): Pair<Color, Color> {
    val name = track.displayName.lowercase()
    return when {
        name.contains("khaibt") || name.contains("خيبت") -> Pair(Color(0xFF4A0A0A), Color(0xFF8B1A1A)) 
        name.contains("bazat") || name.contains("باظت") -> Pair(Color(0xFF0D1117), Color(0xFFE8A838)) 
        name.contains("tesla") -> Pair(Color(0xFF0A2A4A), Color(0xFF00C8FF)) 
        name.contains("segara") || name.contains("سيجارة") -> Pair(Color(0xFF1A0533), Color(0xFF9B59B6)) 
        name.contains("sindbad") -> Pair(Color(0xFF1A1200), Color(0xFFF0A500)) 
        name.contains("masr7ya") || name.contains("مسرحية") -> Pair(Color(0xFF0D1F0D), Color(0xFF2ECC71)) 
        name.contains("yoram") || name.contains("يرام") -> Pair(Color(0xFF1A1A1A), Color(0xFFE74C3C)) 
        name.contains("toht") || name.contains("تهت") -> Pair(Color(0xFF1A0A00), Color(0xFFFF6B35)) 
        name.contains("ayam") || name.contains("ليالي") -> Pair(Color(0xFF001A33), Color(0xFF3498DB))
        name.contains("emshy") || name.contains("امشي") -> Pair(Color(0xFF1A1A0D), Color(0xFFF1C40F))
        name.contains("akher") || name.contains("اخر") -> Pair(Color(0xFF0D0D1A), Color(0xFF8E44AD))
        name.contains("laffa") || name.contains("لفة") -> Pair(Color(0xFF1A0D00), Color(0xFFE67E22))
        name.contains("ebtadena") || name.contains("ابتدينا") -> Pair(Color(0xFF001A1A), Color(0xFF1ABC9C))
        name.contains("7adota") || name.contains("حدوتة") -> Pair(Color(0xFF1A0A1A), Color(0xFFE91E8C))
        name.contains("7obk") || name.contains("حبك") -> Pair(Color(0xFF0A1A0A), Color(0xFF27AE60))
        else -> {
            val hash = track.displayName.hashCode()
            val colors = listOf(
                Pair(Color(0xFF00E5FF), Color(0xFFFF007F)), 
                Pair(Color(0xFFCCFF00), Color(0xFFE040FB)), 
                Pair(Color(0xFFFFD700), Color(0xFF00E5FF)), 
                Pair(Color(0xFF06D6A0), Color(0xFF7209B7)), 
                Pair(Color(0xFFFF5722), Color(0xFF00E5FF))
            )
            val index = Math.abs(hash) % colors.size
            colors[index]
        }
    }
}

@Composable
fun MiniPlayerComponent(
    viewModel: MediaViewModel,
    onClick: () -> Unit
) {
    val currentMedia by viewModel.currentMedia.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    
    val media = currentMedia ?: return
    
    // Mini Player displays for all audio format models
    val extension = media.path.substringAfterLast(".").lowercase()
    val isAudio = media.videoCodec == "Audio Only" || 
            listOf("mp3", "flac", "aac", "m4a", "ogg", "wav", "opus", "wma", "alac").contains(extension)
            
    if (!isAudio) return

    val player = remember { viewModel.playerManager.getPlayer() }
    val duration = media.duration.coerceAtLeast(1L)
    var currentPositionMs by remember { mutableStateOf(0L) }
    var currentPlaybackProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(isPlaying, media.id) {
        while (isPlaying) {
            currentPositionMs = player.currentPosition
            currentPlaybackProgress = currentPositionMs.toFloat() / duration.toFloat()
            delay(500)
        }
    }

    Surface(
        color = Color(0xFF10131E),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("mini_player_root"),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.12f))
    ) {
        Column {
            LinearProgressIndicator(
                progress = { currentPlaybackProgress.coerceIn(0f, 1f) },
                color = Color(0xFF00E5FF),
                trackColor = Color.White.copy(alpha = 0.08f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray)
                ) {
                    if (media.thumbnailUri.isNotEmpty()) {
                        AsyncImage(
                            model = media.thumbnailUri,
                            contentDescription = "Track Artwork",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = media.displayName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = media.audioCodec, 
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause Button",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FullLyricsPopupScreen(
    viewModel: MediaViewModel,
    onClose: () -> Unit
) {
    val currentMedia by viewModel.currentMedia.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    
    val track = currentMedia ?: return
    
    val colors = getAuroraColorsForTrack(track)
    val player = remember { viewModel.playerManager.getPlayer() }
    val duration = track.duration.coerceAtLeast(1L)
    
    var currentPlaybackProgress by remember { mutableStateOf(0f) }
    var currentPositionMs by remember { mutableStateOf(0L) }

    LaunchedEffect(isPlaying, track.id) {
        while (isPlaying) {
            currentPositionMs = player.currentPosition
            currentPlaybackProgress = currentPositionMs.toFloat() / duration.toFloat()
            delay(150)
        }
    }

    var trackLyrics by remember(track.id) { mutableStateOf<List<LyricLine>?>(null) }

    LaunchedEffect(track.path) {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val file = findLrcFile(track.path)
            trackLyrics = if (file != null) {
                parseLrcFile(file)
            } else {
                emptyList()
            }
        }
    }

    var showProgressTracker by remember { mutableStateOf(true) }
    var userInteractionCount by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(userInteractionCount) {
        showProgressTracker = true
        delay(7000)
        showProgressTracker = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { userInteractionCount++ } 
            .testTag("full_lyrics_screen_root")
    ) {
        MovingAuroraBackground(color1 = colors.first, color2 = colors.second)
        
        if (track.thumbnailUri.isNotEmpty()) {
            AsyncImage(
                model = track.thumbnailUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.12f)
                    .blur(25.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .systemBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Close fullLyrics",
                        tint = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.displayName,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.audioCodec, 
                        color = Color(0xFF00E5FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val lyricsList = trackLyrics
                if (lyricsList == null) {
                    CircularProgressIndicator(color = Color(0xFF00E5FF))
                } else {
                    val finalLyrics = if (lyricsList.isEmpty()) {
                        generateLyrics(track.displayName, track.duration)
                    } else {
                        lyricsList
                    }
                    
                    val activeLyricIndex = finalLyrics.indexOfLast { currentPositionMs >= it.timeMs }.coerceAtLeast(0)
                    val lazyListState = rememberLazyListState()

                    LaunchedEffect(activeLyricIndex) {
                        if (finalLyrics.isNotEmpty()) {
                            lazyListState.animateScrollToItem(activeLyricIndex)
                        }
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(vertical = 140.dp)
                    ) {
                        itemsIndexed(finalLyrics) { index, line ->
                            val isActive = index == activeLyricIndex
                            val scale by animateFloatAsState(if (isActive) 1.25f else 0.92f, label = "scaledLyric")
                            val textAlpha by animateFloatAsState(if (isActive) 1.0f else 0.35f, label = "alphaLyric")
                            
                            Text(
                                text = line.text,
                                fontSize = if (isActive) 22.sp else 16.sp,
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isActive) Color(0xFF00E5FF) else Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .graphicsLayer(scaleX = scale, scaleY = scale)
                                    .alpha(textAlpha)
                                    .clickable {
                                        userInteractionCount++
                                        player.seekTo(line.timeMs)
                                    }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showProgressTracker,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDurationLocal(currentPositionMs),
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                        Text(
                            text = formatDurationLocal(duration),
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }
                    Slider(
                        value = currentPlaybackProgress.coerceIn(0f, 1f),
                        onValueChange = {
                            userInteractionCount++
                            currentPlaybackProgress = it
                            currentPositionMs = (it * duration).toLong()
                            player.seekTo(currentPositionMs)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color.White.copy(alpha = 0.20f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val allMedia by viewModel.allMedia.collectAsStateWithLifecycle()
                val musicFiles = remember(allMedia) {
                    allMedia.filter { it.videoCodec == "Audio Only" }
                }
                
                IconButton(
                    onClick = {
                        userInteractionCount++
                        val idx = musicFiles.indexOfFirst { it.id == track.id }
                        if (idx > 0) {
                            viewModel.selectMedia(musicFiles[idx - 1])
                        }
                    },
                    modifier = Modifier.size(54.dp),
                    enabled = musicFiles.indexOfFirst { it.id == track.id } > 0
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Song",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(28.dp))

                FilledIconButton(
                    onClick = {
                        userInteractionCount++
                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF00E5FF)),
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause Button",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.width(28.dp))

                IconButton(
                    onClick = {
                        userInteractionCount++
                        val idx = musicFiles.indexOfFirst { it.id == track.id }
                        if (idx >= 0 && idx < musicFiles.size - 1) {
                            viewModel.selectMedia(musicFiles[idx + 1])
                        }
                    },
                    modifier = Modifier.size(54.dp),
                    enabled = musicFiles.indexOfFirst { it.id == track.id } < musicFiles.size - 1
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Song",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

private fun formatDurationLocal(millis: Long): String {
    val totalSecs = millis / 1000
    val minutes = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%02d:%02d", minutes, secs)
}

