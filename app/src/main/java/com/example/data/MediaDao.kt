package com.example.data

import androidx.room.*
import com.example.domain.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    // Media files
    @Query("SELECT * FROM media_files ORDER BY dateAdded DESC")
    fun getAllMedia(): Flow<List<MediaFile>>

    @Query("SELECT * FROM media_files WHERE isFavorite = 1 ORDER BY dateAdded DESC")
    fun getFavorites(): Flow<List<MediaFile>>

    @Query("SELECT * FROM media_files WHERE id = :id LIMIT 1")
    suspend fun getMediaById(id: Long): MediaFile?

    @Query("SELECT * FROM media_files WHERE path = :path LIMIT 1")
    suspend fun getMediaByPath(path: String): MediaFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaFile): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaBatch(mediaList: List<MediaFile>)

    @Update
    suspend fun updateMedia(media: MediaFile)

    @Query("UPDATE media_files SET resumePosition = :position WHERE id = :id")
    suspend fun updateResumePosition(id: Long, position: Long)

    @Query("UPDATE media_files SET isFavorite = :isFav WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFav: Boolean)

    @Delete
    suspend fun deleteMedia(media: MediaFile)

    @Query("DELETE FROM media_files WHERE id = :id")
    suspend fun deleteMediaById(id: Long)

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT mf.* FROM media_files mf INNER JOIN playlist_items pi ON mf.id = pi.mediaId WHERE pi.playlistId = :playlistId ORDER BY pi.orderIndex ASC")
    fun getPlaylistMedia(playlistId: Long): Flow<List<MediaFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItem(item: PlaylistItem)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND mediaId = :mediaId")
    suspend fun removePlaylistItem(playlistId: Long, mediaId: Long)

    // Watch History
    @Query("SELECT wh.*, mf.name as mediaName, mf.path as mediaPath, mf.displayName as mediaDisplayName, mf.thumbnailUri as mediaThumbnail FROM watch_history wh INNER JOIN media_files mf ON wh.mediaId = mf.id ORDER BY wh.watchedAt DESC LIMIT 30")
    fun getWatchHistory(): Flow<List<WatchHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: WatchHistory)

    @Query("DELETE FROM watch_history")
    suspend fun clearHistory()

    // Network Streams
    @Query("SELECT * FROM network_streams ORDER BY dateAdded DESC")
    fun getAllStreams(): Flow<List<NetworkStream>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStream(stream: NetworkStream): Long

    @Query("DELETE FROM network_streams WHERE id = :id")
    suspend fun deleteStream(id: Long)
}

// Helper POJO for Watch History joins
data class WatchHistoryItem(
    val id: Long,
    val mediaId: Long,
    val watchedAt: Long,
    val progress: Long,
    val duration: Long,
    val completed: Boolean,
    val mediaName: String,
    val mediaPath: String,
    val mediaDisplayName: String,
    val mediaThumbnail: String
)
