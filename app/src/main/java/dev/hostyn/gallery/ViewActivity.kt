package dev.hostyn.gallery

import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hostyn.gallery.data.Media
import dev.hostyn.gallery.ui.MediaAccess
import dev.hostyn.gallery.ui.mediaAccess
import dev.hostyn.gallery.ui.theme.GalleryTheme
import dev.hostyn.gallery.ui.viewer.ViewerScreen
import java.io.File

/** Opens a photo or video from another app: a file manager, a chat, the camera... */
class ViewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }
        val type = intent.type?.takeIf { '*' !in it } ?: contentResolver.getType(uri) ?: intent.type.orEmpty()
        setContent {
            GalleryTheme { ExternalViewer(uri, type, onClose = ::finish) }
        }
    }
}

@Composable
private fun ExternalViewer(uri: Uri, type: String, onClose: () -> Unit) {
    val context = LocalContext.current
    // One of our own photos (the camera's latest, say): show it among the others, so you can swipe.
    val libraryId = remember {
        if (uri.authority == MediaStore.AUTHORITY && mediaAccess(context) != MediaAccess.None) uri.lastPathSegment?.toLongOrNull() else null
    }
    if (libraryId != null) {
        val repository = GalleryApp.of(context).repository
        LaunchedEffect(Unit) { repository.start() }
        val media by repository.media.collectAsStateWithLifecycle()
        val all = media
        if (all == null) {
            Box(Modifier.fillMaxSize().background(Color.Black))
            return
        }
        if (all.any { it.id == libraryId }) {
            ViewerScreen(all, libraryId, inTrash = false, onBack = onClose)
            return
        }
    }
    val single = remember(uri) { externalMedia(context, uri, type) }
    ViewerScreen(listOf(single), single.id, inTrash = false, onBack = onClose, external = true)
}

/** What we can learn about a file we only have a link to. */
private fun externalMedia(context: android.content.Context, uri: Uri, type: String): Media {
    var name = uri.lastPathSegment.orEmpty()
    var size = 0L
    if (uri.scheme == "file") {
        uri.path?.let { File(it) }?.let { name = it.name; size = it.length() }
    } else {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 && !c.isNull(it) }?.let { name = c.getString(it) }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
                }
            }
        }
    }
    return Media(
        id = -1,
        uri = uri,
        isVideo = type.startsWith("video/"),
        mimeType = type.ifEmpty { "image/*" },
        name = name,
        date = 0,
        modified = 0,
        width = 0,
        height = 0,
        size = size,
        durationMs = 0,
        bucketId = -1,
        bucketName = "",
        relativePath = null,
        isFavorite = false,
        expires = 0,
    )
}
