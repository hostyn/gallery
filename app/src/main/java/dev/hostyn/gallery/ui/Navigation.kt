package dev.hostyn.gallery.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.hostyn.gallery.GalleryApp
import dev.hostyn.gallery.data.Collection
import dev.hostyn.gallery.data.Media
import dev.hostyn.gallery.data.itemsOf
import dev.hostyn.gallery.data.mimeMatches
import dev.hostyn.gallery.ui.components.LocalNavAnimatedScope
import dev.hostyn.gallery.ui.components.LocalSharedTransitionScope
import dev.hostyn.gallery.ui.photos.CollectionScreen
import dev.hostyn.gallery.ui.photos.HomeScreen
import dev.hostyn.gallery.ui.settings.SettingsScreen
import dev.hostyn.gallery.ui.theme.LocalTransformOrigin
import dev.hostyn.gallery.ui.theme.TransformOrigin
import dev.hostyn.gallery.ui.theme.backwardEnter
import dev.hostyn.gallery.ui.theme.backwardExit
import dev.hostyn.gallery.ui.theme.forwardEnter
import dev.hostyn.gallery.ui.theme.forwardExit
import dev.hostyn.gallery.ui.theme.transformEnter
import dev.hostyn.gallery.ui.theme.transformExit
import dev.hostyn.gallery.ui.viewer.ViewerScreen

/** Set when another app asked us to choose photos or videos for it. */
class PickConfig(private val filters: List<String>, val multiple: Boolean, val onPicked: (List<Media>) -> Unit) {
    fun accepts(media: Media) = filters.any { mimeMatches(it, media.mimeType) }
}

/** Media access state from [PermissionGate]. */
class Access(val partial: Boolean, val manage: () -> Unit)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavigation(access: Access, pick: PickConfig? = null) {
    val nav = rememberNavController()
    val density = LocalDensity.current
    val origin = remember { TransformOrigin() }
    /** [route], plus the element it grows out of if an album just asked for a container transform. */
    fun withOrigin(route: String): String {
        val key = origin.take() ?: return route
        return "$route?from=${Uri.encode(key)}"
    }
    val openViewer = { c: Collection, id: Long -> nav.navigate("viewer/${c.route()}/$id") }
    val openCollection = { c: Collection -> nav.navigate(withOrigin("collection/${c.route()}")) }
    // A photo grows from its thumbnail into the viewer over the grid, which stays put underneath.
    val isViewer = { entry: NavBackStackEntry -> entry.destination.route?.startsWith("viewer/") == true }
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this, LocalTransformOrigin provides origin) {
            NavHost(
                navController = nav,
                startDestination = "home",
                // M3 forward and backward pattern. An album grows into its screen (container
                // transform), with the screens underneath only fading.
                enterTransition = {
                    when {
                        isViewer(targetState) -> fadeIn(tween(300))
                        targetState.transformKey() != null -> transformEnter()
                        else -> density.forwardEnter()
                    }
                },
                exitTransition = {
                    when {
                        isViewer(targetState) -> ExitTransition.KeepUntilTransitionsFinished
                        targetState.transformKey() != null -> transformExit()
                        else -> density.forwardExit()
                    }
                },
                popEnterTransition = {
                    when {
                        isViewer(initialState) -> EnterTransition.None
                        initialState.transformKey() != null -> transformEnter()
                        else -> density.backwardEnter()
                    }
                },
                popExitTransition = {
                    when {
                        isViewer(initialState) -> fadeOut(tween(300))
                        initialState.transformKey() != null -> transformExit()
                        else -> density.backwardExit()
                    }
                },
            ) {
                screen("home") {
                    HomeScreen(
                        access = access,
                        pick = pick,
                        onOpenCollection = openCollection,
                        onOpenViewer = openViewer,
                        onSettings = { nav.navigate("settings") },
                    )
                }
                screen(
                    "collection/{c}?from={from}",
                    listOf(
                        navArgument("c") { type = NavType.StringType },
                        navArgument("from") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    CollectionScreen(
                        collection = Collection.fromRoute(entry.arguments?.getString("c").orEmpty()),
                        transformKey = entry.transformKey(),
                        pick = pick,
                        onBack = { nav.popBackStack() },
                        onOpenViewer = openViewer,
                    )
                }
                screen(
                    "viewer/{c}/{id}",
                    listOf(navArgument("c") { type = NavType.StringType }, navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val collection = Collection.fromRoute(entry.arguments?.getString("c").orEmpty())
                    val app = GalleryApp.of(LocalContext.current)
                    val media by app.repository.media.collectAsStateWithLifecycle()
                    val trash by app.repository.trash.collectAsStateWithLifecycle()
                    val hidden = app.settings.hiddenFolders
                    val items = remember(media, trash, hidden, collection) { media?.let { itemsOf(collection, it, trash, hidden) } }
                    if (items != null) {
                        ViewerScreen(
                            items = items,
                            startId = entry.arguments?.getLong("id") ?: 0L,
                            inTrash = collection == Collection.Trash,
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
                screen("settings") {
                    SettingsScreen(GalleryApp.of(LocalContext.current).settings, onBack = { nav.popBackStack() })
                }
            }
        }
    }
}

/** The element this screen grew out of, if it was opened with a container transform. */
private fun NavBackStackEntry.transformKey(): String? = arguments?.getString("from")

/** A destination that tells its content about its navigation animation, for shared elements. */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) }
}
