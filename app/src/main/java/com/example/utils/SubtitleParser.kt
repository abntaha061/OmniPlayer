package com.example.utils

import android.util.Log
import java.io.File

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

object SubtitleParser {
    
    // Parse raw SRT/VTT/ASS/SUB subtitle strings
    fun parse(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        try {
            val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
            
            // 1. Detect ASS/SSA format
            val isAss = lines.any { it.startsWith("Dialogue:") }
            if (isAss) {
                for (line in lines) {
                    if (line.startsWith("Dialogue:")) {
                        val subLine = line.substringAfter("Dialogue:")
                        val fields = subLine.split(",", limit = 10)
                        if (fields.size >= 10) {
                            val startMs = parseTimeToMs(fields[1])
                            val endMs = parseTimeToMs(fields[2])
                            val textRaw = fields[9].trim()
                            val cleanText = textRaw.replace(Regex("\\{.*?\\}"), "").replace("\\N", "\n").replace("\\n", "\n")
                            cues.add(SubtitleCue(startMs, endMs, cleanText))
                        }
                    }
                }
                return cues.sortedBy { it.startMs }
            }

            // 2. Detect SUB format (MicroDVD frame-based format: {frame1}{frame2}text)
            val isSub = lines.any { it.startsWith("{") && Regex("^\\{\\d+\\}\\{\\d+\\}").containsMatchIn(it) }
            if (isSub) {
                val regex = Regex("^\\{(\\d+)\\}\\{(\\d+)\\}(.*)")
                for (line in lines) {
                    val match = regex.find(line)
                    if (match != null) {
                        val startFrame = match.groupValues[1].toLongOrNull() ?: 0L
                        val endFrame = match.groupValues[2].toLongOrNull() ?: 0L
                        val text = match.groupValues[3].trim().replace("|", "\n")
                        // Standard translation assumes 25 fps => 40ms / frame
                        val startMs = startFrame * 40L
                        val endMs = endFrame * 40L
                        cues.add(SubtitleCue(startMs, endMs, text))
                    }
                }
                return cues.sortedBy { it.startMs }
            }

            // 3. Fallback to standard SRT/VTT parsing
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                if (line.toIntOrNull() != null || line == "WEBVTT" || line.startsWith("WEBVTT")) {
                    i++
                    continue
                }
                
                if (line.contains("-->")) {
                    val times = line.split("-->").map { it.trim() }
                    if (times.size == 2) {
                        val startMs = parseTimeToMs(times[0])
                        val endMs = parseTimeToMs(times[1])
                        
                        val textLines = mutableListOf<String>()
                        i++
                        while (i < lines.size && !lines[i].contains("-->") && lines[i].toIntOrNull() == null) {
                            textLines.add(lines[i])
                            i++
                        }
                        cues.add(SubtitleCue(startMs, endMs, textLines.joinToString("\n")))
                        continue
                    }
                }
                i++
            }
        } catch (e: Exception) {
            Log.e("SubtitleParser", "Error parsing subtitles: ${e.message}")
        }
        return cues.sortedBy { it.startMs }
    }

    private fun parseTimeToMs(timeStr: String): Long {
        val cleaned = timeStr.replace(",", ".").trim()
        val parts = cleaned.split(":")
        var hrs = 0L
        var mins = 0L
        var secsWithMillis = ""
        
        if (parts.size == 3) {
            hrs = parts[0].toLongOrNull() ?: 0L
            mins = parts[1].toLongOrNull() ?: 0L
            secsWithMillis = parts[2]
        } else if (parts.size == 2) {
            mins = parts[0].toLongOrNull() ?: 0L
            secsWithMillis = parts[1]
        }

        val subParts = secsWithMillis.split(".")
        val secs = subParts[0].toLongOrNull() ?: 0L
        val millis = if (subParts.size == 2) {
            // pad millisecond component if needed
            val mStr = subParts[1].padEnd(3, '0').take(3)
            mStr.toLongOrNull() ?: 0L
        } else {
            0L
        }
        
        return (hrs * 3600000) + (mins * 60000) + (secs * 1000) + millis
    }

    // Returns standard default subtitle tracks for demo videos based on playback progress
    fun getDemoCuesForTime(mediaId: Long): List<SubtitleCue> {
        return emptyList()
    }

    // Auto-detect subtitle file by matching video filename in same folder (case insensitive, language codes supported)
    fun findSubtitleFile(videoPath: String?): File? {
        if (videoPath == null) return null
        try {
            val videoFile = File(videoPath)
            val parentDir = videoFile.parentFile
            if (parentDir != null && parentDir.exists() && parentDir.isDirectory) {
                val videoBaseName = videoFile.nameWithoutExtension.lowercase()
                val files = parentDir.listFiles() ?: return null
                val allowedExtensions = listOf("srt", "vtt", "ass", "sub")
                
                for (file in files) {
                    if (file.isFile) {
                        val ext = file.extension.lowercase()
                        if (allowedExtensions.contains(ext)) {
                            val fileBaseName = file.nameWithoutExtension.lowercase()
                            if (fileBaseName == videoBaseName) {
                                return file
                            }
                            if (fileBaseName.startsWith("$videoBaseName.")) {
                                val suffix = fileBaseName.substringAfter("$videoBaseName.")
                                if (suffix.length in 2..3) {
                                    return file
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SubtitleParser", "Error auto-detecting subtitles: ${e.message}")
        }
        return null
    }
}
