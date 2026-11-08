package dev.hostyn.gallery.ui.viewer

import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.hostyn.gallery.R
import dev.hostyn.gallery.data.Details
import dev.hostyn.gallery.data.Media
import dev.hostyn.gallery.data.formatDuration
import dev.hostyn.gallery.data.readDetails
import dev.hostyn.gallery.ui.components.fullDate
import dev.hostyn.gallery.ui.startSafely
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Date, file, camera and place of a photo or video. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(media: Media, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val details by produceState<Details?>(null, media) { value = withContext(Dispatchers.IO) { readDetails(context, media) } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.info),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            if (media.date > 0) {
                val (date, time) = fullDate(media.date)
                InfoRow(Icons.Outlined.Event, date, time)
            }
            val specs = buildList {
                if (media.width > 0 && media.height > 0) {
                    val mp = media.width.toLong() * media.height / 1_000_000.0
                    if (!media.isVideo && mp >= 1) add(String.format(Locale.getDefault(), "%.0f MP", mp))
                    add("${media.width} × ${media.height}")
                }
                if (media.isVideo && media.durationMs > 0) add(formatDuration(media.durationMs))
                if (media.size > 0) add(Formatter.formatShortFileSize(context, media.size))
            }.joinToString(" · ")
            InfoRow(if (media.isVideo) Icons.Outlined.Videocam else Icons.Outlined.Image, media.name, specs.ifEmpty { null })
            val folder = media.relativePath?.trimEnd('/') ?: media.bucketName.takeIf { it.isNotEmpty() }
            if (folder != null) InfoRow(Icons.Outlined.Folder, folder, null)
            details?.camera?.let { InfoRow(Icons.Outlined.PhotoCamera, it, details?.settings) }
            details?.location?.let { (lat, lon) ->
                InfoRow(
                    Icons.Outlined.Place,
                    String.format(Locale.US, "%.5f, %.5f", lat, lon),
                    stringResource(R.string.open_in_maps),
                ) {
                    startSafely(context, Intent(Intent.ACTION_VIEW, "geo:$lat,$lon?q=$lat,$lon".toUri()))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, summary: String?, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!summary.isNullOrEmpty()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
