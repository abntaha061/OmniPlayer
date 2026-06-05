package com.example.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import com.example.domain.MediaFile
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object AlbumArtHelper {
    // Memory cache for extracted Bitmap objects
    private val bitmapCache = ConcurrentHashMap<String, Bitmap>()
    
    // Memory cache for extracted Color pairs
    private val colorCache = ConcurrentHashMap<String, Pair<Color, Color>>()

    /**
     * Extracts album art from local mp3 file path
     */
    fun extractAlbumArt(filePath: String): Bitmap? {
        if (filePath.isEmpty() || filePath.startsWith("http://") || filePath.startsWith("https://") || filePath.startsWith("rtsp://")) {
            return null
        }
        
        // Return from cache if we already loaded it
        bitmapCache[filePath]?.let { return it }
        
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            return null
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val art = retriever.embeddedPicture
            if (art != null) {
                val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                if (bitmap != null) {
                    bitmapCache[filePath] = bitmap
                }
                bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    /**
     * Obtains cached bitmap or null if not yet extracted
     */
    fun getCachedBitmap(filePath: String): Bitmap? {
        return bitmapCache[filePath] ?: extractAlbumArt(filePath)
    }

    /**
     * Extracts dominant/vibrant colors using androidx.palette
     */
    fun extractColors(bitmap: Bitmap): Pair<Color, Color> {
        return try {
            val palette = Palette.from(bitmap).generate()
            val color1 = palette.getDominantColor(0xFF1A1A2E.toInt())
            val color2 = palette.getVibrantColor(palette.getMutedColor(0xFF00E5FF.toInt()))
            Pair(Color(color1), Color(color2))
        } catch (e: Exception) {
            Pair(Color(0xFF1A1A2E), Color(0xFF00E5FF))
        }
    }

    /**
     * Retrieves colors for a path (checking cached first, then extracting, then falling back to name/preset check)
     */
    fun getColorsForTrack(track: MediaFile): Pair<Color, Color> {
        val path = track.path
        
        // 1. Check colors cache
        colorCache[path]?.let { return it }
        
        // 2. Try to get bitmap and generate colors
        val bitmap = getCachedBitmap(path)
        if (bitmap != null) {
            val colors = extractColors(bitmap)
            colorCache[path] = colors
            return colors
        }
        
        // 3. Fallback to name-based preset colors if no album art
        val fallback = getFallbackColorsByName(track.displayName)
        colorCache[path] = fallback
        return fallback
    }

    /**
     * Preserved designated name-based coloring as fallback
     */
    fun getFallbackColorsByName(displayName: String): Pair<Color, Color> {
        val name = displayName.lowercase()
        return when {
            name.contains("khaibt") || name.contains("خيبت") -> Pair(Color(0xFF4A0A0A), Color(0xFF8B1A1A))
            name.contains("bazat") || name.contains("باظت") -> Pair(Color(0xFF0D1117), Color(0xFFE8A838))
            name.contains("tesla") -> Pair(Color(0xFF0A2A4A), Color(0xFF00C8FF))
            name.contains("segara") || name.contains("سيجارة") -> Pair(Color(0xFF1A0533), Color(0xFF9B59B6))
            name.contains("sindbad") -> Pair(Color(0xFF1A1200), Color(0xFFF0A500))
            name.contains("masr7ya") || name.contains("مسرحية") -> Pair(Color(0xFF0D1F0D), Color(0xFF2ECC71))
            name.contains("yoram") || name.contains("يرام") -> Pair(Color(0xFF1A1A1A), Color(0xFFE74C3C))
            name.contains("toht") || name.contains("تهت") -> Pair(Color(0xFF1A0A00), Color(0xFFFF6B35))
            name.contains("ayam") || name.contains("ليالي") -> Pair(Color(0xFF001A33), Color(0xFF3498DB))
            name.contains("emshy") || name.contains("امشي") -> Pair(Color(0xFF1A1A0D), Color(0xFFF1C40F))
            name.contains("akher") || name.contains("اخر") -> Pair(Color(0xFF0D0D1A), Color(0xFF8E44AD))
            name.contains("laffa") || name.contains("لفة") -> Pair(Color(0xFF1A0D00), Color(0xFFE67E22))
            name.contains("ebtadena") || name.contains("ابتدينا") -> Pair(Color(0xFF001A1A), Color(0xFF1ABC9C))
            name.contains("7adota") || name.contains("حدوتة") -> Pair(Color(0xFF1A0A1A), Color(0xFFE91E8C))
            name.contains("7obk") || name.contains("حبك") -> Pair(Color(0xFF0A1A0A), Color(0xFF27AE60))
            else -> {
                val hash = displayName.hashCode()
                val colors = listOf(
                    Pair(Color(0xFF011627), Color(0xFF00E5FF)),
                    Pair(Color(0xFF0D1B2A), Color(0xFFFF007F)),
                    Pair(Color(0xFF1B4965), Color(0xFFCCFF00)),
                    Pair(Color(0xFF2F3E46), Color(0xFFE040FB)),
                    Pair(Color(0xFF1F2421), Color(0xFFFFD700)),
                    Pair(Color(0xFF001219), Color(0xFF06D6A0))
                )
                val index = Math.abs(hash) % colors.size
                colors[index]
            }
        }
    }
}
