package com.lxmusic.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lxmusic.app.LxApp
import com.lxmusic.app.data.model.Album
import com.lxmusic.app.ui.appViewModel
import com.lxmusic.app.ui.components.AlbumCard
import com.lxmusic.app.ui.components.SectionTitle
import com.lxmusic.app.ui.theme.BrandGreen
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
    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = false
            try {
                _random.value = repo.randomAlbums(8)
                _newest.value = repo.newestAlbums(8)
            } catch (_: Exception) {
                _error.value = true
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
    val error by vm.error.collectAsState()

    LaunchedEffect(Unit) {
        // 只在数据为空时加载，切 tab 不重复请求
        if (random.isEmpty() && newest.isEmpty()) vm.load()
    }

    PullToRefreshBox(
        isRefreshing = loading,
        onRefresh = { vm.load() },
        modifier = Modifier.fillMaxSize()
    ) {
        if (!loading && error && random.isEmpty() && newest.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("加载失败", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                    Text(
                        "下拉重试",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { vm.load() },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
                    ) { Text("重试", color = Color(0xFF06281A)) }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item { SectionTitle("随机推荐", "换个心情，发现新声音") }
                item {
                    val rowState = rememberLazyListState()
                    LazyRow(
                        state = rowState,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(random, key = { it.id.orEmpty() }) { album ->
                            AlbumCard(
                                album,
                                onClick = { album.id?.let(onAlbumClick) },
                                imageEnabled = !rowState.isScrollInProgress
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
                item { SectionTitle("最新专辑", "新鲜出炉，不容错过") }
                item {
                    val rowState2 = rememberLazyListState()
                    LazyRow(
                        state = rowState2,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(newest, key = { it.id.orEmpty() }) { album ->
                            AlbumCard(
                                album,
                                onClick = { album.id?.let(onAlbumClick) },
                                imageEnabled = !rowState2.isScrollInProgress
                            )
                        }
                    }
                }
            }
        }
    }
}
