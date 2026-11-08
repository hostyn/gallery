package dev.hostyn.gallery.ui.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.hostyn.gallery.R
import dev.hostyn.gallery.data.Album
import dev.hostyn.gallery.data.Collection
import dev.hostyn.gallery.data.Media
import dev.hostyn.gallery.data.Thumb
import dev.hostyn.gallery.data.albumsOf
import dev.hostyn.gallery.ui.hasSystemTrash

/** Folders, plus shortcuts to favorites, videos and the trash. [trashCount] is null to hide the trash. */
@Composable
fun AlbumsGrid(media: List<Media>, trashCount: Int?, onOpen: (Collection) -> Unit, contentPadding: PaddingValues) {
    val albums = remember(media) { albumsOf(media) }
    val hasVideos = remember(media) { media.any { it.isVideo } }
    val hasFavorites = remember(media) { media.any { it.isFavorite } }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "shortcuts") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (hasSystemTrash && (hasFavorites || trashCount != null)) {
                    Shortcut(Icons.Rounded.Favorite, stringResource(R.string.favorites), Modifier.weight(1f)) { onOpen(Collection.Favorites) }
                }
                if (hasVideos) Shortcut(Icons.Rounded.Videocam, stringResource(R.string.videos), Modifier.weight(1f)) { onOpen(Collection.Videos) }
                if (hasSystemTrash && trashCount != null) {
                    Shortcut(Icons.Rounded.Delete, stringResource(R.string.trash), Modifier.weight(1f)) { onOpen(Collection.Trash) }
                }
            }
        }
        items(albums, key = { it.id }) { album -> AlbumCard(album) { onOpen(Collection.Folder(album.id)) } }
    }
}

@Composable
private fun Shortcut(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            AsyncImage(Thumb(album.cover), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text(
            album.name.ifEmpty { stringResource(R.string.internal_storage) },
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
        )
        Text(
            album.count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp).padding(bottom = 4.dp),
        )
    }
}

