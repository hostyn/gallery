package dev.hostyn.gallery.ui.photos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PhotoAlbum
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hostyn.gallery.GalleryApp
import dev.hostyn.gallery.R
import dev.hostyn.gallery.data.Collection
import dev.hostyn.gallery.data.Media
import dev.hostyn.gallery.ui.Access
import dev.hostyn.gallery.ui.MediaActions
import dev.hostyn.gallery.ui.PickConfig
import dev.hostyn.gallery.ui.albums.AlbumsGrid
import dev.hostyn.gallery.ui.components.EmptyState
import dev.hostyn.gallery.ui.components.MediaGrid
import dev.hostyn.gallery.ui.components.PartialAccessBanner
import dev.hostyn.gallery.ui.components.SelectionSaver
import dev.hostyn.gallery.ui.components.SelectionTopBar
import dev.hostyn.gallery.ui.components.StatusBarScrim
import dev.hostyn.gallery.ui.hasSystemTrash
import dev.hostyn.gallery.ui.rememberMediaActions
import dev.hostyn.gallery.ui.share

/** The library, filtered to what the picking app accepts. Null until loaded. */
@Composable
fun rememberLibrary(pick: PickConfig?): List<Media>? {
    val all by GalleryApp.of(LocalContext.current).repository.media.collectAsStateWithLifecycle()
    return remember(all, pick) { all?.let { list -> if (pick == null) list else list.filter(pick::accepts) } }
}

/** Photos and Albums tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    access: Access,
    pick: PickConfig?,
    onOpenCollection: (Collection) -> Unit,
    onOpenViewer: (Collection, Long) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val media = rememberLibrary(pick)
    val trash by GalleryApp.of(context).repository.trash.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selection by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf(emptySet<Long>()) }
    val snackbar = remember { SnackbarHostState() }
    val actions = rememberMediaActions(snackbar)
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // Forget what was deleted elsewhere.
    LaunchedEffect(media) {
        val ids = media.orEmpty().mapTo(HashSet()) { it.id }
        if (selection.any { it !in ids }) selection = selection.filterTo(HashSet()) { it in ids }
    }
    BackHandler(selection.isNotEmpty()) { selection = emptySet() }
    val selected = remember(media, selection) { media.orEmpty().filter { it.id in selection } }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            if (selection.isNotEmpty() && tab == 0) {
                SelectionTopBar(selection.size, onClear = { selection = emptySet() }) {
                    if (pick == null) SelectionActions(selected, inTrash = false, actions) { selection = emptySet() }
                }
            } else {
                TopAppBar(
                    title = {
                        Text(
                            when {
                                pick != null -> pluralStringResource(R.plurals.pick_title, if (pick.multiple) 2 else 1)
                                tab == 0 -> stringResource(R.string.photos)
                                else -> stringResource(R.string.albums)
                            },
                        )
                    },
                    navigationIcon = {
                        if (pick != null) {
                            IconButton(onClick = { pick.onPicked(emptyList()) }) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel)) }
                        }
                    },
                    actions = {
                        if (pick == null) IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings)) }
                    },
                    scrollBehavior = scroll,
                    colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surface),
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(if (tab == 0) Icons.Rounded.Photo else Icons.Outlined.Photo, null) },
                    label = { Text(stringResource(R.string.photos)) },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(if (tab == 1) Icons.Rounded.PhotoAlbum else Icons.Outlined.PhotoAlbum, null) },
                    label = { Text(stringResource(R.string.albums)) },
                )
            }
        },
        floatingActionButton = {
            if (pick?.multiple == true && selection.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { pick.onPicked(selected) },
                    icon = { Icon(Icons.Rounded.Check, null) },
                    text = { Text(stringResource(R.string.done)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            media == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            tab == 0 -> PhotosTab(
                media = media,
                access = access,
                selection = selection,
                onSelectionChange = { selection = it },
                onOpen = { m ->
                    when {
                        pick == null -> onOpenViewer(Collection.All, m.id)
                        pick.multiple -> selection = selection + m.id
                        else -> pick.onPicked(listOf(m))
                    }
                },
                padding = padding,
            )
            else -> AlbumsGrid(
                media = media,
                trashCount = if (pick == null) trash.size else null,
                onOpen = onOpenCollection,
                contentPadding = padding,
            )
        }
        StatusBarScrim()
    }
}

@Composable
private fun PhotosTab(
    media: List<Media>,
    access: Access,
    selection: Set<Long>,
    onSelectionChange: (Set<Long>) -> Unit,
    onOpen: (Media) -> Unit,
    padding: PaddingValues,
) {
    if (media.isEmpty() && !access.partial) {
        EmptyState(Icons.Rounded.PhotoLibrary, stringResource(R.string.no_photos), stringResource(R.string.no_photos_body), padding)
        return
    }
    MediaGrid(
        media = media,
        selection = selection,
        onSelectionChange = onSelectionChange,
        onOpen = onOpen,
        contentPadding = padding,
        header = if (access.partial) {
            { item(key = "partial", span = { GridItemSpan(maxLineSpan) }) { PartialAccessBanner(access.manage) } }
        } else null,
    )
}

/** Actions for the selected items, in the selection bar. */
@Composable
fun RowScope.SelectionActions(selected: List<Media>, inTrash: Boolean, actions: MediaActions, onDone: () -> Unit) {
    val context = LocalContext.current
    if (inTrash) {
        IconButton(onClick = { actions.restore(selected, onDone) }) { Icon(Icons.Rounded.RestoreFromTrash, stringResource(R.string.restore)) }
        IconButton(onClick = { actions.deleteForever(selected, onDone) }) { Icon(Icons.Rounded.DeleteForever, stringResource(R.string.delete_forever)) }
        return
    }
    IconButton(onClick = { share(context, selected) }) { Icon(Icons.Rounded.Share, stringResource(R.string.share)) }
    if (hasSystemTrash) {
        val allFavorite = selected.isNotEmpty() && selected.all { it.isFavorite }
        IconButton(onClick = { actions.setFavorite(selected, !allFavorite, onDone) }) {
            Icon(
                if (allFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                stringResource(if (allFavorite) R.string.unfavorite else R.string.favorite),
            )
        }
    }
    IconButton(onClick = { actions.moveToTrash(selected, onDone) }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete)) }
}

/** Back arrow for screens below the tabs. */
@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
}
