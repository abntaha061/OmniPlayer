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
import com.example.domain.VideoFolder
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

    // Premium MX Player View State Customization (cyan accent bindings)
    private val _displayMode = MutableStateFlow("List") // "List", "Grid"
    val displayMode: StateFlow<String> = _displayMode.asStateFlow()

    private val _sortBy = MutableStateFlow("Date") // "Title", "Date", "Size", "Duration" ...
    val sortBy: StateFlow<String> = _sortBy.asStateFlow()

    private val _sortOrder = MutableStateFlow("Newest") // "Newest", "Oldest"
    val sortOrder: StateFlow<String> = _sortOrder.asStateFlow()

    // Interactive toggleable fields
    private val _showFileExtension = MutableStateFlow(true)
    val showFileExtension: StateFlow<Boolean> = _showFileExtension.asStateFlow()

    private val _showDuration = MutableStateFlow(true)
    val showDuration: StateFlow<Boolean> = _showDuration.asStateFlow()

    private val _showThumbnail = MutableStateFlow(true)
    val showThumbnail: StateFlow<Boolean> = _showThumbnail.asStateFlow()

    private val _showFrameRate = MutableStateFlow(true)
    val showFrameRate: StateFlow<Boolean> = _showFrameRate.asStateFlow()

    private val _showQuality = MutableStateFlow(true)
    val showQuality: StateFlow<Boolean> = _showQuality.asStateFlow()

    private val _showWatchTime = MutableStateFlow(true)
    val showWatchTime: StateFlow<Boolean> = _showWatchTime.asStateFlow()

    private val _showDate = MutableStateFlow(false)
    val showDate: StateFlow<Boolean> = _showDate.asStateFlow()

    private val _showSize = MutableStateFlow(true)
    val showSize: StateFlow<Boolean> = _showSize.asStateFlow()

    private val _showPath = MutableStateFlow(true)
    val showPath: StateFlow<Boolean> = _showPath.asStateFlow()

    // Mock Folders list
    private val _foldersList = MutableStateFlow<List<VideoFolder>>(emptyList())
    val foldersList: StateFlow<List<VideoFolder>> = _foldersList.asStateFlow()

    // Active folder videos
    private val _currentFolderVideos = MutableStateFlow<List<VideoFile>>(emptyList())
    val currentFolderVideos: StateFlow<List<VideoFile>> = _currentFolderVideos.asStateFlow()

    private var rawFolderVideos = listOf<VideoFile>()
    var activeFolderName = ""
        private set

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
            
            // Set up high fidelity folders list
            _foldersList.value = listOf(
                VideoFolder("#DeutschLernen_Netzwerk_Neu_A2_2026", 9, "٣٫٥ غيغابايت", 0, "folder"),
                VideoFolder("#Netzwerk_Neu_A1_2026", 25, "٧٫٨ غيغابايت", 0, "folder"),
                VideoFolder("#Netzwerk_Neu_A2_2026", 13, "٣٫٧ غيغابايت", 0, "folder"),
                VideoFolder("Camera", 22, "٣٫٥ غيغابايت", 1, "camera"),
                VideoFolder("Movies", 2, "٩٫٩ ميغابايت", 2, "movie"),
                VideoFolder("Quick Share", 2, "٣٨ ميغابايت", 0, "folder"),
                VideoFolder("Screen recordings", 11, "٩٣ ميغابايت", 11, "rec"),
                VideoFolder("WhatsApp Business Video", 1, "٤٣ ميغابايت", 0, "whatsapp"),
                VideoFolder("WhatsApp Video", 2, "١١ ميغابايت", 0, "whatsapp")
            )
            _isLoading.value = false
        }
    }

    fun setDisplayMode(mode: String) { _displayMode.value = mode }
    fun setSortBy(field: String) { _sortBy.value = field }
    fun setSortOrder(order: String) { _sortOrder.value = order }

    fun setShowFileExtension(v: Boolean) { _showFileExtension.value = v }
    fun setShowDuration(v: Boolean) { _showDuration.value = v }
    fun setShowThumbnail(v: Boolean) { _showThumbnail.value = v }
    fun setShowFrameRate(v: Boolean) { _showFrameRate.value = v }
    fun setShowQuality(v: Boolean) { _showQuality.value = v }
    fun setShowWatchTime(v: Boolean) { _showWatchTime.value = v }
    fun setShowDate(v: Boolean) { _showDate.value = v }
    fun setShowSize(v: Boolean) { _showSize.value = v }
    fun setShowPath(v: Boolean) { _showPath.value = v }

    fun loadVideosForFolder(folderName: String) {
        activeFolderName = folderName
        rawFolderVideos = getMockVideosForFolder(folderName)
        applySortingAndFilters()
    }

    fun applySortingAndFilters() {
        val list = rawFolderVideos.toMutableList()
        val isNewest = _sortOrder.value == "Newest"
        
        when (_sortBy.value) {
            "Title", "العنوان" -> {
                list.sortBy { it.title }
                if (isNewest) list.reverse()
            }
            "Date", "التاريخ" -> {
                list.sortBy { it.dateString }
                if (isNewest) list.reverse()
            }
            "Duration", "المدة" -> {
                list.sortBy { it.duration }
                if (isNewest) list.reverse()
            }
            "Size", "الحجم" -> {
                list.sortBy { it.size }
                if (isNewest) list.reverse()
            }
            else -> {
                list.sortBy { it.title }
                if (isNewest) list.reverse()
            }
        }
        _currentFolderVideos.value = list
    }

    private fun getMockVideosForFolder(folderName: String): List<VideoFile> {
        val pathPrefix = "/storage/emulated/0/$folderName"
        val sampleVideos = listOf(
            VideoFile(1L, "٠٠٤١٥٨_٢٠٢٦٠٤١١", "$pathPrefix/٠٠٤١٥٨_٢٠٢٦٠٤١١.mp4", 14000L, 30932992L, "H.264", "AAC", "2026-04-11", "1080p", 60),
            VideoFile(2L, "٠١٣٣٣١_٢٠٢٦٠٤١١", "$pathPrefix/٠١٣٣٣١_٢٠٢٦٠٤١١.mp4", 28000L, 60922368L, "H.264", "AAC", "2026-04-11", "1080p", 60),
            VideoFile(3L, "٠١٣٥١٧_٢٠٢٦٠٤١١", "$pathPrefix/٠١٣٥١٧_٢٠٢٦٠٤١١.mp4", 30000L, 65326284L, "H.264", "AAC", "2026-04-11", "1080p", 60),
            VideoFile(4L, "٠١٣٩٢٨_٢٠٢٦٠٤١١", "$pathPrefix/٠١٣٩٢٨_٢٠٢٦٠٤١١.mp4", 1000L, 1572864L, "H.264", "AAC", "2026-04-11", "720p", 30),
            VideoFile(5L, "١٤١٦٠٣_٢٠٢٦٠٤١١", "$pathPrefix/١٤١٦٠٣_٢٠٢٦٠٤١١.mp4", 12000L, 26424115L, "H.264", "AAC", "2026-04-11", "1080p", 60),
            VideoFile(6L, "١٩٤٨٢١_٢٠٢٦٠٤٢٤", "$pathPrefix/١٩٤٨٢١_٢٠٢٦٠٤٢٤.mp4", 48000L, 105277030L, "H.264", "AAC", "2026-04-24", "1080p", 60)
        )
        
        val count = when (folderName) {
            "#DeutschLernen_Netzwerk_Neu_A2_2026" -> 9
            "#Netzwerk_Neu_A1_2026" -> 25
            "#Netzwerk_Neu_A2_2026" -> 13
            "Camera" -> 22
            "Movies" -> 2
            "Quick Share" -> 2
            "Screen recordings" -> 11
            "WhatsApp Business Video" -> 1
            "WhatsApp Video" -> 2
            else -> 5
        }
        
        val list = mutableListOf<VideoFile>()
        for (i in 0 until count) {
            val baseIndex = i % sampleVideos.size
            val baseVideo = sampleVideos[baseIndex]
            if (i < sampleVideos.size) {
                // Return pristine requested entries first
                list.add(
                    VideoFile(
                        id = (baseVideo.id + i * 10).toLong(),
                        title = baseVideo.title,
                        filePath = baseVideo.filePath,
                        duration = baseVideo.duration,
                        size = baseVideo.size,
                        videoCodec = baseVideo.videoCodec,
                        audioCodec = baseVideo.audioCodec,
                        dateString = baseVideo.dateString,
                        resolution = baseVideo.resolution,
                        fps = baseVideo.fps
                    )
                )
            } else {
                // Generate high fidelity extra entries using Arabic numeric formatting if helpful
                val sizeBytes = baseVideo.size + (i * 1239840L)
                val day = 10 + (i % 18)
                val name = "١${i}٤${i}٣_٢٠٢٦٠٤$day"
                list.add(
                    VideoFile(
                        id = (100L + i * 5),
                        title = name,
                        filePath = "$pathPrefix/$name.mp4",
                        duration = baseVideo.duration + (i * 3000L),
                        size = sizeBytes,
                        videoCodec = if (i % 2 == 0) "H.264" else "HEVC",
                        audioCodec = if (i % 3 == 0) "MP3" else "AAC",
                        dateString = "2026-04-$day",
                        resolution = if (i % 2 == 0) "1080p" else "720p",
                        fps = if (i % 2 == 0) 60 else 30
                    )
                )
            }
        }
        return list
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
