@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package dev.hostyn.gallery.ui.viewer

import android.content.Intent
import android.text.format.Formatter
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
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
import dev.hostyn.gallery.ui.components.SegmentedGroup
import dev.hostyn.gallery.ui.components.SegmentedGroupScope
import dev.hostyn.gallery.ui.components.fullDate
import dev.hostyn.gallery.ui.startSafely
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * How far the details panel has come up from the bottom of the viewer. The photo moves up the same
 * amount, so the panel pushes it out of the way instead of covering it.
 */
@Stable
class InfoState(private val scope: CoroutineScope) {
    /** Pixels the panel shows, from 0 (closed) to [height] (open). */
    var shown by mutableFloatStateOf(0f)
        private set

    /** The panel's full height, once laid out. */
    var height by mutableFloatStateOf(0f)
        private set

    val isOpen get() = shown > 0.5f
    private var settling: Job? = null

    /** Follows a finger: moving up ([delta] < 0) brings the panel up. */
    fun dragBy(delta: Float) {
        settling?.cancel()
        shown = (shown - delta).coerceIn(0f, height)
    }

    /** At the end of a drag, opens or closes depending on the fling, or else on how far it got. */
    fun settle(velocity: Float) = animateTo(
        when {
            velocity < -1000f -> true
            velocity > 1000f -> false
            else -> shown > height / 2
        },
    )

    fun animateTo(open: Boolean) {
        settling?.cancel()
        settling = scope.launch {
            animate(shown, if (open) height else 0f, animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) { v, _ -> shown = v }
        }
    }

    internal fun measured(new: Float) {
        // Keep an open panel fully open when its content grows, like when the camera details load.
        val wasOpen = height > 0 && shown >= height - 0.5f
        height = new
        shown = if (wasOpen) new else shown.coerceAtMost(new)
    }
}

@Composable
fun rememberInfoState(): InfoState {
    val scope = rememberCoroutineScope()
    return remember(scope) { InfoState(scope) }
}

/**
 * Date, file, camera and place of a photo or video, in a panel that comes up from the bottom of
 * the viewer as it is dragged (see [InfoState]). It is always laid out, just below the screen when
 * closed, so its height is known before it opens.
 */
@Composable
fun InfoPanel(media: Media, state: InfoState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val details by produceState<Details?>(null, media) { value = withContext(Dispatchers.IO) { readDetails(context, media) } }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { state.measured(it.height.toFloat()) }
            .graphicsLayer { translationY = state.height - state.shown }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { state.dragBy(it) },
                onDragStopped = { state.settle(it) },
            ),
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
            Text(
                stringResource(R.string.info),
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 12.dp),
            )
            // An M3 segmented list, as in the settings.
            SegmentedGroup {
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
}

/** One fact about the photo; tapping it does something only with [onClick]. */
private fun SegmentedGroupScope.InfoRow(icon: ImageVector, title: String, summary: String?, onClick: (() -> Unit)? = null) = item { shapes ->
    val colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    val leading: @Composable () -> Unit = { Icon(icon, null) }
    val supporting: (@Composable () -> Unit)? = summary?.takeIf { it.isNotEmpty() }?.let {
        { Text(it, color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (onClick != null) {
        SegmentedListItem(onClick = onClick, shapes = shapes, colors = colors, leadingContent = leading, supportingContent = supporting) { Text(title) }
    } else {
        SegmentedListItem(shapes = shapes, colors = colors, leadingContent = leading, supportingContent = supporting) { Text(title) }
    }
}
