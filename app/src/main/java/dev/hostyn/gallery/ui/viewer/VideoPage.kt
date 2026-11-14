@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.hostyn.gallery.ui.viewer

import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import dev.hostyn.gallery.R
import dev.hostyn.gallery.data.Media
import dev.hostyn.gallery.data.Thumb
import dev.hostyn.gallery.data.formatDuration
import kotlinx.coroutines.delay

/**
 * A video that plays while it is the page on screen. Shows its thumbnail until the first
 * frame, and our own controls (play/pause and seek bar) along with the viewer's.
 */
@Composable
fun VideoPage(
    media: Media,
    isCurrent: Boolean,
    chrome: Boolean,
    shared: Boolean,
    transitioning: Boolean,
    onTap: () -> Unit,
    onDismiss: () -> Unit,
    onDismissProgress: (Float) -> Unit,
    info: InfoState,
) {
    val context = LocalContext.current
    val player = remember(isCurrent, media.uri) {
        if (!isCurrent) return@remember null
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(media.uri))
                prepare()
                playWhenReady = true
            }
    }
    var playing by remember(player) { mutableStateOf(false) }
    var ended by remember(player) { mutableStateOf(false) }
    var rendered by remember(player) { mutableStateOf(false) }
    var position by remember(player) { mutableLongStateOf(0L) }
    var duration by remember(player) { mutableLongStateOf(media.durationMs) }
    var aspect by remember(player) { mutableFloatStateOf(if (media.width > 0 && media.height > 0) media.width.toFloat() / media.height else 16f / 9f) }
    var seeking by remember(player) { mutableStateOf<Float?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                ended = state == Player.STATE_ENDED
                player?.duration?.takeIf { it > 0 }?.let { duration = it }
            }
            override fun onRenderedFirstFrame() { rendered = true }
            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) aspect = size.width * size.pixelWidthHeightRatio / size.height
            }
        }
        player?.addListener(listener)
        onDispose {
            player?.removeListener(listener)
            player?.release()
        }
    }
    LaunchedEffect(player, playing) {
        val p = player ?: return@LaunchedEffect
        do {
            position = p.currentPosition
            delay(200)
        } while (playing)
        position = p.currentPosition
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player?.pause() }

    DismissBox(enabled = true, aspect = aspect, info = info, onDismiss = onDismiss, onProgress = onDismissProgress) {
        if (shared) TransitionPicture(media, visible = transitioning)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (transitioning) 0f else 1f }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
            contentAlignment = Alignment.Center,
        ) {
            if (player != null) {
                AndroidView(
                    factory = { TextureView(it) },
                    update = { view ->
                        player.setVideoTextureView(view)
                        view.keepScreenOn = playing
                    },
                    onRelease = { player.clearVideoTextureView(it) },
                    modifier = Modifier.aspectRatio(aspect),
                )
            }
            if (!rendered) {
                AsyncImage(
                    model = if (media.id >= 0) Thumb(media) else null,
                    contentDescription = media.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            AnimatedVisibility(visible = chrome || !playing, enter = fadeIn(), exit = fadeOut()) {
                FilledTonalIconButton(
                    onClick = {
                        val p = player ?: return@FilledTonalIconButton
                        when {
                            ended -> { p.seekTo(0); p.play() }
                            playing -> p.pause()
                            else -> p.play()
                        }
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White),
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(80.dp),
                ) {
                    Icon(
                        when {
                            ended -> Icons.Rounded.Replay
                            playing -> Icons.Rounded.Pause
                            else -> Icons.Rounded.PlayArrow
                        },
                        stringResource(if (playing) R.string.pause else R.string.play),
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = chrome && player != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = BottomBarHeight)
                        .padding(horizontal = 16.dp),
                ) {
                    val shown = seeking?.let { (it * duration).toLong() } ?: position
                    TimeText(formatDuration(shown))
                    Slider(
                        value = if (duration > 0) (seeking ?: (position.toFloat() / duration)).coerceIn(0f, 1f) else 0f,
                        onValueChange = { seeking = it },
                        onValueChangeFinished = {
                            seeking?.let { player?.seekTo((it * duration).toLong()) }
                            seeking = null
                        },
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    TimeText(formatDuration(duration))
                }
            }
        }
    }
}

@Composable
private fun TimeText(text: String) {
    Text(text, style = MaterialTheme.typography.labelLargeEmphasized, color = Color.White)
}
