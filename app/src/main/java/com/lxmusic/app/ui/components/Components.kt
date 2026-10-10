package com.lxmusic.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lxmusic.app.LxApp
import com.lxmusic.app.data.model.Album
import com.lxmusic.app.data.model.Song

@Composable
fun rememberCoverUrl(coverArt: String?): String? {
    val app = LocalContext.current.applicationContext as LxApp
    return app.repository.coverUrl(coverArt)
}

@Composable
fun SongRow(song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val coverUrl = rememberCoverUrl(song.coverArt)
    ListItem(
        headlineContent = { Text(song.title.orEmpty(), maxLines = 1) },
        supportingContent = {
            Text(
                listOfNotNull(song.artist, song.album).joinToString(" · "),
                maxLines = 1
            )
        },
        leadingContent = {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                placeholder = null
            )
        },
        trailingContent = {
            song.duration?.let { Text(formatDuration(it * 1000L)) }
        },
        modifier = modifier.clickable(onClick = onClick)
    )
}

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val coverUrl = rememberCoverUrl(album.coverArt)
    Column(
        modifier = modifier.width(140.dp).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            modifier = Modifier.size(132.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.padding(top = 4.dp))
        Text(album.name.orEmpty(), maxLines = 1, style = MaterialTheme.typography.bodyMedium)
        Text(
            album.artist.orEmpty(), maxLines = 1,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
