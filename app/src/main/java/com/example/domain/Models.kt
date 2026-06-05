package com.example.domain

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val filePath: String,
    val albumArt: Bitmap?,    // Extracted with MediaMetadataRetriever
    val color1: Color,         // From Palette API
    val color2: Color          // From Palette API
)

data class LrcLine(
    val timeMs: Long,
    val text: String
)

data class VideoFile(
    val id: Long,
    val title: String,
    val filePath: String,
    val duration: Long,
    val size: Long,
    val videoCodec: String = "H.264",
    val audioCodec: String = "AAC"
)

data class SubtitleStyle(
    val fontSize: Float = 18f, // SP
    val fontColor: Color = Color.White,
    val backgroundColor: Color = Color.Black.copy(alpha = 0.5f),
    val fontFamily: String = "Cairo", // Cairo, Tajawal, Arial, Georgia
    val position: String = "Bottom" // Top, Center, Bottom
)
