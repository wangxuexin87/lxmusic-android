package com.lxmusic.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lxmusic.app.LxApp
import com.lxmusic.app.data.model.Album
import com.lxmusic.app.data.model.Artist
import com.lxmusic.app.data.model.Song
import com.lxmusic.app.ui.appViewModel
import com.lxmusic.app.ui.components.AlbumCard
import com.lxmusic.app.ui.components.SongRow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel(app: LxApp) : ViewModel() {
    private val repo = app.repository
    private val player = app.playerManager

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs
    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums
    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    val artists: StateFlow<List<Artist>> = _artists
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private var job: Job? = null

    /** 输入防抖 500ms 后搜索 */
    fun onQueryChange(query: String) {
        job?.cancel()
        if (query.isBlank()) {
            _songs.value = emptyList(); _albums.value = emptyList(); _artists.value = emptyList()
            return
        }
        job = viewModelScope.launch {
            delay(500)
            _loading.value = true
            try {
                val (s, a, ar) = repo.search(query.trim())
                _songs.value = s; _albums.value = a; _artists.value = ar
            } catch (_: Exception) {
            } finally {
                _loading.value = false
            }
        }
    }

    fun playSong(song: Song) {
        val list = _songs.value
        player.playList(list, list.indexOf(song).coerceAtLeast(0))
    }
}

@Composable
fun SearchScreen(onAlbumClick: (String) -> Unit, onPlay: () -> Unit) {
    val vm: SearchViewModel = appViewModel { SearchViewModel(it) }
    val songs by vm.songs.collectAsState()
    val albums by vm.albums.collectAsState()
    val artists by vm.artists.collectAsState()
    val loading by vm.loading.collectAsState()

    var query by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("歌曲", "专辑", "歌手")

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; vm.onQueryChange(it) },
            label = { Text("搜索（支持 wy:/kg:/tx: 前缀）") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { vm.onQueryChange(query) }) {
                    Icon(Icons.Default.Search, contentDescription = "搜索")
                }
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )

        if (loading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }

        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
            }
        }

        when (tab) {
            0 -> LazyColumn(Modifier.fillMaxSize()) {
                items(songs, key = { it.id.orEmpty() }) { song ->
                    SongRow(song, onClick = { vm.playSong(song); onPlay() })
                }
            }
            1 -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(albums, key = { it.id.orEmpty() }) { album ->
                            AlbumCard(album, onClick = { album.id?.let(onAlbumClick) })
                        }
                    }
                }
            }
            2 -> LazyColumn(Modifier.fillMaxSize()) {
                items(artists, key = { it.id.orEmpty() }) { artist ->
                    Text(
                        artist.name.orEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
