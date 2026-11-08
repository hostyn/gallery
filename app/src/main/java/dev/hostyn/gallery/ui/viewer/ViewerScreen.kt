package dev.hostyn.gallery.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.request.ImageRequest
import dev.hostyn.gallery.R
import dev.hostyn.gallery.data.Media
import dev.hostyn.gallery.data.thumbKey
import dev.hostyn.gallery.ui.components.fullDate
import dev.hostyn.gallery.ui.edit
import dev.hostyn.gallery.ui.hasSystemTrash
import dev.hostyn.gallery.ui.openWith
import dev.hostyn.gallery.ui.rememberMediaActions
import dev.hostyn.gallery.ui.share
import dev.hostyn.gallery.ui.theme.appInDarkTheme
import dev.hostyn.gallery.ui.useAs
import androidx.compose.material3.LocalContentColor
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import kotlin.math.abs

/** Space the bottom actions take, so video controls and snackbars sit above them. */
val BottomBarHeight = 72.dp

/**
 * Full-screen photos and videos: swipe between them, pinch or double-tap to zoom, swipe down
 * to close, tap to hide the controls. [external] is a file another app opened with us, which we
 * can't delete or favorite.
 */
@Composable
fun ViewerScreen(
    items: List<Media>,
    startId: Long,
    inTrash: Boolean,
    onBack: () -> Unit,
    external: Boolean = false,
) {
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val context = LocalContext.current
    val startIndex = remember { items.indexOfFirst { it.id == startId }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = startIndex) { items.size }
    val current = items[pager.currentPage.coerceIn(0, items.lastIndex)]
    var chrome by rememberSaveable { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var dismissProgress by remember { mutableFloatStateOf(0f) }
    val snackbar = remember { SnackbarHostState() }
    val actions = rememberMediaActions(snackbar)
    ImmersiveBars(visible = chrome)

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1f - dismissProgress))) {
        HorizontalPager(
            state = pager,
            key = { items[it].id },
            beyondViewportPageCount = 1,
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val media = items[page]
            val onProgress = { p: Float -> if (page == pager.currentPage) dismissProgress = p }
            if (media.isVideo) {
                VideoPage(
                    media = media,
                    isCurrent = pager.settledPage == page,
                    chrome = chrome,
                    onTap = { chrome = !chrome },
                    onDismiss = onBack,
                    onDismissProgress = onProgress,
                )
            } else {
                ImagePage(media, onTap = { chrome = !chrome }, onDismiss = onBack, onDismissProgress = onProgress)
            }
        }

        CompositionLocalProvider(LocalContentColor provides Color.White) {
            AnimatedVisibility(
                visible = chrome && dismissProgress == 0f,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                TopBar(current, external, onBack)
            }
            AnimatedVisibility(
                visible = chrome && dismissProgress == 0f,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Row(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                        .navigationBarsPadding()
                        .padding(vertical = 12.dp),
                ) {
                    when {
                        inTrash -> {
                            IconButton(onClick = { actions.restore(listOf(current)) }) { Icon(Icons.Rounded.RestoreFromTrash, stringResource(R.string.restore)) }
                            IconButton(onClick = { showInfo = true }) { Icon(Icons.Outlined.Info, stringResource(R.string.info)) }
                            IconButton(onClick = { actions.deleteForever(listOf(current)) }) { Icon(Icons.Rounded.DeleteForever, stringResource(R.string.delete_forever)) }
                        }
                        else -> {
                            IconButton(onClick = { share(context, listOf(current)) }) { Icon(Icons.Rounded.Share, stringResource(R.string.share)) }
                            IconButton(onClick = { edit(context, current) }) { Icon(Icons.Rounded.Edit, stringResource(R.string.edit)) }
                            if (!external && hasSystemTrash) {
                                IconButton(onClick = { actions.setFavorite(listOf(current), !current.isFavorite) }) {
                                    Icon(
                                        if (current.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        stringResource(if (current.isFavorite) R.string.unfavorite else R.string.favorite),
                                    )
                                }
                            }
                            IconButton(onClick = { showInfo = true }) { Icon(Icons.Outlined.Info, stringResource(R.string.info)) }
                            if (!external) {
                                IconButton(onClick = { actions.moveToTrash(listOf(current)) }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete)) }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = BottomBarHeight))
    }

    if (showInfo) InfoSheet(current, onDismiss = { showInfo = false })
}

@Composable
private fun TopBar(media: Media, external: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            if (media.date > 0) {
                val (date, time) = fullDate(media.date)
                Text(date, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(time, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
            } else if (external) {
                Text(media.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (!media.isVideo) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.use_as)) }, onClick = { menu = false; useAs(context, media) })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.open_with)) }, onClick = { menu = false; openWith(context, media) })
            }
        }
    }
}

@Composable
private fun ImagePage(media: Media, onTap: () -> Unit, onDismiss: () -> Unit, onDismissProgress: (Float) -> Unit) {
    val context = LocalContext.current
    val zoom = rememberZoomableImageState(rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 8f)))
    val zoomed = (zoom.zoomableState.zoomFraction ?: 0f) > 0.01f
    DismissBox(enabled = !zoomed, onDismiss = onDismiss, onProgress = onDismissProgress) {
        val request = remember(media) {
            ImageRequest.Builder(context)
                .data(media.uri)
                .apply { if (media.id >= 0) placeholderMemoryCacheKey(thumbKey(media)) }
                .crossfade(false)
                .build()
        }
        ZoomableAsyncImage(
            model = request,
            contentDescription = media.name,
            state = zoom,
            onClick = { onTap() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Follows a vertical drag on the page and closes the viewer when it goes far or fast enough. */
@Composable
fun DismissBox(enabled: Boolean, onDismiss: () -> Unit, onProgress: (Float) -> Unit, content: @Composable BoxScope.() -> Unit) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { 120.dp.toPx() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        LaunchedEffect(offset.value) { onProgress((abs(offset.value) / (height / 2)).coerceIn(0f, 1f)) }
        Box(
            Modifier
                .fillMaxSize()
                .draggable(
                    orientation = Orientation.Vertical,
                    enabled = enabled,
                    state = rememberDraggableState { delta -> scope.launch { offset.snapTo(offset.value + delta) } },
                    onDragStopped = { velocity ->
                        if (abs(offset.value) > threshold || abs(velocity) > 3000f) {
                            onDismiss()
                        } else {
                            offset.animateTo(0f)
                        }
                    },
                )
                .graphicsLayer {
                    translationY = offset.value
                    val scale = 1f - (abs(offset.value) / height).coerceIn(0f, 1f) * 0.3f
                    scaleX = scale
                    scaleY = scale
                },
            content = content,
        )
    }
}

/** Light icons on the black viewer, and hides the system bars along with our controls. */
@Composable
private fun ImmersiveBars(visible: Boolean) {
    val view = LocalView.current
    val window = view.context.findActivity()?.window ?: return
    val controller = remember(window) { WindowCompat.getInsetsController(window, view) }
    val dark = appInDarkTheme()
    DisposableEffect(controller, dark) {
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    LaunchedEffect(visible) {
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
