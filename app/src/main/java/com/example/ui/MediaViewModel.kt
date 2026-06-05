package com.example.ui

import android.app.Application
import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.MediaRepository
import com.example.domain.MediaFile
import com.example.domain.NetworkStream
import com.example.domain.Playlist
import com.example.player.PlayerManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MediaViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = MediaRepository(application, db.mediaDao())
    val playerManager = PlayerManager(application)

    // State flows
    val allMedia: StateFlow<List<MediaFile>> = repository.allMedia
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<MediaFile>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchHistory = repository.watchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val networkStreams: StateFlow<List<NetworkStream>> = repository.networkStreams
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Currently playing media
    val currentMedia = playerManager.currentMedia
    val isPlaying = playerManager.isPlaying

    // Searching and filtering
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Sleep Timer
    private val _sleepTimeRemaining = MutableStateFlow(0L) // in seconds
    val sleepTimeRemaining: StateFlow<Long> = _sleepTimeRemaining.asStateFlow()
    
    private var sleepCountDownTimer: CountDownTimer? = null

    init {
        // Run initial scan to populate database with local + demo media
        scanMedia()
    }

    fun scanMedia() {
        viewModelScope.launch {
            _isScanning.value = true
            repository.scanLocalMedia()
            _isScanning.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavorite(mediaId: Long, isFav: Boolean) {
        viewModelScope.launch {
            repository.updateFavorite(mediaId, isFav)
        }
    }

    fun deleteMedia(id: Long) {
        viewModelScope.launch {
            repository.deleteMedia(id)
        }
    }

    fun selectMedia(media: MediaFile) {
        playerManager.play(media)
    }

    fun saveHistoryProgress(mediaId: Long, progress: Long, duration: Long) {
        viewModelScope.launch {
            repository.insertHistory(mediaId, progress, duration)
        }
    }

    // Playlists Custom Functions
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(id)
        }
    }

    fun addVideoToPlaylist(playlistId: Long, mediaId: Long) {
        viewModelScope.launch {
            repository.addToPlaylist(playlistId, mediaId)
        }
    }

    fun getPlaylistMedia(playlistId: Long): Flow<List<MediaFile>> {
        return repository.getPlaylistMedia(playlistId)
    }

    // Stream inputs
    fun addNetworkStream(title: String, url: String) {
        viewModelScope.launch {
            repository.insertStream(
                NetworkStream(
                    title = title,
                    url = url,
                    type = if (url.contains(".m3u8")) "HLS" else if (url.contains(".mpd")) "DASH" else "Direct Link"
                )
            )
        }
    }

    fun deleteNetworkStream(id: Long) {
        viewModelScope.launch {
            repository.deleteStream(id)
        }
    }

    // Sleep Timer Functions
    fun startSleepTimer(minutes: Int) {
        sleepCountDownTimer?.cancel()
        if (minutes <= 0) {
            _sleepTimeRemaining.value = 0
            return
        }
        
        val totalMs = minutes * 60000L
        _sleepTimeRemaining.value = totalMs / 1000
        
        sleepCountDownTimer = object : CountDownTimer(totalMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _sleepTimeRemaining.value = millisUntilFinished / 1000
            }

            override fun onFinish() {
                _sleepTimeRemaining.value = 0
                playerManager.getPlayer().pause()
                Log.d("MediaViewModel", "Sleep timer finished. Playback paused.")
            }
        }.start()
    }

    fun cancelSleepTimer() {
        sleepCountDownTimer?.cancel()
        _sleepTimeRemaining.value = 0
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    override fun onCleared() {
        super.onCleared()
        sleepCountDownTimer?.cancel()
        playerManager.release()
    }
}
