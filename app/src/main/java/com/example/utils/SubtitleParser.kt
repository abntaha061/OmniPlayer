package com.example.utils

import android.util.Log

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
        return listOf(
            SubtitleCue(1000, 5000, "[Aura Media Player] Welcome to Aura Video Player Pro!"),
            SubtitleCue(6000, 11000, "[Spectacular Cinematics] Testing high-fidelity H265 rendering..."),
            SubtitleCue(12000, 16000, "[Full Gestures] Slide vertically on left to control brightness."),
            SubtitleCue(17000, 21000, "[Acoustic Pro] Slide on the right column to control volume boost up to 200%!"),
            SubtitleCue(22000, 27000, "Double Tap on either side to seek 10s backward or forward."),
            SubtitleCue(28000, 34000, "Equalizer enables pristine audio enhancements with virtual surround bounds."),
            SubtitleCue(35000, 1200000, "Enjoy your premium media experience. Built with Jetpack Compose & Media3.")
        )
    }
}
