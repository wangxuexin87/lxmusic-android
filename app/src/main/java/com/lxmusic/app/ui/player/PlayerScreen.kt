package com.lxmusic.app.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.lxmusic.app.LxApp
import com.lxmusic.app.ui.appViewModel
import com.lxmusic.app.ui.components.formatDuration
import com.lxmusic.app.ui.components.rememberCoverUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(app: LxApp) : ViewModel() {
    val player = app.playerManager
    private val repo = app.repository

    private val _lyrics = MutableStateFlow<String?>(null)
    val lyrics: StateFlow<String?> = _lyrics

    fun loadLyrics(songId: String?) {
        if (songId.isNullOrEmpty()) {
            _lyrics.value = null
            return
        }
        viewModelScope.launch {
            _lyrics.value = try {
                repo.lyrics(songId)?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** 底部迷你播放条 */
@Composable
fun MiniPlayer(onClick: () -> Unit) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as LxApp
    val player = app.playerManager
    val song by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val current = song ?: return

    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val coverUrl = rememberCoverUrl(current.coverArt)
            AsyncImage(
                model = coverUrl, contentDescription = null,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(current.title.orEmpty(), maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                Text(
                    current.artist.orEmpty(), maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { player.toggle() }) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放"
                )
            }
        }
    }
}

/** 全屏播放页 */
@Composable
fun PlayerScreen() {
    val vm: PlayerViewModel = appViewModel { PlayerViewModel(it) }
    val player = vm.player
    val song by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val position by player.positionMs.collectAsState()
    val duration by player.durationMs.collectAsState()
    val lyrics by vm.lyrics.collectAsState()

    val current = song
    LaunchedEffect(current?.id) { vm.loadLyrics(current?.id) }

    var sliderValue by remember { mutableStateOf<Float?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (current == null) {
            Text("还没有播放歌曲", modifier = Modifier.padding(top = 64.dp))
            return@Column
        }
        val coverUrl = rememberCoverUrl(current.coverArt)
        Spacer(Modifier.height(24.dp))
        AsyncImage(
            model = coverUrl, contentDescription = null,
            modifier = Modifier.size(280.dp).clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(24.dp))
        Text(current.title.orEmpty(), style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        Text(
            listOfNotNull(current.artist, current.album).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
        )
        Spacer(Modifier.height(16.dp))

        val safeDuration = duration.coerceAtLeast(1L)
        Slider(
            value = sliderValue ?: position.toFloat().coerceIn(0f, safeDuration.toFloat()),
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                sliderValue?.let { player.seekTo(it.toLong()) }
                sliderValue = null
            },
            valueRange = 0f..safeDuration.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(position), style = MaterialTheme.typography.bodySmall)
            Text(formatDuration(duration), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { player.previous() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "上一首",
                    modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = { player.toggle() }, modifier = Modifier.size(72.dp)) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(56.dp)
                )
            }
            IconButton(onClick = { player.next() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.SkipNext, contentDescription = "下一首",
                    modifier = Modifier.size(40.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        // 歌词
        val lyricText = lyrics
        if (!lyricText.isNullOrBlank()) {
            Text(
                "歌词",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(lyricText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
