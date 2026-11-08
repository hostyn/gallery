package dev.hostyn.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.provider.MediaStore
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import coil.size.pxOrElse
import java.io.IOException

/** Coil model for a grid thumbnail. Uses the thumbnails Android already keeps, which is much faster than decoding. */
data class Thumb(val media: Media)

/** Memory cache key of a thumbnail, also used as the viewer's placeholder while the full image loads. */
fun thumbKey(media: Media) = "thumb:${media.uri}:${media.modified}"

class ThumbKeyer : Keyer<Thumb> {
    override fun key(data: Thumb, options: Options) = thumbKey(data.media)
}

class ThumbFetcher(private val context: Context, private val thumb: Thumb, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val media = thumb.media
        val resolver = context.contentResolver
        val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val w = options.size.width.pxOrElse { DEFAULT_SIZE }
            val h = options.size.height.pxOrElse { DEFAULT_SIZE }
            resolver.loadThumbnail(media.uri, android.util.Size(w, h), null)
        } else {
            @Suppress("DEPRECATION")
            if (media.isVideo) {
                MediaStore.Video.Thumbnails.getThumbnail(resolver, media.id, MediaStore.Video.Thumbnails.MINI_KIND, null)
            } else {
                MediaStore.Images.Thumbnails.getThumbnail(resolver, media.id, MediaStore.Images.Thumbnails.MINI_KIND, null)
            } ?: throw IOException("No thumbnail for ${media.uri}")
        }
        return DrawableResult(BitmapDrawable(context.resources, bitmap), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory(private val context: Context) : Fetcher.Factory<Thumb> {
        override fun create(data: Thumb, options: Options, imageLoader: ImageLoader) = ThumbFetcher(context, data, options)
    }

    private companion object {
        const val DEFAULT_SIZE = 384
    }
}
