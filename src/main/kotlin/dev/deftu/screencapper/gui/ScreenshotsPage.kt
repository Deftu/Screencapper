@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.gui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.deftu.screencapper.ScreenshotActions
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.metadata.MetadataIndex
import dev.deftu.screencapper.metadata.MetadataKeys
import dev.deftu.screencapper.metadata.SearchEntry
import dev.deftu.screencapper.metadata.SearchQuery
import dev.deftu.screencapper.screenshot.Screenshot
import dev.deftu.screencapper.screenshot.ScreenshotLibrary
import dev.deftu.screencapper.screenshot.UploadHistory
import dev.deftu.screencapper.screenshot.UploadRecord
import org.polyfrost.oneconfig.api.platform.v1.DesktopHelper
import java.net.URI
import dev.deftu.screencapper.upload.UploaderProfile
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.Tooltip
import org.polyfrost.oneconfig.api.ui.v1.components.onClick
import org.polyfrost.oneconfig.api.ui.v1.components.rememberInteractionSource
import org.polyfrost.oneconfig.api.ui.v1.page.OneConfigPage
import org.polyfrost.oneconfig.api.ui.v1.page.PageGroup
import org.polyfrost.oneconfig.api.ui.v1.page.PageText
import org.polyfrost.oneconfig.api.ui.v1.page.currentPageSearchQuery
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.utils.v1.ClipboardHelper
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val THUMBNAIL_WIDTH = 480
private const val VIEWER_WIDTH = 1600

object ScreenshotsPage : OneConfigPage(
    "screencapper:screenshots",
    PageText.translatable("screencapper.page.screenshots", "Screenshots"),
    "/assets/screencapper/screencapper.svg",
    PageGroup.MODS_AND_OPTIONS,
) {
    override val order: Int get() = 400

    override val searchPlaceholder: PageText = PageText.translatable("screencapper.page.screenshots.search", "Search screenshots...")

    internal var pendingFocus by mutableStateOf<String?>(null)

    fun focus(screenshot: Screenshot?) {
        pendingFocus = screenshot?.name
    }

    @Composable
    override fun Content() = ScreenshotManager()
}

private enum class SortMode(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    LARGEST("Largest"),
    NAME("Name"),
}

private enum class GridSize(val minWidth: Int, val icon: String, val label: String) {
    LARGE(320, "maximise", "Large tiles"),
    SMALL(200, "dots-grid", "Small tiles"),
}

@Composable
private fun ScreenshotManager() {
    val revision = ScreenshotLibrary.revision
    var sort by remember { mutableStateOf(SortMode.NEWEST) }
    var gridSize by remember { mutableStateOf(GridSize.LARGE) }
    var viewing by remember { mutableStateOf<String?>(null) }
    val selection = remember { mutableStateListOf<String>() }
    val query = currentPageSearchQuery.trim()

    val gridState = rememberLazyGridState()
    // Re-lists the folder every few seconds to catch files added outside the game, but only publishes a new list
    // when something actually changed so the grid doesn't recompose on every poll.
    val listed by produceState<List<Screenshot>?>(null, revision) {
        while (true) {
            val fresh = withContext(Dispatchers.IO) { ScreenshotLibrary.list() }
            if (fresh != value) value = fresh
            delay(4000)
        }
    }
    val all = listed.orEmpty()
    val indexRevision = MetadataIndex.revision
    val search = remember(query) { SearchQuery.parse(query) }
    // Filtering reads the index, so it runs off the render thread; typing cancels the previous run.
    var filtered by remember { mutableStateOf<List<Screenshot>?>(null) }
    LaunchedEffect(all, sort, search, indexRevision) {
        filtered = withContext(Dispatchers.Default) { filterAndSort(all, sort, search) }
    }
    val shown = filtered.orEmpty()
    LaunchedEffect(all) {
        if (all.isNotEmpty()) MetadataIndex.backfill(all.map { it.file })
    }

    LaunchedEffect(ScreenshotsPage.pendingFocus) {
        ScreenshotsPage.pendingFocus?.let {
            viewing = it
            ScreenshotsPage.pendingFocus = null
        }
    }
    LaunchedEffect(all) { selection.retainAll(all.map { it.name }.toSet()) }

    val viewed = viewing?.let { name -> shown.firstOrNull { it.name == name } ?: all.firstOrNull { it.name == name } }
    if (viewed != null) {
        Viewer(viewed, if (viewed in shown) shown else all, onNavigate = { viewing = it.name }, onClose = { viewing = null })
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 12.dp)) {
            Toolbar(all, shown.size, sort, { sort = it }, gridSize, { gridSize = it })
            Spacer(Modifier.height(16.dp))
            if (listed == null || filtered == null) {
                Spacer(Modifier.weight(1f))
            } else if (shown.isEmpty()) {
                EmptyState(all.isEmpty())
            } else {
                ScreenshotGrid(
                    state = gridState,
                    screenshots = shown,
                    sort = sort,
                    gridSize = gridSize,
                    selection = selection,
                    onOpen = { screenshot ->
                        if (selection.isNotEmpty()) toggle(selection, screenshot.name) else viewing = screenshot.name
                    },
                    onSelect = { toggle(selection, it.name) },
                )
            }
        }

        AnimatedVisibility(
            visible = selection.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            // Keeps showing the last selection while the bar animates out after it's cleared.
            val selected = selection.toList().mapNotNull { name -> all.firstOrNull { it.name == name } }
            var lastSelected by remember { mutableStateOf(selected) }
            if (selected.isNotEmpty()) lastSelected = selected
            SelectionBar(lastSelected, onClear = { selection.clear() })
        }
    }
}

private fun filterAndSort(all: List<Screenshot>, sort: SortMode, search: SearchQuery): List<Screenshot> {
    val filtered = if (search.isEmpty) all else all.filter { screenshot ->
        val fields = MetadataIndex.fields(screenshot.name).orEmpty()
        search.matches(SearchEntry(screenshot.name, fields, takenOn(screenshot, fields)))
    }
    return when (sort) {
        SortMode.NEWEST -> filtered
        SortMode.OLDEST -> filtered.reversed()
        SortMode.LARGEST -> filtered.sortedByDescending { it.size }
        SortMode.NAME -> filtered.sortedBy { it.name.lowercase() }
    }
}

private fun takenOn(screenshot: Screenshot, fields: Map<String, String>): LocalDate =
    fields[MetadataKeys.TIME]?.let { runCatching { OffsetDateTime.parse(it).toLocalDate() }.getOrNull() }
        ?: Instant.ofEpochMilli(screenshot.modified).atZone(ZONE).toLocalDate()

private fun toggle(selection: MutableList<String>, name: String) {
    if (!selection.remove(name)) selection.add(name)
}

@Composable
private fun Toolbar(
    all: List<Screenshot>,
    shownCount: Int,
    sort: SortMode,
    onSort: (SortMode) -> Unit,
    gridSize: GridSize,
    onGridSize: (GridSize) -> Unit,
) {
    val theme = OneConfigTheme.current
    val revision = ScreenshotLibrary.revision
    val totalBytes = remember(all) { all.sumOf { it.size } }
    val uploadedCount = remember(all, revision) { all.count { UploadHistory.latest(it) != null } }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (mode in SortMode.entries) Chip(mode.label, sort == mode) { onSort(mode) }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(countLabel(shownCount, all.size), Modifier, theme.textColor, 12.sp, FontWeight.Medium)
            Text(
                listOfNotNull(sizeLabel(totalBytes), uploadedCount.takeIf { it > 0 }?.let { "$it uploaded" }).joinToString(" · "),
                Modifier, theme.textColorSecondary, 11.sp,
            )
        }
        Spacer(Modifier.width(4.dp))
        for (size in GridSize.entries) {
            IconAction(size.icon, size.label, large = true, active = gridSize == size) { onGridSize(size) }
        }
        IconAction("folder", "Open screenshots folder", large = true) { ScreenshotActions.openFolder() }
    }
}

@Composable
private fun ScreenshotGrid(
    state: LazyGridState,
    screenshots: List<Screenshot>,
    sort: SortMode,
    gridSize: GridSize,
    selection: List<String>,
    onOpen: (Screenshot) -> Unit,
    onSelect: (Screenshot) -> Unit,
) {
    val groups = remember(screenshots, sort) {
        if (sort == SortMode.NEWEST || sort == SortMode.OLDEST) screenshots.groupBy { dayLabel(it.modified) }.toList()
        else listOf("" to screenshots)
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(gridSize.minWidth.dp),
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for ((label, items) in groups) {
            if (label.isNotEmpty()) {
                item(key = "header:$label", span = { GridItemSpan(maxLineSpan) }) {
                    GroupHeading(label, items.size)
                }
            }
            items(items, key = { it.name }) { screenshot ->
                ScreenshotTile(
                    screenshot = screenshot,
                    selected = screenshot.name in selection,
                    selecting = selection.isNotEmpty(),
                    onOpen = { onOpen(screenshot) },
                    onSelect = { onSelect(screenshot) },
                )
            }
        }
    }
}

@Composable
private fun GroupHeading(label: String, count: Int) {
    val theme = OneConfigTheme.current
    Row(Modifier.padding(top = 8.dp, start = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label.uppercase(Locale.ROOT), Modifier, theme.textColorSecondary, 11.sp, FontWeight.Medium)
        Text("$count", Modifier, theme.textColorSecondary.copy(alpha = 0.6f), 11.sp, FontWeight.Medium)
    }
}

@Composable
private fun ScreenshotTile(screenshot: Screenshot, selected: Boolean, selecting: Boolean, onOpen: () -> Unit, onSelect: () -> Unit) {
    val theme = OneConfigTheme.current
    val interaction = rememberInteractionSource()
    val hovered by interaction.collectIsHoveredAsState()
    val revision = ScreenshotLibrary.revision
    val uploading = remember(revision, screenshot.name) { ScreenshotActions.isUploading(screenshot) }
    val uploaded = remember(revision, screenshot.name) { UploadHistory.latest(screenshot) != null }
    val metadata = remember(MetadataIndex.revision, screenshot.name) { MetadataIndex.peek(screenshot.name) }
    val overlay by animateFloatAsState(if (hovered || selected) 1f else 0f)
    val border by animateColorAsState(
        when {
            selected -> OneConfigTheme.accent
            hovered -> theme.textColorSecondary.copy(alpha = 0.6f)
            else -> theme.borderColor
        },
    )

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(theme.modCardShape)
            .background(theme.modCardBackground)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .onClick(interaction, onOpen),
    ) {
        ScreenshotImage(screenshot, THUMBNAIL_WIDTH, Modifier.fillMaxSize(), ContentScale.Crop)

        Box(
            Modifier
                .fillMaxSize()
                .alpha(overlay)
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))),
        )

        Column(Modifier.align(Alignment.BottomStart).padding(12.dp).alpha(overlay), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(screenshot.file.nameWithoutExtension, Modifier, Color.White, 12.sp, null, BaselineShift.None, FontWeight.Medium, TextAlign.Start, 1, TextOverflow.Ellipsis)
            Text("${timeLabel(screenshot.modified)} · ${sizeLabel(screenshot.size)}", Modifier, Color.White.copy(alpha = 0.7f), 11.sp)
        }

        if (!hovered) {
            MetadataTags(metadata, Modifier.align(Alignment.TopEnd).padding(8.dp))
        }

        if (hovered && !selecting) {
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconAction("copy", "Copy", style = IconActionStyle.OVERLAY) { ScreenshotActions.copy(screenshot) }
                IconAction("cloud", "Upload to ${ScreencapperConfig.defaultProfile.name}", style = IconActionStyle.OVERLAY) { ScreenshotActions.upload(screenshot) }
                var armed by remember(screenshot.name) { mutableStateOf(false) }
                LaunchedEffect(armed) {
                    if (armed) {
                        delay(3000)
                        armed = false
                    }
                }
                IconAction(if (armed) "tick" else "trash", if (armed) "Click again to delete" else "Delete", style = IconActionStyle.OVERLAY, destructive = true, active = armed) {
                    if (armed) ScreenshotActions.delete(screenshot) else armed = true
                }
            }
        }

        if (hovered || selecting) {
            SelectDot(selected, Modifier.align(Alignment.TopStart).padding(8.dp), onSelect)
        } else if (uploading || uploaded) {
            StatusTag(if (uploading) "UPLOADING" else "UPLOADED", Modifier.align(Alignment.TopStart).padding(8.dp))
        }

        Box(Modifier.fillMaxSize().border(if (selected) 2.dp else 1.dp, border, theme.modCardShape))
    }
}

@Composable
private fun SelectDot(selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    val interaction = rememberInteractionSource()
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(if (selected) OneConfigTheme.accent else Color.Black.copy(alpha = 0.4f))
    val ring by animateColorAsState(if (selected) OneConfigTheme.accent else Color.White.copy(alpha = if (hovered) 1f else 0.7f))
    Box(
        modifier
            .size(22.dp)
            .background(fill, theme.circleShape)
            .border(1.5.dp, ring, theme.circleShape)
            .hoverable(interaction)
            .onClick(interaction, onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon("tick", Color.White, Modifier.size(14.dp))
    }
}

@Composable
private fun SelectionBar(selected: List<Screenshot>, onClear: () -> Unit) {
    val theme = OneConfigTheme.current
    var confirmDelete by remember(selected.map { it.name }.toSet()) { mutableStateOf(false) }
    LaunchedEffect(confirmDelete) {
        if (confirmDelete) {
            delay(3000)
            confirmDelete = false
        }
    }
    Row(
        Modifier
            .background(theme.popupBackground, theme.popupShape)
            .border(1.dp, theme.borderColor, theme.popupShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (selected.size == 1) "1 selected" else "${selected.size} selected", Modifier.padding(end = 8.dp), theme.textColor, 14.sp, FontWeight.Medium)
        if (selected.size == 1) SmallButton("Copy", "copy") { ScreenshotActions.copy(selected.first()) }
        SmallButton("Upload", "cloud", hierarchy = ButtonHierarchy.PRIMARY) { selected.forEach { ScreenshotActions.upload(it) } }
        SmallButton(
            if (confirmDelete) "Delete ${selected.size}?" else "Delete",
            "trash",
            hierarchy = if (confirmDelete) ButtonHierarchy.PRIMARY else ButtonHierarchy.SECONDARY,
            destructive = true,
        ) {
            if (confirmDelete) {
                ScreenshotActions.deleteAll(selected)
                onClear()
            } else {
                confirmDelete = true
            }
        }
        IconAction("close", "Clear selection") { onClear() }
    }
}

@Composable
private fun Viewer(screenshot: Screenshot, list: List<Screenshot>, onNavigate: (Screenshot) -> Unit, onClose: () -> Unit) {
    val theme = OneConfigTheme.current
    val index = list.indexOfFirst { it.name == screenshot.name }
    val previous = list.getOrNull(index - 1)
    val next = list.getOrNull(index + 1)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Row(
        Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> previous?.let(onNavigate) != null
                    Key.DirectionRight -> next?.let(onNavigate) != null
                    Key.Escape, Key.Backspace -> { onClose(); true }
                    else -> false
                }
            },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("All screenshots", "left-arrow") { onClose() }
                Spacer(Modifier.weight(1f))
                if (index >= 0) Text("${index + 1} of ${list.size}", Modifier, theme.textColorSecondary, 12.sp)
            }
            val aspect by produceState(Thumbnails.peek(screenshot, VIEWER_WIDTH)?.let { it.width.toFloat() / it.height } ?: 16f / 9f, screenshot) {
                withContext(Dispatchers.IO) { imageAspect(screenshot) }?.let { value = it }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                // The frame hugs the screenshot, so there's no letterboxing inside it.
                val frameWidth = minOf(maxWidth, maxHeight * aspect)
                Box(
                    Modifier
                        .size(frameWidth, frameWidth / aspect)
                        .clip(theme.modCardShape)
                        .background(theme.componentBackground)
                        .border(1.dp, theme.borderColor, theme.modCardShape),
                ) {
                    ScreenshotImage(screenshot, VIEWER_WIDTH, Modifier.fillMaxSize(), ContentScale.Crop, background = false)
                }
                if (previous != null) NavButton("left-arrow", Modifier.align(Alignment.CenterStart).padding(12.dp)) { onNavigate(previous) }
                if (next != null) NavButton("right-arrow", Modifier.align(Alignment.CenterEnd).padding(12.dp)) { onNavigate(next) }
            }
            FilmStrip(list, screenshot, onNavigate)
        }
        DetailsPanel(screenshot, onDeleted = {
            val fallback = next ?: previous
            if (fallback != null) onNavigate(fallback) else onClose()
        })
    }
}

@Composable
private fun NavButton(icon: String, modifier: Modifier, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    val interaction = rememberInteractionSource()
    val hovered by interaction.collectIsHoveredAsState()
    val fill = Color.Black.copy(alpha = 0.45f)
    Box(
        modifier
            .size(40.dp)
            .background(fill, theme.circleShape)
            .border(1.dp, Color.White.copy(alpha = if (hovered) 0.4f else 0.15f), theme.circleShape)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .onClick(interaction, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, Color.White, Modifier.size(18.dp))
    }
}

@Composable
private fun FilmStrip(list: List<Screenshot>, current: Screenshot, onNavigate: (Screenshot) -> Unit) {
    val index = list.indexOfFirst { it.name == current.name }.coerceAtLeast(0)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Fixed pages of whole tiles; a short last page is centred, and the arrows never move.
        val usable = maxWidth - (FILM_ARROW + FILM_GAP) * 2
        val count = ((usable + FILM_GAP) / (FILM_MIN_WIDTH + FILM_GAP)).toInt().coerceIn(1, 9)
        val tileWidth = (usable - FILM_GAP * (count - 1)) / count
        val tileHeight = tileWidth * 9f / 16f
        val pages = (list.size + count - 1) / count
        var page by remember { mutableStateOf(index / count) }

        LaunchedEffect(current.name, count) { page = index / count }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FILM_GAP)) {
            IconAction("left-arrow", "Earlier screenshots", enabled = page > 0) { page = (page - 1).coerceAtLeast(0) }
            AnimatedContent(
                targetState = page.coerceIn(0, (pages - 1).coerceAtLeast(0)),
                modifier = Modifier.size(usable, tileHeight).clipToBounds(),
                transitionSpec = {
                    val forward = targetState > initialState
                    slideInHorizontally { if (forward) it else -it } togetherWith slideOutHorizontally { if (forward) -it else it }
                },
            ) { shown ->
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(FILM_GAP, Alignment.CenterHorizontally)) {
                    for (item in list.subList(shown * count, minOf(list.size, (shown + 1) * count))) {
                        key(item.name) {
                            FilmTile(item, item.name == current.name, Modifier.size(tileWidth, tileHeight)) { onNavigate(item) }
                        }
                    }
                }
            }
            IconAction("right-arrow", "Later screenshots", enabled = page < pages - 1) { page = (page + 1).coerceAtMost(pages - 1) }
        }
    }
}

@Composable
private fun FilmTile(item: Screenshot, isCurrent: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    val interaction = rememberInteractionSource()
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier
            .clip(theme.buttonShape)
            .alpha(if (isCurrent || hovered) 1f else 0.55f)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .onClick(interaction) { onClick() },
    ) {
        ScreenshotImage(item, THUMBNAIL_WIDTH, Modifier.fillMaxSize(), ContentScale.Crop)
        Box(Modifier.fillMaxSize().border(if (isCurrent) 2.dp else 1.dp, if (isCurrent) OneConfigTheme.accent else theme.borderColor, theme.buttonShape))
    }
}

/** The screenshot's width over height, read from the file header without decoding it */
private fun imageAspect(screenshot: Screenshot): Float? = runCatching {
    javax.imageio.ImageIO.createImageInputStream(screenshot.file).use { stream ->
        val reader = javax.imageio.ImageIO.getImageReaders(stream ?: return null).asSequence().firstOrNull() ?: return null
        try {
            reader.input = stream
            reader.getWidth(0).toFloat() / reader.getHeight(0)
        } finally {
            reader.dispose()
        }
    }
}.getOrNull()?.takeIf { it.isFinite() && it > 0f }

private val FILM_MIN_WIDTH = 104.dp
private val FILM_ARROW = 28.dp
private val FILM_GAP = 6.dp

@Composable
private fun DetailsPanel(screenshot: Screenshot, onDeleted: () -> Unit) {
    val theme = OneConfigTheme.current
    val revision = ScreenshotLibrary.revision
    val links = remember(revision, screenshot.name) { UploadHistory.of(screenshot) }
    val uploading = remember(revision, screenshot.name) { ScreenshotActions.isUploading(screenshot) }
    val profiles = remember(revision) { ScreencapperConfig.profiles }
    val defaultProfile = remember(revision) { ScreencapperConfig.defaultProfile }
    val metadata = remember(MetadataIndex.revision, screenshot.name) { MetadataIndex.peek(screenshot.name) }
    var confirmDelete by remember(screenshot.name) { mutableStateOf(false) }
    val thumbnail by produceState(Thumbnails.peek(screenshot, VIEWER_WIDTH), screenshot) {
        value = Thumbnails.peek(screenshot, VIEWER_WIDTH)
        value = Thumbnails.load(screenshot, VIEWER_WIDTH)
    }
    LaunchedEffect(confirmDelete) {
        if (confirmDelete) {
            delay(3000)
            confirmDelete = false
        }
    }

    Column(
        Modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(theme.modCardBackground, theme.modCardShape)
            .border(1.dp, theme.borderColor, theme.modCardShape)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(screenshot.file.nameWithoutExtension, Modifier, theme.textColor, 16.sp, null, BaselineShift.None, FontWeight.Medium, TextAlign.Start, 2, TextOverflow.Ellipsis)
            Text(fullTimeLabel(screenshot.modified), Modifier, theme.textColorSecondary, 12.sp)
            MetadataTags(metadata, Modifier.padding(top = 4.dp), overImage = false)
        }

        Section("Actions") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallButton("Copy", "copy", Modifier.weight(1f), hierarchy = ButtonHierarchy.PRIMARY) { ScreenshotActions.copy(screenshot) }
                    SmallButton("Open", "external-link", Modifier.weight(1f)) { ScreenshotActions.open(screenshot) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallButton("Folder", "folder", Modifier.weight(1f)) { ScreenshotActions.openFolder() }
                    SmallButton(
                        if (confirmDelete) "Confirm" else "Delete",
                        "trash",
                        Modifier.weight(1f),
                        hierarchy = if (confirmDelete) ButtonHierarchy.PRIMARY else ButtonHierarchy.SECONDARY,
                        destructive = true,
                    ) {
                        if (confirmDelete) {
                            if (ScreenshotActions.delete(screenshot)) onDeleted()
                        } else {
                            confirmDelete = true
                        }
                    }
                }
            }
        }

        Section("Upload") {
            UploadSplitButton(profiles, defaultProfile, uploading, again = links.isNotEmpty()) { ScreenshotActions.upload(screenshot, it) }
            if (links.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (link in links) LinkRow(screenshot, link)
                }
            }
        }

        Section("Details") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("Size", sizeLabel(screenshot.size))
                thumbnail?.let { InfoRow("Resolution", "${it.width} × ${it.height}") }
                InfoRow("Format", screenshot.file.extension.uppercase(Locale.ROOT))
                InfoRow("File", screenshot.name)
            }
        }

        val game = metadataRows(metadata.orEmpty())
        if (game.isNotEmpty()) {
            Section("In game") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((label, value) in game) InfoRow(label, value)
                }
            }
        }

        val chat = metadata?.get(MetadataKeys.CHAT)?.takeIf { it.isNotBlank() }
        if (chat != null) {
            Section("Chat") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(theme.componentBackground, theme.buttonShape)
                        .border(1.dp, theme.borderColor, theme.buttonShape)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (line in chat.lines()) Text(line, Modifier, theme.textColor, 11.sp)
                }
            }
        }
    }
}

@Composable
private fun MetadataTags(metadata: Map<String, String>?, modifier: Modifier = Modifier, overImage: Boolean = true) {
    val coords = !metadata?.get(MetadataKeys.COORDINATES).isNullOrBlank()
    val chat = !metadata?.get(MetadataKeys.CHAT).isNullOrBlank()
    if (!coords && !chat) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (coords) StatusTag("COORDS", overImage = overImage)
        if (chat) StatusTag("CHAT", overImage = overImage)
    }
}

private fun metadataRows(fields: Map<String, String>): List<Pair<String, String>> = listOfNotNull(
    fields[MetadataKeys.TIME]?.let { time ->
        "Taken" to (runCatching { FULL_FORMAT.format(OffsetDateTime.parse(time)) }.getOrNull() ?: time)
    },
    fields[MetadataKeys.WORLD]?.let { "World" to it },
    fields[MetadataKeys.SERVER]?.let { "Server" to it },
    fields[MetadataKeys.SERVER_ADDRESS]?.let { "Address" to it },
    fields[MetadataKeys.DIMENSION]?.let { "Dimension" to idLabel(it) },
    fields[MetadataKeys.BIOME]?.let { "Biome" to idLabel(it) },
    fields[MetadataKeys.COORDINATES]?.let { "Coordinates" to it },
    fields[MetadataKeys.PLAYER]?.let { "Player" to it },
    fields[MetadataKeys.VERSION]?.let { "Version" to it },
).filter { it.second.isNotBlank() }

/** `minecraft:cherry_grove` reads as "Cherry grove"; other namespaces stay visible */
private fun idLabel(id: String): String {
    val path = id.removePrefix("minecraft:").replace('_', ' ')
    return path.replaceFirstChar { it.titlecase(Locale.ROOT) }
}

@Composable
private fun UploadSplitButton(
    profiles: List<UploaderProfile>,
    defaultProfile: UploaderProfile,
    uploading: Boolean,
    again: Boolean,
    onUpload: (UploaderProfile) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SmallButton(
            if (uploading) "Uploading..." else if (again) "Upload again to ${defaultProfile.name}" else "Upload to ${defaultProfile.name}",
            "cloud",
            Modifier.weight(1f),
            hierarchy = ButtonHierarchy.PRIMARY,
            enabled = !uploading,
        ) { onUpload(defaultProfile) }
        if (profiles.size > 1) {
            Box {
                IconAction("down", "Upload somewhere else", active = menuOpen, enabled = !uploading) { menuOpen = !menuOpen }
                if (menuOpen) {
                    Popup(
                        alignment = Alignment.TopEnd,
                        offset = with(LocalDensity.current) { IntOffset(0, 36.dp.roundToPx()) },
                        onDismissRequest = { menuOpen = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        UploaderMenu(profiles, defaultProfile) {
                            menuOpen = false
                            onUpload(it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UploaderMenu(profiles: List<UploaderProfile>, defaultProfile: UploaderProfile, onPick: (UploaderProfile) -> Unit) {
    val theme = OneConfigTheme.current
    Column(
        Modifier
            .width(240.dp)
            .background(theme.popupBackground, theme.popupShape)
            .border(1.dp, theme.borderColor, theme.popupShape)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("UPLOAD TO", Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp), theme.textColorSecondary, 11.sp, FontWeight.Medium)
        for (profile in profiles) {
            val interaction = rememberInteractionSource()
            val hovered by interaction.collectIsHoveredAsState()
            val fill by animateColorAsState(if (hovered) theme.componentBackground else Color.Transparent)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(fill, theme.buttonShape)
                    .hoverable(interaction)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .onClick(interaction) { onPick(profile) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(profile.name, Modifier, theme.textColor, 12.sp, FontWeight.Medium)
                    Text(profile.host, Modifier, theme.textColorSecondary, 11.sp)
                }
                if (profile.id == defaultProfile.id) StatusTag("DEFAULT", overImage = false)
            }
        }
    }
}

@Composable
private fun LinkRow(screenshot: Screenshot, link: UploadRecord) {
    val theme = OneConfigTheme.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(theme.componentBackground, theme.buttonShape)
            .border(1.dp, theme.borderColor, theme.buttonShape)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(link.uploaderName, Modifier.weight(1f), theme.textColor, 12.sp, FontWeight.Medium)
            Text(relativeTime(link.uploadedAt), Modifier.padding(end = 8.dp), theme.textColorSecondary, 11.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(link.url.removePrefix("https://").removePrefix("http://"), Modifier.weight(1f), theme.textColorSecondary, 12.sp, null, BaselineShift.None, FontWeight.Normal, TextAlign.Start, 1, TextOverflow.Ellipsis)
            IconAction("copy", "Copy link") { ClipboardHelper.setString(link.url) }
            IconAction("external-link", "Open link") { ScreenshotActions.browse(link.url) }
            IconAction("close", "Forget this link", destructive = true) {
                UploadHistory.remove(screenshot, link)
                ScreenshotLibrary.changed()
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title.uppercase(Locale.ROOT), Modifier.padding(bottom = 8.dp), OneConfigTheme.current.textColorSecondary, 11.sp, FontWeight.Medium)
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val theme = OneConfigTheme.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.width(80.dp), theme.textColorSecondary, 12.sp)
        Text(value, Modifier.weight(1f), theme.textColor, 12.sp)
    }
}

@Composable
private fun ScreenshotImage(screenshot: Screenshot, width: Int, modifier: Modifier, scale: ContentScale, background: Boolean = true) {
    val theme = OneConfigTheme.current
    val thumbnail by produceState(Thumbnails.peek(screenshot, width), screenshot, width) {
        value = Thumbnails.peek(screenshot, width)
        value = Thumbnails.load(screenshot, width)
    }
    val fade by animateFloatAsState(if (thumbnail != null) 1f else 0f)
    Box(if (background) modifier.background(theme.componentBackground) else modifier) {
        thumbnail?.let { Image(it.bitmap, screenshot.name, Modifier.fillMaxSize().alpha(fade), contentScale = scale) }
    }
}

@Composable
private fun EmptyState(noScreenshots: Boolean) {
    val theme = OneConfigTheme.current
    Column(Modifier.fillMaxSize().padding(bottom = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(64.dp).background(theme.componentBackground, theme.circleShape).border(1.dp, theme.borderColor, theme.circleShape), contentAlignment = Alignment.Center) {
            Icon(if (noScreenshots) "/assets/screencapper/screencapper.svg" else "search", theme.textColorSecondary, Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(if (noScreenshots) "No screenshots yet" else "No screenshots match that search", Modifier, theme.textColor, 16.sp, FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(
            if (noScreenshots) "Press your screenshot key in game and it shows up here." else "Try another word, or a filter like server:, biome: or before:2026-10.",
            Modifier, theme.textColorSecondary, 12.sp,
        )
        if (noScreenshots) {
            Spacer(Modifier.height(16.dp))
            SmallButton("Open screenshots folder", "folder") { ScreenshotActions.openFolder() }
        }
    }
}

private val ZONE: ZoneId get() = ZoneId.systemDefault()
private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)
private val DAY_YEAR_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val FULL_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' HH:mm", Locale.ENGLISH)

private fun dayLabel(millis: Long): String {
    val date = Instant.ofEpochMilli(millis).atZone(ZONE).toLocalDate()
    val today = LocalDate.now(ZONE)
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> DAY_FORMAT.format(date)
        else -> DAY_YEAR_FORMAT.format(date)
    }
}

private fun relativeTime(millis: Long): String {
    val minutes = (System.currentTimeMillis() - millis) / 60_000
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d ago"
        else -> DAY_YEAR_FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZONE))
    }
}

private fun timeLabel(millis: Long): String = TIME_FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZONE))

private fun fullTimeLabel(millis: Long): String = FULL_FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZONE))

private fun sizeLabel(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun countLabel(shown: Int, total: Int): String = when {
    shown == total -> if (total == 1) "1 screenshot" else "$total screenshots"
    else -> "$shown of $total"
}
