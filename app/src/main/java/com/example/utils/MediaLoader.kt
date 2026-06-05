package com.example.utils

import android.content.Context
import android.provider.MediaStore
import androidx.compose.ui.graphics.Color
import com.example.domain.Song
import com.example.domain.VideoFile
import java.io.File

object MediaLoader {

    fun loadSongs(context: Context): List<Song> {
        val songs = mutableListOf<Song>()
        val pathsSeen = HashSet<String>()

        // 1. Scan MediaStore
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        try {
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (it.moveToNext()) {
                    val path = it.getString(dataCol) ?: ""
                    if (path.isEmpty() || !path.lowercase().endsWith(".mp3") || pathsSeen.contains(path)) {
                        continue
                    }

                    pathsSeen.add(path)
                    val bitmap = AlbumArtHelper.extractAlbumArt(path)
                    val (c1, c2) = if (bitmap != null) {
                        AlbumArtHelper.extractColors(bitmap)
                    } else {
                        Pair(Color(0xFF1A1A2E), Color(0xFF00E5FF))
                    }

                    songs.add(
                        Song(
                            id = it.getLong(idCol),
                            title = it.getString(titleCol) ?: File(path).nameWithoutExtension,
                            artist = it.getString(artistCol) ?: "Unknown Artist",
                            duration = it.getLong(durCol),
                            filePath = path,
                            albumArt = bitmap,
                            color1 = c1,
                            color2 = c2
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Direct folder scan as fallback/guarantee for "/storage/emulated/0/Music"
        try {
            val musicDir = File("/storage/emulated/0/Music")
            if (musicDir.exists() && musicDir.isDirectory) {
                musicDir.listFiles()?.forEach { file ->
                    val path = file.absolutePath
                    if (file.isFile && path.lowercase().endsWith(".mp3") && !pathsSeen.contains(path)) {
                        pathsSeen.add(path)
                        val bitmap = AlbumArtHelper.extractAlbumArt(path)
                        val (c1, c2) = if (bitmap != null) {
                            AlbumArtHelper.extractColors(bitmap)
                        } else {
                            Pair(Color(0xFF1A1A2E), Color(0xFF00E5FF))
                        }

                        // Use simple CRC/hash as ID
                        val id = path.hashCode().toLong()
                        songs.add(
                            Song(
                                id = id,
                                title = file.nameWithoutExtension,
                                artist = "Local File",
                                duration = 180000L, // Default placeholder, will be resolved by ExoPlayer if played
                                filePath = path,
                                albumArt = bitmap,
                                color1 = c1,
                                color2 = c2
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return songs
    }

    fun loadVideos(context: Context): List<VideoFile> {
        val videos = mutableListOf<VideoFile>()
        val pathsSeen = HashSet<String>()

        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )

        try {
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val durCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                while (it.moveToNext()) {
                    val path = it.getString(dataCol) ?: ""
                    if (path.isEmpty() || pathsSeen.contains(path)) continue
                    pathsSeen.add(path)

                    videos.add(
                        VideoFile(
                            id = it.getLong(idCol),
                            title = it.getString(titleCol) ?: File(path).nameWithoutExtension,
                            filePath = path,
                            duration = it.getLong(durCol),
                            size = it.getLong(sizeCol)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Direct directory checks (Movies, DCIM, Download, etc.)
        val dirs = listOf(
            File("/storage/emulated/0/Movies"),
            File("/storage/emulated/0/DCIM/Camera"),
            File("/storage/emulated/0/Download"),
            File("/storage/emulated/0/Music") // Sometimes video files are here
        )

        for (dir in dirs) {
            try {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { file ->
                        val path = file.absolutePath
                        if (file.isFile && (path.lowercase().endsWith(".mp4") || path.lowercase().endsWith(".mkv") || path.lowercase().endsWith(".webm")) && !pathsSeen.contains(path)) {
                            pathsSeen.add(path)
                            videos.add(
                                VideoFile(
                                    id = path.hashCode().toLong(),
                                    title = file.nameWithoutExtension,
                                    filePath = path,
                                    duration = 0L, // will determine during playback
                                    size = file.length()
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return videos
    }
}
