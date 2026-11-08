package dev.hostyn.gallery.ui

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import dev.hostyn.gallery.GalleryApp
import dev.hostyn.gallery.R
import dev.hostyn.gallery.data.Media
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android keeps a trash and favorites for all apps since Android 11. */
val hasSystemTrash get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

/** Whether the user lets us change media without a confirmation each time (Android 12+). */
fun canManageMedia(context: Context) = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(context)

/** Opens the system page where the user lets us manage media without confirmations. */
fun requestManageMedia(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    startSafely(context, Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, "package:${context.packageName}".toUri()))
}

fun startSafely(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, R.string.error_no_app, Toast.LENGTH_SHORT).show()
    }
}

fun share(context: Context, items: List<Media>) {
    if (items.isEmpty()) return
    val type = items.map { it.mimeType }.distinct().singleOrNull()
        ?: items.map { it.mimeType.substringBefore('/') }.distinct().singleOrNull()?.let { "$it/*" }
        ?: "*/*"
    val intent = if (items.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, items[0].uri)
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(items.map { it.uri }))
    }
    intent.setType(type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // So the receiving app can read every item, not only the first one.
    intent.clipData = ClipData.newRawUri(null, items[0].uri).apply { items.drop(1).forEach { addItem(ClipData.Item(it.uri)) } }
    startSafely(context, Intent.createChooser(intent, null))
}

/** Opens it in an editor app. */
fun edit(context: Context, media: Media) {
    val intent = Intent(Intent.ACTION_EDIT)
        .setDataAndType(media.uri, media.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    startSafely(context, Intent.createChooser(intent, null))
}

/** Wallpaper, contact photo... */
fun useAs(context: Context, media: Media) {
    val intent = Intent(Intent.ACTION_ATTACH_DATA)
        .setDataAndType(media.uri, media.mimeType)
        .putExtra("mimeType", media.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startSafely(context, Intent.createChooser(intent, null))
}

fun openWith(context: Context, media: Media) {
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(media.uri, media.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startSafely(context, Intent.createChooser(intent, null))
}

/**
 * Changes to the library: trash, restore, delete and favorites. On Android 11+ these go through
 * MediaStore requests, which the system confirms unless the user lets us manage media.
 */
class MediaActions internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val snackbar: SnackbarHostState,
    private val messages: Messages,
) {
    internal var launch: (IntentSenderRequest) -> Unit = {}
    private var pending: ((Boolean) -> Unit)? = null

    /** A deletion waiting for the user to confirm it in our own dialog. */
    internal var confirm by mutableStateOf<Confirm?>(null)

    internal class Confirm(val count: Int, val run: () -> Unit)

    internal class Messages(
        val trashed: (Int) -> String,
        val restored: (Int) -> String,
        val deleted: (Int) -> String,
        val undo: String,
        val skipConfirmations: String,
        val allow: String,
    )

    internal fun onResult(result: ActivityResult) {
        val callback = pending
        pending = null
        callback?.invoke(result.resultCode == android.app.Activity.RESULT_OK)
    }

    private fun request(intent: PendingIntent, after: (Boolean) -> Unit) {
        val confirmed = !canManageMedia(context)
        pending = { ok ->
            after(ok)
            if (ok && confirmed) suggestManageMedia()
        }
        launch(IntentSenderRequest.Builder(intent.intentSender).build())
    }

    /** After a system confirmation, once: point out that it can be skipped. */
    private fun suggestManageMedia() {
        val settings = GalleryApp.of(context).settings
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || settings.manageMediaHintShown) return
        settings.markManageMediaHintShown()
        scope.launch {
            val result = snackbar.showSnackbar(messages.skipConfirmations, messages.allow, withDismissAction = true, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) requestManageMedia(context)
        }
    }

    private fun uris(items: List<Media>) = items.map { it.uri }

    /** Moves to the trash, or deletes after confirming where there is no trash. */
    fun moveToTrash(items: List<Media>, onDone: () -> Unit = {}) {
        if (items.isEmpty()) return
        if (!hasSystemTrash) {
            confirm = Confirm(items.size) { deleteLegacy(items, onDone) }
            return
        }
        request(MediaStore.createTrashRequest(context.contentResolver, uris(items), true)) { ok ->
            if (!ok) return@request
            onDone()
            scope.launch {
                // Undoing needs no extra confirmation only when we can manage media.
                val undo = if (canManageMedia(context)) messages.undo else null
                val result = snackbar.showSnackbar(messages.trashed(items.size), undo, duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) restore(items, quiet = true)
            }
        }
    }

    fun restore(items: List<Media>, onDone: () -> Unit = {}, quiet: Boolean = false) {
        if (items.isEmpty() || !hasSystemTrash) return
        request(MediaStore.createTrashRequest(context.contentResolver, uris(items), false)) { ok ->
            if (!ok) return@request
            onDone()
            if (!quiet) scope.launch { snackbar.showSnackbar(messages.restored(items.size)) }
        }
    }

    /** Deletes for good. Asks first when the system won't. */
    fun deleteForever(items: List<Media>, onDone: () -> Unit = {}) {
        if (items.isEmpty()) return
        if (!hasSystemTrash) {
            confirm = Confirm(items.size) { deleteLegacy(items, onDone) }
            return
        }
        val run = {
            request(MediaStore.createDeleteRequest(context.contentResolver, uris(items))) { ok ->
                if (ok) {
                    onDone()
                    scope.launch { snackbar.showSnackbar(messages.deleted(items.size)) }
                }
            }
        }
        if (canManageMedia(context)) confirm = Confirm(items.size, run) else run()
    }

    fun setFavorite(items: List<Media>, favorite: Boolean, onDone: () -> Unit = {}) {
        if (items.isEmpty() || !hasSystemTrash) return
        request(MediaStore.createFavoriteRequest(context.contentResolver, uris(items), favorite)) { ok -> if (ok) onDone() }
    }

    /** Android 8–10: no trash, delete directly (we hold the storage permission there). */
    private fun deleteLegacy(items: List<Media>, onDone: () -> Unit) {
        scope.launch {
            var deleted = 0
            for (item in items) {
                try {
                    deleted += withContext(Dispatchers.IO) { context.contentResolver.delete(item.uri, null, null) }
                } catch (e: SecurityException) {
                    // Android 10 without legacy storage: ask for this item.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException && items.size == 1) {
                        request(e.userAction.actionIntent) { ok -> if (ok) moveToTrashRetry(item, onDone) }
                        return@launch
                    }
                }
            }
            if (deleted > 0) {
                onDone()
                snackbar.showSnackbar(messages.deleted(deleted))
            }
        }
    }

    private fun moveToTrashRetry(item: Media, onDone: () -> Unit) = deleteLegacy(listOf(item), onDone)
}

@Composable
fun rememberMediaActions(snackbar: SnackbarHostState): MediaActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = MediaActions.Messages(
        trashed = { n -> context.resources.getQuantityString(R.plurals.moved_to_trash, n, n) },
        restored = { n -> context.resources.getQuantityString(R.plurals.restored, n, n) },
        deleted = { n -> context.resources.getQuantityString(R.plurals.deleted, n, n) },
        undo = stringResource(R.string.undo),
        skipConfirmations = stringResource(R.string.manage_media_hint),
        allow = stringResource(R.string.allow),
    )
    val actions = remember(snackbar) { MediaActions(context, scope, snackbar, messages) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { actions.onResult(it) }
    actions.launch = { launcher.launch(it) }

    actions.confirm?.let { pending ->
        AlertDialog(
            onDismissRequest = { actions.confirm = null },
            title = { Text(pluralStringResource(R.plurals.delete_forever_title, pending.count, pending.count)) },
            text = { Text(stringResource(R.string.delete_forever_body)) },
            confirmButton = {
                TextButton(onClick = {
                    actions.confirm = null
                    pending.run()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { actions.confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    return actions
}
