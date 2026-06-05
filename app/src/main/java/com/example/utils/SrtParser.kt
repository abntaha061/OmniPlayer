package com.example.utils

import java.io.File

data class SrtCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

object SrtParser {
    fun parseSrt(filePath: String): List<SrtCue> {
        val srtPath = if (filePath.lowercase().contains(".")) {
            filePath.substringBeforeLast(".") + ".srt"
        } else {
            "$filePath.srt"
        }
        val file = File(srtPath)
        if (!file.exists()) return emptyList()

        val cues = mutableListOf<SrtCue>()
        try {
            val content = file.readText(Charsets.UTF_8)
            // Split by double newlines (paragraphs)
            val blocks = content.split(Regex("(\\r?\\n){2,}"))
            for (block in blocks) {
                val lines = block.trim().lines()
                if (lines.size >= 3) {
                    val timeLine = lines[1]
                    val timeRegex = Regex("""(\d{2}):(\d{2}):(\d{2}),(\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2}),(\d{3})""")
                    val match = timeRegex.find(timeLine)
                    if (match != null) {
                        val startH = match.groupValues[1].toLong()
                        val startM = match.groupValues[2].toLong()
                        val startS = match.groupValues[3].toLong()
                        val startMsVal = match.groupValues[4].toLong()

                        val endH = match.groupValues[5].toLong()
                        val endM = match.groupValues[6].toLong()
                        val endS = match.groupValues[7].toLong()
                        val endMsVal = match.groupValues[8].toLong()

                        val startTotalMs = ((startH * 3600 + startM * 60 + startS) * 1000) + startMsVal
                        val endTotalMs = ((endH * 3600 + endM * 60 + endS) * 1000) + endMsVal

                        val textLines = lines.drop(2).joinToString("\n").trim()
                        cues.add(SrtCue(startTotalMs, endTotalMs, textLines))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return cues.sortedBy { it.startMs }
    }
}
