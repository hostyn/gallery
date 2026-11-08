package dev.hostyn.gallery.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.hostyn.gallery.GalleryApp
import dev.hostyn.gallery.data.Media
import dev.hostyn.gallery.data.Section
import dev.hostyn.gallery.data.sectionsOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** What each slot of the grid holds: a date header or a photo (by its index in the list). */
private sealed interface Entry {
    data class Header(val section: Section) : Entry
    data class Item(val index: Int) : Entry
}

/**
 * Photos in a square grid, by day (or month when dense). Pinch to change the size, long-press
 * and drag to select, and drag the handle on the right to jump through the years.
 */
@Composable
fun MediaGrid(
    media: List<Media>,
    selection: Set<Long>,
    onSelectionChange: (Set<Long>) -> Unit,
    onOpen: (Media) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(),
    groupByDate: Boolean = true,
    selectable: Boolean = true,
    badge: (@Composable BoxScope.(Media) -> Unit)? = null,
    header: (LazyGridScope.() -> Unit)? = null,
) {
    val settings = GalleryApp.of(LocalContext.current).settings
    val columns = settings.columns
    val byMonth = columns >= 5
    val entries = remember(media, byMonth, groupByDate) {
        if (!groupByDate) {
            media.indices.map<Int, Entry> { Entry.Item(it) }
        } else {
            buildList<Entry> {
                for (s in sectionsOf(media.map { it.date }, byMonth, ZoneId.systemDefault(), LocalDate.now())) {
                    add(Entry.Header(s))
                    for (i in s.start until s.end) add(Entry.Item(i))
                }
            }
        }
    }
    val indexById = remember(media) { HashMap<Long, Int>(media.size).also { m -> media.forEachIndexed { i, it -> m[it.id] = i } } }
    val selecting = selection.isNotEmpty()

    // Keep the same photo at the top when the number of columns changes.
    var anchor by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(entries) {
        val id = anchor ?: return@LaunchedEffect
        anchor = null
        val offset = state.layoutInfo.totalItemsCount - entries.size
        val slot = entries.indexOfFirst { it is Entry.Item && media[it.index].id == id }
        if (slot >= 0) state.scrollToItem(slot + offset.coerceAtLeast(0))
    }

    val autoScroll = remember { mutableFloatStateOf(0f) }
    // While dragging to select, the finger selects instead of scrolling (the edges still scroll).
    var dragSelecting by remember { mutableStateOf(false) }
    LaunchedEffect(autoScroll.floatValue) {
        val speed = autoScroll.floatValue
        if (speed == 0f) return@LaunchedEffect
        while (isActive) {
            state.scrollBy(speed)
            delay(16)
        }
    }

    val currentMedia by rememberUpdatedState(media)
    val currentIndex by rememberUpdatedState(indexById)
    val currentSelection by rememberUpdatedState(selection)
    val currentOnChange by rememberUpdatedState(onSelectionChange)
    val currentColumns by rememberUpdatedState(columns)
    // Taps on photos: open, or toggle while selecting.
    val currentClick by rememberUpdatedState { item: Media ->
        if (selection.isNotEmpty()) {
            onSelectionChange(if (item.id in selection) selection - item.id else selection + item.id)
        } else {
            onOpen(item)
        }
    }
    val haptics = LocalHapticFeedback.current
    val edge = with(LocalDensity.current) { 72.dp.toPx() }

    fun mediaIndexAt(position: Offset): Int? {
        val info = state.layoutInfo
        // Item offsets start after the top content padding (viewportStartOffset is minus that padding).
        val y = position.y.roundToInt() + info.viewportStartOffset
        val x = position.x.roundToInt()
        val item = info.visibleItemsInfo.firstOrNull {
            x >= it.offset.x && x < it.offset.x + it.size.width && y >= it.offset.y && y < it.offset.y + it.size.height
        } ?: return null
        return (item.key as? Long)?.let { currentIndex[it] }
    }

    fun firstVisibleId(): Long? =
        state.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { (it.key as? Long)?.takeIf { _ -> it.offset.y >= 0 } }
            ?: state.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { it.key as? Long }

    Box(modifier) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            contentPadding = contentPadding,
            userScrollEnabled = !dragSelecting,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Pinch: fewer, bigger photos when spreading the fingers, more when pinching.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var zoom = 1f
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                zoom *= event.calculateZoom()
                                event.changes.forEach { it.consume() }
                                val step = when {
                                    zoom > 1.35f -> -1
                                    zoom < 0.74f -> 1
                                    else -> 0
                                }
                                if (step != 0) {
                                    zoom = 1f
                                    val next = (currentColumns + step).coerceIn(2, 6)
                                    if (next != currentColumns) {
                                        anchor = firstVisibleId()
                                        settings.updateColumns(next)
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                // Taps are handled here rather than on each photo, so that releasing a long press
                // (which starts selecting) doesn't also count as a tap that opens the photo.
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {},
                        onTap = { position -> mediaIndexAt(position)?.let { currentClick(currentMedia[it]) } },
                    )
                }
                .then(
                    if (!selectable) Modifier else Modifier.pointerInput(Unit) {
                        var first: Int? = null
                        var last: Int? = null
                        var base = emptySet<Long>()
                        var adding = true
                        fun apply(to: Int) {
                            val from = first ?: return
                            val ids = (minOf(from, to)..maxOf(from, to)).map { currentMedia[it].id }
                            currentOnChange(if (adding) base + ids else base - ids.toSet())
                        }
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                val index = mediaIndexAt(offset) ?: return@detectDragGesturesAfterLongPress
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragSelecting = true
                                first = index
                                last = index
                                base = currentSelection
                                adding = currentMedia[index].id !in base
                                apply(index)
                            },
                            onDragEnd = { first = null; dragSelecting = false; autoScroll.floatValue = 0f },
                            onDragCancel = { first = null; dragSelecting = false; autoScroll.floatValue = 0f },
                            onDrag = { change, _ ->
                                if (first == null) return@detectDragGesturesAfterLongPress
                                val y = change.position.y
                                autoScroll.floatValue = when {
                                    y > size.height - edge -> (y - (size.height - edge)) / 4f
                                    y < edge -> -(edge - y) / 4f
                                    else -> 0f
                                }
                                val index = mediaIndexAt(change.position)
                                if (index != null && index != last) {
                                    last = index
                                    apply(index)
                                }
                            },
                        )
                    },
                ),
        ) {
            header?.invoke(this)
            for (entry in entries) {
                when (entry) {
                    is Entry.Header -> item(key = "header-${entry.section.date}", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                        val ids = remember(entry.section, media) { (entry.section.start until entry.section.end).map { media[it].id } }
                        SectionHeader(
                            label = sectionLabel(entry.section),
                            selection = if (selecting && selectable) ids.all { it in selection } else null,
                            onToggle = {
                                onSelectionChange(if (ids.all { it in selection }) selection - ids.toSet() else selection + ids)
                            },
                        )
                    }
                    is Entry.Item -> {
                        val item = media[entry.index]
                        item(key = item.id, contentType = "media") {
                            MediaCell(
                                media = item,
                                selected = if (selecting) item.id in selection else null,
                                onClick = { currentClick(item) },
                                badge = badge?.let { b -> { b(item) } },
                            )
                        }
                    }
                }
            }
        }
        run {
            // Slots added by [header] come before ours.
            FastScroller(
                state = state,
                labelAt = { slot ->
                    val offset = state.layoutInfo.totalItemsCount - entries.size
                    when (val e = entries.getOrNull((slot - offset).coerceIn(0, (entries.size - 1).coerceAtLeast(0)))) {
                        is Entry.Header -> media[e.section.start].date
                        is Entry.Item -> media[e.index].date
                        null -> null
                    }
                },
                modifier = Modifier.padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()),
            )
        }
    }
}

@Composable
private fun SectionHeader(label: String, selection: Boolean?, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selection != null) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(start = 16.dp, end = 12.dp, top = 20.dp, bottom = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (selection != null) {
            Icon(
                if (selection) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                null,
                tint = if (selection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** A handle on the right edge that appears while scrolling; drag it to move fast, with the month shown beside it. */
@Composable
private fun BoxScope.FastScroller(state: LazyGridState, labelAt: (Int) -> Long?, modifier: Modifier = Modifier) {
    val total = state.layoutInfo.totalItemsCount
    val visibleCount = state.layoutInfo.visibleItemsInfo.size
    if (total < visibleCount * 3 || visibleCount == 0) return

    var dragging by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(state.isScrollInProgress, dragging) {
        if (state.isScrollInProgress || dragging) shown = true else {
            delay(1500)
            shown = false
        }
    }
    val scope = rememberCoroutineScope()
    val thumbHeight = 52.dp

    BoxWithConstraints(modifier.align(Alignment.TopEnd).fillMaxSize()) {
        val density = LocalDensity.current
        val track = with(density) { (maxHeight - thumbHeight).toPx() }.coerceAtLeast(1f)
        val scrollable = (total - visibleCount).coerceAtLeast(1)
        val progress = (state.firstVisibleItemIndex.toFloat() / scrollable).coerceIn(0f, 1f)
        var dragY by remember { mutableFloatStateOf(0f) }
        val currentTrack by rememberUpdatedState(track)
        val currentScrollable by rememberUpdatedState(scrollable)
        val currentProgress by rememberUpdatedState(progress)
        val y = if (dragging) dragY else progress * track

        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).offset { IntOffset(0, y.roundToInt()) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dragging) {
                    val ms = labelAt((y / track * scrollable).roundToInt())
                    if (ms != null) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shadowElevation = 2.dp,
                        ) {
                            Text(
                                monthLabel(ms),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                }
                Surface(
                    shape = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(width = 32.dp, height = thumbHeight)
                        .clip(RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp))
                        .pointerInput(Unit) {
                            // Keyed on Unit: the sizes change while scrolling, and restarting would drop the drag.
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragY = currentProgress * currentTrack
                                    dragging = true
                                },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                                onVerticalDrag = { change, delta ->
                                    change.consume()
                                    dragY = (dragY + delta).coerceIn(0f, currentTrack)
                                    val target = (dragY / currentTrack * currentScrollable).roundToInt()
                                    scope.launch { state.scrollToItem(target) }
                                },
                            )
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.UnfoldMore, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
