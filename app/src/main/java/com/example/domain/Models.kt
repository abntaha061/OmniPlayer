package com.example.domain

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_files")
data class MediaFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String, // Can be local path or stream URL
    val name: String,
    val displayName: String,
    val folderPath: String,
    val size: Long,
    val duration: Long,
    val width: Int = 1920,
    val height: Int = 1080,
    val videoCodec: String = "h264",
    val audioCodec: String = "aac",
    val dateAdded: Long = System.currentTimeMillis(),
    val resumePosition: Long = 0, // Location in ms to resume
    var isFavorite: Boolean = false,
    val thumbnailUri: String = "",
    val subtitlePath: String = "",
    val decoderMode: String = "AUTO",
    val fps: Float = 30.0f,
    val lastWatched: Long = 0L,
    val isPrivate: Boolean = false
) {
    val isStream: Boolean get() = path.startsWith("http://") || path.startsWith("https://") || path.startsWith("rtsp://")
}

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_items")
data class PlaylistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val mediaId: Long,
    val orderIndex: Int
)

@Entity(tableName = "watch_history")
data class WatchHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val watchedAt: Long = System.currentTimeMillis(),
    val progress: Long,
    val duration: Long,
    val completed: Boolean = false
)

@Entity(tableName = "network_streams")
data class NetworkStream(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val type: String = "HLS", // HLS, DASH, Direct, RTSP
    val dateAdded: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)
