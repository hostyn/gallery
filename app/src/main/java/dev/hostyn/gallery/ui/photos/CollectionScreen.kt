package dev.hostyn.gallery.ui.photos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hostyn.gallery.GalleryApp
import dev.hostyn.gallery.R
import dev.hostyn.gallery.data.Collection
import dev.hostyn.gallery.data.itemsOf
import dev.hostyn.gallery.ui.PickConfig
import dev.hostyn.gallery.ui.components.EmptyState
import dev.hostyn.gallery.ui.components.GridNote
import dev.hostyn.gallery.ui.components.MediaGrid
import dev.hostyn.gallery.ui.components.SelectionSaver
import dev.hostyn.gallery.ui.components.SelectionTopBar
import dev.hostyn.gallery.ui.components.StatusBarScrim
import dev.hostyn.gallery.ui.rememberMediaActions
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/** An album, favorites, videos or the trash. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    collection: Collection,
    pick: PickConfig?,
    onBack: () -> Unit,
    onOpenViewer: (Collection, Long) -> Unit,
) {
    val media = rememberLibrary(pick)
    val trash by GalleryApp.of(LocalContext.current).repository.trash.collectAsStateWithLifecycle()
    val items = remember(media, trash, collection) { media?.let { itemsOf(collection, it, trash) } }
    val inTrash = collection == Collection.Trash
    var selection by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf(emptySet<Long>()) }
    val snackbar = remember { SnackbarHostState() }
    val actions = rememberMediaActions(snackbar)
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // The folder name, kept once its last photo is gone.
    var folderName by rememberSaveable { mutableStateOf("") }
    if (collection is Collection.Folder) items?.firstOrNull()?.bucketName?.let { folderName = it }
    val title = when (collection) {
        Collection.All -> stringResource(R.string.photos)
        is Collection.Folder -> folderName.ifEmpty { stringResource(R.string.internal_storage) }
        Collection.Favorites -> stringResource(R.string.favorites)
        Collection.Videos -> stringResource(R.string.videos)
        Collection.Trash -> stringResource(R.string.trash)
    }

    LaunchedEffect(items) {
        val ids = items.orEmpty().mapTo(HashSet()) { it.id }
        if (selection.any { it !in ids }) selection = selection.filterTo(HashSet()) { it in ids }
    }
    BackHandler(selection.isNotEmpty()) { selection = emptySet() }
    val selected = remember(items, selection) { items.orEmpty().filter { it.id in selection } }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            if (selection.isNotEmpty()) {
                SelectionTopBar(selection.size, onClear = { selection = emptySet() }) {
                    if (pick == null) SelectionActions(selected, inTrash, actions) { selection = emptySet() }
                }
            } else {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = { BackButton(onBack) },
                    actions = {
                        if (inTrash && !items.isNullOrEmpty()) {
                            TextButton(onClick = { actions.deleteForever(items) }) { Text(stringResource(R.string.empty_trash)) }
                        }
                    },
                    scrollBehavior = scroll,
                    colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surface),
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
            items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            items.isEmpty() -> when (collection) {
                Collection.Trash -> EmptyState(Icons.Rounded.Delete, stringResource(R.string.trash_empty), stringResource(R.string.trash_note), padding)
                Collection.Favorites -> EmptyState(Icons.Rounded.FavoriteBorder, stringResource(R.string.no_favorites), stringResource(R.string.no_favorites_body), padding)
                Collection.Videos -> EmptyState(Icons.Rounded.Videocam, stringResource(R.string.no_videos), null, padding)
                else -> EmptyState(Icons.Rounded.PhotoLibrary, stringResource(R.string.no_photos), null, padding)
            }
            else -> MediaGrid(
                media = items,
                selection = selection,
                onSelectionChange = { selection = it },
                onOpen = { m ->
                    when {
                        pick == null -> onOpenViewer(collection, m.id)
                        pick.multiple -> selection = selection + m.id
                        else -> pick.onPicked(listOf(m))
                    }
                },
                contentPadding = padding,
                groupByDate = !inTrash,
                header = if (inTrash) {
                    { item(key = "note", span = { GridItemSpan(maxLineSpan) }) { GridNote(stringResource(R.string.trash_note)) } }
                } else null,
                badge = if (inTrash) {
                    { m ->
                        val days = ceil((m.expires - System.currentTimeMillis()).toDouble() / TimeUnit.DAYS.toMillis(1)).toInt().coerceAtLeast(0)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                        ) {
                            Text(
                                pluralStringResource(R.plurals.days_left, days, days),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                } else null,
            )
        }
        StatusBarScrim()
    }
}
