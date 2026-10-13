package com.lxmusic.app.player

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lxmusic.app.LxApp
import com.lxmusic.app.data.DownloadManager
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
    private val repositoryProvider: () -> MusicRepository
) {

    private val repository get() = repositoryProvider()

    private val downloadManager: DownloadManager?
        get() = try {
            (context.applicationContext as? LxApp)?.downloadManager
        } catch (_: Exception) {
            null
        }

    /**
     * ExoPlayer 延迟初始化：部分机型上创建可能抛异常，
     * 失败时置空，UI 层判空避免闪退。
     */
    val player: ExoPlayer? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        try {
            ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val index = currentMediaItemIndex
                        if (index in playlist.indices) {
                            val song = playlist[index]
                            _currentSong.value = song
                            song.id?.let { id ->
                                scope.launch { repository.scrobble(id) }
                                loadLyrics(id)
                            }
                            // 边听边下载
                            maybeAutoDownload(song)
                        }
                    }
                })
            }
        } catch (_: Exception) {
            null
        }
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

    /** 当前歌曲的歌词行（迷你栏和全屏共用） */
    private val _lyricLines = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyricLines: StateFlow<List<LyricLine>> = _lyricLines.asStateFlow()

    /** 当前演唱中的那一行歌词文本 */
    private val _currentLyricText = MutableStateFlow<String?>(null)
    val currentLyricText: StateFlow<String?> = _currentLyricText.asStateFlow()

    /** 播放歌单并从指定位置开始 */
    fun playList(songs: List<Song>, startIndex: Int = 0) {
        val p = player ?: return
        if (songs.isEmpty()) return
        // 离线模式：只保留本地有文件的歌曲
        val dm = downloadManager
        val filtered = if (dm?.offlineMode?.value == true) {
            songs.filter { dm.isDownloaded(it.id) }
        } else {
            songs
        }
        if (filtered.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, filtered.size - 1)
        // 如果过滤后起始歌曲变了，调整索引
        val actualIndex = if (filtered === songs) startIndex
        else filtered.indexOf(songs.getOrNull(startIndex)).coerceAtLeast(0)
        playlist = filtered
        val items = filtered.map { it.toMediaItem() }
        p.setMediaItems(items, actualIndex, 0L)
        p.prepare()
        p.play()
        startService()
        startPositionPolling()
        // 预加载第一首歌词
        filtered.getOrNull(actualIndex)?.id?.let { loadLyrics(it) }
    }

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun next() { player?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() } }
    fun previous() { player?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() } }

    fun seekTo(ms: Long) {
        try {
            player?.seekTo(ms.coerceAtLeast(0L))
        } catch (_: Exception) { }
    }

    fun release() {
        positionJob?.cancel()
        player?.release()
    }

    /** 停止播放并清空状态（退出登录时用） */
    fun stopPlayback() {
        try {
            positionJob?.cancel()
            player?.let {
                it.pause()
                it.clearMediaItems()
            }
        } catch (_: Exception) { }
        playlist = emptyList()
        _currentSong.value = null
        _isPlaying.value = false
        _positionMs.value = 0L
        _durationMs.value = 0L
        try {
            context.stopService(Intent(context, PlaybackService::class.java))
        } catch (_: Exception) { }
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
        // 优先播放本地已下载文件（重装后 songId 对不上时按歌名兜底）
        val localFile = downloadManager?.localSongFile(id)
            ?: downloadManager?.localSongFileByName(artist, title)
        val uri = if (localFile != null) {
            android.net.Uri.fromFile(localFile)
        } else {
            android.net.Uri.parse(repository.streamUrl(id.orEmpty()))
        }
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    /** 边听边下载：切歌时若开启自动下载且本地没有，后台下载当前歌曲+歌词 */
    private fun maybeAutoDownload(song: Song) {
        val dm = downloadManager ?: return
        if (!dm.autoDownload) return
        val songId = song.id ?: return
        // 重装后 songId 可能对不上，加按歌名匹配防止重复下载
        if (dm.isDownloaded(songId) || dm.isDownloadedByName(song.artist, song.title)) return
        scope.launch(Dispatchers.IO) {
            try {
                val lyrics = try {
                    dm.localLyricsText(songId) ?: repository.lyrics(songId)
                } catch (_: Exception) {
                    null
                }
                dm.download(song, repository.streamUrl(songId), lyrics, repository.coverUrl(song.coverArt))
            } catch (_: Exception) { }
        }
    }

    private fun startService() {
        ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
    }

    private fun startPositionPolling() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                val p = player ?: break
                val pos = p.currentPosition
                _positionMs.value = pos
                _durationMs.value = p.duration.coerceAtLeast(0L)
                // 更新当前歌词行
                val lines = _lyricLines.value
                if (lines.isNotEmpty()) {
                    val idx = lines.currentLyricIndex(pos)
                    _currentLyricText.value = lines.getOrNull(idx)?.text
                } else {
                    _currentLyricText.value = null
                }
                // 低配模式降频：1000ms，否则 500ms
                val lowSpec = try {
                    (context.applicationContext as com.lxmusic.app.LxApp)
                        .themeManager.lowSpec.value
                } catch (_: Exception) { false }
                delay(if (lowSpec) 1000 else 500)
            }
        }
    }

    /** 加载歌词（优先本地），供迷你栏和全屏共用 */
    private fun loadLyrics(songId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val dm = downloadManager
                val raw = dm?.localLyricsText(songId)
                    ?: try { repository.lyrics(songId) } catch (_: Exception) { null }
                val lines = if (raw.isNullOrBlank()) emptyList() else parseLrcTimed(raw)
                _lyricLines.value = lines
                _currentLyricText.value = null
            } catch (_: Exception) {
                _lyricLines.value = emptyList()
                _currentLyricText.value = null
            }
        }
    }
}
