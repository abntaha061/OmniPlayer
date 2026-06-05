package com.example.data

import android.content.ContentResolver
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
    val favorites: Flow<List<MediaFile>> = mediaDao.getFavorites()
    val playlists: Flow<List<Playlist>> = mediaDao.getAllPlaylists()
    val watchHistory: Flow<List<WatchHistoryItem>> = mediaDao.getWatchHistory()
    val networkStreams: Flow<List<NetworkStream>> = mediaDao.getAllStreams()

    suspend fun getMediaById(id: Long): MediaFile? = mediaDao.getMediaById(id)
    
    suspend fun updateFavorite(id: Long, isFav: Boolean) = mediaDao.updateFavorite(id, isFav)

    suspend fun updateResumePosition(id: Long, position: Long) = mediaDao.updateResumePosition(id, position)

    suspend fun insertStream(stream: NetworkStream) = mediaDao.insertStream(stream)

    suspend fun deleteStream(id: Long) = mediaDao.deleteStream(id)

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
            
            // Querying MediaStore.Video
            val contentResolver: ContentResolver = context.contentResolver
            val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.DATE_ADDED
            )
            
            val cursor = contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val widthCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                while (c.moveToNext()) {
                    val path = c.getString(dataCol)
                    val name = c.getString(nameCol) ?: File(path).name
                    val size = c.getLong(sizeCol)
                    val duration = c.getLong(durationCol)
                    val width = c.getInt(widthCol)
                    val height = c.getInt(heightCol)
                    val dateAdded = c.getLong(dateCol) * 1000L // convert to ms
                    
                    val file = File(path)
                    val folderPath = file.parent ?: "/Storage"
                    
                    val mediaFile = MediaFile(
                        path = path,
                        name = name,
                        displayName = name.substringBeforeLast("."),
                        folderPath = folderPath,
                        size = size,
                        duration = duration,
                        width = if (width > 0) width else 1920,
                        height = if (height > 0) height else 1080,
                        dateAdded = dateAdded
                    )
                    localFiles.add(mediaFile)
                }
            }

            // Always insert our premium high quality streaming links so that the user is guaranteed to enjoy 
            // a great media player demo with fully functional playback and subtitles, regardless of clean emulator storages!
            val demoMedia = getDemoStreams()
            
            // Batch inserting
            val allToInsert = localFiles + demoMedia
            mediaDao.insertMediaBatch(allToInsert)
            
            Log.d("MediaRepository", "Smart scan completed. Found ${localFiles.size} local videos and ${demoMedia.size} demo videos.")
        } catch (e: Exception) {
            Log.e("MediaRepository", "Error scanning media: ${e.message}", e)
        }
    }

    private fun getDemoStreams(): List<MediaFile> {
        val folder = "Public Demo Streams"
        return listOf(
            MediaFile(
                id = 900001,
                path = "https://storage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                name = "Tears of Steel (4K Sci-Fi Demo)",
                displayName = "Tears of Steel",
                folderPath = folder,
                size = 138000000,
                duration = 734000,
                width = 3840,
                height = 2160,
                videoCodec = "HEVC/H265",
                audioCodec = "DTS/AC3 Stereo",
                thumbnailUri = "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?q=80&w=400"
            ),
            MediaFile(
                id = 900002,
                path = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                name = "Big Buck Bunny (HLS Live Stream)",
                displayName = "Big Buck Bunny",
                folderPath = folder,
                size = 85000000,
                duration = 596000,
                width = 1920,
                height = 1080,
                videoCodec = "AVC/H264",
                audioCodec = "AAC Dual 2.0",
                thumbnailUri = "https://images.unsplash.com/photo-1534447677768-be436bb09401?q=80&w=400"
            ),
            MediaFile(
                id = 900003,
                path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                name = "Elephants Dream (Visual FX Demo)",
                displayName = "Elephants Dream",
                folderPath = folder,
                size = 115000000,
                duration = 654000,
                width = 1280,
                height = 720,
                videoCodec = "MPEG4",
                audioCodec = "Dolby Digital Atmos",
                thumbnailUri = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?q=80&w=400"
            ),
            MediaFile(
                id = 900004,
                path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                name = "Chromecast Demo Video",
                displayName = "Chromecast Video Tutorial",
                folderPath = folder,
                size = 45000000,
                duration = 15000,
                width = 1920,
                height = 1080,
                videoCodec = "H264",
                audioCodec = "Stereo AAC",
                thumbnailUri = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?q=80&w=400"
            )
        )
    }
}
