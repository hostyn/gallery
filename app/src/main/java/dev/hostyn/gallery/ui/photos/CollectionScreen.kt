@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.hostyn.gallery.ui.photos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
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
import dev.hostyn.gallery.ui.components.Loading
import dev.hostyn.gallery.ui.components.SelectionToolbar
import dev.hostyn.gallery.ui.components.SelectionToolbarSpace
import dev.hostyn.gallery.ui.components.GridNote
import dev.hostyn.gallery.ui.components.MediaGrid
import dev.hostyn.gallery.ui.components.SelectionSaver
import dev.hostyn.gallery.ui.components.SelectionTopBar
import dev.hostyn.gallery.ui.components.StatusBarScrim
import dev.hostyn.gallery.ui.rememberMediaActions
import dev.hostyn.gallery.ui.theme.containerTransform
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/** An album, favorites, videos or the trash. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    collection: Collection,
    pick: PickConfig?,
    transformKey: String? = null,
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
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
    val toolbar = selection.isNotEmpty() && pick == null

    Scaffold(
        // Grows out of the album it was opened from.
        modifier = Modifier
            .containerTransform(transformKey, MaterialTheme.shapes.extraLarge)
            .nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            if (selection.isNotEmpty()) {
                SelectionTopBar(selection.size, onClear = { selection = emptySet() })
            } else {
                // M3 Expressive: the album's name large, with how many items it has; it collapses
                // into a small app bar while scrolling.
                MediumFlexibleTopAppBar(
                    title = { Text(title) },
                    subtitle = items?.let { { Text(pluralStringResource(R.plurals.item_count, it.size, it.size)) } },
                    navigationIcon = { BackButton(onBack) },
                    actions = {
                        if (inTrash && !items.isNullOrEmpty()) {
                            TextButton(onClick = { actions.deleteForever(items) }, shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(R.string.empty_trash))
                            }
                        }
                        if (collection is Collection.Folder && pick == null) {
                            FolderMenu(collection.bucketId, snackbar)
                        }
                    },
                    scrollBehavior = scroll,
                    colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surface),
                )
            }
        },
        floatingActionButton = {
            if (pick?.multiple == true && selection.isNotEmpty()) {
                MediumExtendedFloatingActionButton(
                    onClick = { pick.onPicked(selected) },
                    icon = { Icon(Icons.Rounded.Check, null, Modifier.size(FloatingActionButtonDefaults.MediumIconSize)) },
                    text = { Text(stringResource(R.string.done)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { scaffoldPadding ->
        // The last photos can scroll above the floating toolbar.
        val direction = LocalLayoutDirection.current
        val padding = if (!toolbar) scaffoldPadding else PaddingValues(
            start = scaffoldPadding.calculateStartPadding(direction),
            end = scaffoldPadding.calculateEndPadding(direction),
            top = scaffoldPadding.calculateTopPadding(),
            bottom = scaffoldPadding.calculateBottomPadding() + SelectionToolbarSpace,
        )
        Box(Modifier.fillMaxSize()) {
            when {
                items == null -> Loading()
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
                                shape = MaterialTheme.shapes.small,
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
            SelectionToolbar(visible = toolbar) {
                SelectionActions(selected, inTrash, actions) { selection = emptySet() }
            }
        }
    }
}

/** An album's menu: show or hide its photos in the Photos tab. */
@Composable
private fun FolderMenu(bucketId: Long, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val settings = GalleryApp.of(context).settings
    val scope = rememberCoroutineScope()
    val hidden = bucketId in settings.hiddenFolders
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (hidden) R.string.show_in_photos else R.string.hide_from_photos)) },
                leadingIcon = { Icon(if (hidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, null) },
                onClick = {
                    open = false
                    settings.setFolderHidden(bucketId, !hidden)
                    val message = context.getString(if (hidden) R.string.folder_shown else R.string.folder_hidden)
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar(message)
                    }
                },
            )
        }
    }
}
