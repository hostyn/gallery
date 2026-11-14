@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.hostyn.gallery.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.hostyn.gallery.GalleryApp
import dev.hostyn.gallery.R

enum class MediaAccess { None, Partial, Full }

private fun granted(context: Context, permission: String) =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/** How much of the library we can see. Android 14 lets the user share only some photos. */
fun mediaAccess(context: Context): MediaAccess = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> when {
        granted(context, Manifest.permission.READ_MEDIA_IMAGES) || granted(context, Manifest.permission.READ_MEDIA_VIDEO) -> MediaAccess.Full
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> MediaAccess.Partial
        else -> MediaAccess.None
    }
    granted(context, Manifest.permission.READ_EXTERNAL_STORAGE) -> MediaAccess.Full
    else -> MediaAccess.None
}

/** Everything we ask for at once: photos and videos, their location, and deleting on old versions. */
private fun mediaPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.READ_MEDIA_IMAGES)
        add(Manifest.permission.READ_MEDIA_VIDEO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    } else {
        add(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACCESS_MEDIA_LOCATION)
}.toTypedArray()

/** Asks for media access and shows [content] once there is some. */
@Composable
fun PermissionGate(content: @Composable (Access) -> Unit) {
    val context = LocalContext.current
    val repository = GalleryApp.of(context).repository
    var access by remember { mutableStateOf(mediaAccess(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        access = mediaAccess(context)
        // With partial access the user may have picked other photos.
        repository.refresh()
    }
    val request = { launcher.launch(mediaPermissions()) }

    LifecycleResumeEffect(Unit) {
        val now = mediaAccess(context)
        if (now != access) access = now else if (now == MediaAccess.Partial) repository.refresh()
        onPauseOrDispose {}
    }
    LaunchedEffect(access) { if (access != MediaAccess.None) repository.start() }
    LaunchedEffect(Unit) { if (access == MediaAccess.None && !asked) request() }

    if (access == MediaAccess.None) {
        PermissionScreen(
            onAllow = {
                val activity = context as? Activity
                val canAsk = !asked || mediaPermissions().any { activity?.shouldShowRequestPermissionRationale(it) == true }
                if (canAsk) {
                    request()
                } else {
                    // Denied for good: only the app's settings page can grant it now.
                    startSafely(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
                }
            },
        )
    } else {
        content(remember(access) { Access(partial = access == MediaAccess.Partial, manage = request) })
    }
}

@Composable
private fun PermissionScreen(onAllow: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(
                    Modifier.size(128.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie12Sided.toShape()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(52.dp))
                }
                Spacer(Modifier.height(28.dp))
                Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.permission_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                val height = ButtonDefaults.MediumContainerHeight
                Button(
                    onClick = onAllow,
                    shapes = ButtonDefaults.shapesFor(height),
                    contentPadding = ButtonDefaults.contentPaddingFor(height),
                    modifier = Modifier.heightIn(height),
                ) { Text(stringResource(R.string.allow_access), style = ButtonDefaults.textStyleFor(height)) }
            }
        }
    }
}
