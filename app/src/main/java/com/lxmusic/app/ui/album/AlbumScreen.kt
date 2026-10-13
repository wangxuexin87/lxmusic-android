package com.lxmusic.app.ui.album

import androidx.compose.foundation.layout.Box
import com.lxmusic.app.ui.components.conditionalClip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.lxmusic.app.LxApp
import com.lxmusic.app.data.model.Song
import com.lxmusic.app.ui.appViewModel
import com.lxmusic.app.ui.components.SongRow
import com.lxmusic.app.ui.components.rememberCoverUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AlbumViewModel(app: LxApp) : ViewModel() {
    private val repo = app.repository
    private val player = app.playerManager

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs
    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title
    private val _cover = MutableStateFlow<String?>(null)
    val cover: StateFlow<String?> = _cover
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    fun load(albumId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val songs = repo.albumSongs(albumId)
                _songs.value = songs
                _title.value = songs.firstOrNull()?.album.orEmpty()
                _cover.value = songs.firstOrNull()?.coverArt
            } catch (_: Exception) {
            } finally {
                _loading.value = false
            }
        }
    }

    fun playAll() {
        if (_songs.value.isNotEmpty()) player.playList(_songs.value, 0)
    }

    fun playSong(song: Song) {
        val list = _songs.value
        player.playList(list, list.indexOf(song).coerceAtLeast(0))
    }
}

@Composable
fun AlbumScreen(albumId: String, onBack: () -> Unit = {}) {
    val vm: AlbumViewModel = appViewModel { AlbumViewModel(it) }
    val songs by vm.songs.collectAsState()
    val title by vm.title.collectAsState()
    val cover by vm.cover.collectAsState()
    val loading by vm.loading.collectAsState()
    val coverUrl = rememberCoverUrl(cover)

    LaunchedEffect(albumId) { vm.load(albumId) }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onBackground)
            }
            AsyncImage(
                model = coverUrl, contentDescription = null,
                modifier = Modifier.size(96.dp).conditionalClip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                Text(
                    "${songs.size} 首", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Button(
            onClick = { vm.playAll() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Text("播放全部")
        }
        val listState = rememberLazyListState()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(songs, key = { it.id.orEmpty() }) { song ->
                SongRow(
                    song,
                    onClick = { vm.playSong(song) },
                    imageEnabled = !listState.isScrollInProgress
                )
            }
        }
    }
}
