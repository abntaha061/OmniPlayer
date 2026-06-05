package com.example.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette

object AlbumArtHelper {
    private val bitmapCache = HashMap<String, Bitmap>()

    fun extractAlbumArt(filePath: String): Bitmap? {
        synchronized(bitmapCache) {
            bitmapCache[filePath]?.let { return it }
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val art = retriever.embeddedPicture
            if (art != null) {
                val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                if (bitmap != null) {
                    synchronized(bitmapCache) {
                        bitmapCache[filePath] = bitmap
                    }
                }
                bitmap
            } else null
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (ex: Exception) {
                // ignore
            }
        }
    }

    fun extractColors(bitmap: Bitmap): Pair<Color, Color> {
        return try {
            val palette = Palette.from(bitmap).generate()
            val color1 = palette.getDominantColor(0xFF1a1a2e.toInt())
            val color2 = palette.getVibrantColor(palette.getMutedColor(0xFF00e5ff.toInt()))
            Pair(Color(color1), Color(color2))
        } catch (e: Exception) {
            Pair(Color(0xFF1A1A2E), Color(0xFF00E5FF))
        }
    }
}
