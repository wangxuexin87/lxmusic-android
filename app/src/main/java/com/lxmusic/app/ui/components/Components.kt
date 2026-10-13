package com.lxmusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import com.lxmusic.app.LxApp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lxmusic.app.ui.theme.BrandGreen
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.lxmusic.app.data.model.Album
import com.lxmusic.app.data.model.Song

/** 低配模式下去掉圆角裁剪，省 GPU */
@Composable
fun Modifier.conditionalClip(shape: RoundedCornerShape): Modifier {
    val app = LocalContext.current.applicationContext as LxApp
    val lowSpec = try { app.themeManager.lowSpec.value } catch (_: Exception) { false }
    return if (lowSpec) this else this.clip(shape)
}

@Composable
fun rememberCoverUrl(coverArt: String?): String? {
    val app = LocalContext.current.applicationContext as LxApp
    return try {
        // 低配模式要 300px 小图，省流量省内存
        val lowSpec = try { app.themeManager.lowSpec.value } catch (_: Exception) { false }
        app.repository.coverUrl(coverArt, size = if (lowSpec) 50 else null)
    } catch (_: Exception) {
        null
    }
}

/** 优先本地封面：有下载就用本地文件，没有再用网络地址 */
@Composable
fun rememberCoverModel(songId: String?, coverArt: String?, baseName: String? = null): Any? {
    val app = LocalContext.current.applicationContext as LxApp
    val localCover = androidx.compose.runtime.remember(songId, baseName) {
        app.downloadManager.findCoverFile(songId, baseName)
    }
    if (localCover != null) return localCover
    return rememberCoverUrl(coverArt)
}

@Composable
fun CoverPlaceholder(modifier: Modifier = Modifier, iconSize: androidx.compose.ui.unit.Dp = 32.dp) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(Color(0xFF1E2740), Color(0xFF131A2B))
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            tint = Color(0xFF3A4763),
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onArtistClick: ((String) -> Unit)? = null,
    imageEnabled: Boolean = true
) {
    val app = LocalContext.current.applicationContext as LxApp
    val ultraMinimal by app.themeManager.ultraMinimal.collectAsState()
    // 极简模式：纯文字行，保留收藏和下载状态显示（和正常模式同步）
    if (ultraMinimal) {
        val dm = app.downloadManager
        val downloads by dm.downloads.collectAsState()
        val isStarredState = remember(song.id) { mutableStateOf(!song.starred.isNullOrEmpty()) }
        val isDl = remember(song.id, downloads) {
            dm.isDownloaded(song.id) || dm.isDownloadedByName(song.artist, song.title)
        }
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 极简模式：歌名 - 歌手 单行显示
            Text(
                buildString {
                    append(song.title?.takeIf { it.isNotBlank() } ?: "未知歌曲")
                    song.artist?.takeIf { it.isNotBlank() }?.let { append(" - $it") }
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            // 收藏状态（和正常模式同图标）
            if (isStarredState.value) {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Favorite, null,
                        tint = Color(0xFFFF5C7A),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            // 下载状态：已下载绿勾，未下载灰箭头（和正常模式同步）
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(
                    if (isDl) Icons.Default.CheckCircle else Icons.Default.Download,
                    null,
                    tint = if (isDl) BrandGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            // 时长（和正常模式同步）
            song.duration?.takeIf { it > 0 }?.let {
                Text(
                    formatDuration(it * 1000L),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
        return
    }
    val scope = rememberCoroutineScope()
    var isStarred by remember(song.id) { mutableStateOf(!song.starred.isNullOrEmpty()) }
    val coverUrl = rememberCoverUrl(song.coverArt)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .conditionalClip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .conditionalClip(RoundedCornerShape(10.dp))
        ) {
            // 滚动时不加载图片，省 CPU
            if (coverUrl != null && imageEnabled) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = null,
                    error = null
                )
            } else {
                CoverPlaceholder(Modifier.matchParentSize(), 24.dp)
            }
        }
        Spacer(Modifier.width(12.dp))
        // 单行：歌名 - 歌手
        Text(
            buildString {
                append(song.title?.takeIf { it.isNotBlank() } ?: "未知歌曲")
                song.artist?.takeIf { it.isNotBlank() }?.let { append(" - $it") }
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        // 收藏按钮
        IconButton(
            onClick = {
                val id = song.id ?: return@IconButton
                val newState = !isStarred
                isStarred = newState  // 先更新 UI
                scope.launch {
                    if (newState) app.repository.star(id)
                    else app.repository.unstar(id)
                }
            },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                if (isStarred) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isStarred) "取消收藏" else "收藏",
                tint = if (isStarred) Color(0xFFFF5C7A) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        // 下载按钮
        val dm = app.downloadManager
        val downloadingSet by dm.downloading.collectAsState()
        val isDownloading = song.id in downloadingSet
        val isDownloaded = remember(song.id, dm.downloads.collectAsState().value) {
            dm.isDownloaded(song.id) || dm.isDownloadedByName(song.artist, song.title)
        }
        IconButton(
            onClick = {
                val id = song.id ?: return@IconButton
                if (isDownloaded || isDownloading) return@IconButton
                scope.launch {
                    try {
                        val lyrics = try { app.repository.lyrics(id) } catch (_: Exception) { null }
                        val ok = dm.download(song, app.repository.streamUrl(id), lyrics, app.repository.coverUrl(song.coverArt))
                        // 可加 Toast 提示，这里静默
                    } catch (_: Exception) { }
                }
            },
            modifier = Modifier.size(40.dp)
        ) {
            when {
                isDownloading -> androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = BrandGreen
                )
                isDownloaded -> Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "已下载",
                    tint = BrandGreen,
                    modifier = Modifier.size(22.dp)
                )
                else -> Icon(
                    Icons.Default.Download,
                    contentDescription = "下载",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        song.duration?.takeIf { it > 0 }?.let {
            Text(
                formatDuration(it * 1000L),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageEnabled: Boolean = true
) {
    val coverUrl = rememberCoverUrl(album.coverArt)
    Column(
        modifier = modifier
            .width(148.dp)
            .conditionalClip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .conditionalClip(RoundedCornerShape(14.dp))
        ) {
            if (coverUrl != null && imageEnabled) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                CoverPlaceholder(Modifier.matchParentSize(), 40.dp)
            }
            // 底部渐变罩，提升质感
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0x40000000))
                        )
                    )
            )
        }
        Spacer(Modifier.padding(top = 8.dp))
        Text(
            album.name?.takeIf { it.isNotBlank() } ?: "未知专辑",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            album.artist?.takeIf { it.isNotBlank() } ?: "未知艺人",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
