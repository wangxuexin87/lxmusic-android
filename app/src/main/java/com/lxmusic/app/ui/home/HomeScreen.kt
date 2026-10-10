package com.lxmusic.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.lxmusic.app.data.model.Album
import com.lxmusic.app.ui.appViewModel
import com.lxmusic.app.ui.components.AlbumCard
import com.lxmusic.app.ui.components.SectionTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(app: LxApp) : ViewModel() {
    private val repo = app.repository

    private val _random = MutableStateFlow<List<Album>>(emptyList())
    val random: StateFlow<List<Album>> = _random
    private val _newest = MutableStateFlow<List<Album>>(emptyList())
    val newest: StateFlow<List<Album>> = _newest
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            try {
                _random.value = repo.randomAlbums(20)
                _newest.value = repo.newestAlbums(20)
            } catch (_: Exception) {
            } finally {
                _loading.value = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onAlbumClick: (String) -> Unit) {
    val vm: HomeViewModel = appViewModel { HomeViewModel(it) }
    val random by vm.random.collectAsState()
    val newest by vm.newest.collectAsState()
    val loading by vm.loading.collectAsState()

    LaunchedEffect(Unit) { vm.load() }

    PullToRefreshBox(
        isRefreshing = loading,
        onRefresh = { vm.load() },
        modifier = Modifier.fillMaxSize()
    ) {
        if (!loading && random.isEmpty() && newest.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载失败，下拉重试")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { SectionTitle("随机推荐") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(random, key = { it.id.orEmpty() }) { album ->
                            AlbumCard(album, onClick = { album.id?.let(onAlbumClick) })
                        }
                    }
                }
                item { SectionTitle("最新专辑") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(newest, key = { it.id.orEmpty() }) { album ->
                            AlbumCard(album, onClick = { album.id?.let(onAlbumClick) })
                        }
                    }
                }
            }
        }
    }
}
