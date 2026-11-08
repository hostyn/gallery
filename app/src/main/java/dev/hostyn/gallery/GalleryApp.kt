package dev.hostyn.gallery

import android.app.Application
import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import dev.hostyn.gallery.data.AppSettings
import dev.hostyn.gallery.data.MediaRepository
import dev.hostyn.gallery.data.ThumbFetcher
import dev.hostyn.gallery.data.ThumbKeyer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class GalleryApp : Application(), ImageLoaderFactory {
    val settings by lazy { AppSettings(this) }

    /** For work that must outlive a screen. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repository by lazy { MediaRepository(this, scope) }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(ThumbFetcher.Factory(this@GalleryApp))
            add(ThumbKeyer())
            // Animated GIFs (and WebP/HEIF on Android 9+); still images go on to the default decoder.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
        }
        .crossfade(120)
        .build()

    companion object {
        fun of(context: Context) = context.applicationContext as GalleryApp
    }
}
