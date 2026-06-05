package com.example.player

enum class VideoResizeMode {
    FIT, FILL, STRETCH, RATIO_16_9, RATIO_4_3, ORIGINAL
}

enum class EqualizerPreset(val displayName: String, val gains: IntArray) {
    NORMAL("Normal", intArrayOf(0, 0, 0, 0, 0)),
    ROCK("Rock", intArrayOf(4, 2, -1, 3, 5)),
    POP("Pop", intArrayOf(-1, 2, 4, 1, -2)),
    JAZZ("Jazz", intArrayOf(3, 1, -2, 2, 3)),
    CLASSICAL("Classical", intArrayOf(5, 3, -1, -3, -4)),
    FLAT("Flat", intArrayOf(0, 0, 0, 0, 0))
}

data class PlayerSettings(
    val resizeMode: VideoResizeMode = VideoResizeMode.FIT,
    val speed: Float = 1.0f,
    val brightness: Float = 0.5f,
    val volumeBoost: Int = 100, // 100 is normal, boost can go up to 200%
    val audioDelay: Int = 0, // Delay in ms
    val equalizerEnabled: Boolean = false,
    val equalizerPreset: EqualizerPreset = EqualizerPreset.NORMAL,
    val bassBoost: Int = 0, // 0 to 1000 millibels
    val virtualizer: Int = 0, // 0 to 1000 millibels
    val subtitleSize: Float = 18f,
    val subtitleColor: String = "#FFFFFFFF",
    val subtitleDelay: Int = 0, // ms
    val isNightModeFilter: Boolean = false,
    val isBackgroundPlayback: Boolean = false,
    val audioOnly: Boolean = false
)
