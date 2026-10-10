package com.lxmusic.app.player

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lxmusic.app.data.MusicRepository
import com.lxmusic.app.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 应用级播放器：单例 ExoPlayer，UI 直接调用。
 * 后台播放由 [PlaybackService]（MediaSessionService）保活。
 */
class PlayerManager(
    private val context: Context,
    private val repository: MusicRepository
) {

    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = currentMediaItemIndex
                if (index in playlist.indices) {
                    _currentSong.value = playlist[index]
                    // 切歌时上报播放历史（fire-and-forget）
                    playlist[index].id?.let { id ->
                        scope.launch { repository.scrobble(id) }
                    }
                }
            }
        })
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var positionJob: Job? = null

    private var playlist: List<Song> = emptyList()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    /** 播放歌单并从指定位置开始 */
    fun playList(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        playlist = songs
        val items = songs.map { it.toMediaItem() }
        player.setMediaItems(items, startIndex, 0L)
        player.prepare()
        player.play()
        startService()
        startPositionPolling()
    }

    fun toggle() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }
    fun previous() { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() }

    fun seekTo(ms: Long) = player.seekTo(ms)

    fun release() {
        positionJob?.cancel()
        player.release()
    }

    private fun Song.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title.orEmpty())
            .setArtist(artist.orEmpty())
            .setAlbumTitle(album.orEmpty())
            .apply {
                repository.coverUrl(coverArt)?.let { setArtworkUri(android.net.Uri.parse(it)) }
            }
            .build()
        return MediaItem.Builder()
            .setUri(repository.streamUrl(id.orEmpty()))
            .setMediaMetadata(metadata)
            .build()
    }

    private fun startService() {
        ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
    }

    private fun startPositionPolling() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                _positionMs.value = player.currentPosition
                _durationMs.value = player.duration.coerceAtLeast(0L)
                delay(500)
            }
        }
    }
}
