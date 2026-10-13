package com.lxmusic.app.ui.player

import androidx.compose.foundation.background
import com.lxmusic.app.ui.components.conditionalClip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.lxmusic.app.LxApp
import com.lxmusic.app.data.model.Song
import com.lxmusic.app.player.LyricLine
import com.lxmusic.app.player.currentLyricIndex
import com.lxmusic.app.ui.appViewModel
import com.lxmusic.app.ui.components.CoverPlaceholder
import com.lxmusic.app.ui.components.formatDuration
import com.lxmusic.app.ui.components.rememberCoverModel
import com.lxmusic.app.ui.components.rememberCoverUrl
import com.lxmusic.app.ui.theme.BrandDark
import com.lxmusic.app.ui.theme.BrandGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(app: LxApp) : ViewModel() {
    val player = app.playerManager
    private val repo = app.repository

    // 歌词用 PlayerManager 的共享状态（迷你栏也在用同一份）
    val lyricLines: StateFlow<List<LyricLine>> = player.lyricLines

    private val _lyricsLoading = MutableStateFlow(false)
    val lyricsLoading: StateFlow<Boolean> = _lyricsLoading

    fun loadLyrics(songId: String?) {
        // PlayerManager 切歌时已自动加载歌词，这里只做加载状态的短暂提示
        if (songId.isNullOrEmpty()) return
        viewModelScope.launch {
            _lyricsLoading.value = true
            var waited = 0
            while (player.lyricLines.value.isEmpty() && waited < 15) {
                delay(100)
                waited++
            }
            _lyricsLoading.value = false
        }
    }

    /** 根据播放进度找到当前歌词行索引（用共享扩展函数） */
    fun currentLyricIndex(positionMs: Long): Int {
        return lyricLines.value.currentLyricIndex(positionMs)
    }
}

/** 底部迷你播放条：浮动卡片，屏幕水平居中 */
@Composable
fun MiniPlayer(onClick: () -> Unit) {
    val app = LocalContext.current.applicationContext as LxApp
    val player = app.playerManager
    val song by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val lyricText by player.currentLyricText.collectAsState()
    val showLyric by app.themeManager.miniLyric.collectAsState()
    val transparent by app.themeManager.miniTransparent.collectAsState()
    val ultraMinimal by app.themeManager.ultraMinimal.collectAsState()
    val positionMs by player.positionMs.collectAsState()
    val durationMs by player.durationMs.collectAsState()
    val current = song ?: return

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val isDark = MaterialTheme.colorScheme.background == BrandDark
        // 极简模式：纯色背景，无渐变无透明
        val bgBrush = if (ultraMinimal) {
            Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface))
        } else if (isDark) {
            // 透明模式：背景色带 55% 透明度
            val a = if (transparent) 0.55f else 1f
            Brush.linearGradient(
                listOf(
                    Color(0xFF1E3A2F).copy(alpha = a),
                    Color(0xFF14261F).copy(alpha = a)
                )
            )
        } else {
            val a = if (transparent) 0.55f else 1f
            Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = a),
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = a)
                )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 6.dp)
                .conditionalClip(RoundedCornerShape(16.dp))
        ) {
            Column {
                // 顶部进度条：支持点击定位
                val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .pointerInput(durationMs) {
                            detectTapGestures { offset ->
                                if (durationMs > 0) {
                                    val frac = (offset.x / size.width).coerceIn(0f, 1f)
                                    try { player.seekTo((durationMs * frac).toLong()) } catch (_: Exception) {}
                                }
                            }
                        }
                ) {
                    // 背景轨道
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.Center)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    )
                    // 进度
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .align(Alignment.CenterStart)
                            .background(BrandGreen)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgBrush)
                        .clickable(onClick = onClick)
                ) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // 封面 + 歌名/歌手（左侧）
                Row(
                    Modifier.align(Alignment.CenterStart).fillMaxWidth(0.38f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 极简模式：不显示封面
                    if (!ultraMinimal) {
                        val coverModel = rememberCoverModel(current.id, current.coverArt)
                        Box(modifier = Modifier.size(46.dp).conditionalClip(RoundedCornerShape(10.dp))) {
                            if (coverModel != null) {
                                AsyncImage(
                                    model = coverModel, contentDescription = null,
                                    modifier = Modifier.matchParentSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                CoverPlaceholder(Modifier.matchParentSize(), 22.dp)
                            }
                        }
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            current.title?.takeIf { it.isNotBlank() } ?: "未知歌曲",
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground
                        )
                        // 歌词：标题后面位置，字号加大加粗，和播放按钮一样醒目
                        // 极简模式：只显示歌手，不刷歌词
                        val hasLyric = !ultraMinimal && showLyric && !lyricText.isNullOrBlank()
                        val subText: String = if (hasLyric) lyricText!!
                        else current.artist?.takeIf { it.isNotBlank() } ?: "未知艺人"
                        Text(
                            subText,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontSize = if (hasLyric) 18.sp else 13.sp,
                            fontWeight = if (hasLyric) FontWeight.Bold else FontWeight.Normal,
                            color = if (hasLyric) BrandGreen
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 控制区：上一曲 / 播放暂停 / 下一曲，屏幕绝对居中
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    IconButton(
                        onClick = { try { player.previous() } catch (_: Exception) {} },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious, "上一首",
                            tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(
                        onClick = { try { player.toggle() } catch (_: Exception) {} },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(BrandGreen)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = Color(0xFF06281A), modifier = Modifier.size(26.dp)
                        )
                    }
                    IconButton(
                        onClick = { try { player.next() } catch (_: Exception) {} },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipNext, "下一首",
                            tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
                } // 关闭 Column
            } // 关闭卡片 Box
        } // 关闭外层居中 Box
    } // 关闭 MiniPlayer 的最外层 Box
}

/**
 * 全屏播放页（仿 LX Music 桌面端）：
 * 顶部 NOW PLAYING → 封面 + 歌名/歌手 → 歌词（当前行高亮跟随）→ 进度条 → 控制区
 */
@Composable
fun PlayerScreen(onBack: () -> Unit = {}) {
    val vm: PlayerViewModel = appViewModel { PlayerViewModel(it) }
    val app = LocalContext.current.applicationContext as LxApp
    val ultraMinimal by app.themeManager.ultraMinimal.collectAsState()
    val player = vm.player
    val song by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val position by player.positionMs.collectAsState()
    val duration by player.durationMs.collectAsState()
    val lyricLines by vm.lyricLines.collectAsState()
    val lyricsLoading by vm.lyricsLoading.collectAsState()

    val current = song
    LaunchedEffect(current?.id) { vm.loadLyrics(current?.id) }

    var sliderValue by remember { mutableStateOf<Float?>(null) }
    var seekError by remember { mutableStateOf(false) }

    // 当前歌词行 + 自动滚动
    val currentLyricIdx = remember(position, lyricLines) {
        vm.currentLyricIndex(sliderValue?.toLong() ?: position)
    }
    val listState = rememberLazyListState()
    LaunchedEffect(currentLyricIdx) {
        if (currentLyricIdx >= 0) {
            try {
                listState.animateScrollToItem(
                    (currentLyricIdx - 2).coerceAtLeast(0)
                )
            } catch (_: Exception) { }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (MaterialTheme.colorScheme.background == BrandDark) {
                    Brush.verticalGradient(
                        listOf(Color(0xFF0E2A20), Color(0xFF0B0F1A), Color(0xFF0B0F1A))
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background
                        )
                    )
                }
            )
    ) {
        // 顶栏
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.KeyboardArrowDown, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "NOW PLAYING",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }

        if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.MusicNote, null,
                        tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(64.dp)
                    )
                    Text(
                        "还没有播放歌曲",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            return@Column
        }

        // 封面 + 标题区
        val coverModel = rememberCoverModel(current.id, current.coverArt)
        val repo = (LocalContext.current.applicationContext as LxApp).repository
        var isStarred by remember(current.id) {
            mutableStateOf(!current.starred.isNullOrEmpty())
        }
        val favScope = rememberCoroutineScope()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 极简模式：不显示封面
            if (!ultraMinimal) {
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .conditionalClip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (coverModel != null) {
                        AsyncImage(
                            model = coverModel, contentDescription = null,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        CoverPlaceholder(Modifier.matchParentSize(), 40.dp)
                    }
                }
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    current.title?.takeIf { it.isNotBlank() } ?: "未知歌曲",
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    current.artist?.takeIf { it.isNotBlank() } ?: "未知艺人",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
                current.album?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it, style = MaterialTheme.typography.bodySmall,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            // 收藏按钮
            IconButton(
                onClick = {
                    val id = current.id ?: return@IconButton
                    val newState = !isStarred
                    isStarred = newState
                    favScope.launch {
                        try {
                            if (newState) repo.star(id) else repo.unstar(id)
                        } catch (_: Exception) { }
                    }
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    if (isStarred) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isStarred) "取消收藏" else "收藏",
                    tint = if (isStarred) Color(0xFFFF5C7A) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
            // 下载按钮
            val dm = (LocalContext.current.applicationContext as LxApp).downloadManager
            val dlScope = rememberCoroutineScope()
            val isDl = dm.isDownloaded(current.id)
            var downloading by remember(current.id) { mutableStateOf(false) }
            IconButton(
                onClick = {
                    val song = current
                    val id = song.id ?: return@IconButton
                    if (dm.isDownloaded(id) || downloading) return@IconButton
                    downloading = true
                    dlScope.launch {
                        try {
                            val lyrics = try {
                                dm.localLyricsText(id)
                                    ?: repo.lyrics(id)
                            } catch (_: Exception) { null }
                            dm.download(song, repo.streamUrl(id), lyrics, repo.coverUrl(song.coverArt))
                        } catch (_: Exception) { }
                        downloading = false
                    }
                },
                modifier = Modifier.size(48.dp)
            ) {
                when {
                    downloading -> CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = BrandGreen
                    )
                    isDl -> Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "已下载",
                        tint = BrandGreen,
                        modifier = Modifier.size(28.dp)
                    )
                    else -> Icon(
                        Icons.Default.Download,
                        contentDescription = "下载",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 歌词区（当前行高亮绿 + 自动跟随）
        // 极简模式：只显示当前一句静态歌词
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                lyricsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandGreen, modifier = Modifier.size(28.dp))
                }
                lyricLines.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无歌词", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ultraMinimal -> {
                    val currentLine = lyricLines.getOrNull(currentLyricIdx)?.text ?: ""
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            currentLine,
                            color = BrandGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    itemsIndexed(lyricLines, key = { i, l -> "$i-${l.timeMs}" }) { index, line ->
                        val isCurrent = index == currentLyricIdx
                        Text(
                            line.text,
                            color = if (isCurrent) BrandGreen else Color(0xFF6B7690),
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            fontSize = if (isCurrent) 20.sp else 18.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 30.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // 进度条（防闪退：seek 包 try-catch，duration 为 0 时禁用）
        val safeDuration = duration.coerceAtLeast(1L)
        val canSeek = duration > 0
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Slider(
                value = sliderValue ?: position.toFloat().coerceIn(0f, safeDuration.toFloat()),
                onValueChange = { if (canSeek) sliderValue = it },
                onValueChangeFinished = {
                    val v = sliderValue
                    sliderValue = null
                    if (v != null && canSeek) {
                        try {
                            player.seekTo(v.toLong().coerceIn(0L, duration))
                            seekError = false
                        } catch (_: Exception) {
                            seekError = true
                        }
                    }
                },
                valueRange = 0f..safeDuration.toFloat(),
                enabled = canSeek,
                colors = SliderDefaults.colors(
                    thumbColor = BrandGreen,
                    activeTrackColor = BrandGreen,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatDuration(sliderValue?.toLong() ?: position),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (seekError) {
                    Text(
                        "跳转失败", style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF8A8A)
                    )
                }
                Text(
                    formatDuration(duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 当前歌词（控制区上方）
        val nowLyric by player.currentLyricText.collectAsState()
        if (!nowLyric.isNullOrBlank()) {
            Text(
                nowLyric!!,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                color = BrandGreen,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            )
            Spacer(Modifier.height(4.dp))
        }

        // 控制区：按钮透明化
        Row(
            Modifier.fillMaxWidth().padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { try { player.previous() } catch (_: Exception) {} },
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
            ) {
                Icon(Icons.Default.SkipPrevious, "上一首", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(32.dp))
            }
            IconButton(
                onClick = { try { player.toggle() } catch (_: Exception) {} },
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(BrandGreen.copy(alpha = 0.85f))
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Color(0xFF06281A), modifier = Modifier.size(36.dp)
                )
            }
            IconButton(
                onClick = { try { player.next() } catch (_: Exception) {} },
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
            ) {
                Icon(Icons.Default.SkipNext, "下一首", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(32.dp))
            }
        }
    }
}
