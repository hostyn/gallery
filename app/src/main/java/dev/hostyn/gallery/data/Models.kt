package dev.hostyn.gallery.data

import android.net.Uri

/** A photo or video from the device's media library. */
data class Media(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val mimeType: String,
    val name: String,
    /** When it was taken, or else last modified (ms). */
    val date: Long,
    /** For thumbnail caching: changes when the file is edited (ms). */
    val modified: Long,
    val width: Int,
    val height: Int,
    val size: Long,
    val durationMs: Long,
    val bucketId: Long,
    val bucketName: String,
    /** Folder relative to the storage root ("DCIM/Camera/"), Android 10+. */
    val relativePath: String?,
    val isFavorite: Boolean,
    /** When the system deletes it for good, for items in the trash (ms). */
    val expires: Long,
)

/** A folder with photos or videos. */
data class Album(val id: Long, val name: String, val cover: Media, val count: Int)

/** Which photos a grid or the viewer shows. */
sealed interface Collection {
    data object All : Collection
    data class Folder(val bucketId: Long) : Collection
    data object Favorites : Collection
    data object Videos : Collection
    data object Trash : Collection

    fun route(): String = when (this) {
        All -> "all"
        is Folder -> "album-$bucketId"
        Favorites -> "favorites"
        Videos -> "videos"
        Trash -> "trash"
    }

    companion object {
        fun fromRoute(route: String): Collection = when {
            route == "favorites" -> Favorites
            route == "videos" -> Videos
            route == "trash" -> Trash
            route.startsWith("album-") -> route.removePrefix("album-").toLongOrNull()?.let(::Folder) ?: All
            else -> All
        }
    }
}

/** Groups a library (newest first) into folders: the camera first, then by most recent photo. */
fun albumsOf(media: List<Media>): List<Album> =
    media.groupBy { it.bucketId }
        .map { (id, items) -> Album(id, items.first().bucketName, items.first(), items.size) }
        .sortedWith(compareByDescending<Album> { isCameraFolder(it) }.thenByDescending { it.cover.date })

private fun isCameraFolder(album: Album): Boolean {
    val path = album.cover.relativePath
    return if (path != null) path.trimEnd('/').equals("DCIM/Camera", ignoreCase = true) else album.name.equals("Camera", ignoreCase = true)
}

/** The items of a collection, in display order. */
fun itemsOf(collection: Collection, media: List<Media>, trash: List<Media>): List<Media> = when (collection) {
    Collection.All -> media
    is Collection.Folder -> media.filter { it.bucketId == collection.bucketId }
    Collection.Favorites -> media.filter { it.isFavorite }
    Collection.Videos -> media.filter { it.isVideo }
    Collection.Trash -> trash
}
