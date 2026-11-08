package dev.hostyn.gallery.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.hostyn.gallery.data.Media
import dev.hostyn.gallery.data.Thumb
import dev.hostyn.gallery.data.formatDuration
import dev.hostyn.gallery.data.mayBeAnimated

private val TextShadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 6f)

/**
 * One square of the grid. [selected] is null outside selection mode.
 */
@Composable
fun MediaCell(
    media: Media,
    selected: Boolean?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: (@Composable BoxScope.() -> Unit)? = null,
) {
    val isSelected = selected == true
    val scale by animateFloatAsState(if (isSelected) 0.84f else 1f, label = "scale")
    val corner by animateDpAsState(if (isSelected) 14.dp else 0.dp, label = "corner")
    Box(
        modifier
            .aspectRatio(1f)
            // The grid handles touches; this keeps the cell usable with TalkBack and switches.
            .semantics {
                if (selected != null) {
                    this.selected = isSelected
                    role = Role.Checkbox
                } else {
                    role = Role.Image
                }
                onClick { onClick(); true }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .scale(scale)
                .clip(RoundedCornerShape(corner))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            AsyncImage(
                model = if (media.mayBeAnimated) media.uri else Thumb(media),
                contentDescription = media.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (media.isVideo || media.isFavorite || badge != null) {
                // Keeps the white labels readable on bright photos.
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.28f), 0.35f to Color.Transparent)))
            }
            if (media.isVideo) {
                Row(Modifier.align(Alignment.TopEnd).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatDuration(media.durationMs),
                        style = MaterialTheme.typography.labelMedium.copy(shadow = TextShadow),
                        color = Color.White,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.PlayCircle, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            if (media.isFavorite) {
                Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(16.dp))
            }
            badge?.invoke(this)
        }
        if (selected != null) {
            val icon = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked
            Icon(
                icon,
                null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(24.dp)
                    .then(if (isSelected) Modifier.background(MaterialTheme.colorScheme.surface, CircleShape) else Modifier),
            )
        }
    }
}
