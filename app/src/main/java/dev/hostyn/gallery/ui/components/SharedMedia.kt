package dev.hostyn.gallery.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import dev.hostyn.gallery.data.Media

/** The shared transition around the app's screens, so a photo can grow from its thumbnail into the viewer. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The navigation animation of the screen we are in. */
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks the picture of [media] as the same element on every screen: when the screen changes,
 * it moves and resizes from where it was to where it is now. Does nothing outside navigation.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedMedia(media: Media): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    if (media.id < 0) return this
    return with(shared) { this@sharedMedia.sharedElement(rememberSharedContentState("media-${media.id}"), animated) }
}

/** Whether this screen is in the middle of coming in or going out. */
@Composable
fun inNavTransition(): Boolean {
    val transition = LocalNavAnimatedScope.current?.transition ?: return false
    return transition.currentState != transition.targetState || transition.isRunning
}
