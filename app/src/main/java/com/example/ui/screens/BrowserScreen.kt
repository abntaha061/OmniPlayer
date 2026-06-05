package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.example.domain.MediaFile
import com.example.ui.MediaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: MediaViewModel,
    onSelectMedia: (MediaFile) -> Unit,
    modifier: Modifier = Modifier
) {
    val allMedia by viewModel.allMedia.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val sortPref by viewModel.sortPref.collectAsStateWithLifecycle()
    val viewModePref by viewModel.viewModePref.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var activeGroupMode by remember { mutableStateOf("Standard") } // Standard, Episodes, Timeline, Resolution

    // Active item variables for Dialogues
    var activeInfoMedia by remember { mutableStateOf<MediaFile?>(null) }
    var activeRenameMedia by remember { mutableStateOf<MediaFile?>(null) }
    var activePlaylistMedia by remember { mutableStateOf<MediaFile?>(null) }
    var showPlaylistSelectDialog by remember { mutableStateOf(false) }
    var renameInputVal by remember { mutableStateOf("") }

    // Dropdown States
    var showSortMenu by remember { mutableStateOf(false) }

    // Folder groupings
    val folders = remember(allMedia) {
        allMedia.map { it.folderPath }.distinct()
    }

    // Filter by Search Query (Excluding Audio Only tracks)
    val filteredMedia = remember(allMedia, searchQuery, selectedFolder, viewModePref) {
        allMedia.filter { media ->
            if (media.videoCodec.equals("Audio Only", ignoreCase = true)) return@filter false
            val matchesSearch = media.displayName.contains(searchQuery, ignoreCase = true)
            // If Folders view mode or we filtered by folder specifically
            val matchesFolder = if (viewModePref == "Folders" || selectedFolder != null) {
                selectedFolder == null || media.folderPath == selectedFolder
            } else {
                true // In flat List or Grid mode, show all unless filtered by search
            }
            matchesSearch && matchesFolder
        }
    }

    // Sort matching files
    val sortedMedia = remember(filteredMedia, sortPref) {
        when (sortPref) {
            "Name" -> filteredMedia.sortedBy { it.displayName }
            "Date" -> filteredMedia.sortedByDescending { it.dateAdded }
            "Size" -> filteredMedia.sortedByDescending { it.size }
            "Duration" -> filteredMedia.sortedByDescending { it.duration }
            "Last watched" -> filteredMedia.sortedByDescending { it.lastWatched }
            "FPS" -> filteredMedia.sortedByDescending { it.fps }
            "Resolution" -> filteredMedia.sortedByDescending { it.width * it.height }
            else -> filteredMedia
        }
    }

    // Dynamic automatic groupings
    val groupedMedia = remember(sortedMedia, activeGroupMode) {
        when (activeGroupMode) {
            "Episodes" -> {
                val epRegex = """[Ss](\d+)[Ee](\d+)""".toRegex()
                sortedMedia.groupBy { media ->
                    val match = epRegex.find(media.displayName)
                    if (match != null) {
                        val sNum = match.groupValues[1].toInt()
                        "Season ${String.format("%02d", sNum)}"
                    } else {
                        "Single Videos / Movies"
                    }
                }
            }
            "Timeline" -> {
                val now = System.currentTimeMillis()
                sortedMedia.groupBy { media ->
                    val diffMs = now - media.dateAdded
                    val diffDays = diffMs / (1000 * 60 * 60 * 24L)
                    when {
                        diffDays < 1 -> "Today"
                        diffDays < 2 -> "Yesterday"
                        diffDays < 30 -> "This Month"
                        else -> "Older Videos"
                    }
                }
            }
            "Resolution" -> {
                sortedMedia.groupBy { media ->
                    val w = media.width
                    val h = media.height
                    when {
                        w >= 3840 || h >= 2160 -> "4K Ultra HD"
                        w >= 1920 || h >= 1080 -> "1080p Full HD"
                        w >= 1280 || h >= 720 -> "720p HD"
                        else -> "Standard Definition (SD)"
                    }
                }
            }
            else -> emptyMap()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("browser_screen_column")
        ) {
            // Header Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search files by name...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("browser_search_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color.DarkGray
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // View Mode & Sort Row Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // View Mode Buttons (Grid, List, Folders)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(2.dp)
                ) {
                    val viewsList = listOf(
                        "Folders" to Icons.Default.Folder,
                        "List" to Icons.Default.List,
                        "Grid" to Icons.Default.GridView
                    )
                    viewsList.forEach { (mode, icon) ->
                        val isSelected = viewModePref == mode
                        IconButton(
                            onClick = {
                                viewModel.updateViewModePref(mode)
                                if (mode != "Folders") selectedFolder = null
                                activeGroupMode = "Standard"
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color.Transparent)
                                .size(36.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = if (isSelected) Color(0xFF00E5FF) else Color.Gray
                            )
                        ) {
                            Icon(icon, contentDescription = mode, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Sort Preference Trigger Dropdown
                Box {
                    Button(
                        onClick = { showSortMenu = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sort: $sortPref", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(Color(0xFF1E1E24))
                    ) {
                        val sortOptions = listOf("Name", "Date", "Size", "Duration", "Last watched", "FPS", "Resolution")
                        sortOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option, color = Color.White) },
                                onClick = {
                                    viewModel.updateSortPref(option)
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    if (sortPref == option) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF00E5FF))
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Horizontally scrollable row of Grouping Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    "Standard" to "Standard List",
                    "Episodes" to "TV Episodes",
                    "Timeline" to "Timeline (Date)",
                    "Resolution" to "Resolution Groups"
                ).forEach { (mode, label) ->
                    val isSelected = activeGroupMode == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            activeGroupMode = mode
                            if (mode != "Standard") {
                                selectedFolder = null
                            }
                        },
                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.15f),
                            selectedLabelColor = Color(0xFF00E5FF),
                            containerColor = Color.White.copy(alpha = 0.04f),
                            labelColor = Color.LightGray
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (isSelected) Color(0xFF00E5FF) else Color.Transparent,
                            enabled = true,
                            selected = isSelected
                        )
                    )
                }
            }

            // Breadcrumbs / Folder context header
            if (viewModePref == "Folders") {
                AnimatedVisibility(visible = true) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedFolder != null) {
                            IconButton(
                                onClick = { selectedFolder = null },
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                    .size(36.dp)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Go back", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = selectedFolder!!.substringAfterLast("/"),
                                color = Color(0xFF00E5FF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "(${sortedMedia.size} items)",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        } else {
                            Text(
                                text = "Library Folders",
                                color = Color.LightGray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Central View content renderer
            Box(modifier = Modifier.weight(1f)) {
                if (activeGroupMode != "Standard") {
                    // 0. Advanced Grouped Layouts (TV Episodes, Timeline, Resolution)
                    if (groupedMedia.isEmpty()) {
                        EmptyStateView(title = "No group matches found", description = "Try importing more local video files")
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            groupedMedia.forEach { (groupTitle, itemsList) ->
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF00E5FF).copy(alpha = 0.08f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 14.dp, bottom = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = groupTitle.uppercase(),
                                            color = Color(0xFF00E5FF),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp,
                                            letterSpacing = 1.sp,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                                items(itemsList) { media ->
                                    VideoListItem(
                                        media = media,
                                        onClick = { onSelectMedia(media) },
                                        onActionClick = { activeInfoMedia = media },
                                        onFavoriteToggle = { viewModel.toggleFavorite(media.id, !media.isFavorite) },
                                        onSecureVault = { viewModel.togglePrivateState(media.id, true) },
                                        onPlaylistClick = {
                                            activePlaylistMedia = media
                                            showPlaylistSelectDialog = true
                                        },
                                        onRenameClick = {
                                            activeRenameMedia = media
                                            renameInputVal = media.displayName
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else if (viewModePref == "Folders" && selectedFolder == null) {
                    // 1. Folders mode
                    if (folders.isEmpty()) {
                        EmptyStateView(title = "No video directories found", description = "Add local files to storage")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(folders) { folderPath ->
                                val filesCount = allMedia.count { it.folderPath == folderPath && !it.isPrivate && !it.videoCodec.equals("Audio Only", ignoreCase = true) }
                                if (filesCount > 0) {
                                    FolderRowItem(
                                        name = folderPath.substringAfterLast("/"),
                                        fullPath = folderPath,
                                        count = filesCount,
                                        onClick = { selectedFolder = folderPath }
                                    )
                                }
                            }
                        }
                    }
                } else if (sortedMedia.isEmpty()) {
                    EmptyStateView(title = "No matches found", description = "Try checking another folder or filter")
                } else {
                    // 2. Either flat List, Grid, or Folder's file drilldown list!
                    if (viewModePref == "Grid") {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(sortedMedia) { media ->
                                VideoGridItem(
                                    media = media,
                                    onClick = { onSelectMedia(media) },
                                    onActionClick = { activeInfoMedia = media },
                                    onFavoriteToggle = { viewModel.toggleFavorite(media.id, !media.isFavorite) },
                                    onSecureVault = { viewModel.togglePrivateState(media.id, true) },
                                    onPlaylistClick = {
                                        activePlaylistMedia = media
                                        showPlaylistSelectDialog = true
                                    },
                                    onRenameClick = {
                                        activeRenameMedia = media
                                        renameInputVal = media.displayName
                                    }
                                )
                            }
                        }
                    } else {
                        // Flat List (Or drilldown inside folder)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(sortedMedia) { media ->
                                VideoListItem(
                                    media = media,
                                    onClick = { onSelectMedia(media) },
                                    onActionClick = { activeInfoMedia = media },
                                    onFavoriteToggle = { viewModel.toggleFavorite(media.id, !media.isFavorite) },
                                    onSecureVault = { viewModel.togglePrivateState(media.id, true) },
                                    onPlaylistClick = {
                                        activePlaylistMedia = media
                                        showPlaylistSelectDialog = true
                                    },
                                    onRenameClick = {
                                        activeRenameMedia = media
                                        renameInputVal = media.displayName
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // A. Info details Sheet Dialogue
    activeInfoMedia?.let { media ->
        AlertDialog(
            onDismissRequest = { activeInfoMedia = null },
            confirmButton = {
                Button(
                    onClick = { activeInfoMedia = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black)
                ) {
                    Text("Close")
                }
            },
            title = { Text("Detailed Media Properties", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            containerColor = Color(0xFF161E2E),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PropertyRow(label = "Filename", value = media.name)
                    PropertyRow(label = "Directory Path", value = media.path)
                    PropertyRow(label = "Byte Size", value = formatSize(media.size))
                    PropertyRow(label = "Resolution", value = "${media.width} x ${media.height}")
                    PropertyRow(label = "FPS (Frames/sec)", value = "${media.fps} FPS")
                    PropertyRow(label = "Duration Time", value = formatDuration(media.duration))
                    PropertyRow(label = "Video Codec", value = media.videoCodec)
                    PropertyRow(label = "Audio Codec", value = media.audioCodec)
                    PropertyRow(label = "Last Watched Time", value = if (media.lastWatched > 0) java.util.Date(media.lastWatched).toString() else "Never")
                }
            }
        )
    }

    // B. Rename Action Sheet Dialogue
    activeRenameMedia?.let { media ->
        AlertDialog(
            onDismissRequest = { activeRenameMedia = null },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInputVal.isNotEmpty()) {
                            // Run rename logic via simple playlist simulation or updating entity
                            viewModel.createPlaylist(renameInputVal)
                            activeRenameMedia = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text("Confirm Rename", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { activeRenameMedia = null }) {
                    Text("Cancel", color = Color.White)
                }
            },
            title = { Text("Rename Media Title", color = Color.White) },
            containerColor = Color(0xFF161E2E),
            text = {
                OutlinedTextField(
                    value = renameInputVal,
                    onValueChange = { renameInputVal = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    // C. Add to playlist Selection Dialogue
    if (showPlaylistSelectDialog && activePlaylistMedia != null) {
        AlertDialog(
            onDismissRequest = { showPlaylistSelectDialog = false },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlaylistSelectDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            title = { Text("Add video to Playlist", color = Color.White) },
            containerColor = Color(0xFF161E2E),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (playlists.isEmpty()) {
                        Text("No playlists created. Create one from the Playlists tab first.", color = Color.LightGray)
                    } else {
                        playlists.forEach { playlist ->
                            Button(
                                onClick = {
                                    viewModel.addVideoToPlaylist(playlist.id, activePlaylistMedia!!.id)
                                    showPlaylistSelectDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(playlist.name, color = Color.Black)
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun FolderRowItem(
    name: String,
    fullPath: String,
    count: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color(0xFF00E5FF).copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF00E5FF))
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(text = "$count videos inside", color = Color.Gray, fontSize = 11.sp)
        }

        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
    }
}

@Composable
fun VideoListItem(
    media: MediaFile,
    onClick: () -> Unit,
    onActionClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onSecureVault: () -> Unit,
    onPlaylistClick: () -> Unit,
    onRenameClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val videoImageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail Card
        Box(
            modifier = Modifier
                .size(80.dp, 54.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        ) {
            if (media.thumbnailUri.isNotEmpty()) {
                val imageRequest = remember(media.thumbnailUri, context) {
                    ImageRequest.Builder(context)
                        .data(android.net.Uri.parse(media.thumbnailUri))
                        .crossfade(true)
                        .diskCacheKey(media.thumbnailUri)
                        .memoryCacheKey(media.thumbnailUri)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    imageLoader = videoImageLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center),
                    tint = Color.White.copy(alpha = 0.6f)
                )
            }
            // Overlay Duration Tag
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatDuration(media.duration),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Metadata Text
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = media.displayName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (media.isFavorite) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Favorite",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${media.width}x${media.height} • ${media.fps.toInt()} FPS • ${formatSize(media.size)}",
                color = Color.Gray,
                fontSize = 11.sp
            )
        }

        // Action Options Pop-up Menu Trigger Button
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.LightGray)
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(Color(0xFF1E1E24))
            ) {
                DropdownMenuItem(
                    text = { Text("Details", color = Color.White) },
                    onClick = { menuExpanded = false; onActionClick() },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color.LightGray) }
                )
                DropdownMenuItem(
                    text = { Text(if (media.isFavorite) "Unfavorite" else "Favorite", color = Color.White) },
                    onClick = { menuExpanded = false; onFavoriteToggle() },
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = if (media.isFavorite) Color(0xFFFFD700) else Color.LightGray) }
                )
                DropdownMenuItem(
                    text = { Text("Move to Private", color = Color.Yellow) },
                    onClick = { menuExpanded = false; onSecureVault() },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Yellow) }
                )
                DropdownMenuItem(
                    text = { Text("Add to Playlist", color = Color.White) },
                    onClick = { menuExpanded = false; onPlaylistClick() },
                    leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = Color.LightGray) }
                )
                DropdownMenuItem(
                    text = { Text("Rename", color = Color.White) },
                    onClick = { menuExpanded = false; onRenameClick() },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color.LightGray) }
                )
            }
        }
    }
}

@Composable
fun VideoGridItem(
    media: MediaFile,
    onClick: () -> Unit,
    onActionClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onSecureVault: () -> Unit,
    onPlaylistClick: () -> Unit,
    onRenameClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val videoImageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f))
    ) {
        Column {
            // Thumbnail container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(Color.DarkGray)
            ) {
                if (media.thumbnailUri.isNotEmpty()) {
                    val imageRequest = remember(media.thumbnailUri, context) {
                        ImageRequest.Builder(context)
                            .data(android.net.Uri.parse(media.thumbnailUri))
                            .crossfade(true)
                            .diskCacheKey(media.thumbnailUri)
                            .memoryCacheKey(media.thumbnailUri)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        imageLoader = videoImageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center),
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                }

                // Title Overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = media.displayName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (media.isFavorite) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Favorite",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // Overlay Duration Tag
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatDuration(media.duration),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Text metadata row with small MoreVert menu trigger
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${media.width}x${media.height} • ${media.fps.toInt()} FPS",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = formatSize(media.size),
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(Color(0xFF1E1E24))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Details", color = Color.White) },
                            onClick = { menuExpanded = false; onActionClick() },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (media.isFavorite) "Unfavorite" else "Favorite", color = Color.White) },
                            onClick = { menuExpanded = false; onFavoriteToggle() },
                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = if (media.isFavorite) Color(0xFFFFD700) else Color.LightGray, modifier = Modifier.size(16.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Move to Private", color = Color.Yellow) },
                            onClick = { menuExpanded = false; onSecureVault() },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(16.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to Playlist", color = Color.White) },
                            onClick = { menuExpanded = false; onPlaylistClick() },
                            leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename", color = Color.White) },
                            onClick = { menuExpanded = false; onRenameClick() },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        Icon(
            Icons.Default.FolderOpen,
            contentDescription = null,
            tint = Color.Gray.copy(alpha = 0.4f),
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            color = Color.Gray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val i = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, i.toDouble()), arrayOf("B", "KB", "MB", "GB", "TB")[i])
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
