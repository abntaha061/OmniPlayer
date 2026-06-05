package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
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

        setContent {
            MyApplicationTheme {
                val mediaViewModel: MediaViewModel = viewModel()
                val currentMedia by mediaViewModel.currentMedia.collectAsStateWithLifecycle()
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (currentMedia != null) {
                        // Full Screen Overlay Video Player is active!
                        VideoPlayerScreen(
                            viewModel = mediaViewModel,
                            onBack = {
                                mediaViewModel.playerManager.stopAndSaveProgress { progress, duration ->
                                    mediaViewModel.saveHistoryProgress(currentMedia!!.id, progress, duration)
                                }
                                // Setting current media to null exits the player overlay
                                mediaViewModel.playerManager.play(currentMedia!!) // Force-pause release
                                mediaViewModel.playerManager.release()
                            }
                        )
                    } else {
                        // Regular tabbed shell navigator layout
                        MainTabbedShell(viewModel = mediaViewModel)
                    }
                }
            }
        }
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
    var selectedScreen by remember { mutableStateOf("home") }

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                NavigationBarItem(
                    selected = selectedScreen == "home",
                    onClick = { selectedScreen = "home" },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    modifier = Modifier.testTag("nav_item_home")
                )
                NavigationBarItem(
                    selected = selectedScreen == "browser",
                    onClick = { selectedScreen = "browser" },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Directory Library Explorer") },
                    label = { Text("Browser") },
                    modifier = Modifier.testTag("nav_item_browser")
                )
                NavigationBarItem(
                    selected = selectedScreen == "playlists",
                    onClick = { selectedScreen = "playlists" },
                    icon = { Icon(Icons.Default.PlaylistPlay, contentDescription = "Smart Lists") },
                    label = { Text("Playlists") },
                    modifier = Modifier.testTag("nav_item_playlists")
                )
                NavigationBarItem(
                    selected = selectedScreen == "streams",
                    onClick = { selectedScreen = "streams" },
                    icon = { Icon(Icons.Default.Wifi, contentDescription = "Network Link Inputs") },
                    label = { Text("Streams") },
                    modifier = Modifier.testTag("nav_item_streams")
                )
                NavigationBarItem(
                    selected = selectedScreen == "settings",
                    onClick = { selectedScreen = "settings" },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Engine Adjustments") },
                    label = { Text("Settings") },
                    modifier = Modifier.testTag("nav_item_settings")
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        when (selectedScreen) {
            "home" -> HomeScreen(
                viewModel = viewModel,
                onSelectMedia = { viewModel.selectMedia(it) },
                modifier = Modifier.padding(innerPadding)
            )
            "browser" -> BrowserScreen(
                viewModel = viewModel,
                onSelectMedia = { viewModel.selectMedia(it) },
                modifier = Modifier.padding(innerPadding)
            )
            "playlists" -> PlaylistScreen(
                viewModel = viewModel,
                onSelectMedia = { viewModel.selectMedia(it) },
                modifier = Modifier.padding(innerPadding)
            )
            "streams" -> StreamingScreen(
                viewModel = viewModel,
                onSelectMedia = { viewModel.selectMedia(it) },
                modifier = Modifier.padding(innerPadding)
            )
            "settings" -> SettingsScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
