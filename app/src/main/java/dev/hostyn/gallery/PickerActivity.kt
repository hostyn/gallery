package dev.hostyn.gallery

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.hostyn.gallery.data.Media
import dev.hostyn.gallery.ui.AppNavigation
import dev.hostyn.gallery.ui.PermissionGate
import dev.hostyn.gallery.ui.PickConfig
import dev.hostyn.gallery.ui.theme.GalleryTheme

/** Lets another app choose photos or videos from ours (GET_CONTENT and PICK). */
class PickerActivity : ComponentActivity() {
    private var multiple = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        multiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        val config = PickConfig(filters(intent), multiple, ::finishWith)
        setContent {
            GalleryTheme {
                PermissionGate { access -> AppNavigation(access, config) }
            }
        }
    }

    /** MIME types the caller accepts; "any type" means any photo or video. */
    private fun filters(intent: Intent): List<String> {
        val requested = intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList()?.takeIf { it.isNotEmpty() }
            ?: listOf(intent.type ?: typeFromData(intent))
        return requested.flatMap { type ->
            when {
                type == "*/*" || type == "*" -> listOf("image/*", "video/*")
                type.startsWith("vnd.android.cursor.dir/") -> listOf(type.substringAfterLast('/') + "/*")
                else -> listOf(type)
            }
        }
    }

    /** PICK without a type points at a MediaStore table instead. */
    private fun typeFromData(intent: Intent): String {
        val data = intent.data ?: return "*/*"
        return when (data) {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI -> "image/*"
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI -> "video/*"
            else -> contentResolver.getType(data) ?: "*/*"
        }
    }

    private fun finishWith(items: List<Media>) {
        if (items.isEmpty()) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        val result = Intent().setData(items[0].uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (multiple || items.size > 1) {
            result.clipData = ClipData.newRawUri(null, items[0].uri).apply { items.drop(1).forEach { addItem(ClipData.Item(it.uri)) } }
        }
        setResult(RESULT_OK, result)
        finish()
    }
}
