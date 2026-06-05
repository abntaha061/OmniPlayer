package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.domain.MediaFile
import com.example.ui.MediaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(
    viewModel: MediaViewModel,
    onSelectMedia: (MediaFile) -> Unit,
    modifier: Modifier = Modifier
) {
    val privateMedia by viewModel.privateMedia.collectAsStateWithLifecycle()
    val watchHistory by viewModel.watchHistory.collectAsStateWithLifecycle()
    val isLocked by viewModel.isPrivateFolderLocked.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf("history") } // "history", "vault", "settings"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .testTag("me_screen_root")
    ) {
        // Me Header Section
        ProfileHeader()

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation Tabs for ME Screen
        TabRow(
            selectedTabIndex = when (activeTab) {
                "history" -> 0
                "vault" -> 1
                else -> 2
            },
            containerColor = Color.Transparent,
            contentColor = Color(0xFF00E5FF),
            divider = {},
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Tab(
                selected = activeTab == "history",
                onClick = { activeTab = "history" },
                text = { Text("Recent Progress", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) },
                selectedContentColor = Color(0xFF00E5FF),
                unselectedContentColor = Color.Gray
            )
            Tab(
                selected = activeTab == "vault",
                onClick = { activeTab = "vault" },
                text = { Text("Private Vault", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(
                    if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isLocked) Color(0xFFFFD54F) else Color(0xFF00E5FF)
                ) },
                selectedContentColor = Color(0xFF00E5FF),
                unselectedContentColor = Color.Gray
            )
            Tab(
                selected = activeTab == "settings",
                onClick = { activeTab = "settings" },
                text = { Text("Preferences", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) },
                selectedContentColor = Color(0xFF00E5FF),
                unselectedContentColor = Color.Gray
            )
        }

        // Active Panel Page
        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                "history" -> WatchHistoryPanel(watchHistory, viewModel, onSelectMedia)
                "vault" -> PrivateVaultPanel(isLocked, privateMedia, viewModel, onSelectMedia)
                "settings" -> SettingsSubPanel(viewModel)
            }
        }
    }
}

@Composable
fun ProfileHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // User profile image artwork
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color(0xFF00E5FF).copy(alpha = 0.15f))
                .border(1.5.dp, Color(0xFF00E5FF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(28.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = "Aura Player Pro",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "abntaha061@gmail.com",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun WatchHistoryPanel(
    historyList: List<com.example.data.WatchHistoryItem>,
    viewModel: MediaViewModel,
    onSelectMedia: (MediaFile) -> Unit
) {
    if (historyList.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(54.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text("No playback history", color = Color.White, fontWeight = FontWeight.Bold)
            Text("Your watched movies and audio will appear here", color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recently Watched", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                TextButton(
                    onClick = { viewModel.clearHistory() },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Clear All", fontSize = 11.sp)
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(historyList) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Find media element inside allMedia list or play directly
                                viewModel.allMedia.value.find { it.id == item.mediaId }?.let {
                                    onSelectMedia(it)
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f))
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp, 40.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.DarkGray)
                            ) {
                                if (item.mediaThumbnail.isNotEmpty()) {
                                    AsyncImage(
                                        model = item.mediaThumbnail,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.align(Alignment.Center))
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.mediaDisplayName,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                LinearProgressIndicator(
                                    progress = {
                                        if (item.duration > 0) item.progress.toFloat() / item.duration else 0f
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .height(3.dp),
                                    color = Color(0xFF00E5FF),
                                    trackColor = Color.DarkGray
                                )
                                Text(
                                    text = "Progress: ${item.progress / 1000}s / ${item.duration / 1000}s",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrivateVaultPanel(
    isLocked: Boolean,
    privateMedia: List<MediaFile>,
    viewModel: MediaViewModel,
    onSelectMedia: (MediaFile) -> Unit
) {
    var hasPin by remember { mutableStateOf(viewModel.hasPrivatePin()) }

    var inputPin by remember { mutableStateOf("") }
    var setupPin by remember { mutableStateOf("") }
    var setupEmail by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    var recoveryMode by remember { mutableStateOf(false) }
    var recoveryEmailInput by remember { mutableStateOf("") }
    var recoveryNewPin by remember { mutableStateOf("") }

    // Re-check PIN presence on start
    LaunchedEffect(isLocked) {
        hasPin = viewModel.hasPrivatePin()
    }

    if (!hasPin) {
        // Render Vault PIN Initialization screen
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text("Setup Private Vault", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Hold sensitive files away from external explorers", color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = setupPin,
                onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) setupPin = it },
                label = { Text("Specify PIN (4-6 digits)", color = Color.Gray) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color.DarkGray
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = setupEmail,
                onValueChange = { setupEmail = it },
                label = { Text("Local Recovery Email", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color.DarkGray
                )
            )

            if (errorMessage.isNotEmpty()) {
                Text(errorMessage, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (setupPin.length < 4) {
                        errorMessage = "PIN must be at least 4 digits"
                    } else if (setupEmail.isEmpty() || !setupEmail.contains("@")) {
                        errorMessage = "Specify a valid recovery email"
                    } else {
                        viewModel.setPrivatePin(setupPin, setupEmail)
                        hasPin = true
                        errorMessage = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Confirm & Create", fontWeight = FontWeight.Bold)
            }
        }
    } else if (isLocked) {
        // Vault PIN validation keypad display
        if (recoveryMode) {
            // recovery Email view
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.MailOutline, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(44.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Pin Recovery Wizard", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Enter the matching local email to reset your PIN", color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = recoveryEmailInput,
                    onValueChange = { recoveryEmailInput = it },
                    label = { Text("Recovery Email", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color.DarkGray
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = recoveryNewPin,
                    onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) recoveryNewPin = it },
                    label = { Text("Set New PIN (4-6 digits)", color = Color.Gray) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color.DarkGray
                    )
                )

                if (errorMessage.isNotEmpty()) {
                    Text(errorMessage, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (viewModel.resetPinWithRecovery(recoveryEmailInput, recoveryNewPin)) {
                            recoveryMode = false
                            errorMessage = ""
                        } else {
                            errorMessage = "Incorrect recovery email match!"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply & Reset", fontWeight = FontWeight.Bold)
                }

                TextButton(onClick = { recoveryMode = false; errorMessage = "" }) {
                    Text("Cancel", color = Color.White)
                }
            }
        } else {
            // Render regular validation UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(54.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Text("Private Folder Locked", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Verify credentials to enter vault", color = Color.Gray, fontSize = 11.sp)

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = inputPin,
                    onValueChange = {
                        if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                            inputPin = it
                            if (it.length >= 4) {
                                if (viewModel.unlockPrivateFolder(it)) {
                                    inputPin = ""
                                    errorMessage = ""
                                }
                            }
                        }
                    },
                    label = { Text("Enter Numeric PIN", color = Color.Gray) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.testTag("private_pin_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color.DarkGray
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { recoveryMode = true; errorMessage = "" }) {
                        Text("Forgot PIN?", color = Color.Gray, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            if (viewModel.unlockPrivateFolder(inputPin)) {
                                inputPin = ""
                                errorMessage = ""
                            } else {
                                errorMessage = "Invalid PIN specified"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black)
                    ) {
                        Text("Unlock", fontWeight = FontWeight.Bold)
                    }
                }

                if (errorMessage.isNotEmpty()) {
                    Text(errorMessage, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    } else {
        // Vault UI - displays matching secured items!
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vault Elements (${privateMedia.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Yellow
                )

                Button(
                    onClick = { viewModel.lockPrivateFolder() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.Yellow)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Lock Manual", fontSize = 10.sp, color = Color.Yellow)
                }
            }

            if (privateMedia.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(54.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Empty Vault", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Move clips from public library inside options menu", color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(privateMedia) { media ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .clickable { onSelectMedia(media) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(70.dp, 46.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.DarkGray)
                            ) {
                                if (media.thumbnailUri.isNotEmpty()) {
                                    AsyncImage(
                                        model = media.thumbnailUri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.align(Alignment.Center))
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = media.displayName,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${media.width}x${media.height} • ${media.fps.toInt()} FPS • ${media.videoCodec}",
                                    color = Color.LightGray,
                                    fontSize = 10.sp
                                )
                            }

                            // Unlock and restore button action
                            IconButton(
                                onClick = { viewModel.togglePrivateState(media.id, false) }
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = "Restore to public", tint = Color.Yellow, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSubPanel(viewModel: MediaViewModel) {
    var timerVal by remember { mutableStateOf("") }
    val sleepTimeRemaining by viewModel.sleepTimeRemaining.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Theme & Media Preferences", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)

        // Equalizer shortcut box
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Equalizer Engine Status", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF00E5FF))
                Spacer(modifier = Modifier.height(4.dp))
                Text("All sound parameters (Spatializer sound, Bass Engine) are optimized automatically under Sw decoder mode.", fontSize = 11.sp, color = Color.LightGray)
            }
        }

        // Sleep Timer Panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Aura Sleep Timer", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))

                if (sleepTimeRemaining > 0) {
                    val mm = sleepTimeRemaining / 60
                    val ss = sleepTimeRemaining % 60
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format("Shutoff standard player in %02d:%02d", mm, ss),
                            color = Color(0xFF00E5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { viewModel.cancelSleepTimer() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
                        ) {
                            Text("Cancel", fontSize = 11.sp)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = timerVal,
                            onValueChange = { if (it.all { ch -> ch.isDigit() }) timerVal = it },
                            placeholder = { Text("Mins", color = Color.Gray) },
                            modifier = Modifier.width(90.dp).height(48.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color.DarkGray
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                val mins = timerVal.toIntOrNull() ?: 0
                                if (mins > 0) {
                                    viewModel.startSleepTimer(mins)
                                    timerVal = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text("Start Timer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PropertyRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
