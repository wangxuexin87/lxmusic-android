package com.lxmusic.app.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lxmusic.app.LxApp
import com.lxmusic.app.data.model.Playlist
import com.lxmusic.app.data.model.Song
import com.lxmusic.app.ui.appViewModel
import com.lxmusic.app.ui.components.SongRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlaylistsViewModel(app: LxApp) : ViewModel() {
    private val repo = app.repository
    private val player = app.playerManager

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private val _currentName = MutableStateFlow("")
    val currentName: StateFlow<String> = _currentName

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            try {
                _playlists.value = repo.playlists()
            } catch (_: Exception) {
            } finally {
                _loading.value = false
            }
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

    fun playSong(song: Song) {
        val list = _songs.value
        player.playList(list, list.indexOf(song).coerceAtLeast(0))
    }

    fun playAll() {
        if (_songs.value.isNotEmpty()) player.playList(_songs.value, 0)
    }
}

@Composable
fun PlaylistsScreen(onPlay: () -> Unit) {
    val vm: PlaylistsViewModel = appViewModel { PlaylistsViewModel(it) }
    val playlists by vm.playlists.collectAsState()
    val songs by vm.songs.collectAsState()
    val loading by vm.loading.collectAsState()
    val currentName by vm.currentName.collectAsState()

    LaunchedEffect(Unit) { vm.load() }

    Column(Modifier.fillMaxSize()) {
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }
        if (songs.isEmpty()) {
            // 歌单列表
            LazyColumn(Modifier.fillMaxSize()) {
                items(playlists, key = { it.id.orEmpty() }) { pl ->
                    ListItem(
                        headlineContent = { Text(pl.name.orEmpty()) },
                        supportingContent = { Text("${pl.songCount ?: 0} 首") },
                        modifier = Modifier.clickable {
                            pl.id?.let { vm.openPlaylist(it, pl.name.orEmpty()) }
                        }
                    )
                }
            }
            if (playlists.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无歌单")
                }
            }
        } else {
            // 歌单详情
            Text(
                currentName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(songs, key = { it.id.orEmpty() }) { song ->
                    SongRow(song, onClick = { vm.playSong(song); onPlay() })
                }
            }
        }
    }
}
