package com.lxmusic.app.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lxmusic.app.LxApp
import com.lxmusic.app.data.model.Playlist
import com.lxmusic.app.data.model.Song
import com.lxmusic.app.ui.appViewModel
import com.lxmusic.app.ui.components.SongRow
import com.lxmusic.app.ui.theme.BrandGreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlaylistsViewModel(app: LxApp) : ViewModel() {
    private val repo = app.repository
    private val player = app.playerManager
    private val dm = app.downloadManager
    val downloads = dm.downloads

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs
    private val _starred = MutableStateFlow<List<Song>>(emptyList())
    val starred: StateFlow<List<Song>> = _starred
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private val _currentName = MutableStateFlow("")
    val currentName: StateFlow<String> = _currentName
    val offlineMode = dm.offlineMode

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            try {
                _playlists.value = repo.playlists()
            } catch (_: Exception) {
            }
            try {
                _starred.value = repo.starredSongs()
            } catch (_: Exception) {
            }
            _loading.value = false
        }
    }

    fun openPlaylist(id: String, name: String) {
        viewModelScope.launch {
            _loading.value = true
            _currentName.value = name
            try {
                _songs.value = repo.playlistSongs(id)
            } catch (_: Exception) {
            } finally {
                _loading.value = false
            }
        }
    }

    fun backToList() {
        _songs.value = emptyList()
        _currentName.value = ""
    }

    /** 切换收藏状态，同步服务器 */
    fun toggleStar(songId: String, currentlyStarred: Boolean) {
        viewModelScope.launch {
            try {
                if (currentlyStarred) repo.unstar(songId) else repo.star(songId)
                // 刷新收藏列表
                _starred.value = repo.starredSongs()
            } catch (_: Exception) { }
        }
    }

    fun refreshDownloads() {
        dm.refresh()
    }

    fun playSong(song: Song, fromStarred: Boolean = false) {
        val list = if (fromStarred) _starred.value else _songs.value
        player.playList(list, list.indexOf(song).coerceAtLeast(0))
    }

    fun playAll(fromStarred: Boolean = false) {
        val list = if (fromStarred) _starred.value else _songs.value
        if (list.isNotEmpty()) player.playList(list, 0)
    }

    /** 播放本地下载的歌曲 */
    fun playDownloaded(item: com.lxmusic.app.data.DownloadedSong) {
        val song = Song(
            id = item.songId,
            title = item.title,
            album = item.album,
            artist = item.artist,
            albumId = null,
            artistId = null,
            coverArt = item.coverArt,
            duration = null,
            track = null,
            year = null,
            genre = null,
            size = null
        )
        // 构造仅含本地歌曲的列表播放（PlayerManager 会自动用本地文件）
        player.playList(listOf(song), 0)
    }
}

/**
 * 歌单页：顶部横排固定 Tab（我的喜爱 / 歌单列表），默认打开我的喜爱。
 */
@Composable
fun PlaylistsScreen() {
    val vm: PlaylistsViewModel = appViewModel { PlaylistsViewModel(it) }
    val playlists by vm.playlists.collectAsState()
    val songs by vm.songs.collectAsState()
    val starred by vm.starred.collectAsState()
    val loading by vm.loading.collectAsState()
    val currentName by vm.currentName.collectAsState()

    var tab by remember { mutableIntStateOf(0) } // 0=本地歌曲，1=我的喜爱，2=歌单列表
    val tabs = listOf("本地歌曲", "我的喜爱", "歌单列表")

    LaunchedEffect(Unit) {
        // 只在数据为空时加载，切 tab 不重复请求；本地歌曲每次刷新（文件可能变化）
        if (vm.playlists.value.isEmpty() && vm.starred.value.isEmpty()) vm.load()
        vm.refreshDownloads()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部横排固定 Tab
        TabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            indicator = { tabPositions ->
                TabRowDefaults.PrimaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[tab]),
                    color = BrandGreen
                )
            }
        ) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = tab == i,
                    onClick = {
                        // 离线模式下只能看本地
                        if (vm.offlineMode.value && i != 0) return@Tab
                        tab = i
                        vm.backToList()
                    },
                    text = {
                        val offline = vm.offlineMode.collectAsState().value
                        val enabled = !offline || i == 0
                        Text(
                            title,
                            fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                tab == i -> BrandGreen
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                )
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandGreen)
            }
            return@Column
        }

        val localSongs by vm.downloads.collectAsState()

        when {
            // 歌单详情（从歌单列表点进去）
            songs.isNotEmpty() -> {
                Row_Header(
                    title = currentName,
                    onBack = { vm.backToList() }
                )
                val listState1 = rememberLazyListState()
                LazyColumn(state = listState1, modifier = Modifier.fillMaxSize()) {
                    items(songs, key = { it.id.orEmpty() }) { song ->
                        SongRow(song, onClick = { vm.playSong(song) }, imageEnabled = !listState1.isScrollInProgress)
                    }
                }
            }
            // 本地歌曲（默认）
            tab == 0 -> {
                if (localSongs.isEmpty()) {
                    EmptyHint("还没有下载歌曲", "在歌曲右侧点下载图标保存到本地")
                } else {
                    // 收藏状态：先按 songId 匹配，重装后 songId 对不上时按歌名兜底
                    val starredIds = starred.mapNotNull { it.id }.toSet()
                    val starredTitles = starred.mapNotNull { it.title?.trim() }.toSet()
                    val listState2 = rememberLazyListState()
                LazyColumn(state = listState2, modifier = Modifier.fillMaxSize()) {
                        items(localSongs, key = { it.songId }) { item ->
                            val isStarred = item.songId in starredIds ||
                                starredTitles.contains(item.title.trim())
                            com.lxmusic.app.ui.downloads.DownloadedRow(
                                item = item,
                                onPlay = { vm.playDownloaded(item) },
                                onDelete = null,
                                isStarred = isStarred,
                                onToggleStar = { vm.toggleStar(item.songId, isStarred) }
                            )
                        }
                    }
                }
            }
            // 我的喜爱
            tab == 1 -> {
                if (starred.isEmpty()) {
                    EmptyHint("还没有收藏歌曲", "在歌曲右侧点心形即可收藏")
                } else {
                    val listState3 = rememberLazyListState()
                LazyColumn(state = listState3, modifier = Modifier.fillMaxSize()) {
                        items(starred, key = { it.id.orEmpty() }) { song ->
                            SongRow(
                                song,
                                onClick = { vm.playSong(song, fromStarred = true) }
                            )
                        }
                    }
                }
            }
            // 歌单列表
            tab == 2 -> {
                if (playlists.isEmpty()) {
                    EmptyHint("暂无歌单", null)
                } else {
                    val listState4 = rememberLazyListState()
                LazyColumn(state = listState4, modifier = Modifier.fillMaxSize()) {
                        items(playlists, key = { it.id.orEmpty() }) { pl ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        pl.name.orEmpty(),
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        "${pl.songCount ?: 0} 首",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent
                                ),
                                modifier = Modifier.clickable {
                                    pl.id?.let { vm.openPlaylist(it, pl.name.orEmpty()) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Row_Header(title: String, onBack: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.Default.ArrowBack, "返回",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun EmptyHint(title: String, subtitle: String?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Favorite, null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(48.dp)
            )
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
