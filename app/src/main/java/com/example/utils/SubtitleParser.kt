package com.example.utils

import android.util.Log

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

object SubtitleParser {
    
    // Parse raw SRT/VTT subtitle strings
    fun parse(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        try {
            val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                if (line.toIntOrNull() != null || line == "WEBVTT") {
                    // SRT list index or VTT header, advance
                    i++
                    continue
                }
                
                if (line.contains("-->")) {
                    val times = line.split("-->").map { it.trim() }
                    if (times.size == 2) {
                        val startMs = parseTimeToMs(times[0])
                        val endMs = parseTimeToMs(times[1])
                        
                        // Collect text lines next
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
        return cues
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
