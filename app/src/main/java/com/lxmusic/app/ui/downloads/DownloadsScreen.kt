package com.lxmusic.app.ui.downloads

import androidx.compose.foundation.background
import com.lxmusic.app.ui.components.conditionalClip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lxmusic.app.LxApp
import com.lxmusic.app.data.DownloadedSong
import com.lxmusic.app.data.formatBytes
import com.lxmusic.app.data.model.Song
import com.lxmusic.app.ui.components.CoverPlaceholder
import com.lxmusic.app.ui.components.rememberCoverUrl
import com.lxmusic.app.ui.theme.BrandGreen

/**
 * 我的下载：已下载歌曲列表，可播放、可删除（歌曲+歌词一起删）。
 */
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onPlay: (DownloadedSong) -> Unit
) {
    val app = LocalContext.current.applicationContext as LxApp
    val dm = app.downloadManager
    val downloads by dm.downloads.collectAsState()
    var toDelete by remember { mutableStateOf<DownloadedSong?>(null) }
    // 从服务器同步收藏状态（重装后本地收藏状态丢失，按歌名匹配）
    var starredTitles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var starredTitlesOnly by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        dm.refresh()
        try {
            val starred = app.repository.starredSongs()
            starredTitles = starred.map { "${it.artist?.trim()}|${it.title?.trim()}" }.toSet()
            starredTitlesOnly = starred.mapNotNull { it.title?.trim() }.toSet()
        } catch (_: Exception) {}
    }

    toDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("删除下载") },
            text = { Text("确定删除「${target.title}」吗？歌曲和歌词会一起删除。") },
            confirmButton = {
                Button(
                    onClick = {
                        dm.delete(target.songId)
                        toDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5484D))
                ) {
                    Text("删除", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) {
                    Text("取消", color = BrandGreen)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶栏
        Row(
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
                "我的下载",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${downloads.size} 首 · ${formatBytes(dm.totalSize())}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp)
            )
        }

        if (downloads.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Download, null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        "还没有下载歌曲",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        "在歌曲右侧点下载图标即可保存到本地",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
            ) {
                items(downloads, key = { it.songId }) { item ->
                    val key = "${item.artist.trim()}|${item.title.trim()}"
                    val isStarred = starredTitles.contains(key) || starredTitlesOnly.contains(item.title.trim())
                    DownloadedRow(
                        item = item,
                        onPlay = { onPlay(item) },
                        onDelete = { toDelete = item },
                        isStarred = isStarred
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadedRow(
    item: DownloadedSong,
    onPlay: () -> Unit,
    onDelete: (() -> Unit)? = null,
    isStarred: Boolean = false,
    onToggleStar: (() -> Unit)? = null
) {
    val app = LocalContext.current.applicationContext as LxApp
    val ultraMinimal by app.themeManager.ultraMinimal.collectAsState()
    // 极简模式：纯文字行，保留收藏和下载状态
    if (ultraMinimal) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPlay)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 极简模式：歌名 - 歌手 单行显示
            Text(
                buildString {
                    append(item.title.takeIf { it.isNotBlank() } ?: "未知歌曲")
                    item.artist.takeIf { it.isNotBlank() }?.let { append(" - $it") }
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            if (isStarred) {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Favorite, null,
                        tint = Color(0xFFFF5C7A),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            // 本地歌曲无需显示下载状态（全是已下载的）
        }
        return
    }
    val coverUrl = rememberCoverUrl(item.coverArt)
    // 优先本地封面（新命名和老命名都查）
    val localCover = remember(item.songId, item.fileBaseName) {
        app.downloadManager.findCoverFile(item.songId, item.fileBaseName)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .conditionalClip(RoundedCornerShape(14.dp))
            .clickable { onPlay() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .conditionalClip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (localCover != null) {
                AsyncImage(
                    model = localCover,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.MusicNote, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title.ifBlank { "未知歌曲" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                listOf(item.artist, item.album).filter { it.isNotBlank() }
                    .joinToString(" · ").ifEmpty { "未知艺人" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                formatBytes(item.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        // 收藏按钮
        onToggleStar?.let { toggle ->
            IconButton(onClick = toggle, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (isStarred) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isStarred) "取消收藏" else "收藏",
                    tint = if (isStarred) Color(0xFFFF5C7A) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        onDelete?.let { del ->
            IconButton(onClick = del, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Delete, "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
