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

            // Batch inserting local files and pre-populating with Arabic music tracks requested by user
            val presets = listOf(
                MediaFile(
                    id = 10001L,
                    path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                    name = "Khaibt Tawaqo3atk - Houda.mp3",
                    displayName = "خيبت توقعاتك - حودة (Khaibt Tawaqo3atk - Houda)",
                    folderPath = "Aura Hi-Res / Pop",
                    size = 5400000L,
                    duration = 226000L,
                    width = 0,
                    height = 0,
                    videoCodec = "Audio Only",
                    audioCodec = "Houda (حودة)",
                    thumbnailUri = "https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=300",
                    isPrivate = false
                ),
                MediaFile(
                    id = 10002L,
                    path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                    name = "Wegz - Bazat.mp3",
                    displayName = "باظت - Wegz",
                    folderPath = "Aura Hi-Res / Wegz",
                    size = 4200000L,
                    duration = 162000L,
                    width = 0,
                    height = 0,
                    videoCodec = "Audio Only",
                    audioCodec = "Wegz",
                    thumbnailUri = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300",
                    isPrivate = false
                ),
                MediaFile(
                    id = 10003L,
                    path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                    name = "Tesla - Marwan Moussa.mp3",
                    displayName = "Tesla - Marwan Moussa",
                    folderPath = "Aura Hi-Res / Rap",
                    size = 4800000L,
                    duration = 186000L,
                    width = 0,
                    height = 0,
                    videoCodec = "Audio Only",
                    audioCodec = "Marwan Moussa",
                    thumbnailUri = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=300",
                    isPrivate = false
                ),
                MediaFile(
                    id = 10004L,
                    path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
                    name = "Segara - Afroto.mp3",
                    displayName = "Segara - Afroto (سيجارة - عفروتو)",
                    folderPath = "Aura Hi-Res / Afroto",
                    size = 6200000L,
                    duration = 255000L,
                    width = 0,
                    height = 0,
                    videoCodec = "Audio Only",
                    audioCodec = "Afroto",
                    thumbnailUri = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=300",
                    isPrivate = false
                ),
                MediaFile(
                    id = 10005L,
                    path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
                    name = "SINdBAD - Marwan Pablo.mp3",
                    displayName = "SINdBAD - Marwan Pablo",
                    folderPath = "Aura Hi-Res / Pablo",
                    size = 5300000L,
                    duration = 218000L,
                    width = 0,
                    height = 0,
                    videoCodec = "Audio Only",
                    audioCodec = "Marwan Pablo",
                    thumbnailUri = "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=300",
                    isPrivate = false
                ),
                MediaFile(
                    id = 10006L,
                    path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3",
                    name = "Masr7ya - Marwan Pablo.mp3",
                    displayName = "Masr7ya - Marwan Pablo (مسرحية - مروان بابلو)",
                    folderPath = "Aura Hi-Res / Pablo",
                    size = 5200000L,
                    duration = 217000L,
                    width = 0,
                    height = 0,
                    videoCodec = "Audio Only",
                    audioCodec = "Marwan Pablo",
                    thumbnailUri = "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=300",
                    isPrivate = false
                ),
                MediaFile(
                    id = 10007L,
                    path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3",
                    name = "MA YORAM - Lil Baba.mp3",
                    displayName = "MA YORAM - Lil Baba (ما يرام - ليل بابا)",
                    folderPath = "Aura Hi-Res / Rap",
                    size = 5100000L,
                    duration = 203000L,
                    width = 0,
                    height = 0,
                    videoCodec = "Audio Only",
                    audioCodec = "Lil Baba",
                    thumbnailUri = "https://images.unsplash.com/photo-1511379938547-c1f69419868d?w=300",
                    isPrivate = false
                ),
                MediaFile(
                    id = 10008L,
                    path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
                    name = "Ma3lesh Ana Toht - Abyusif.mp3",
                    displayName = "Ma3lesh Ana Toht - Abyusif, Youssef Mohamed",
                    folderPath = "Aura Hi-Res / Abyusif",
                    size = 5200000L,
                    duration = 213000L,
                    width = 0,
                    height = 0,
                    videoCodec = "Audio Only",
                    audioCodec = "Abyusif, Youssef Mohamed",
                    thumbnailUri = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=300",
                    isPrivate = false
                ),
                MediaFile(
                    id = 10009L,
                    path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3",
                    name = "Ayam W Layaly - Tommyy.mp3",
                    displayName = "Ayam W Layaly - Tommyy",
                    folderPath = "Aura Hi-Res / Tommyy",
                    size = 3500000L,
                    duration = 149000L,
                    width = 0,
                    height = 0,
                    videoCodec = "Audio Only",
                    audioCodec = "Tommyy",
                    thumbnailUri = "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=300",
                    isPrivate = false
                ),
                MediaFile(
                    id = 10010L,
                    path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3",
                    name = "Emshy Acoustic Version - Tommyy, Galaleo.mp3",
                    displayName = "Emshy Acoustic Version - Tommyy, Galaleo",
                    folderPath = "Aura Hi-Res / Acoustics",
                    size = 4300000L,
                    duration = 179000L,
                    width = 0,
                    height = 0,
                    videoCodec = "Audio Only",
                    audioCodec = "Tommyy, Galaleo",
                    thumbnailUri = "https://images.unsplash.com/photo-1487180144351-b8472da7a4c3?w=300",
                    isPrivate = false
                ),
                MediaFile(
                    id = 10011L,
                    path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-11.mp3",
                    name = "Akher Oghnya - Cairokee.mp3",
                    displayName = "Akher Oghnya - Cairokee, Amir Eid",
                    folderPath = "Aura Hi-Res / Cairokee",
                    size = 6800000L,
                    duration = 287000L,
                    width = 0,
                    height = 0,
                    videoCodec = "Audio Only",
                    audioCodec = "Cairokee, Amir Eid",
                    thumbnailUri = "https://images.unsplash.com/photo-1483412033650-1015ddeb83d1?w=300",
                    isPrivate = false
                ),
                MediaFile(
                    id = 10012L,
                    path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-12.mp3",
                    name = "Geina El Donia Fi Laffa - Cairokee.mp3",
                    displayName = "Geina El Donia Fi Laffa - Cairokee, Amir Eid",
                    folderPath = "Aura Hi-Res / Cairokee",
                    size = 6700000L,
                    duration = 286000L,
                    width = 0,
                    height = 0,
                    videoCodec = "Audio Only",
                    audioCodec = "Cairokee, Amir Eid",
                    thumbnailUri = "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=300",
                    isPrivate = false
                ),
                MediaFile(
                    id = 10013L,
                    path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-13.mp3",
                    name = "Ebtadena - Amr Diab.mp3",
                    displayName = "Ebtadena - Amr Diab",
                    folderPath = "Aura Hi-Res / Amr Diab",
                    size = 5600000L,
                    duration = 233000L,
                    width = 0,
                    height = 0,
                    videoCodec = "Audio Only",
                    audioCodec = "Amr Diab",
                    thumbnailUri = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=300",
                    isPrivate = false
                ),
                MediaFile(
                    id = 10014L,
                    path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-14.mp3",
                    name = "7adota Almany - Marwan Pablo.mp3",
                    displayName = "7adota Almany - Marwan Pablo",
                    folderPath = "Aura Hi-Res / Pablo",
                    size = 4900000L,
                    duration = 195000L,
                    width = 0,
                    height = 0,
                    videoCodec = "Audio Only",
                    audioCodec = "Marwan Pablo",
                    thumbnailUri = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=300",
                    isPrivate = false
                ),
                MediaFile(
                    id = 10015L,
                    path = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-15.mp3",
                    name = "7obk Rezk.mp3",
                    displayName = "7obk Rezk",
                    folderPath = "Aura Hi-Res / Classical",
                    size = 6100000L,
                    duration = 250000L,
                    width = 0,
                    height = 0,
                    videoCodec = "Audio Only",
                    audioCodec = "Houda (حودة)",
                    thumbnailUri = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300",
                    isPrivate = false
                )
            )
            localFiles.addAll(presets)
            
            mediaDao.insertMediaBatch(localFiles)
            
            Log.d("MediaRepository", "Smart scan completed. Found ${localFiles.size} local media items.")
        } catch (e: Exception) {
            Log.e("MediaRepository", "Error scanning media: ${e.message}", e)
        }
    }
}
