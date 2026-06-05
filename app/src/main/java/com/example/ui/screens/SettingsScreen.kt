package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.player.EqualizerPreset
import com.example.player.VideoResizeMode
import com.example.ui.MediaViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier
) {
    val playerManager = viewModel.playerManager
    val settings by playerManager.settings.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(scrollState)
            .testTag("settings_screen_scroll"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "App Settings & Equalizer",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // 1. Video Playback Prefs Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Video Rendering & Playback", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Default Aspect Layout", color = Color.White, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        VideoResizeMode.values().take(3).forEach { mode ->
                            FilterChip(
                                selected = settings.resizeMode == mode,
                                onClick = { playerManager.applyResizeMode(mode) },
                                label = { Text(mode.name, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                    selectedLabelColor = Color(0xFF00E5FF)
                                )
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Default Player Speed", color = Color.White, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { speedVal ->
                            FilterChip(
                                selected = settings.speed == speedVal,
                                onClick = { playerManager.applySpeed(speedVal) },
                                label = { Text("${speedVal}x", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                    selectedLabelColor = Color(0xFF00E5FF)
                                )
                            )
                        }
                    }
                }
            }
        }

        // 2. Global Equalizer Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Global Audio Equalizer", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Switch(
                        checked = settings.equalizerEnabled,
                        onCheckedChange = { playerManager.toggleEqualizer(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF))
                    )
                }

                if (settings.equalizerEnabled) {
                    Text("Preset Profiles", color = Color.LightGray, fontSize = 12.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (chunk in EqualizerPreset.values().toList().chunked(3)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (preset in chunk) {
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
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text("Bass Boost Power Option (${settings.bassBoost})", color = Color.White, fontSize = 13.sp)
                Slider(
                    value = settings.bassBoost.toFloat(),
                    onValueChange = { playerManager.applyBassBoost(it.roundToInt()) },
                    valueRange = 0f..1000f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                )

                Text("Surround Virtualizer Bound (${settings.virtualizer})", color = Color.White, fontSize = 13.sp)
                Slider(
                    value = settings.virtualizer.toFloat(),
                    onValueChange = { playerManager.applyVirtualizer(it.roundToInt()) },
                    valueRange = 0f..1000f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                )
            }
        }

        // 3. Clear Cache Operations Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Cache & Clear Databases", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                
                Button(
                    onClick = { showResetDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.75f)),
                    modifier = Modifier.fillMaxWidth().testTag("clear_history_button")
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear Watch History Caches", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistory()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Confirm Reset", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            title = { Text("Reset History Log?", color = Color.White) },
            containerColor = Color(0xFF161E2E),
            text = { Text("Are you absolutely sure you want to empty the video continue-play pointers and watch logs? This cannot be undone.", color = Color.LightGray) }
        )
    }
}
