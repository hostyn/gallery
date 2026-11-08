package dev.hostyn.gallery.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.util.Locale
import kotlin.math.roundToLong

/** What the info sheet shows beyond the MediaStore columns: camera, settings and place. */
data class Details(val camera: String?, val settings: String?, val location: Pair<Double, Double>?)

/** Reads EXIF from photos and the location from videos. Blocking. */
fun readDetails(context: Context, media: Media): Details {
    val uri = originalUri(context, media)
    return runCatching {
        if (media.isVideo) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                Details(null, null, parseIso6709(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)))
            } finally {
                retriever.release()
            }
        } else {
            val exif = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) } ?: return@runCatching Details(null, null, null)
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim().orEmpty()
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim().orEmpty()
            val camera = when {
                model.isEmpty() -> make.ifEmpty { null }
                make.isEmpty() || model.startsWith(make, ignoreCase = true) -> model
                else -> "$make $model"
            }
            val settings = listOfNotNull(
                exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }?.let(::formatAperture),
                exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let(::formatExposure),
                exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 }?.let { "ISO $it" },
                exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 }?.let { "${trim(it)} mm" },
            ).joinToString(" · ").ifEmpty { null }
            val location = exif.latLong?.let { it[0] to it[1] }?.takeIf { it.first != 0.0 || it.second != 0.0 }
            Details(camera, settings, location)
        }
    }.getOrElse { Details(null, null, null) }
}

/** Android 10+ removes the location from photos unless we ask for the original with ACCESS_MEDIA_LOCATION. */
private fun originalUri(context: Context, media: Media): Uri {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || media.id < 0) return media.uri
    if (context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED) return media.uri
    return runCatching { MediaStore.setRequireOriginal(media.uri) }.getOrDefault(media.uri)
}

private fun trim(value: Double): String =
    if (value == value.roundToLong().toDouble()) value.roundToLong().toString() else String.format(Locale.US, "%.1f", value)

/** "ƒ/1.8" */
fun formatAperture(f: Double) = "ƒ/" + trim(f)

/** "1/120 s", "0.5 s", "2 s" */
fun formatExposure(seconds: Double): String = when {
    seconds >= 0.3 -> trim(seconds) + " s"
    else -> "1/" + (1 / seconds).roundToLong() + " s"
}

/** Video location in ISO 6709, like "+40.4168-003.7038/" or "+40.4168-003.7038+650.0/". */
fun parseIso6709(value: String?): Pair<Double, Double>? {
    val match = Regex("^([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)").find(value?.trim() ?: return null) ?: return null
    val lat = match.groupValues[1].toDoubleOrNull() ?: return null
    val lon = match.groupValues[2].toDoubleOrNull() ?: return null
    return if (lat in -90.0..90.0 && lon in -180.0..180.0 && (lat != 0.0 || lon != 0.0)) lat to lon else null
}
