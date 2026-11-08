package dev.hostyn.gallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The device's photos and videos, read straight from MediaStore and reloaded whenever it
 * changes (a new photo, something deleted by another app...).
 */
class MediaRepository(private val context: Context, private val scope: CoroutineScope) {
    private val _media = MutableStateFlow<List<Media>?>(null)
    /** Everything outside the trash, newest first. Null until the first load. */
    val media: StateFlow<List<Media>?> = _media

    private val _trash = MutableStateFlow<List<Media>>(emptyList())
    /** Items in the trash (Android 11+), newest first. */
    val trash: StateFlow<List<Media>> = _trash

    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val loading = Mutex()
    private var started = false

    /** Starts loading and watching the library. Call once media permission is granted. */
    @OptIn(FlowPreview::class)
    @Synchronized
    fun start() {
        if (started) {
            refresh()
            return
        }
        started = true
        context.contentResolver.registerContentObserver(
            MediaStore.Files.getContentUri(VOLUME),
            true,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) { changes.tryEmit(Unit) }
            },
        )
        scope.launch { changes.debounce(300).collect { load() } }
        scope.launch { load() }
    }

    /** Reloads now, e.g. after the user changed which photos the app can see. */
    fun refresh() {
        if (started) scope.launch { load() }
    }

    private suspend fun load() = loading.withLock {
        val all = runCatching { query(trashed = false) }.getOrElse { emptyList() }
        _media.value = all
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            _trash.value = runCatching { query(trashed = true) }.getOrElse { emptyList() }
        }
    }

    /** Finds an item by its MediaStore id, also in the trash. */
    fun find(id: Long): Media? = _media.value?.firstOrNull { it.id == id } ?: _trash.value.firstOrNull { it.id == id }

    private fun query(trashed: Boolean): List<Media> {
        val projection = buildList {
            addAll(listOf(ID, MEDIA_TYPE, MIME_TYPE, NAME, DATE_TAKEN, DATE_MODIFIED, WIDTH, HEIGHT, SIZE, DURATION, BUCKET_ID, BUCKET_NAME, ORIENTATION))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(RELATIVE_PATH)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) addAll(listOf(IS_FAVORITE, DATE_EXPIRES))
        }.toTypedArray()
        val selection = "$MEDIA_TYPE IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
        val uri = MediaStore.Files.getContentUri(VOLUME)
        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val args = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, if (trashed) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE)
            }
            context.contentResolver.query(uri, projection, args, null)
        } else {
            context.contentResolver.query(uri, projection, selection, null, null)
        }
        val items = cursor?.use { c -> buildList { while (c.moveToNext()) add(read(c)) } }.orEmpty()
        return items.sortedWith(compareByDescending<Media> { it.date }.thenByDescending { it.id })
    }

    private fun read(c: Cursor): Media {
        fun long(column: String) = c.getColumnIndex(column).takeIf { it >= 0 && !c.isNull(it) }?.let(c::getLong) ?: 0L
        fun int(column: String) = c.getColumnIndex(column).takeIf { it >= 0 && !c.isNull(it) }?.let(c::getInt) ?: 0
        fun string(column: String) = c.getColumnIndex(column).takeIf { it >= 0 && !c.isNull(it) }?.let(c::getString)

        val id = long(ID)
        val isVideo = int(MEDIA_TYPE) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
        val modified = long(DATE_MODIFIED) * 1000
        val rotated = int(ORIENTATION) % 180 != 0
        val (w, h) = int(WIDTH) to int(HEIGHT)
        return Media(
            id = id,
            uri = contentUri(id, isVideo),
            isVideo = isVideo,
            mimeType = string(MIME_TYPE) ?: if (isVideo) "video/*" else "image/*",
            name = string(NAME).orEmpty(),
            date = long(DATE_TAKEN).takeIf { it > 0 } ?: modified,
            modified = modified,
            width = if (rotated) h else w,
            height = if (rotated) w else h,
            size = long(SIZE),
            durationMs = long(DURATION),
            bucketId = long(BUCKET_ID),
            bucketName = string(BUCKET_NAME).orEmpty(),
            relativePath = string(RELATIVE_PATH),
            isFavorite = int(IS_FAVORITE) == 1,
            expires = long(DATE_EXPIRES) * 1000,
        )
    }

    companion object {
        /** All external volumes (internal storage and SD cards) on Android 10+. */
        private const val VOLUME = "external"

        private const val ID = MediaStore.Files.FileColumns._ID
        private const val MEDIA_TYPE = MediaStore.Files.FileColumns.MEDIA_TYPE
        private const val MIME_TYPE = MediaStore.Files.FileColumns.MIME_TYPE
        private const val NAME = MediaStore.Files.FileColumns.DISPLAY_NAME
        private const val DATE_MODIFIED = MediaStore.Files.FileColumns.DATE_MODIFIED
        private const val SIZE = MediaStore.Files.FileColumns.SIZE

        // Columns of the files table that the constants only declare in newer versions.
        private const val DATE_TAKEN = "datetaken"
        private const val WIDTH = "width"
        private const val HEIGHT = "height"
        private const val DURATION = "duration"
        private const val BUCKET_ID = "bucket_id"
        private const val BUCKET_NAME = "bucket_display_name"
        private const val ORIENTATION = "orientation"
        private const val RELATIVE_PATH = "relative_path"
        private const val IS_FAVORITE = "is_favorite"
        private const val DATE_EXPIRES = "date_expires"

        fun contentUri(id: Long, isVideo: Boolean): Uri = ContentUris.withAppendedId(
            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            id,
        )
    }
}
