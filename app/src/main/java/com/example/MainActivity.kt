package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.MediaViewModel
import com.example.ui.components.MiniPlayer
import com.example.ui.screens.LyricsScreen
import com.example.ui.screens.MeScreen
import com.example.ui.screens.MusicScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.screens.VideosScreen
import com.example.ui.screens.FolderVideosScreen
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {

    private val viewModel: MediaViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            viewModel.loadMediaFiles()
        } else {
            Toast.makeText(this, "يرجى منح أذونات الملفات لتنزيل وعرض الأغاني ومقاطع الفيديو.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    primary = Color(0xFF00E5FF),
                    background = Color(0xFF0A0A0F),
                    surface = Color(0xFF12121A)
                )
            ) {
                AppMainLayout()
            }
        }

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
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

    @Composable
    fun AppMainLayout() {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        val currentSong by viewModel.currentSong.collectAsState()
        val isPlayingAudio by viewModel.isPlayingAudio.collectAsState()
        val audioProgress by viewModel.audioProgress.collectAsState()

        // Deterimine navigation bar and player visibility
        val showBars = currentRoute in listOf("videos", "music", "me")

        Scaffold(
            bottomBar = {
                if (showBars) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0A0A0F))
                    ) {
                        // MINI PLAYER placed above standard Bottom Nav
                        if (currentSong != null) {
                            MiniPlayer(
                                currentSong = currentSong,
                                isPlaying = isPlayingAudio,
                                progress = audioProgress,
                                onPlayPause = { viewModel.togglePlayPauseAudio() },
                                onExpand = {
                                    navController.navigate("lyrics")
                                }
                            )
                        }

                        // Bottom navigation bar (Videos | Music | Me)
                        NavigationBar(
                            containerColor = Color(0xFF12121A).copy(alpha = 0.95f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                            tonalElevation = 8.dp
                        ) {
                            // Me Screen
                            NavigationBarItem(
                                selected = currentRoute == "me",
                                onClick = {
                                    if (currentRoute != "me") {
                                        navController.navigate("me") {
                                            popUpTo("videos") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "الملف الشخصي",
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                label = {
                                    Text("ملفي", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF00E5FF),
                                    selectedTextColor = Color(0xFF00E5FF),
                                    unselectedIconColor = Color.White.copy(alpha = 0.4f),
                                    unselectedTextColor = Color.White.copy(alpha = 0.4f),
                                    indicatorColor = Color(0xFF00E5FF).copy(alpha = 0.15f)
                                )
                            )

                            // Music Screen
                            NavigationBarItem(
                                selected = currentRoute == "music",
                                onClick = {
                                    if (currentRoute != "music") {
                                        navController.navigate("music") {
                                            popUpTo("videos") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Audiotrack,
                                        contentDescription = "الموسيقى",
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                label = {
                                    Text("الموسيقى", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF00E5FF),
                                    selectedTextColor = Color(0xFF00E5FF),
                                    unselectedIconColor = Color.White.copy(alpha = 0.4f),
                                    unselectedTextColor = Color.White.copy(alpha = 0.4f),
                                    indicatorColor = Color(0xFF00E5FF).copy(alpha = 0.15f)
                                )
                            )

                            // Videos (Default tab/home screen)
                            NavigationBarItem(
                                selected = currentRoute == "videos",
                                onClick = {
                                    if (currentRoute != "videos") {
                                        navController.navigate("videos") {
                                            popUpTo("videos") { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.VideoLibrary,
                                        contentDescription = "الفيديو",
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                label = {
                                    Text("الفيديو", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF00E5FF),
                                    selectedTextColor = Color(0xFF00E5FF),
                                    unselectedIconColor = Color.White.copy(alpha = 0.4f),
                                    unselectedTextColor = Color.White.copy(alpha = 0.4f),
                                    indicatorColor = Color(0xFF00E5FF).copy(alpha = 0.15f)
                                )
                            )
                        }
                    }
                }
            },
            containerColor = Color(0xFF0A0A0F)
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (showBars) paddingValues.calculateBottomPadding() else 0.dp)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = "videos" // Starts ALWAYS on Videos tab
                ) {
                    composable("videos") {
                        VideosScreen(navController = navController, viewModel = viewModel)
                    }

                    composable(
                        route = "folder_videos/{folderName}",
                        arguments = listOf(
                            navArgument("folderName") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val encodedFolder = backStackEntry.arguments?.getString("folderName") ?: ""
                        val decodedFolder = URLDecoder.decode(encodedFolder, StandardCharsets.UTF_8.toString())
                        FolderVideosScreen(
                            folderName = decodedFolder,
                            navController = navController,
                            viewModel = viewModel
                        )
                    }

                    composable("music") {
                        MusicScreen(navController = navController, viewModel = viewModel)
                    }

                    composable("me") {
                        MeScreen(navController = navController, viewModel = viewModel)
                    }

                    composable("lyrics") {
                        LyricsScreen(
                            navController = navController,
                            viewModel = viewModel,
                            onClose = {
                                navController.navigate("music") {
                                    popUpTo("music") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(
                        route = "video_player?filePath={filePath}",
                        arguments = listOf(
                            navArgument("filePath") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val encodedPath = backStackEntry.arguments?.getString("filePath") ?: ""
                        val decodedPath = URLDecoder.decode(encodedPath, StandardCharsets.UTF_8.toString())
                        VideoPlayerScreen(
                            filePath = decodedPath,
                            navController = navController,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}
