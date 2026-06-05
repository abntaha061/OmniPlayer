package com.example.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.CountDownTimer
import android.os.IBinder
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.example.domain.LrcLine
import com.example.domain.Song
import com.example.domain.SubtitleStyle
import com.example.domain.VideoFile
import com.example.player.MusicService
import com.example.utils.LyricsParser
import com.example.utils.MediaLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    // Lists
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _videos = MutableStateFlow<List<VideoFile>>(emptyList())
    val videos: StateFlow<List<VideoFile>> = _videos.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Current State
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlayingAudio = MutableStateFlow(false)
    val isPlayingAudio: StateFlow<Boolean> = _isPlayingAudio.asStateFlow()

    private val _audioPositionMs = MutableStateFlow(0L)
    val audioPositionMs: StateFlow<Long> = _audioPositionMs.asStateFlow()

    private val _audioProgress = MutableStateFlow(0f)
    val audioProgress: StateFlow<Float> = _audioProgress.asStateFlow()

    private val _lrcLines = MutableStateFlow<List<LrcLine>>(emptyList())
    val lrcLines: StateFlow<List<LrcLine>> = _lrcLines.asStateFlow()

    // Service Connection
    private var musicService: MusicService? = null
    private val _isServiceBound = MutableStateFlow(false)

    // Subtitles Customize
    private val _subtitleStyle = MutableStateFlow(SubtitleStyle())
    val subtitleStyle: StateFlow<SubtitleStyle> = _subtitleStyle.asStateFlow()

    // Video Player Features
    private val _videoSpeed = MutableStateFlow(1.0f)
    val videoSpeed: StateFlow<Float> = _videoSpeed.asStateFlow()

    private val _isFlipped = MutableStateFlow(false)
    val isFlipped: StateFlow<Boolean> = _isFlipped.asStateFlow()

    private val _isHwAccelerationEnabled = MutableStateFlow(true)
    val isHwAccelerationEnabled: StateFlow<Boolean> = _isHwAccelerationEnabled.asStateFlow()

    // Sleep Timer
    private val _sleepTimerText = MutableStateFlow("موقد النوم")
    val sleepTimerText: StateFlow<String> = _sleepTimerText.asStateFlow()

    private val _isSleepTimerActive = MutableStateFlow(false)
    val isSleepTimerActive: StateFlow<Boolean> = _isSleepTimerActive.asStateFlow()

    private var countDownTimer: CountDownTimer? = null
    private var progressJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            _isServiceBound.value = true
            observeServicePlayer()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            _isServiceBound.value = false
        }
    }

    init {
        // Bind to Service
        Intent(context, MusicService::class.java).also { intent ->
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
        loadMediaFiles()
    }

    fun loadMediaFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            val loadedSongs = withContext(Dispatchers.IO) {
                MediaLoader.loadSongs(context)
            }
            val loadedVideos = withContext(Dispatchers.IO) {
                MediaLoader.loadVideos(context)
            }
            _songs.value = loadedSongs
            _videos.value = loadedVideos
            _isLoading.value = false
        }
    }

    private fun observeServicePlayer() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                val player = musicService?.exoPlayer
                if (player != null) {
                    _isPlayingAudio.value = player.isPlaying
                    _audioPositionMs.value = player.currentPosition
                    
                    val duration = player.duration
                    if (duration > 0) {
                        _audioProgress.value = player.currentPosition.toFloat() / duration
                    } else {
                        _audioProgress.value = 0f
                    }
                }
                delay(200)
            }
        }
    }

    fun playSong(song: Song) {
        _currentSong.value = song
        
        // Ensure service is running
        val intent = Intent(context, MusicService::class.java)
        context.startService(intent)
        
        musicService?.playSong(song)
        _isPlayingAudio.value = true

        // Load lyrics
        viewModelScope.launch(Dispatchers.IO) {
            val lrc = LyricsParser.parseLrc(song.filePath)
            _lrcLines.value = lrc
        }
    }

    fun togglePlayPauseAudio() {
        musicService?.togglePlayPause()
        val isPlaying = musicService?.exoPlayer?.isPlaying ?: false
        _isPlayingAudio.value = isPlaying
    }

    fun playNext() {
        val currentList = _songs.value
        val current = _currentSong.value ?: return
        val currentIndex = currentList.indexOfFirst { it.id == current.id }
        if (currentIndex != -1 && currentIndex < currentList.size - 1) {
            playSong(currentList[currentIndex + 1])
        } else if (currentList.isNotEmpty()) {
            playSong(currentList[0])
        }
    }

    fun playPrevious() {
        val currentList = _songs.value
        val current = _currentSong.value ?: return
        val currentIndex = currentList.indexOfFirst { it.id == current.id }
        if (currentIndex > 0) {
            playSong(currentList[currentIndex - 1])
        } else if (currentList.isNotEmpty()) {
            playSong(currentList[currentList.size - 1])
        }
    }

    fun seekAudio(progress: Float) {
        val player = musicService?.exoPlayer ?: return
        val duration = player.duration
        if (duration > 0) {
            val targetPosition = (progress * duration).toLong()
            player.seekTo(targetPosition)
            _audioPositionMs.value = targetPosition
            _audioProgress.value = progress
        }
    }

    // Video options
    fun setVideoSpeed(speed: Float) {
        _videoSpeed.value = speed
    }

    fun toggleHorizontalFlip() {
        _isFlipped.value = !_isFlipped.value
    }

    fun toggleHwAcceleration() {
        _isHwAccelerationEnabled.value = !_isHwAccelerationEnabled.value
    }

    fun updateSubtitleStyle(style: SubtitleStyle) {
        _subtitleStyle.value = style
    }

    // Sleep Timer helper
    fun setSleepTimer(minutes: Int) {
        countDownTimer?.cancel()
        if (minutes <= 0) {
            _isSleepTimerActive.value = false
            _sleepTimerText.value = "موقد النوم"
            return
        }

        _isSleepTimerActive.value = true
        val timeMs = minutes * 60 * 1000L

        countDownTimer = object : CountDownTimer(timeMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = (millisUntilFinished / 1000) % 60
                val min = (millisUntilFinished / 1000) / 60
                _sleepTimerText.value = String.format("%02d:%02d", min, sec)
            }

            override fun onFinish() {
                _isSleepTimerActive.value = false
                _sleepTimerText.value = "موقد النوم"
                // Pause all media
                musicService?.exoPlayer?.pause()
                _isPlayingAudio.value = false
            }
        }.start()
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
        countDownTimer?.cancel()
        try {
            context.unbindService(serviceConnection)
        } catch (e: Exception) {
            // ignore
        }
    }
}
