package dev.hostyn.gallery.ui.viewer

import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import dev.hostyn.gallery.data.Media
import dev.hostyn.gallery.data.MotionVideo
import dev.hostyn.gallery.data.extractMotionVideo
import java.io.File

/**
 * Plays the short video of a motion photo once, over the photo, then calls [onEnd]. The photo
 * stays visible until the first frame is ready, so it starts without a flash.
 */
@Composable
fun MotionVideoPlayer(media: Media, video: MotionVideo, onEnd: () -> Unit) {
    val context = LocalContext.current
    val currentOnEnd by rememberUpdatedState(onEnd)
    val file by produceState<File?>(null, media.id, video) {
        value = extractMotionVideo(context, media, video)
        if (value == null) currentOnEnd()
    }
    val source = file ?: return
    val player = remember(source) {
        // A motion photo plays silently, like in the camera: no sound track and no audio focus, so
        // music playing in another app keeps playing.
        ExoPlayer.Builder(context)
            .build()
            .apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
                volume = 0f
                setMediaItem(MediaItem.fromUri(Uri.fromFile(source)))
                prepare()
                playWhenReady = true
            }
    }
    var rendered by remember(player) { mutableStateOf(false) }
    var aspect by remember(player) { mutableFloatStateOf(if (media.width > 0 && media.height > 0) media.width.toFloat() / media.height else 4f / 3f) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) currentOnEnd() }
            override fun onPlayerError(error: PlaybackException) { currentOnEnd() }
            override fun onRenderedFirstFrame() { rendered = true }
            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) aspect = size.width * size.pixelWidthHeightRatio / size.height
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { currentOnEnd() }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { TextureView(it) },
            update = { player.setVideoTextureView(it) },
            onRelease = { player.clearVideoTextureView(it) },
            modifier = Modifier
                .aspectRatio(aspect)
                .graphicsLayer { alpha = if (rendered) 1f else 0f },
        )
    }
}
