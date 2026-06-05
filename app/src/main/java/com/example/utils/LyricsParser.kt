package com.example.utils

import com.example.domain.LrcLine
import java.io.File

object LyricsParser {
    fun parseLrc(filePath: String): List<LrcLine> {
        val lrcPath = if (filePath.lowercase().endsWith(".mp3")) {
            filePath.substring(0, filePath.length - 4) + ".lrc"
        } else {
            "$filePath.lrc"
        }
        val file = File(lrcPath)
        if (!file.exists()) return emptyList()
        
        val lines = mutableListOf<LrcLine>()
        try {
            file.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEachLine
                
                // Matches [mm:ss.xx] text or [mm:ss.xxx] text or [mm:ss:xx] text
                val regex = Regex("""\[(\d{2}):(\d{2})[.:](\d{2,3})\](.*)""")
                val match = regex.find(trimmed)
                if (match != null) {
                    val min = match.groupValues[1].toLongOrNull() ?: 0L
                    val sec = match.groupValues[2].toLongOrNull() ?: 0L
                    var msStr = match.groupValues[3]
                    // Normalize milliseconds (if 2 digits like 45, it represents 450ms. If 3 digits, it's 450ms)
                    if (msStr.length == 2) {
                        msStr += "0"
                    }
                    val ms = msStr.toLongOrNull() ?: 0L
                    val text = match.groupValues[4].trim()
                    
                    val timeMs = (min * 60 + sec) * 1000 + ms
                    lines.add(LrcLine(timeMs, text))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return lines.sortedBy { it.timeMs }
    }
}
