@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import dev.hostyn.gallery.ui.theme.LocalTransformOrigin
import dev.hostyn.gallery.ui.theme.containerTransform

/**
 * Folders, plus shortcuts to favorites, videos and the trash. [trashCount] is null to hide the
 * trash; [hidden] folders are marked as not showing in Photos.
 */
@Composable
fun AlbumsGrid(media: List<Media>, trashCount: Int?, hidden: Set<Long>, onOpen: (Collection) -> Unit, contentPadding: PaddingValues) {
    val albums = remember(media) { albumsOf(media) }
    val hasVideos = remember(media) { media.any { it.isVideo } }
    val hasFavorites = remember(media) { media.any { it.isFavorite } }
    // Whatever is opened grows out of where it was tapped (container transform).
    val origin = LocalTransformOrigin.current
    val open = { key: String, c: Collection -> origin.key = key; onOpen(c) }
    val colors = MaterialTheme.colorScheme
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
            // M3 Expressive: a different shape and color role for each shortcut.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (hasSystemTrash && (hasFavorites || trashCount != null)) {
                    Shortcut(
                        Icons.Rounded.Favorite, stringResource(R.string.favorites), MaterialShapes.SoftBurst,
                        colors.tertiaryContainer, colors.onTertiaryContainer, "favorites", Modifier.weight(1f),
                    ) { open("favorites", Collection.Favorites) }
                }
                if (hasVideos) {
                    Shortcut(
                        Icons.Rounded.Videocam, stringResource(R.string.videos), MaterialShapes.Clover4Leaf,
                        colors.primaryContainer, colors.onPrimaryContainer, "videos", Modifier.weight(1f),
                    ) { open("videos", Collection.Videos) }
                }
                if (hasSystemTrash && trashCount != null) {
                    Shortcut(
                        Icons.Rounded.Delete, stringResource(R.string.trash), MaterialShapes.Cookie4Sided,
                        colors.secondaryContainer, colors.onSecondaryContainer, "trash", Modifier.weight(1f),
                    ) { open("trash", Collection.Trash) }
                }
            }
        }
        items(albums, key = { it.id }) { album ->
            val key = "album-${album.id}"
            AlbumCard(album, hidden = album.id in hidden, transformKey = key) { open(key, Collection.Folder(album.id)) }
        }
    }
}

/** Favorites, videos or the trash: its icon in an expressive shape, on its own container. */
@Composable
private fun Shortcut(
    icon: ImageVector,
    label: String,
    shape: RoundedPolygon,
    container: Color,
    content: Color,
    transformKey: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.containerTransform(transformKey, MaterialTheme.shapes.extraLarge),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp)) {
            Box(Modifier.size(52.dp).background(container, shape.toShape()), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = content)
            }
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AlbumCard(album: Album, hidden: Boolean, transformKey: String, onClick: () -> Unit) {
    Column(
        Modifier
            .containerTransform(transformKey, MaterialTheme.shapes.extraLarge)
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            AsyncImage(Thumb(album.cover), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (hidden) {
                Icon(
                    Icons.Rounded.VisibilityOff,
                    stringResource(R.string.hidden_from_photos),
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .padding(6.dp)
                        .size(18.dp),
                )
            }
        }
        Text(
            album.name.ifEmpty { stringResource(R.string.internal_storage) },
            style = MaterialTheme.typography.titleSmallEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
        )
        Text(
            album.count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // Clear of the card's round bottom corners, which would clip it.
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
        )
    }
}

