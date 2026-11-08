package dev.hostyn.gallery.data

import android.net.TestUri
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumsTest {
    private fun media(id: Long, bucket: Long, name: String, date: Long, path: String? = null, video: Boolean = false, favorite: Boolean = false) = Media(
        id = id, uri = TestUri("content://media/$id"), isVideo = video, mimeType = if (video) "video/mp4" else "image/jpeg", name = "$id.jpg",
        date = date, modified = date, width = 0, height = 0, size = 0, durationMs = 0, bucketId = bucket, bucketName = name,
        relativePath = path, isFavorite = favorite, expires = 0,
    )

    // Newest first, as the repository returns them.
    private val library = listOf(
        media(1, 20, "WhatsApp Images", 900, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/", favorite = true),
        media(2, 30, "Screenshots", 800, "Pictures/Screenshots/"),
        media(3, 10, "Camera", 700, "DCIM/Camera/", video = true),
        media(4, 20, "WhatsApp Images", 600, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/"),
        media(5, 10, "Camera", 500, "DCIM/Camera/"),
    )

    @Test
    fun cameraFirstThenMostRecent() {
        val albums = albumsOf(library)
        assertEquals(listOf("Camera", "WhatsApp Images", "Screenshots"), albums.map { it.name })
        assertEquals(listOf(2, 2, 1), albums.map { it.count })
        assertEquals(3L, albums[0].cover.id)
    }

    @Test
    fun collections() {
        assertEquals(listOf(3L, 5L), itemsOf(Collection.Folder(10), library, emptyList()).map { it.id })
        assertEquals(listOf(1L), itemsOf(Collection.Favorites, library, emptyList()).map { it.id })
        assertEquals(listOf(3L), itemsOf(Collection.Videos, library, emptyList()).map { it.id })
        for (c in listOf(Collection.All, Collection.Folder(42), Collection.Favorites, Collection.Videos, Collection.Trash)) {
            assertEquals(c, Collection.fromRoute(c.route()))
        }
    }
}
