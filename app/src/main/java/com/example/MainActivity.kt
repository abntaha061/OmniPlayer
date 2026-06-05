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

                LaunchedEffect(isPlaying, isPipMode) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPipMode) {
                        updatePiPParams(isPlaying)
                    }
                }
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (currentMedia != null) {
                        // Full Screen Overlay Video Player is active!
                        VideoPlayerScreen(
                            viewModel = viewModel,
                            onBack = {
                                viewModel.playerManager.stopAndSaveProgress { progress, duration ->
                                    viewModel.saveHistoryProgress(currentMedia!!.id, progress, duration)
                                }
                                viewModel.playerManager.release()
                                viewModel.playerManager.setCurrentMedia(null)
                            }
                        )
                    } else {
                        // Regular tabbed shell navigator layout
                        MainTabbedShell(viewModel = viewModel)
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
    var selectedScreen by remember { mutableStateOf("videos") }

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                NavigationBarItem(
                    selected = selectedScreen == "videos",
                    onClick = { selectedScreen = "videos" },
                    icon = { Icon(Icons.Default.PlayCircle, contentDescription = "Videos") },
                    label = { Text("Videos") },
                    modifier = Modifier.testTag("nav_item_videos")
                )
                NavigationBarItem(
                    selected = selectedScreen == "music",
                    onClick = { selectedScreen = "music" },
                    icon = { Icon(Icons.Default.MusicNote, contentDescription = "Music tracks") },
                    label = { Text("Music") },
                    modifier = Modifier.testTag("nav_item_music")
                )
                NavigationBarItem(
                    selected = selectedScreen == "streams",
                    onClick = { selectedScreen = "streams" },
                    icon = { Icon(Icons.Default.Wifi, contentDescription = "Network & Streams") },
                    label = { Text("Streams") },
                    modifier = Modifier.testTag("nav_item_streams")
                )
                NavigationBarItem(
                    selected = selectedScreen == "me",
                    onClick = { selectedScreen = "me" },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Stats, Settings and secure Vault") },
                    label = { Text("Me") },
                    modifier = Modifier.testTag("nav_item_me")
                )
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
            "streams" -> StreamingScreen(
                viewModel = viewModel,
                onSelectMedia = { viewModel.selectMedia(it) },
                modifier = Modifier.padding(innerPadding)
            )
            "me" -> MeScreen(
                viewModel = viewModel,
                onSelectMedia = { viewModel.selectMedia(it) },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
