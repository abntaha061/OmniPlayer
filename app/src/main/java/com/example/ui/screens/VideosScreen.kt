package com.example.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.domain.VideoFile
import com.example.domain.VideoFolder
import com.example.ui.MediaViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// Formatter Helpers
fun formatBytesToSize(sizeInBytes: Long): String {
    if (sizeInBytes <= 0) return "0 ميغابايت"
    val k = 1024L
    val sizes = arrayOf("بايت", "كيلوبايت", "ميغابايت", "غيغابايت", "تيرابايت")
    val i = (Math.log(sizeInBytes.toDouble()) / Math.log(k.toDouble())).toInt()
    val value = sizeInBytes.toDouble() / Math.pow(k.toDouble(), i.toDouble())
    return String.format("%.1f %s", value, sizes[i])
}

fun formatMillisToDuration(durationMs: Long): String {
    if (durationMs <= 0) return "٠٠:٠٠"
    val totalSeconds = durationMs / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

fun formatSize(sizeInBytes: Long): String {
    return formatBytesToSize(sizeInBytes)
}

fun formatDuration(durationMs: Long): String {
    return formatMillisToDuration(durationMs)
}

// ----------------- SCREEN 1: FOLDERS SCREEN (المجلدات) -----------------
@Composable
fun VideosScreen(
    navController: NavController,
    viewModel: MediaViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backPressedOnce by remember { mutableStateOf(false) }

    // Click back twice to finish
    BackHandler {
        if (backPressedOnce) {
            (context as Activity).finish()
        } else {
            backPressedOnce = true
            Toast.makeText(context, "اضغط مرة أخرى للخروج", Toast.LENGTH_SHORT).show()
            scope.launch {
                delay(2000)
                backPressedOnce = false
            }
        }
    }

    val folders by viewModel.foldersList.collectAsState()
    var showSortSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0F))) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Header Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left tools: Sort panel launcher and search simulation
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = { showSortSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "عرض الفرز والتصنيف",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = {
                        Toast.makeText(context, "البحث غير متاح في العرض التجريبي", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "البحث",
                            tint = Color.White
                        )
                    }
                }

                // Header Title on Right (RTL Layout Alignment)
                Text(
                    text = "المجلدات",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right
                )
            }

            // Top Horizontal scroll row of MX Player styled Quick-access shortcuts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                QuickShortcutButton(label = "موسيقى", icon = Icons.Default.Audiotrack, color = Color(0xFF9C27B0)) {
                    navController.navigate("music")
                }
                QuickShortcutButton(label = "نقل الملفات", icon = Icons.Default.SwapHoriz, color = Color(0xFF2196F3)) {
                    Toast.makeText(context, "ميزة نقل السحابي نشطة", Toast.LENGTH_SHORT).show()
                }
                QuickShortcutButton(label = "حفظ الحالة", icon = Icons.Default.Download, color = Color(0xFF4CAF50)) {
                    Toast.makeText(context, "تم العثور على ٤ حالات جديدة وحفظها!", Toast.LENGTH_SHORT).show()
                }
                QuickShortcutButton(label = "قوائمي", icon = Icons.Default.List, color = Color(0xFFFF9800)) {
                    Toast.makeText(context, "قائمة التشغيل الذكية تحت الاختبار", Toast.LENGTH_SHORT).show()
                }
                QuickShortcutButton(label = "تنظيف", icon = Icons.Default.Delete, color = Color(0xFF00BCD4)) {
                    Toast.makeText(context, "تم تنظيف ٢.٤ جيجابايت من الذاكرة المؤقتة!", Toast.LENGTH_SHORT).show()
                }
                QuickShortcutButton(label = "خصوصية", icon = Icons.Default.Security, color = Color(0xFFE91E63)) {
                    Toast.makeText(context, "المجلد الآمن مقفل بكلمة مرور", Toast.LENGTH_SHORT).show()
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Folders List Area
            if (folders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00E5FF))
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(folders) { folder ->
                        FolderRowItem(folder = folder) {
                            // Navigate to Video list of folder
                            viewModel.loadVideosForFolder(folder.name)
                            navController.navigate("folder_videos/${URLEncoder.encode(folder.name, StandardCharsets.UTF_8.toString())}")
                        }
                    }
                }
            }
        }

        // Floating Action Button to play random or fallback video
        FloatingActionButton(
            onClick = {
                Toast.makeText(context, "تشغيل أول فيديو عشوائي تلقائيا", Toast.LENGTH_SHORT).show()
                viewModel.loadVideosForFolder("#DeutschLernen_Netzwerk_Neu_A2_2026")
                val encodedPath = URLEncoder.encode("/storage/emulated/0/#DeutschLernen_Netzwerk_Neu_A2_2026/٠٠٤١٥٨_٢٠٢٦٠٤١١.mp4", StandardCharsets.UTF_8.toString())
                navController.navigate("video_player?filePath=$encodedPath")
            },
            containerColor = Color(0xFF00E5FF),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "تشغيل الكل", tint = Color.Black)
        }

        // Sort Settings overlay
        if (showSortSheet) {
            SortSettingsBottomSheet(viewModel = viewModel) {
                showSortSheet = false
            }
        }
    }
}

@Composable
fun QuickShortcutButton(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .width(68.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun FolderRowItem(folder: VideoFolder, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF14141F))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Metadata chips on Left (pills)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Video count badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${folder.count} فيديو",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            // Size badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF00E5FF).copy(alpha = 0.08f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = folder.size,
                    color = Color(0xFF00E5FF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Expanded Middle title, right aligned
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = folder.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
        }

        // Folder Themed Icon on Right with badging
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A1A2A)),
            contentAlignment = Alignment.Center
        ) {
            val folderIcon = when (folder.iconType) {
                "camera" -> Icons.Default.Camera
                "movie" -> Icons.Default.Movie
                "rec" -> Icons.Default.VideoLibrary
                "whatsapp" -> Icons.Default.PhoneAndroid
                else -> Icons.Default.Folder
            }
            val folderColor = when (folder.iconType) {
                "camera" -> Color(0xFF00E5FF)
                "movie" -> Color(0xFFFFB300)
                "whatsapp" -> Color(0xFF4CAF50)
                "rec" -> Color(0xFFE91E63)
                else -> Color(0xFF00BCD4)
            }

            Icon(
                imageVector = folderIcon,
                contentDescription = null,
                tint = folderColor,
                modifier = Modifier.size(26.dp)
            )

            // Red Dot Badge count
            if (folder.badge > 0) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${folder.badge}",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}


// ----------------- SCREEN 2: VIDEOS LIST SCREEN (محتوى المجلد) -----------------
@Composable
fun FolderVideosScreen(
    folderName: String,
    navController: NavController,
    viewModel: MediaViewModel
) {
    val context = LocalContext.current
    val videos by viewModel.currentFolderVideos.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()

    // Config states
    val showFileExtension by viewModel.showFileExtension.collectAsState()
    val showDuration by viewModel.showDuration.collectAsState()
    val showThumbnail by viewModel.showThumbnail.collectAsState()
    val showQuality by viewModel.showQuality.collectAsState()
    val showSize by viewModel.showSize.collectAsState()

    var showSortSheet by remember { mutableStateOf(false) }

    // Init videos
    LaunchedEffect(folderName) {
        viewModel.loadVideosForFolder(folderName)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0F))) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Header Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Header sort / filters launcher
                IconButton(onClick = { showSortSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "فرز وتصفية",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Folder Title and Subtitle aligned to the right (RTL Layout)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = folderName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp)
                    )
                    Text(
                        text = "مكتبة الفيديو (${videos.size})",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Back arrow pointing RTL
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "الرجوع",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Main Video lists container
            if (videos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00E5FF))
                }
            } else {
                if (displayMode == "Grid") {
                    // TWO COLUMN GRID VIEW
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(videos) { video ->
                            VideoGridItemCard(
                                video = video,
                                showThumbnail = showThumbnail,
                                showFileExtension = showFileExtension
                            ) {
                                val encodedPath = URLEncoder.encode(video.filePath, StandardCharsets.UTF_8.toString())
                                navController.navigate("video_player?filePath=$encodedPath")
                            }
                        }
                    }
                } else {
                    // SEAMLESS VERTICAL LIST VIEW
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(videos) { video ->
                            VideoRowItemCustom(
                                video = video,
                                showFileExtension = showFileExtension,
                                showDuration = showDuration,
                                showThumbnail = showThumbnail,
                                showQuality = showQuality,
                                showSize = showSize
                            ) {
                                val encodedPath = URLEncoder.encode(video.filePath, StandardCharsets.UTF_8.toString())
                                navController.navigate("video_player?filePath=$encodedPath")
                            }
                        }
                    }
                }
            }
        }

        // Bottom sheet sort overlay
        if (showSortSheet) {
            SortSettingsBottomSheet(viewModel = viewModel) {
                showSortSheet = false
            }
        }
    }
}

@Composable
fun VideoRowItemCustom(
    video: VideoFile,
    showFileExtension: Boolean,
    showDuration: Boolean,
    showThumbnail: Boolean,
    showQuality: Boolean,
    showSize: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF14141F))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Metadata / Sizing / Timestamps column on the far Left
        Column(
            modifier = Modifier.width(80.dp),
            horizontalAlignment = Alignment.Start
        ) {
            if (showSize) {
                Text(
                    text = formatBytesToSize(video.size),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
            }
            if (showDuration) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatMillisToDuration(video.duration),
                    color = Color(0xFF00E5FF).copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Codecs / File Title in Center
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.End
        ) {
            val titleText = if (showFileExtension) "${video.title}.mp4" else video.title
            
            Text(
                text = titleText,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )

            if (showQuality) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = video.audioCodec,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = video.videoCodec,
                        color = Color(0xFF00E5FF).copy(alpha = 0.82f),
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF00E5FF).copy(alpha = 0.08f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Elegant Thumbnail / Play icon on Right
        if (showThumbnail) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1B1B2A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun VideoGridItemCard(
    video: VideoFile,
    showThumbnail: Boolean,
    showFileExtension: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14141F)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            
            // Image Box placeholder on Top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(Color(0xFF1B1B2A)),
                contentAlignment = Alignment.Center
            ) {
                if (showThumbnail) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(34.dp)
                    )
                }
                
                // Overlay duration inside card bottom left
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatMillisToDuration(video.duration),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Name in card Bottom details (clean RTL spacing)
            val gridTitle = if (showFileExtension) "${video.title}.mp4" else video.title
            Text(
                text = gridTitle,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Right,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }
    }
}


// ----------------- SCREEN 3: SORT & DISPLAY BOTTOM SHEET MODAL -----------------
@Composable
fun SortSettingsBottomSheet(
    viewModel: MediaViewModel,
    onClose: () -> Unit
) {
    val displayMode by viewModel.displayMode.collectAsState()
    val sortBy by viewModel.sortBy.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()

    val showFileExtension by viewModel.showFileExtension.collectAsState()
    val showDuration by viewModel.showDuration.collectAsState()
    val showThumbnail by viewModel.showThumbnail.collectAsState()
    val showQuality by viewModel.showQuality.collectAsState()
    val showSize by viewModel.showSize.collectAsState()
    val showWatchTime by viewModel.showWatchTime.collectAsState()

    // Local state variables for transactional done actions
    var localDisplayMode by remember { mutableStateOf(displayMode) }
    var localSortBy by remember { mutableStateOf(sortBy) }
    var localSortOrder by remember { mutableStateOf(sortOrder) }

    var localShowFileExtension by remember { mutableStateOf(showFileExtension) }
    var localShowDuration by remember { mutableStateOf(showDuration) }
    var localShowThumbnail by remember { mutableStateOf(showThumbnail) }
    var localShowQuality by remember { mutableStateOf(showQuality) }
    var localShowSize by remember { mutableStateOf(showSize) }
    var localShowWatchTime by remember { mutableStateOf(showWatchTime) }

    var fieldsExpanded by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable { onClose() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {} // block click throughs
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFF161622))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.End
        ) {
            
            // Slide Bar indicator
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: طريقة العرض
            Text(
                text = "طريقة العرض",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // List option Row
                val isList = localDisplayMode == "List"
                Button(
                    onClick = { localDisplayMode = "List" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isList) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF1E1E2C)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "قائمة",
                        color = if (isList) Color(0xFF00E5FF) else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // Grid option Row
                val isGrid = localDisplayMode == "Grid"
                Button(
                    onClick = { localDisplayMode = "Grid" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isGrid) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF1E1E2C)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "شبكة",
                        color = if (isGrid) Color(0xFF00E5FF) else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2: فرز
            Text(
                text = "فرز حسب",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )

            // FlowRow criteria buttons
            val listCriteria = listOf(
                "العنوان" to "Title",
                "التاريخ" to "Date",
                "المدة" to "Duration",
                "الحجم" to "Size"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listCriteria.forEach { (label, value) ->
                    val isSelected = localSortBy == value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF1E1E2C))
                            .clickable { localSortBy = value }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Direction buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isOldest = localSortOrder == "Oldest"
                Button(
                    onClick = { localSortOrder = "Oldest" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOldest) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF1E1E2C)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "الأقدم ↑",
                        color = if (isOldest) Color(0xFF00E5FF) else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                val isNewest = localSortOrder == "Newest"
                Button(
                    onClick = { localSortOrder = "Newest" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isNewest) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF1E1E2C)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "الأجدد ↓",
                        color = if (isNewest) Color(0xFF00E5FF) else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 3: الحقول (Collapsible fields selector)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { fieldsExpanded = !fieldsExpanded }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (fieldsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f)
                )
                Text(
                    text = "الحقول المعروضة",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (fieldsExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    FieldCheckboxRowCustom("المدة الزمنية للفيديو", localShowDuration) { localShowDuration = it }
                    FieldCheckboxRowCustom("الصورة المصغرة (Thumbnail)", localShowThumbnail) { localShowThumbnail = it }
                    FieldCheckboxRowCustom("دقة الفيديو ومثبّت Codec", localShowQuality) { localShowQuality = it }
                    FieldCheckboxRowCustom("الحجم بالميغابايت", localShowSize) { localShowSize = it }
                    FieldCheckboxRowCustom("لاحقة ملفات الفيديو (.mp4)", localShowFileExtension) { localShowFileExtension = it }
                    FieldCheckboxRowCustom("وقت المشاهدة والعداد", localShowWatchTime) { localShowWatchTime = it }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom Buttons (Action triggers)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Done button on Right
                Button(
                    onClick = {
                        viewModel.setDisplayMode(localDisplayMode)
                        viewModel.setSortBy(localSortBy)
                        viewModel.setSortOrder(localSortOrder)

                        viewModel.setShowDuration(localShowDuration)
                        viewModel.setShowThumbnail(localShowThumbnail)
                        viewModel.setShowQuality(localShowQuality)
                        viewModel.setShowSize(localShowSize)
                        viewModel.setShowFileExtension(localShowFileExtension)
                        viewModel.setShowWatchTime(localShowWatchTime)

                        viewModel.applySortingAndFilters()
                        onClose()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("اكتمل", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                // Close Text on Left
                TextButton(onClick = { onClose() }) {
                    Text("إلغاء", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun FieldCheckboxRowCustom(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 13.sp,
            modifier = Modifier.padding(end = 12.dp)
        )
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF00E5FF),
                uncheckedColor = Color.White.copy(alpha = 0.3f),
                checkmarkColor = Color.Black
            )
        )
    }
}
