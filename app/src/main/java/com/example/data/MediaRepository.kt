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
    val privateMedia: Flow<List<MediaFile>> = mediaDao.getPrivateMedia()
    val favorites: Flow<List<MediaFile>> = mediaDao.getFavorites()
    val playlists: Flow<List<Playlist>> = mediaDao.getAllPlaylists()
    val watchHistory: Flow<List<WatchHistoryItem>> = mediaDao.getWatchHistory()
    val networkStreams: Flow<List<NetworkStream>> = mediaDao.getAllStreams()

    suspend fun getMediaById(id: Long): MediaFile? = mediaDao.getMediaById(id)
    
    suspend fun updateFavorite(id: Long, isFav: Boolean) = mediaDao.updateFavorite(id, isFav)

    suspend fun updatePrivateState(id: Long, isPrivate: Boolean) = mediaDao.updatePrivateState(id, isPrivate)

    suspend fun updateResumePosition(id: Long, position: Long) = mediaDao.updateResumePosition(id, position)

    suspend fun updateDecoderMode(id: Long, decoderMode: String) = mediaDao.updateDecoderMode(id, decoderMode)

    suspend fun updateSubtitlePath(id: Long, subtitlePath: String) = mediaDao.updateSubtitlePath(id, subtitlePath)

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
                thumbnailUri = "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?q=80&w=400",
                fps = 60.0f
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
                thumbnailUri = "https://images.unsplash.com/photo-1534447677768-be436bb09401?q=80&w=400",
                fps = 30.0f
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
                thumbnailUri = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?q=80&w=400",
                fps = 24.0f
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
                thumbnailUri = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?q=80&w=400",
                fps = 25.0f,
                dateAdded = System.currentTimeMillis() - 7200000 // 2 hours ago -> Today
            ),
            MediaFile(
                id = 900005,
                path = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                name = "Cosmic Voyage S01E01 - The Pioneer.mp4",
                displayName = "Cosmic Voyage S01E01 - The Pioneer",
                folderPath = "Sci-Fi Series/Season 01",
                size = 62000000,
                duration = 14000,
                width = 1920,
                height = 1080,
                videoCodec = "AVC/H264",
                audioCodec = "AAC 5.1",
                thumbnailUri = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?q=80&w=400",
                fps = 30.0f,
                dateAdded = System.currentTimeMillis() - 95000000 // ~26 hours ago -> Yesterday
            ),
            MediaFile(
                id = 900006,
                path = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                name = "Cosmic Voyage S01E02 - Dark Nebula.mp4",
                displayName = "Cosmic Voyage S01E02 - Dark Nebula",
                folderPath = "Sci-Fi Series/Season 01",
                size = 59000000,
                duration = 12000,
                width = 1280,
                height = 720,
                videoCodec = "AVC/H264",
                audioCodec = "AAC 5.1",
                thumbnailUri = "https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?q=80&w=400",
                fps = 24.0f,
                dateAdded = System.currentTimeMillis() - (15 * 86400000L) // 15 days ago -> This Month
            ),
            MediaFile(
                id = 900007,
                path = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
                name = "Cosmic Voyage S02E01 - Andromeda's Edge.mp4",
                displayName = "Cosmic Voyage S02E01 - Andromeda's Edge",
                folderPath = "Sci-Fi Series/Season 02",
                size = 71000000,
                duration = 16000,
                width = 1920,
                height = 1080,
                videoCodec = "AVC/H264",
                audioCodec = "AAC Stereo",
                thumbnailUri = "https://images.unsplash.com/photo-1506318137071-a8e063b4bec0?q=80&w=400",
                fps = 30.0f,
                dateAdded = System.currentTimeMillis() - (45 * 86400000L) // 45 days ago -> Older
            ),
            MediaFile(
                id = 900011,
                path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                name = "SoundHelix Song 1 (Synth Chill)",
                displayName = "SoundHelix Instrumental Vol. 1",
                folderPath = "Aura Music Library",
                size = 12500000,
                duration = 372000, // 6:12
                width = 0,
                height = 0,
                videoCodec = "Audio Only",
                audioCodec = "MP3 Stereo 320kbps",
                thumbnailUri = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=400",
                fps = 0.0f
            ),
            MediaFile(
                id = 900012,
                path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                name = "SoundHelix Song 2 (Ambient Groove)",
                displayName = "SoundHelix Instrumental Vol. 2",
                folderPath = "Aura Music Library",
                size = 10500000,
                duration = 425000, // 7:05
                width = 0,
                height = 0,
                videoCodec = "Audio Only",
                audioCodec = "MP3 Stereo 320kbps",
                thumbnailUri = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=400",
                fps = 0.0f
            ),
            MediaFile(
                id = 900013,
                path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
                name = "SoundHelix Song 4 (Epic Lofi Study)",
                displayName = "SoundHelix Instrumental Vol. 4",
                folderPath = "Aura Music Library",
                size = 11200000,
                duration = 302000, // 5:02
                width = 0,
                height = 0,
                videoCodec = "Audio Only",
                audioCodec = "MP3 Stereo 320kbps",
                thumbnailUri = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=400",
                fps = 0.0f
            )
        )
    }
}
