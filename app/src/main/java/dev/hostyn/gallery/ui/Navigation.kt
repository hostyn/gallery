package dev.hostyn.gallery.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
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
import dev.hostyn.gallery.ui.photos.CollectionScreen
import dev.hostyn.gallery.ui.photos.HomeScreen
import dev.hostyn.gallery.ui.settings.SettingsScreen
import dev.hostyn.gallery.ui.viewer.ViewerScreen

/** Set when another app asked us to choose photos or videos for it. */
class PickConfig(private val filters: List<String>, val multiple: Boolean, val onPicked: (List<Media>) -> Unit) {
    fun accepts(media: Media) = filters.any { mimeMatches(it, media.mimeType) }
}

/** Media access state from [PermissionGate]. */
class Access(val partial: Boolean, val manage: () -> Unit)

@Composable
fun AppNavigation(access: Access, pick: PickConfig? = null) {
    val nav = rememberNavController()
    val openViewer = { c: Collection, id: Long -> nav.navigate("viewer/${c.route()}/$id") }
    val openCollection = { c: Collection -> nav.navigate("collection/${c.route()}") }
    NavHost(
        navController = nav,
        startDestination = "home",
        enterTransition = { fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(200)) },
    ) {
        composable("home") {
            HomeScreen(
                access = access,
                pick = pick,
                onOpenCollection = openCollection,
                onOpenViewer = openViewer,
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("collection/{c}", arguments = listOf(navArgument("c") { type = NavType.StringType })) { entry ->
            CollectionScreen(
                collection = Collection.fromRoute(entry.arguments?.getString("c").orEmpty()),
                pick = pick,
                onBack = { nav.popBackStack() },
                onOpenViewer = openViewer,
            )
        }
        composable(
            "viewer/{c}/{id}",
            arguments = listOf(navArgument("c") { type = NavType.StringType }, navArgument("id") { type = NavType.LongType }),
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
        composable("settings") {
            SettingsScreen(GalleryApp.of(LocalContext.current).settings, onBack = { nav.popBackStack() })
        }
    }
}
