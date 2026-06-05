package com.example.ui.screens

import java.io.File
import android.os.Environment

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.domain.MediaFile
import com.example.ui.MediaViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun getSongColors(trackDisplayName: String): Pair<Color, Color> {
    val name = trackDisplayName.lowercase()
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
        else -> Pair(Color(0xFF00E5FF), Color(0xFF10131E))
    }
}

@Composable
fun MusicScreen(
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier
) {
    val allMedia by viewModel.allMedia.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentMedia by viewModel.currentMedia.collectAsStateWithLifecycle()
    val sleepTimeRemaining by viewModel.sleepTimeRemaining.collectAsStateWithLifecycle()
    val showHiFiCard by viewModel.showHiFiCard.collectAsStateWithLifecycle()

    var showSleepTimerDialog by remember { mutableStateOf(false) }

    // Filter to show ONLY "Audio Only" files
    val musicFiles = remember(allMedia) {
        allMedia.filter { it.videoCodec == "Audio Only" }
    }

    var selectedTrack by remember { mutableStateOf<MediaFile?>(null) }

    // Sync with global player state
    LaunchedEffect(currentMedia) {
        if (currentMedia != null && currentMedia?.videoCodec == "Audio Only") {
            selectedTrack = currentMedia
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .padding(16.dp)
            .testTag("music_screen_root")
    ) {
        // App title and controls bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Aura Hi-Res Audio",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Enjoy offline tracks & synced lyrics",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            // Sleep Timer control button
            IconButton(
                onClick = { showSleepTimerDialog = true },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (sleepTimeRemaining > 0) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
            ) {
                Icon(
                    imageVector = Icons.Outlined.Timer,
                    contentDescription = "Sleep Timer",
                    tint = if (sleepTimeRemaining > 0) Color(0xFF00E5FF) else Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Timer duration banner if active
        if (sleepTimeRemaining > 0) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        val mins = sleepTimeRemaining / 60
                        val secs = sleepTimeRemaining % 60
                        Text(
                            text = "Music Sleep Timer Active: ${String.format("%02d:%02d", mins, secs)}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "Cancel",
                        color = Color.Red,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { viewModel.cancelSleepTimer() }
                            .padding(4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (musicFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No music elements", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Scan storage to populate audio tracks", color = Color.Gray, fontSize = 12.sp)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Tracklist (1472 songs)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Songs List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(musicFiles) { track ->
                        val isCurrent = currentMedia?.id == track.id
                        val itemColors = getSongColors(track.displayName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isCurrent) Color(0xFF00E5FF).copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f))
                                .clickable {
                                    viewModel.selectMedia(track)
                                    selectedTrack = track
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Song Artwork thumbnail with gradient using the song's specific 2 colors
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(itemColors.first, itemColors.second)
                                        )
                                    )
                            ) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.align(Alignment.Center).size(20.dp)
                                )

                                if (isCurrent && isPlaying) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.35f))
                                    ) {
                                        AudioVisualizerMini(modifier = Modifier.align(Alignment.Center))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Song Metadata text
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.displayName,
                                    color = if (isCurrent) Color(0xFF00E5FF) else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.audioCodec,
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Format track duration
                            Text(
                                text = formatDuration(track.duration),
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Sleep Timer Setup Dialog modal
    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            containerColor = Color(0xFF1E1E24),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = Color(0xFF00E5FF))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Aura Audio Sleep Timer", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text("Automatically pause music playback after preset countdown duration. Perfect for falling asleep.", color = Color.LightGray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    listOf(5, 10, 15, 30, 60).forEach { mins ->
                        Button(
                            onClick = {
                                viewModel.startSleepTimer(mins)
                                showSleepTimerDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f), contentColor = Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("$mins Minutes", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text("Close", color = Color(0xFF00E5FF))
                }
            },
            dismissButton = {
                if (sleepTimeRemaining > 0) {
                    TextButton(onClick = {
                        viewModel.cancelSleepTimer()
                        showSleepTimerDialog = false
                    }) {
                        Text("Turn Off Timer", color = Color.Red)
                    }
                }
            }
        )
    }
}

data class LyricLine(val timeMs: Long, val text: String)

fun findLrcFile(audioPath: String): File? {
    try {
        val audioFile = File(audioPath)
        val baseName = audioFile.nameWithoutExtension
        val parentDir = audioFile.parentFile
        if (parentDir != null) {
            val sameFolderFile = File(parentDir, "$baseName.lrc")
            if (sameFolderFile.exists() && sameFolderFile.isFile) {
                return sameFolderFile
            }
            val sameFolderFileUpper = File(parentDir, "$baseName.LRC")
            if (sameFolderFileUpper.exists() && sameFolderFileUpper.isFile) {
                return sameFolderFileUpper
            }
        }
        
        val lyricDirs = listOf(
            File("/storage/emulated/0/Music/Lyrics"),
            File("/sdcard/Music/Lyrics"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Lyrics")
        )
        for (dir in lyricDirs) {
            if (dir.exists() && dir.isDirectory) {
                val lyricsFile = File(dir, "$baseName.lrc")
                if (lyricsFile.exists() && lyricsFile.isFile) {
                    return lyricsFile
                }
                val lyricsFileUpper = File(dir, "$baseName.LRC")
                if (lyricsFileUpper.exists() && lyricsFileUpper.isFile) {
                    return lyricsFileUpper
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

fun parseLrcTime(timeStr: String): Long? {
    try {
        val parts = timeStr.trim().split(":")
        if (parts.size != 2) return null
        val minutes = parts[0].toLong()
        val secondsParts = parts[1].split(".")
        val seconds = secondsParts[0].toLong()
        val milliseconds = if (secondsParts.size > 1) {
            val msStr = secondsParts[1]
            if (msStr.length == 2) {
                msStr.toLong() * 10
            } else if (msStr.length >= 3) {
                msStr.take(3).toLong()
            } else if (msStr.length == 1) {
                msStr.toLong() * 100
            } else {
                0L
            }
        } else {
            0L
        }
        return (minutes * 60 + seconds) * 1000 + milliseconds
    } catch (e: Exception) {
        return null
    }
}

fun parseLrcFile(file: File): List<LyricLine> {
    val lyrics = mutableListOf<LyricLine>()
    val timeRegex = Regex("\\[(\\d+:\\d+\\.\\d+)\\]")
    try {
        file.forEachLine { line ->
            val matchResults = timeRegex.findAll(line).toList()
            if (matchResults.isNotEmpty()) {
                val cleanText = line.replace(timeRegex, "").trim()
                for (match in matchResults) {
                    val timeStr = match.groupValues[1]
                    val timeMs = parseLrcTime(timeStr)
                    if (timeMs != null) {
                        lyrics.add(LyricLine(timeMs, cleanText))
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return lyrics.sortedBy { it.timeMs }
}

fun generateLyrics(songName: String, durationMs: Long): List<LyricLine> {
    val name = songName.lowercase()
    val lines = when {
        name.contains("khaibt") || name.contains("خيبت") -> listOf(
            "خيبتي توقعاتك 💔",
            "دمرت خلاص حياتك",
            "بتراجع في حساباتك",
            "طيب الله المعين 🕊️",
            "راجع كل اللي فاتك",
            "الحلو في ذكرياتك ✨",
            "لو عدت إنجازاتك",
            "مين شاركك فيها مين؟ 🤔",
            "انا نور طريقك يا اللي كنت",
            "صعب تمشي في الطريق 🚶‍♂️",
            "انا كنت كل أمل حياتك",
            "وإنت بتموت بالبطيء",
            "بعد إجازاتي في ثانية بعد... 👋"
        )
        name.contains("bazat") || name.contains("باظت") -> listOf(
            "باظت الدنيا 💥",
            "من غيرك ما بتتحمل",
            "كل حاجة بتذكرني بيك 💔",
            "واللي فات مش هيرجع ⏳",
            "بس القلب لسه بيحلم ❤️"
        )
        name.contains("tesla") -> listOf(
            "زي التيسلا بشتغل بكهرباك ⚡",
            "ما شيش بنزين غير نفسك وابتساماتك 😊",
            "زي التيسلا بالسرعة الفائقة 🏎️",
            "احلامنا طايرة لفوق في السما",
            "وماشيين مع بعض لآخر الطريق"
        )
        name.contains("segara") || name.contains("سيجارة") -> listOf(
            "سيجارة بعد سيجارة 🚬",
            "والوقت بيمشي",
            "وانا لسه هنا بفكر فيك 💭",
            "الليل طال ومفيش بديل",
            "على أمل لقاك قريبًا"
        )
        name.contains("sindbad") -> listOf(
            "زي سيندباد بسافر ⛵",
            "في بحر الأحلام",
            "وما بخافش من العواصف 🌊",
            "طريقي طويل بس ماشي فيه",
            "والنجم فوق بيبص عليا"
        )
        name.contains("masr7ya") || name.contains("مسرحية") -> listOf(
            "مسرحية الحياة 🎭",
            "وانا البطل فيها 👑",
            "بصراحة من غير مجاملة",
            "كل الأدوار مألوفة",
            "والستارة بتنزل في النهاية"
        )
        name.contains("yoram") || name.contains("يرام") -> listOf(
            "ما يرام كل حاجة ما يرام 👍",
            "والدنيا بتحلى معاك ✨",
            "كل اللي جاي هيكون جميل",
            "مفيش مكان للقلق والخوف",
            "طول ما احنا سوا على طول"
        )
        name.contains("toht") || name.contains("تهت") -> listOf(
            "معلش أنا تحت 👇",
            "بس غداً فوق ↗️",
            "اللي اتكسر هيتصلح 🛠️",
            "من الغلط بنتعلم ونقوم",
            "ونرجع أقوى من الأول"
        )
        name.contains("7adota") || name.contains("حدوتة") -> listOf(
            "حدوتة ألماني 🇩🇪",
            "وبطلها عربي 🇪🇬",
            "بيكتب قصته بإيده ✍️"
        )
        name.contains("7obk") || name.contains("حبك") -> listOf(
            "حبك رزق ❤️",
            "ما كل واحد يلاقيه",
            "انت نصيبي وبس 💍"
        )
        else -> listOf(
            "♫ Welcome to Aura Audio Player ♫",
            "Enjoying high resolution acoustics",
            "You are playing: $songName",
            "Supported by advanced spatial equalizer",
            "♫ Premium quality cinematic resonance ♫",
            "High fidelity audio decoders active",
            "You can slide the progress seekbar freely",
            "LRC Lyrics scrolling in perfect synchronization",
            "♫ Synced digital audio streaming ♫",
            "Setting sleep timers is available anytime",
            "Thank you for choosing Aura Player!",
            "♫ Peak acoustic clarity reached ♫"
        )
    }
    val lyricsList = mutableListOf<LyricLine>()
    val interval = (durationMs.coerceAtLeast(30000L)) / lines.size
    lines.forEachIndexed { idx, txt ->
        lyricsList.add(LyricLine(idx * interval, txt))
    }
    return lyricsList
}

@Composable
fun ActiveMusicPlayerCard(
    track: MediaFile,
    isPlaying: Boolean,
    viewModel: MediaViewModel,
    allTracks: List<MediaFile>,
    onPlayPauseToggle: () -> Unit,
    onStopClick: () -> Unit
) {
    var showLyricsTab by remember { mutableStateOf(false) }
    var isCarModeActive by remember { mutableStateOf(false) }

    val player = remember { viewModel.playerManager.getPlayer() }
    val duration = track.duration.coerceAtLeast(1L)
    
    var currentPlaybackProgress by remember { mutableStateOf(0f) }
    var currentPositionMs by remember { mutableStateOf(0L) }

    // Synchronize play progress and lyrics pointer
    LaunchedEffect(isPlaying, track.id) {
        while (isPlaying) {
            currentPositionMs = player.currentPosition
            currentPlaybackProgress = currentPositionMs.toFloat() / duration.toFloat()
            delay(250)
        }
    }

    // Dynamic LRC search & parse logic
    var trackLyrics by remember(track.id) { mutableStateOf<List<LyricLine>?>(null) }

    LaunchedEffect(track.path) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val file = findLrcFile(track.path)
            trackLyrics = if (file != null) {
                parseLrcFile(file)
            } else {
                emptyList()
            }
        }
    }

    // Floating Vinyl Disc Animation rotating
    val infiniteTransition = rememberInfiniteTransition(label = "music_rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinyl_rotation"
    )

    // Master Container Card
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151922)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.2f))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            
            // 1. ALBUM ART ANIMATED BACKGROUND REFLECTION (Vinyl Background Effect)
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0.08f)
                        .blur(50.dp)
                ) {
                    if (track.thumbnailUri.isNotEmpty()) {
                        AsyncImage(
                            model = track.thumbnailUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = 1.3f,
                                    scaleY = 1.3f,
                                    rotationZ = rotationAngle
                                )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(Color(0xFF00E5FF), Color.Transparent),
                                        radius = 300f
                                    )
                                )
                        )
                    }
                }
            }

            // MAIN CONTENT COLUMN
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isPlaying) Color.Green else Color.Red)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPlaying) "Hi-Fi Processing Engine" else "Hi-Fi Paused",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Toggle Lyrics pane
                        IconButton(
                            onClick = { showLyricsTab = !showLyricsTab },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (showLyricsTab) Icons.Default.MusicNote else Icons.Default.Lyrics,
                                contentDescription = "Toggle Synced Lyrics",
                                tint = if (showLyricsTab) Color(0xFF00E5FF) else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Toggle Car mode overlay
                        IconButton(
                            onClick = { isCarModeActive = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = "Car mode panel",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = onStopClick,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close player", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (showLyricsTab) {
                    // ====== LRC SYNCED LYRICS MODE ======
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "LRC SYNCED LYRICS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFF00E5FF),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        val lyricsList = trackLyrics
                        if (lyricsList == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.20f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color(0xFF00E5FF), modifier = Modifier.size(24.dp))
                            }
                        } else if (lyricsList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.20f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No lyrics found",
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            val activeLyricIndex = lyricsList.indexOfLast { currentPositionMs >= it.timeMs }.coerceAtLeast(0)
                            val lazyListState = rememberLazyListState()

                            // Auto-scroll to current active lyric index
                            LaunchedEffect(activeLyricIndex) {
                                lazyListState.animateScrollToItem(activeLyricIndex)
                            }

                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.20f))
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(lyricsList) { index, line ->
                                    val isActive = index == activeLyricIndex
                                    val scale by animateFloatAsState(if (isActive) 1.15f else 0.95f, label = "lyricScale")
                                    val textAlpha by animateFloatAsState(if (isActive) 1.0f else 0.40f, label = "lyricAlpha")
                                    
                                    Text(
                                        text = line.text,
                                        fontSize = 14.sp,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isActive) Color(0xFF00E5FF) else Color.White,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp)
                                            .graphicsLayer(scaleX = scale, scaleY = scale)
                                            .alpha(textAlpha)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ====== VINYL / ALBUM ARTWORK VISUAL COMPONENT ======
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Vinyl CD disc graphic
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .rotate(if (isPlaying) rotationAngle else 0f)
                                .background(Color.Black)
                                .border(4.dp, Color(0xFF1E1E24), CircleShape)
                                .border(10.dp, Color.Black, CircleShape)
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (track.thumbnailUri.isNotEmpty()) {
                                AsyncImage(
                                    model = track.thumbnailUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E5FF).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF00E5FF))
                                }
                            }
                            // Middle hole
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF151922))
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Song text attributes
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.displayName,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Path: ${track.folderPath}",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Decoder: Sw High-Fidelity Audio",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // PROGRESS SEEKER INTERACTIVE BAR
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(currentPositionMs),
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                        Text(
                            text = formatDuration(duration),
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }
                    Slider(
                        value = currentPlaybackProgress.coerceIn(0f, 1f),
                        onValueChange = {
                            currentPlaybackProgress = it
                            currentPositionMs = (it * duration).toLong()
                            player.seekTo(currentPositionMs)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Graphic Waveform Simulating Audio Levels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val waveHeights = if (isPlaying) {
                        listOf(0.4f, 0.8f, 0.5f, 0.9f, 0.2f, 0.7f, 0.6f, 0.8f, 0.3f, 0.7f, 0.5f, 0.9f, 0.4f, 0.8f, 0.5f, 0.6f)
                    } else {
                        listOf(0.2f, 0.2f, 0.2f, 0.2f, 0.2f, 0.2f, 0.2f, 0.2f, 0.2f, 0.2f, 0.2f, 0.2f, 0.2f, 0.2f, 0.2f, 0.2f)
                    }
                    waveHeights.forEach { heightMultiplier ->
                        val animatedhMultiplier by animateFloatAsState(
                            targetValue = if (isPlaying) (heightMultiplier + (kotlin.random.Random.nextFloat() * 0.2f)).coerceIn(0.1f, 1.0f) else 0.15f,
                            animationSpec = tween(durationMillis = 200),
                            label = "waveform"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(animatedhMultiplier)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF00E5FF))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Regular Playback Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val currentIndex = allTracks.indexOfFirst { it.id == track.id }
                            if (currentIndex > 0) {
                                viewModel.selectMedia(allTracks[currentIndex - 1])
                            }
                        },
                        enabled = allTracks.indexOfFirst { it.id == track.id } > 0
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Song", tint = Color.LightGray)
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))

                    FilledIconButton(
                        onClick = onPlayPauseToggle,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF00E5FF)),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause Button",
                            tint = Color.Black,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))
                    
                    IconButton(
                        onClick = {
                            val currentIndex = allTracks.indexOfFirst { it.id == track.id }
                            if (currentIndex >= 0 && currentIndex < allTracks.size - 1) {
                                viewModel.selectMedia(allTracks[currentIndex + 1])
                            }
                        },
                        enabled = allTracks.indexOfFirst { it.id == track.id } < allTracks.size - 1
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next Song", tint = Color.LightGray)
                    }
                }
            }

            // ====== CAR MODE FULL-SCREEN OVERLAY (UPGRADE 8) ======
            if (isCarModeActive) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color(0xFF0D1117))
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Exit Car Mode indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Aura Car Mode Active", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Button(
                                onClick = { isCarModeActive = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.2f), contentColor = Color.Red),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text("Exit Car Mode", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Massive Track metadata Text
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = track.displayName,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Highway Safety Layout",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // GIANT HIGH-CONTRAST DASHBOARD CONTROLS FOR CARS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Giant Skip Previous
                            IconButton(
                                onClick = {
                                    val currentIndex = allTracks.indexOfFirst { it.id == track.id }
                                    if (currentIndex > 0) {
                                        viewModel.selectMedia(allTracks[currentIndex - 1])
                                    }
                                },
                                enabled = allTracks.indexOfFirst { it.id == track.id } > 0,
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color.White.copy(alpha = 0.05f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.SkipPrevious, 
                                    contentDescription = "Prev", 
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            // Massive high-contrast play pause
                            IconButton(
                                onClick = onPlayPauseToggle,
                                modifier = Modifier
                                    .size(96.dp)
                                    .background(Color(0xFF00E5FF), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.Black,
                                    modifier = Modifier.size(54.dp)
                                )
                            }

                            // Giant Skip Next
                            IconButton(
                                onClick = {
                                    val currentIndex = allTracks.indexOfFirst { it.id == track.id }
                                    if (currentIndex >= 0 && currentIndex < allTracks.size - 1) {
                                        viewModel.selectMedia(allTracks[currentIndex + 1])
                                    }
                                },
                                enabled = allTracks.indexOfFirst { it.id == track.id } < allTracks.size - 1,
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color.White.copy(alpha = 0.05f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.SkipNext, 
                                    contentDescription = "Next", 
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        // Bottom progress/Timer Info
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "MUTE",
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        val deviceVolume = player.volume
                                        if (deviceVolume > 0f) {
                                            player.volume = 0f
                                        } else {
                                            player.volume = 1f
                                        }
                                    }
                                    .padding(8.dp)
                            )
                            
                            Text(
                                text = "${formatDuration(currentPositionMs)} / ${formatDuration(duration)}",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioVisualizerMini(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val anims = listOf(
            rememberInfiniteTransition(label = "").animateFloat(
                initialValue = 0.2f, targetValue = 0.8f,
                animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse), label = ""
            ),
            rememberInfiniteTransition(label = "").animateFloat(
                initialValue = 0.4f, targetValue = 1.0f,
                animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse), label = ""
            ),
            rememberInfiniteTransition(label = "").animateFloat(
                initialValue = 0.1f, targetValue = 0.9f,
                animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse), label = ""
            )
        )
        anims.forEach { animState ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(animState.value)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color(0xFF00E5FF))
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSecs = millis / 1000
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}
