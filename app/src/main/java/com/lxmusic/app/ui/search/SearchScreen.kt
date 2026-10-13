package com.lxmusic.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lxmusic.app.LxApp
import com.lxmusic.app.data.model.Album
import com.lxmusic.app.data.model.Artist
import com.lxmusic.app.data.model.Song
import com.lxmusic.app.ui.appViewModel
import com.lxmusic.app.ui.components.SongRow
import com.lxmusic.app.ui.theme.BrandGreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel(app: LxApp) : ViewModel() {
    private val repo = app.repository
    private val player = app.playerManager
    private val prefs = app.getSharedPreferences("search_history", android.content.Context.MODE_PRIVATE)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs
    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums
    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    val artists: StateFlow<List<Artist>> = _artists
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    // 搜索词放 ViewModel，页面切换回来不丢失
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    fun setQuery(q: String) { _query.value = q }

    private var job: Job? = null

    // 搜索历史（SharedPreferences 持久化）
    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<String>> = _history

    private fun loadHistory(): List<String> {
        return prefs.getString("history", "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
    }

    private fun saveHistory(list: List<String>) {
        prefs.edit().putString("history", list.joinToString("|")).apply()
    }

    fun addHistory(q: String) {
        val t = q.trim()
        if (t.isBlank()) return
        val newList = (listOf(t) + _history.value.filter { it != t }).take(10)
        _history.value = newList
        saveHistory(newList)
    }

    fun clearHistory() {
        _history.value = emptyList()
        saveHistory(emptyList())
    }

    /** 手动触发搜索（点按钮或回车） */
    fun search(query: String) {
        val q = query.trim()
        _query.value = query
        job?.cancel()
        if (q.isBlank()) {
            _songs.value = emptyList(); _albums.value = emptyList(); _artists.value = emptyList()
            return
        }
        addHistory(q)
        job = viewModelScope.launch {
            _loading.value = true
            try {
                val (s, a, ar) = repo.search(q)
                _songs.value = s; _albums.value = a; _artists.value = ar
            } catch (_: Exception) {
            } finally {
                _loading.value = false
            }
        }
    }

    /** 输入变化只更新文本，不触发搜索 */
    fun onQueryChange(query: String) {
        _query.value = query
        job?.cancel()
        if (query.isBlank()) {
            _songs.value = emptyList(); _albums.value = emptyList(); _artists.value = emptyList()
        }
    }

    fun playSong(song: Song) {
        val list = _songs.value
        player.playList(list, list.indexOf(song).coerceAtLeast(0))
    }
}

@Composable
fun SearchScreen(onAlbumClick: (String) -> Unit) {
    val vm: SearchViewModel = appViewModel { SearchViewModel(it) }
    val songs by vm.songs.collectAsState()
    val loading by vm.loading.collectAsState()
    val query by vm.query.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.onQueryChange(it) },
                placeholder = { Text("输入歌曲名字", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = BrandGreen)
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { vm.search(query) }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandGreen,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = BrandGreen,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.Button(
                onClick = { vm.search(query) },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = BrandGreen,
                    contentColor = Color(0xFF06281A)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(56.dp)
            ) {
                Text("搜索", fontWeight = FontWeight.Bold)
            }
        }

        // 搜索历史（空输入时显示）
        val history by vm.history.collectAsState()
        if (query.isBlank() && history.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "搜索历史",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                androidx.compose.material3.TextButton(onClick = { vm.clearHistory() }) {
                    Text("清空", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                items(history.size) { i ->
                    FilterChip(
                        selected = false,
                        onClick = { vm.search(history[i]) },
                        label = { Text(history[i], fontSize = 12.sp) },
                        modifier = Modifier.height(32.dp)
                    )
                }
            }
        }

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandGreen, modifier = Modifier.size(28.dp))
            }
        }

        // 歌曲结果列表
        val listState = rememberLazyListState()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(songs, key = { it.id.orEmpty() }) { song ->
                SongRow(
                    song,
                    onClick = { vm.playSong(song) },
                    onArtistClick = { artistName -> vm.search(artistName) },
                    imageEnabled = !listState.isScrollInProgress
                )
            }
            if (songs.isEmpty() && !loading && query.isNotBlank()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("没有找到相关歌曲", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
