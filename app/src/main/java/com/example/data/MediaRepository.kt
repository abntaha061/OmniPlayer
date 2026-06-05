package com.example.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.example.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class MediaRepository(
    private val context: Context,
    private val mediaDao: MediaDao
) {
    val allMedia: Flow<List<MediaFile>> = mediaDao.getAllMedia()
    val privateMedia: Flow<List<MediaFile>> = mediaDao.getPrivateMedia()
    val favorites: Flow<List<MediaFile>> = mediaDao.getFavorites()
    val playlists: Flow<List<Playlist>> = mediaDao.getAllPlaylists()
    val watchHistory: Flow<List<WatchHistoryItem>> = mediaDao.getWatchHistory()

    suspend fun getMediaById(id: Long): MediaFile? = mediaDao.getMediaById(id)
    
    suspend fun updateFavorite(id: Long, isFav: Boolean) = mediaDao.updateFavorite(id, isFav)

    suspend fun updatePrivateState(id: Long, isPrivate: Boolean) = mediaDao.updatePrivateState(id, isPrivate)

    suspend fun updateResumePosition(id: Long, position: Long) = mediaDao.updateResumePosition(id, position)

    suspend fun updateDecoderMode(id: Long, decoderMode: String) = mediaDao.updateDecoderMode(id, decoderMode)

    suspend fun updateSubtitlePath(id: Long, subtitlePath: String) = mediaDao.updateSubtitlePath(id, subtitlePath)

    suspend fun deleteMedia(id: Long) {
        val media = mediaDao.getMediaById(id)
        if (media != null && !media.isStream) {
            try {
                val file = File(media.path)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e("MediaRepository", "Error deleting local file: ${e.message}")
            }
        }
        mediaDao.deleteMediaById(id)
    }

    suspend fun insertHistory(mediaId: Long, progress: Long, duration: Long) {
        val completed = progress >= duration * 0.95
        mediaDao.insertHistory(
            WatchHistory(
                mediaId = mediaId,
                progress = progress,
                duration = duration,
                completed = completed,
                watchedAt = System.currentTimeMillis()
            )
        )
        mediaDao.updateResumePosition(mediaId, if (completed) 0 else progress)
        mediaDao.updateLastWatched(mediaId, System.currentTimeMillis())
    }

    suspend fun clearHistory() = mediaDao.clearHistory()

    // Create a new playlist
    suspend fun createPlaylist(name: String): Long {
        return mediaDao.insertPlaylist(Playlist(name = name))
    }

    suspend fun deletePlaylist(id: Long) = mediaDao.deletePlaylist(id)

    suspend fun addToPlaylist(playlistId: Long, mediaId: Long) {
        mediaDao.insertPlaylistItem(
            PlaylistItem(
                playlistId = playlistId,
                mediaId = mediaId,
                orderIndex = 0 // Simplicity
            )
        )
    }

    fun getPlaylistMedia(playlistId: Long): Flow<List<MediaFile>> = mediaDao.getPlaylistMedia(playlistId)

    // Smart scan mechanism that queries local MediaStore plus inserts wonderful streaming presets
    suspend fun scanLocalMedia() = withContext(Dispatchers.IO) {
        try {
            val localFiles = mutableListOf<MediaFile>()
            val contentResolver: ContentResolver = context.contentResolver
            
            // 1. Querying MediaStore.Video
            val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.DATE_ADDED
            )
            
            val videoCursor = contentResolver.query(
                videoUri,
                videoProjection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )

            videoCursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val widthCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val path = c.getString(dataCol)
                    val name = c.getString(nameCol) ?: File(path).name
                    val size = c.getLong(sizeCol)
                    val duration = c.getLong(durationCol)
                    val width = c.getInt(widthCol)
                    val height = c.getInt(heightCol)
                    val dateAdded = c.getLong(dateCol) * 1000L // convert to ms
                    
                    val file = File(path)
                    val folderPath = file.parent ?: "/Storage"
                    val folderName = file.parentFile?.name ?: ""
                    
                    // Exclude any videos originating from "Screen recordings" folders or containing "Screen recordings" / "ScreenRecordings" in path
                    if (folderName.equals("Screen recordings", ignoreCase = true) || 
                        folderName.equals("ScreenRecordings", ignoreCase = true) ||
                        folderPath.contains("Screen recordings", ignoreCase = true) ||
                        folderPath.contains("ScreenRecordings", ignoreCase = true)) {
                        continue
                    }
                    
                    val videoUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()
                    
                    val mediaFile = MediaFile(
                        path = path,
                        name = name,
                        displayName = name.substringBeforeLast("."),
                        folderPath = folderPath,
                        size = size,
                        duration = duration,
                        width = if (width > 0) width else 1920,
                        height = if (height > 0) height else 1080,
                        dateAdded = dateAdded,
                        thumbnailUri = videoUri
                    )
                    localFiles.add(mediaFile)
                }
            }

            // 2. Querying MediaStore.Audio (Both Internal and External Content URIs)
            val audioProjection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID
            )
            val audioSelection = "${MediaStore.Audio.Media.DURATION} >= ?"
            val audioSelectionArgs = arrayOf("10000") // >= 10 seconds
            val audioSortOrder = "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"

            val audioUris = listOf(
                MediaStore.Audio.Media.INTERNAL_CONTENT_URI,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            )

            for (audioUri in audioUris) {
                val audioCursor = contentResolver.query(
                    audioUri,
                    audioProjection,
                    audioSelection,
                    audioSelectionArgs,
                    audioSortOrder
                )
                audioCursor?.use { c ->
                    val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)

                    while (c.moveToNext()) {
                        val path = c.getString(dataCol)
                        if (path.isNullOrEmpty()) continue
                        val name = c.getString(nameCol) ?: File(path).name
                        val duration = c.getLong(durationCol)
                        
                        val file = File(path)
                        val size = if (file.exists()) file.length() else 0L
                        val dateAdded = if (file.exists()) file.lastModified() else System.currentTimeMillis()
                        val folderPath = file.parent ?: "/Storage"
                        val folderName = file.parentFile?.name ?: ""
                        
                        // Exclude any files from screen recordings path
                        if (folderName.equals("Screen recordings", ignoreCase = true) || 
                            folderName.equals("ScreenRecordings", ignoreCase = true) ||
                            folderPath.contains("Screen recordings", ignoreCase = true) ||
                            folderPath.contains("ScreenRecordings", ignoreCase = true)) {
                            continue
                        }

                        val artistName = c.getString(artistCol) ?: "Unknown Artist"
                        val mediaFile = MediaFile(
                            path = path,
                            name = name,
                            displayName = name.substringBeforeLast("."),
                            folderPath = folderPath,
                            size = size,
                            duration = duration,
                            width = 0,
                            height = 0,
                            videoCodec = "Audio Only",
                            audioCodec = artistName,
                            dateAdded = dateAdded
                        )
                        localFiles.add(mediaFile)
                    }
                }
            }

            // Batch inserting local files only - no demo/placeholder/sample data prepopulation
            mediaDao.insertMediaBatch(localFiles)
            
            Log.d("MediaRepository", "Smart scan completed. Found ${localFiles.size} local media items.")
        } catch (e: Exception) {
            Log.e("MediaRepository", "Error scanning media: ${e.message}", e)
        }
    }
}
