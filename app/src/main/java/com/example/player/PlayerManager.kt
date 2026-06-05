package com.example.player

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import com.example.domain.MediaFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.media3.common.C
import androidx.media3.common.AudioAttributes
import androidx.media3.session.MediaSession

@OptIn(UnstableApi::class)
class PlayerManager(private val context: Context) {
    
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var isNoisyReceiverRegistered = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                exoPlayer?.pause()
            }
        }
    }

    private fun registerNoisyReceiver() {
        if (!isNoisyReceiverRegistered) {
            try {
                val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                context.registerReceiver(noisyReceiver, filter)
                isNoisyReceiverRegistered = true
            } catch (e: Exception) {
                Log.e("PlayerManager", "Failed to register noise receiver: ${e.message}")
            }
        }
    }

    private fun unregisterNoisyReceiver() {
        if (isNoisyReceiverRegistered) {
            try {
                context.unregisterReceiver(noisyReceiver)
                isNoisyReceiverRegistered = false
            } catch (e: Exception) {
                Log.e("PlayerManager", "Failed to unregister noise receiver: ${e.message}")
            }
        }
    }
    
    // Equalizer, Bass & Spatializers
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private val _settings = MutableStateFlow(PlayerSettings())
    val settings: StateFlow<PlayerSettings> = _settings.asStateFlow()

    private val _currentMedia = MutableStateFlow<MediaFile?>(null)
    val currentMedia: StateFlow<MediaFile?> = _currentMedia.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    var activeDecoderMode: String = "AUTO"

    fun setCurrentMedia(media: MediaFile?) {
        _currentMedia.value = media
        if (media != null) {
            activeDecoderMode = media.decoderMode
        }
    }

    fun recreatePlayer() {
        val player = exoPlayer ?: return
        val pos = player.currentPosition
        val playing = player.isPlaying
        
        release()
        
        val newPlayer = getPlayer()
        val media = _currentMedia.value
        if (media != null) {
            play(media, pos)
            if (!playing) {
                newPlayer.pause()
            }
        }
    }

    fun getPlayer(): ExoPlayer {
        if (exoPlayer == null) {
            val customSelector = object : MediaCodecSelector {
                override fun getDecoderInfos(
                    mimeType: String,
                    requiresSecureDecoder: Boolean,
                    requiresTunnelingDecoder: Boolean
                ): List<MediaCodecInfo> {
                    val decoders = MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                    val filtered = when (activeDecoderMode) {
                        "HW", "HW+" -> decoders.filter { it.hardwareAccelerated && !it.softwareOnly }
                        "SW" -> decoders.filter { it.softwareOnly }
                        else -> decoders
                    }
                    return if (filtered.isEmpty()) decoders else filtered
                }
            }
            
            val renderersFactory = DefaultRenderersFactory(context).apply {
                setMediaCodecSelector(customSelector)
            }

            exoPlayer = ExoPlayer.Builder(context, renderersFactory).build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true

                // Enable Wake Lock for background playing capability
                setWakeMode(C.WAKE_MODE_LOCAL)

                // Configure standard movie/media audio attributes & auto audio focus management
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build()
                setAudioAttributes(audioAttributes, true)

                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                        if (playing) {
                            registerNoisyReceiver()
                        } else {
                            unregisterNoisyReceiver()
                        }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            setupAudioFX()
                        }
                    }
                })
            }

            try {
                mediaSession?.release()
                mediaSession = MediaSession.Builder(context, exoPlayer!!).build()
            } catch (e: Exception) {
                Log.e("PlayerManager", "Failed to start MediaSession: ${e.message}")
            }
        }
        return exoPlayer!!
    }

    fun play(media: MediaFile, seekToPos: Long? = null) {
        val player = getPlayer()
        _currentMedia.value = media
        activeDecoderMode = media.decoderMode
        
        val mediaItem = if (media.isStream) {
            val builder = MediaItem.Builder()
                .setUri(Uri.parse(media.path))
                
            if (media.path.contains(".m3u8")) {
                builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            } else if (media.path.contains(".mpd")) {
                builder.setMimeType(MimeTypes.APPLICATION_MPD)
            }

            if (media.subtitlePath.isNotEmpty()) {
                val subtitleUri = Uri.parse(media.subtitlePath)
                val mimeType = when {
                    media.subtitlePath.endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
                    media.subtitlePath.endsWith(".ass", ignoreCase = true) -> MimeTypes.TEXT_SSA
                    media.subtitlePath.endsWith(".ssa", ignoreCase = true) -> MimeTypes.TEXT_SSA
                    else -> MimeTypes.APPLICATION_SUBRIP
                }
                val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                    .setMimeType(mimeType)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .setLanguage("en")
                    .build()
                builder.setSubtitleConfigurations(listOf(subtitleConfig))
            }
            builder.build()
        } else {
            val localFile = File(media.path)
            val builder = MediaItem.Builder().setUri(Uri.fromFile(localFile))
            
            // Auto detect companion subtitle file if not manually specified
            var activeSubtitlePath = media.subtitlePath
            if (activeSubtitlePath.isEmpty()) {
                val parentDir = localFile.parentFile
                if (parentDir != null && parentDir.exists()) {
                    val baseName = localFile.nameWithoutExtension
                    val candidateExtensions = listOf(".srt", ".ass", ".vtt", ".sub", ".SRT", ".ASS", ".VTT", ".SUB")
                    for (ext in candidateExtensions) {
                        val candidateFile = File(parentDir, "$baseName$ext")
                        if (candidateFile.exists() && candidateFile.isFile) {
                            activeSubtitlePath = candidateFile.absolutePath
                            break
                        }
                    }
                }
            }

            if (activeSubtitlePath.isNotEmpty()) {
                val subtitleFile = File(activeSubtitlePath)
                val mimeType = when {
                    activeSubtitlePath.endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
                    activeSubtitlePath.endsWith(".ass", ignoreCase = true) -> MimeTypes.TEXT_SSA
                    activeSubtitlePath.endsWith(".ssa", ignoreCase = true) -> MimeTypes.TEXT_SSA
                    else -> MimeTypes.APPLICATION_SUBRIP
                }
                val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(subtitleFile))
                    .setMimeType(mimeType)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .setLanguage("en")
                    .build()
                builder.setSubtitleConfigurations(listOf(subtitleConfig))
            }
            builder.build()
        }

        player.setMediaItem(mediaItem)
        player.prepare()
        val targetPos = seekToPos ?: media.resumePosition
        if (targetPos > 0) {
            player.seekTo(targetPos)
        }
        player.play()
        
        applySpeed(_settings.value.speed)
    }

    fun stopAndSaveProgress(callback: (progress: Long, duration: Long) -> Unit) {
        val player = exoPlayer ?: return
        val progress = player.currentPosition
        val duration = player.duration
        if (duration > 0) {
            callback(progress, duration)
        }
        player.stop()
    }

    private fun setupAudioFX() {
        val player = exoPlayer ?: return
        val sessionId = player.audioSessionId
        if (sessionId == 0) return

        try {
            // Equalizer Setup
            if (_settings.value.equalizerEnabled) {
                if (equalizer == null || equalizer?.id != sessionId) {
                    equalizer?.release()
                    equalizer = Equalizer(0, sessionId).apply {
                        enabled = true
                    }
                }
                applyEqualizerPreset(_settings.value.equalizerPreset)
            } else {
                equalizer?.enabled = false
            }

            // Bass Boost Setup
            if (_settings.value.bassBoost > 0) {
                if (bassBoost == null || bassBoost?.id != sessionId) {
                    bassBoost?.release()
                    bassBoost = BassBoost(0, sessionId).apply {
                        enabled = true
                    }
                }
                bassBoost?.setStrength(_settings.value.bassBoost.toShort())
            } else {
                bassBoost?.enabled = false
            }

            // Virtualizer Setup
            if (_settings.value.virtualizer > 0) {
                if (virtualizer == null || virtualizer?.id != sessionId) {
                    virtualizer?.release()
                    virtualizer = Virtualizer(0, sessionId).apply {
                        enabled = true
                    }
                }
                virtualizer?.setStrength(_settings.value.virtualizer.toShort())
            } else {
                virtualizer?.enabled = false
            }

        } catch (e: Exception) {
            Log.e("PlayerManager", "Audio FX initialization failed (Standard on some emulators): ${e.message}")
        }
    }

    fun applyResizeMode(mode: VideoResizeMode) {
        _settings.value = _settings.value.copy(resizeMode = mode)
    }

    fun applySpeed(speed: Float) {
        _settings.value = _settings.value.copy(speed = speed)
        exoPlayer?.playbackParameters = PlaybackParameters(speed)
    }

    fun toggleEqualizer(enabled: Boolean) {
        _settings.value = _settings.value.copy(equalizerEnabled = enabled)
        setupAudioFX()
    }

    fun applyEqualizerPreset(preset: EqualizerPreset) {
        _settings.value = _settings.value.copy(equalizerPreset = preset)
        val eq = equalizer ?: return
        try {
            if (!eq.enabled) eq.enabled = true
            val bandsCount = eq.numberOfBands
            val testGains = preset.gains
            for (i in 0 until bandsCount.toInt()) {
                if (i < testGains.size) {
                    val gainRange = eq.getBandLevelRange()
                    val targetLevel = (testGains[i] * 100).coerceIn(gainRange[0].toInt(), gainRange[1].toInt()).toShort()
                    eq.setBandLevel(i.toShort(), targetLevel)
                }
            }
        } catch (e: Exception) {
            Log.e("PlayerManager", "Failed applying preset: ${e.message}")
        }
    }

    fun applyBassBoost(strength: Int) {
        _settings.value = _settings.value.copy(bassBoost = strength)
        setupAudioFX()
    }

    fun applyVirtualizer(strength: Int) {
        _settings.value = _settings.value.copy(virtualizer = strength)
        setupAudioFX()
    }

    fun applyBrightness(brightness: Float) {
        _settings.value = _settings.value.copy(brightness = brightness.coerceIn(0.0f, 1.0f))
    }

    fun applyVolumeBoost(boost: Int) {
        _settings.value = _settings.value.copy(volumeBoost = boost.coerceIn(100, 200))
        val player = exoPlayer ?: return
        // Multiply player volume dynamically if volume boost is over 100
        val targetVolume = (boost / 100f).coerceIn(0f, 2f)
        player.volume = targetVolume
    }

    fun applyNightModeFilter(enabled: Boolean) {
        _settings.value = _settings.value.copy(isNightModeFilter = enabled)
    }

    fun applyAudioOnly(enabled: Boolean) {
        _settings.value = _settings.value.copy(audioOnly = enabled)
    }

    fun seekRelative(seconds: Long) {
        val player = exoPlayer ?: return
        val targetPosition = (player.currentPosition + (seconds * 1000)).coerceIn(0, player.duration)
        player.seekTo(targetPosition)
    }

    fun release() {
        unregisterNoisyReceiver()
        mediaSession?.release()
        mediaSession = null

        equalizer?.release()
        bassBoost?.release()
        virtualizer?.release()
        equalizer = null
        bassBoost = null
        virtualizer = null
        
        exoPlayer?.release()
        exoPlayer = null
    }
}
