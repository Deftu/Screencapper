@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.gui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.deftu.screencapper.ChatCards
import dev.deftu.screencapper.ScreenshotActions
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.gui.editor.ScreenshotEditor
import dev.deftu.screencapper.library.UserData
import dev.deftu.screencapper.library.UserDataStore
import dev.deftu.screencapper.metadata.MetadataIndex
import dev.deftu.screencapper.metadata.MetadataKeys
import dev.deftu.screencapper.metadata.SearchEntry
import dev.deftu.screencapper.metadata.SearchQuery
import dev.deftu.screencapper.screenshot.Screenshot
import dev.deftu.screencapper.screenshot.ScreenshotLibrary
import dev.deftu.screencapper.screenshot.UploadHistory
import dev.deftu.screencapper.screenshot.UploadRecord
import dev.deftu.screencapper.screenshot.naming.RenameCheck
import dev.deftu.screencapper.screenshot.naming.ScreenshotPaths
import dev.deftu.screencapper.upload.UploaderProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.ChipDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.Confirm
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.DropdownMenu
import org.polyfrost.oneconfig.api.ui.v1.components.EmptyState
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.IconButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.ImageViewer
import org.polyfrost.oneconfig.api.ui.v1.components.KeyValueRow
import org.polyfrost.oneconfig.api.ui.v1.components.MasterDetailLayout
import org.polyfrost.oneconfig.api.ui.v1.components.MediaTile
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.MenuEntry
import org.polyfrost.oneconfig.api.ui.v1.components.MenuHeading
import org.polyfrost.oneconfig.api.ui.v1.components.MenuSeparator
import org.polyfrost.oneconfig.api.ui.v1.components.MoreButton
import org.polyfrost.oneconfig.api.ui.v1.components.MultiSelect
import org.polyfrost.oneconfig.api.ui.v1.components.PanelHeader
import org.polyfrost.oneconfig.api.ui.v1.components.PanelSection
import org.polyfrost.oneconfig.api.ui.v1.components.Placement
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.Select
import org.polyfrost.oneconfig.api.ui.v1.components.SelectionBar
import org.polyfrost.oneconfig.api.ui.v1.components.SplitButton
import org.polyfrost.oneconfig.api.ui.v1.components.SurfaceKind
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.components.TextPromptDialog
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
import org.polyfrost.oneconfig.api.ui.v1.components.rememberImageBitmap
import org.polyfrost.oneconfig.api.ui.v1.components.themedSurface
import org.polyfrost.oneconfig.api.ui.v1.page.OneConfigPage
import org.polyfrost.oneconfig.api.ui.v1.page.PageGroup
import org.polyfrost.oneconfig.api.ui.v1.page.PageText
import org.polyfrost.oneconfig.api.ui.v1.page.currentPageSearchQueryOrNull
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.utils.v1.ClipboardHelper
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val THUMBNAIL_WIDTH = 480
internal const val VIEWER_WIDTH = 1600

object ScreenshotsPage : OneConfigPage(
    "screencapper:screenshots",
    PageText.translatable("screencapper.page.screenshots", "Screenshots"),
    "/assets/screencapper/screencapper.svg",
    PageGroup.MODS_AND_OPTIONS,
) {
    override val order: Int get() = 400

    override val searchPlaceholder: PageText = PageText.translatable("screencapper.page.screenshots.search", "Search screenshots...")

    internal var pendingFocus by mutableStateOf<String?>(null)

    internal var renaming by mutableStateOf<Screenshot?>(null)

    internal var editing by mutableStateOf<Screenshot?>(null)

    internal var organizing by mutableStateOf<OrganizePrompt?>(null)

    fun focus(screenshot: Screenshot?) {
        pendingFocus = screenshot?.id
    }

    @Composable
    override fun Content() = ScreenshotManager()
}

internal sealed interface OrganizePrompt {
    class NewAlbum(val ids: List<String>) : OrganizePrompt
    class RenameAlbum(val album: String) : OrganizePrompt
    class AddTags(val ids: List<String>) : OrganizePrompt
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
    val query = currentPageSearchQueryOrNull.orEmpty().trim()

    val spacing = OneConfigTheme.current.spacing
    val gridState = rememberLazyGridState()
    val listed by produceState<List<Screenshot>?>(null, revision) {
        while (true) {
            val fresh = withContext(Dispatchers.IO) { ScreenshotLibrary.list() }
            if (fresh != value) value = fresh
            delay(4000)
        }
    }
    val all = listed.orEmpty()
    val indexRevision = MetadataIndex.revision
    val dataRevision = UserDataStore.revision
    var album by remember { mutableStateOf<String?>(null) }
    val search = remember(query) { SearchQuery.parse(query) }
    var filtered by remember { mutableStateOf<List<Screenshot>?>(null) }
    LaunchedEffect(all, sort, search, indexRevision, album, dataRevision) {
        filtered = withContext(Dispatchers.Default) { filterAndSort(all, sort, search, album) }
    }
    val shown = filtered.orEmpty()
    LaunchedEffect(all) {
        if (all.isNotEmpty()) MetadataIndex.backfill(ScreenshotLibrary.directory, all.associate { it.id to it.file })
    }
    LaunchedEffect(listed) {
        val present = listed?.mapTo(HashSet()) { it.id } ?: return@LaunchedEffect
        withContext(Dispatchers.IO) { UserDataStore.clean(ScreenshotLibrary.directory, present) }
    }
    LaunchedEffect(dataRevision) {
        val current = album ?: return@LaunchedEffect
        if (UserDataStore.albums().none { it == current }) album = null
    }

    ScreenshotsPage.organizing?.let { prompt ->
        OrganizeDialog(prompt, onAlbumRenamed = { from, to -> if (album == from) album = to }) { ScreenshotsPage.organizing = null }
    }

    LaunchedEffect(ScreenshotsPage.pendingFocus) {
        ScreenshotsPage.pendingFocus?.let {
            viewing = it
            ScreenshotsPage.pendingFocus = null
        }
    }
    LaunchedEffect(all) { selection.retainAll(all.map { it.id }.toSet()) }

    var lastRenamed by remember { mutableStateOf<Screenshot?>(null) }
    ScreenshotsPage.renaming?.let { target ->
        RenameDialog(target) { renamed ->
            ScreenshotsPage.renaming = null
            if (renamed != null) {
                lastRenamed = renamed
                val at = selection.indexOf(target.id)
                if (at >= 0) selection[at] = renamed.id
                if (viewing == target.id) viewing = renamed.id
            }
        }
    }

    var lastEdited by remember { mutableStateOf<Screenshot?>(null) }
    ScreenshotsPage.editing?.let { target ->
        ScreenshotEditor(
            target,
            onClose = { ScreenshotsPage.editing = null },
            onSaved = { copy ->
                ScreenshotsPage.editing = null
                lastEdited = copy
                viewing = copy.id
            },
        )
        return
    }

    val viewed = viewing?.let { id ->
        shown.firstOrNull { it.id == id } ?: all.firstOrNull { it.id == id } ?: lastRenamed?.takeIf { it.id == id } ?: lastEdited?.takeIf { it.id == id }
    }
    if (viewed != null) {
        Viewer(viewed, if (viewed in shown) shown else all, onNavigate = { viewing = it.id }, onClose = { viewing = null })
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = spacing.xxl, end = spacing.xxl, top = spacing.l)) {
            Toolbar(all, shown.size, sort, { sort = it }, gridSize, { gridSize = it }, album, { album = it })
            Spacer(Modifier.height(spacing.xl))
            if (listed == null || filtered == null) {
                Spacer(Modifier.weight(1f))
            } else if (shown.isEmpty()) {
                NoScreenshots(all.isEmpty(), emptyAlbum = album != null && search.isEmpty)
            } else {
                ScreenshotGrid(
                    state = gridState,
                    screenshots = shown,
                    sort = sort,
                    gridSize = gridSize,
                    selection = selection,
                    onOpen = { screenshot ->
                        if (selection.isNotEmpty()) toggle(selection, screenshot.id) else viewing = screenshot.id
                    },
                    onView = { viewing = it.id },
                    onSelect = { toggle(selection, it.id) },
                )
            }
        }

        val selected = selection.toList().mapNotNull { id -> all.firstOrNull { it.id == id } }
        var lastSelected by remember { mutableStateOf(selected) }
        if (selected.isNotEmpty()) lastSelected = selected
        SelectionBar(selected.size, onClear = { selection.clear() }, Modifier.align(Alignment.BottomCenter).padding(bottom = spacing.xl)) {
            SelectionActions(lastSelected, onClear = { selection.clear() })
        }
    }
}

private fun filterAndSort(all: List<Screenshot>, sort: SortMode, search: SearchQuery, album: String?): List<Screenshot> {
    val members = album?.let(UserDataStore::inAlbum)
    val filtered = if (search.isEmpty && members == null) all else all.filter { screenshot ->
        if (members != null && screenshot.id !in members) return@filter false
        if (search.isEmpty) return@filter true
        val fields = MetadataIndex.fields(screenshot.id).orEmpty()
        search.matches(
            SearchEntry(
                screenshot.id, fields, takenOn(screenshot, fields),
                tags = UserDataStore.tagsOf(screenshot.id),
                albums = UserDataStore.albumsOf(screenshot.id),
                note = UserDataStore.noteOf(screenshot.id),
            ),
        )
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

private fun toggle(selection: MutableList<String>, id: String) {
    if (!selection.remove(id)) selection.add(id)
}

@Composable
private fun Toolbar(
    all: List<Screenshot>,
    shownCount: Int,
    sort: SortMode,
    onSort: (SortMode) -> Unit,
    gridSize: GridSize,
    onGridSize: (GridSize) -> Unit,
    album: String?,
    onAlbum: (String?) -> Unit,
) {
    val theme = OneConfigTheme.current
    val revision = ScreenshotLibrary.revision
    val totalBytes = remember(all) { all.sumOf { it.size } }
    val uploadedCount = remember(all, revision) { all.count { UploadHistory.latest(it) != null } }
    val albums = remember(UserDataStore.revision) { UserDataStore.albums() }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(theme.spacing.m)) {
        for (mode in SortMode.entries) Chip(mode.label, sort == mode, { onSort(mode) })
        Spacer(Modifier.width(theme.spacing.xs))
        Select(
            album,
            listOf<String?>(null) + albums,
            onAlbum,
            Modifier.widthIn(max = ALBUM_FILTER_WIDTH),
            label = { it ?: "All screenshots" },
            contentDescription = "Album",
            leadingLabel = "Album",
        )
        MoreButton(albumMenu(album, onAlbum), contentDescription = "Album actions", size = ControlSize.Regular)
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(theme.spacing.xxs)) {
            Text(countLabel(shownCount, all.size), Modifier, theme.textColor, OneConfigTheme.typography.label)
            Text(
                listOfNotNull(sizeLabel(totalBytes), uploadedCount.takeIf { it > 0 }?.let { "$it uploaded" }).joinToString(" · "),
                Modifier, theme.textColorSecondary, OneConfigTheme.typography.caption,
            )
        }
        Spacer(Modifier.width(theme.spacing.xs))
        for (size in GridSize.entries) {
            IconButton(size.icon, size.label, { onGridSize(size) }, selected = gridSize == size)
        }
        IconButton("folder", "Open screenshots folder", { ScreenshotActions.openFolder() })
    }
}

private val ALBUM_FILTER_WIDTH = 240.dp

private fun albumMenu(album: String?, onAlbum: (String?) -> Unit): List<MenuEntry> = listOf(
    MenuAction("New album", { ScreenshotsPage.organizing = OrganizePrompt.NewAlbum(emptyList()) }, icon = "plus"),
    MenuAction("Rename album", { album?.let { ScreenshotsPage.organizing = OrganizePrompt.RenameAlbum(it) } }, icon = "text-input", enabled = album != null),
    MenuSeparator,
    MenuAction("Delete album", {
        album?.let { name ->
            Confirm.ask(
                "Delete album?",
                "The album $name will be deleted. Its screenshots stay in your screenshots folder.",
                confirmLabel = "Delete album",
                onConfirm = {
                    UserDataStore.deleteAlbum(name)
                    onAlbum(null)
                },
            )
        }
    }, icon = "trash", enabled = album != null, danger = true),
)

@Composable
private fun ScreenshotGrid(
    state: LazyGridState,
    screenshots: List<Screenshot>,
    sort: SortMode,
    gridSize: GridSize,
    selection: List<String>,
    onOpen: (Screenshot) -> Unit,
    onView: (Screenshot) -> Unit,
    onSelect: (Screenshot) -> Unit,
) {
    val spacing = OneConfigTheme.current.spacing
    val groups = remember(screenshots, sort) {
        if (sort == SortMode.NEWEST || sort == SortMode.OLDEST) screenshots.groupBy { dayLabel(it.modified) }.toList()
        else listOf("" to screenshots)
    }
    ScrollContainer(state, Modifier.fillMaxSize(), scrollbar = false) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(gridSize.minWidth.dp),
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.l),
            verticalArrangement = Arrangement.spacedBy(spacing.l),
        ) {
            for ((label, items) in groups) {
                if (label.isNotEmpty()) {
                    item(key = "header:$label", span = { GridItemSpan(maxLineSpan) }) {
                        GroupHeading(label, items.size)
                    }
                }
                items(items, key = { it.id }) { screenshot ->
                    ScreenshotTile(
                        screenshot = screenshot,
                        selected = screenshot.id in selection,
                        selecting = selection.isNotEmpty(),
                        onOpen = { onOpen(screenshot) },
                        onView = { onView(screenshot) },
                        onSelect = { onSelect(screenshot) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupHeading(label: String, count: Int) {
    val theme = OneConfigTheme.current
    Row(Modifier.padding(top = theme.spacing.m, start = theme.spacing.xxs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(theme.spacing.m)) {
        Text(label.uppercase(Locale.ROOT), Modifier, theme.textColorSecondary, OneConfigTheme.typography.eyebrow)
        Text("$count", Modifier, theme.textColorSecondary.copy(alpha = 0.6f), OneConfigTheme.typography.eyebrow)
    }
}

@Composable
private fun ScreenshotTile(screenshot: Screenshot, selected: Boolean, selecting: Boolean, onOpen: () -> Unit, onView: () -> Unit, onSelect: () -> Unit) {
    val revision = ScreenshotLibrary.revision
    val uploading = remember(revision, screenshot.id) { ScreenshotActions.isUploading(screenshot) }
    val uploaded = remember(revision, screenshot.id) { UploadHistory.latest(screenshot) != null }
    val metadata = remember(MetadataIndex.revision, screenshot.id) { MetadataIndex.peek(screenshot.id) }
    val name = screenshot.file.nameWithoutExtension

    MediaTile(
        onClick = onOpen,
        label = name,
        modifier = Modifier.contextMenu { screenshotMenu(screenshot, onView = onView, onSelect = onSelect, selected = selected) },
        selected = selected,
        onSelectedChange = { onSelect() },
        selecting = selecting,
        title = name,
        subtitle = "${timeLabel(screenshot.modified)} · ${sizeLabel(screenshot.size)}",
        actions = {
            IconButton("copy", "Copy", { ScreenshotActions.copy(screenshot) }, style = IconButtonStyle.Overlay, size = ControlSize.Compact)
            IconButton("upload", "Upload to ${ScreencapperConfig.defaultProfile.name}", { ScreenshotActions.upload(screenshot) }, style = IconButtonStyle.Overlay, size = ControlSize.Compact)
            IconButton("trash", "Delete", { confirmDelete(screenshot) }, style = IconButtonStyle.Overlay, size = ControlSize.Compact, danger = true)
        },
        badge = if (uploading || uploaded) {
            { Badge(if (uploading) "UPLOADING" else "UPLOADED", tone = if (uploading) BadgeTone.Accent else BadgeTone.Success, overImage = true) }
        } else {
            null
        },
        tags = { MetadataBadges(metadata, overImage = true) },
    ) {
        ScreenshotImage(screenshot, THUMBNAIL_WIDTH, Modifier.fillMaxSize(), ContentScale.Crop)
    }
}

private fun screenshotMenu(screenshot: Screenshot, onView: (() -> Unit)? = null, onSelect: (() -> Unit)? = null, selected: Boolean = false, onDeleted: () -> Unit = {}): List<MenuEntry> {
    val uploaded = UploadHistory.latestLink(screenshot) != null
    val uploading = ScreenshotActions.isUploading(screenshot)
    return listOfNotNull(
        onView?.let { MenuAction("View", it,icon = "maximise") },
        MenuAction("Open", {ScreenshotActions.open(screenshot) }, icon = "external-link"),
        onSelect?.let { MenuAction(if (selected) "Deselect" else "Select", it,icon = "tick") },
        MenuSeparator,
        MenuAction("Copy", {ScreenshotActions.copy(screenshot) }, icon = "copy"),
        if (uploaded) MenuAction("Copy link", {ScreenshotActions.copyLink(screenshot) }, icon = "clipboard") else null,
        MenuAction(
            if (uploaded) "Upload again" else "Upload",
            { ScreenshotActions.upload(screenshot) },
            icon = "upload",
            detail = ScreencapperConfig.defaultProfile.name,
            enabled = !uploading,
        ),
        MenuAction("Edit", { ScreenshotsPage.editing = screenshot }, icon = "paintbrush"),
        MenuAction("Rename", { ScreenshotsPage.renaming = screenshot }, icon = "text-input", enabled = !uploading),
        MenuAction("Show in folder", { ScreenshotActions.openFolder(screenshot) }, icon = "folder"),
        MenuSeparator,
        MenuAction("Delete", {confirmDelete(screenshot, onDeleted) }, icon = "trash", danger = true),
    )
}

private fun confirmDelete(screenshot: Screenshot, onDeleted: () -> Unit = {}) {
    Confirm.ask(
        "Delete screenshot?",
        "${screenshot.name} will be deleted from your screenshots folder.",
        confirmLabel = "Delete",
        onConfirm = { if (ScreenshotActions.delete(screenshot)) onDeleted() },
    )
}

@Composable
private fun SelectionActions(selected: List<Screenshot>, onClear: () -> Unit) {
    for (action in selectionActions(selected.size)) {
        if (action == SelectionAction.DELETE) {
            AddToAlbumButton(selected)
            TextButton("Tag", "plus", { ScreenshotsPage.organizing = OrganizePrompt.AddTags(selected.map { it.id }) })
        }
        when (action) {
            SelectionAction.COPY -> TextButton("Copy", "copy", { ScreenshotActions.copyAll(selected) })
            SelectionAction.UPLOAD -> TextButton("Upload", "upload", { selected.forEach { ScreenshotActions.upload(it) } }, style = ControlStyle.Primary)
            SelectionAction.DELETE -> TextButton("Delete", "trash", {
                val count = selected.size
                Confirm.ask(
                    if (count == 1) "Delete screenshot?" else "Delete $count screenshots?",
                    if (count == 1) "${selected.first().name} will be deleted from your screenshots folder." else "$count screenshots will be deleted from your screenshots folder.",
                    confirmLabel = if (count == 1) "Delete" else "Delete $count",
                    onConfirm = {
                        ScreenshotActions.deleteAll(selected)
                        onClear()
                    },
                )
            }, style = ControlStyle.Danger)
        }
    }
}

@Composable
private fun AddToAlbumButton(selected: List<Screenshot>) {
    var open by remember { mutableStateOf(false) }
    val albums = remember(UserDataStore.revision) { UserDataStore.albums() }
    val ids = selected.map { it.id }
    Box {
        TextButton("Add to album", "layers", { open = !open })
        DropdownMenu(
            open,
            { open = false },
            entries = listOf(MenuHeading("Add to album")) +
                albums.map { name -> MenuAction(name, { addToAlbum(ids, name) }, icon = "layers") } +
                listOfNotNull(
                    MenuSeparator.takeIf { albums.isNotEmpty() },
                    MenuAction("New album", { ScreenshotsPage.organizing = OrganizePrompt.NewAlbum(ids) }, icon = "plus"),
                ),
            placement = Placement.AboveStart,
        )
    }
}

private fun addToAlbum(ids: List<String>, album: String) {
    UserDataStore.addToAlbum(ids, album)
    val name = UserDataStore.albums().firstOrNull { it.equals(UserData.clean(album), ignoreCase = true) } ?: album
    ScreenshotActions.notifySuccess("Added to album", if (ids.size == 1) "1 screenshot is in $name." else "${ids.size} screenshots are in $name.")
}

private fun splitTags(text: String): List<String> = text.split(',').map(UserData::clean).filter { it.isNotEmpty() }

@Composable
private fun OrganizeDialog(prompt: OrganizePrompt, onAlbumRenamed: (String, String) -> Unit, onDone: () -> Unit) {
    key(prompt) {
        when (prompt) {
            is OrganizePrompt.NewAlbum -> TextPromptDialog(
                title = "New album",
                initial = "",
                onConfirm = { name ->
                    if (prompt.ids.isEmpty()) UserDataStore.createAlbum(name) else addToAlbum(prompt.ids, name)
                    onDone()
                },
                onDismiss = onDone,
                confirmLabel = "Create",
                validate = { UserDataStore.albumProblem(it) },
                subtitle = when (prompt.ids.size) {
                    0 -> null
                    1 -> "The selected screenshot is added to it."
                    else -> "The ${prompt.ids.size} selected screenshots are added to it."
                },
                placeholder = "Album name",
                confirmIcon = "plus",
            )
            is OrganizePrompt.RenameAlbum -> TextPromptDialog(
                title = "Rename album",
                initial = prompt.album,
                onConfirm = { name ->
                    if (UserDataStore.renameAlbum(prompt.album, name)) onAlbumRenamed(prompt.album, UserData.clean(name))
                    onDone()
                },
                onDismiss = onDone,
                confirmLabel = "Rename",
                validate = { UserDataStore.albumProblem(it, prompt.album) },
                placeholder = "Album name",
                confirmIcon = "text-input",
            )
            is OrganizePrompt.AddTags -> TextPromptDialog(
                title = if (prompt.ids.size == 1) "Tag screenshot" else "Tag ${prompt.ids.size} screenshots",
                initial = "",
                onConfirm = { text ->
                    splitTags(text).forEach { UserDataStore.addTag(prompt.ids, it) }
                    onDone()
                },
                onDismiss = onDone,
                confirmLabel = "Add tags",
                validate = { if (splitTags(it).isEmpty()) "Enter a tag." else null },
                subtitle = "Separate several tags with commas.",
                placeholder = "Tag name",
                confirmIcon = "plus",
            )
        }
    }
}

@Composable
private fun Viewer(screenshot: Screenshot, list: List<Screenshot>, onNavigate: (Screenshot) -> Unit, onClose: () -> Unit) {
    val index = list.indexOfFirst { it.id == screenshot.id }
    val previous = list.getOrNull(index - 1)
    val next = list.getOrNull(index + 1)
    val spacing = OneConfigTheme.current.spacing
    var fullscreen by remember { mutableStateOf(false) }

    MasterDetailLayout(
        list = {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing.l)) {
                PanelHeader(onBack = onClose, backLabel = "All screenshots")
                ImageViewer(
                    count = list.size,
                    index = index.coerceAtLeast(0),
                    onIndexChange = { at -> list.getOrNull(at)?.let(onNavigate) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onDismiss = onClose,
                    thumbnail = { at -> ScreenshotImage(list[at], THUMBNAIL_WIDTH, Modifier.fillMaxSize(), ContentScale.Crop) },
                    thumbnailLabel = { at -> list[at].file.nameWithoutExtension },
                    itemKey = { at -> list[at].id },
                    tileModifier = { at ->
                        val tile = list[at]
                        Modifier.contextMenu { screenshotMenu(tile, onView = { onNavigate(tile) }, onDeleted = { if (tile.id == screenshot.id) (next ?: previous)?.let(onNavigate) ?: onClose() }) }
                    },
                    filmstripPreviousLabel = "Earlier screenshots",
                    filmstripNextLabel = "Later screenshots",
                ) { at ->
                    val shown = list.getOrNull(at) ?: screenshot
                    ViewerFrame(shown, onMaximize = { fullscreen = true }, Modifier.contextMenu { screenshotMenu(shown, onDeleted = { if (shown.id == screenshot.id) (next ?: previous)?.let(onNavigate) ?: onClose() }) })
                }
            }
        },
        detail = {
            DetailsPanel(screenshot, onDeleted = {
                val fallback = next ?: previous
                if (fallback != null) onNavigate(fallback) else onClose()
            })
        },
        modifier = Modifier
            .fillMaxSize()
            .padding(start = spacing.xxl, end = spacing.xxl, top = spacing.l, bottom = spacing.xxl)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace) {
                    onClose()
                    true
                } else {
                    false
                }
            },
        detailWidth = DETAILS_WIDTH,
    )

    if (fullscreen) FullscreenViewer(screenshot, list, onNavigate, onClose = { fullscreen = false }, onEdit = { ScreenshotsPage.editing = screenshot })
}

@Composable
private fun ViewerFrame(screenshot: Screenshot, onMaximize: () -> Unit, modifier: Modifier = Modifier) {
    val loaded = rememberImageBitmap(screenshot.file.toPath(), VIEWER_WIDTH)
    val size = rememberImageSize(screenshot)
    val aspect = loaded?.let { it.width.toFloat() / it.height } ?: size?.let { it.width.toFloat() / it.height } ?: 16f / 9f
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val frameWidth = minOf(maxWidth, maxHeight * aspect)
        Box(
            modifier
                .size(frameWidth, frameWidth / aspect)
                .themedSurface(SurfaceKind.Card)
                .onDoubleClick(onMaximize),
        ) {
            ZoomableImage(screenshot.file, size, intArrayOf(VIEWER_WIDTH), Modifier.fillMaxSize(), doubleClickZooms = false)
            IconButton(
                "maximise", "Full screen", onMaximize,
                Modifier.align(Alignment.TopEnd).padding(OneConfigTheme.current.spacing.m),
                style = IconButtonStyle.Overlay,
            )
        }
    }
}

private fun imageSize(screenshot: Screenshot): IntSize? = runCatching {
    javax.imageio.ImageIO.createImageInputStream(screenshot.file).use { stream ->
        val reader = javax.imageio.ImageIO.getImageReaders(stream ?: return null).asSequence().firstOrNull() ?: return null
        try {
            reader.input = stream
            IntSize(reader.getWidth(0), reader.getHeight(0))
        } finally {
            reader.dispose()
        }
    }
}.getOrNull()?.takeIf { it.width > 0 && it.height > 0 }

private val DETAILS_WIDTH = 300.dp

@Composable
private fun DetailsPanel(screenshot: Screenshot, onDeleted: () -> Unit) {
    val theme = OneConfigTheme.current
    val revision = ScreenshotLibrary.revision
    val links = remember(revision, screenshot.id) { UploadHistory.of(screenshot) }
    val uploading = remember(revision, screenshot.id) { ScreenshotActions.isUploading(screenshot) }
    val profiles = remember(revision) { ScreencapperConfig.profiles }
    val defaultProfile = remember(revision) { ScreencapperConfig.defaultProfile }
    val metadata = remember(MetadataIndex.revision, screenshot.id) { MetadataIndex.peek(screenshot.id) }
    val resolution = rememberImageSize(screenshot)
    val spacing = theme.spacing

    ScrollContainer(
        rememberScrollState(),
        Modifier.fillMaxSize().themedSurface(SurfaceKind.Card),
        scrollbar = false,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.xl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(screenshot.file.nameWithoutExtension, Modifier.semantics { heading() }, theme.textColor, OneConfigTheme.typography.titleSmall, 2, TextOverflow.Ellipsis, textAlign = TextAlign.Start)
                Text(fullTimeLabel(screenshot.modified), Modifier, theme.textColorSecondary, OneConfigTheme.typography.small)
                MetadataTags(metadata, Modifier.padding(top = spacing.xs), overImage = false)
            }

            PanelSection("Actions") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                        TextButton("Copy", "copy", { ScreenshotActions.copy(screenshot) }, Modifier.weight(1f), style = ControlStyle.Primary, fillWidth = true)
                        TextButton("Edit", "paintbrush", { ScreenshotsPage.editing = screenshot }, Modifier.weight(1f), fillWidth = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                        TextButton("Open", "external-link", { ScreenshotActions.open(screenshot) }, Modifier.weight(1f), fillWidth = true)
                        TextButton("Folder", "folder", { ScreenshotActions.openFolder(screenshot) }, Modifier.weight(1f), fillWidth = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                        TextButton("Rename", "text-input", { ScreenshotsPage.renaming = screenshot }, Modifier.weight(1f), enabled = !uploading, fillWidth = true)
                        TextButton("Delete", "trash", { confirmDelete(screenshot, onDeleted) }, Modifier.weight(1f), style = ControlStyle.Danger, fillWidth = true)
                    }
                }
            }

            PanelSection("Upload") {
                UploadSplitButton(profiles, defaultProfile, uploading, again = links.isNotEmpty()) { ScreenshotActions.upload(screenshot, it) }
                if (links.isNotEmpty()) {
                    Spacer(Modifier.height(spacing.m))
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                        for (link in links) LinkRow(screenshot, link)
                    }
                }
            }

            PanelSection("Tags") { TagEditor(screenshot) }

            PanelSection("Albums") { AlbumPicker(screenshot) }

            PanelSection("Note") { NoteEditor(screenshot) }

            PanelSection("Details") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                    KeyValueRow("Size", sizeLabel(screenshot.size))
                    resolution?.let { KeyValueRow("Resolution", "${it.width} × ${it.height}") }
                    KeyValueRow("Format", screenshot.file.extension.uppercase(Locale.ROOT))
                    KeyValueRow("File", screenshot.id)
                }
            }

            val game = metadataRows(metadata.orEmpty())
            if (game.isNotEmpty()) {
                PanelSection("In game") {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                        for ((label, value) in game) KeyValueRow(label, value)
                    }
                }
            }

            val chat = metadata?.get(MetadataKeys.CHAT)?.takeIf { it.isNotBlank() }
            if (chat != null) {
                PanelSection("Chat") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .themedSurface(SurfaceKind.Field)
                            .padding(horizontal = spacing.l, vertical = spacing.m),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        for (line in chat.lines()) Text(line, Modifier, theme.textColor, OneConfigTheme.typography.caption)
                    }
                }
            }
        }
    }
}

private const val TAG_SUGGESTIONS = 6
private const val NOTE_SAVE_DELAY_MS = 800L
private val NOTE_MIN_HEIGHT = 72.dp

@Composable
private fun TagEditor(screenshot: Screenshot) {
    val theme = OneConfigTheme.current
    val spacing = theme.spacing
    val revision = UserDataStore.revision
    val tags = remember(revision, screenshot.id) { UserDataStore.tagsOf(screenshot.id) }
    val known = remember(revision) { UserDataStore.allTags() }
    var draft by remember(screenshot.id) { mutableStateOf("") }
    val typed = UserData.clean(draft)
    val suggestions = if (typed.isEmpty()) emptyList() else known
        .filter { tag -> tags.none { it.equals(tag, ignoreCase = true) } && tag.contains(typed, ignoreCase = true) && !tag.equals(typed, ignoreCase = true) }
        .take(TAG_SUGGESTIONS)
    val add = { text: String ->
        UserDataStore.addTag(listOf(screenshot.id), text)
        draft = ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                for (tag in tags) {
                    Chip(
                        tag, false, { UserDataStore.removeTag(screenshot.id, tag) },
                        size = ControlSize.Compact,
                        contentDescription = "Remove tag $tag",
                        trailing = { Icon("close", null, Modifier.size(ChipDefaults.iconSize(ControlSize.Compact)), tint = theme.textColorSecondary) },
                    )
                }
            }
        }
        TextField(
            draft,
            { draft = it },
            Modifier.fillMaxWidth(),
            placeholder = "Add a tag",
            leadingIcon = "plus",
            onSubmit = { splitTags(draft).forEach(add) },
        )
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                for (tag in suggestions) Chip(tag, false, { add(tag) }, icon = "plus", size = ControlSize.Compact, contentDescription = "Add tag $tag")
            }
        }
    }
}

@Composable
private fun AlbumPicker(screenshot: Screenshot) {
    val theme = OneConfigTheme.current
    val revision = UserDataStore.revision
    val albums = remember(revision) { UserDataStore.albums() }
    val member = remember(revision, screenshot.id) { UserDataStore.albumsOf(screenshot.id).toSet() }

    Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
        if (albums.isEmpty()) {
            Text("No albums yet.", Modifier, theme.textColorSecondary, OneConfigTheme.typography.small)
        } else {
            MultiSelect(
                member,
                albums,
                { next ->
                    (next - member).forEach { UserDataStore.addToAlbum(listOf(screenshot.id), it) }
                    (member - next).forEach { UserDataStore.removeFromAlbum(screenshot.id, it) }
                },
                Modifier.fillMaxWidth(),
                contentDescription = "Albums",
            )
        }
        TextButton("New album", "plus", { ScreenshotsPage.organizing = OrganizePrompt.NewAlbum(listOf(screenshot.id)) }, Modifier.fillMaxWidth(), fillWidth = true)
    }
}

@Composable
private fun NoteEditor(screenshot: Screenshot) {
    key(screenshot.id) {
        var text by remember { mutableStateOf(UserDataStore.noteOf(screenshot.id)) }
        val latest by rememberUpdatedState(text)
        val save = { if (screenshot.file.exists()) UserDataStore.setNote(screenshot.id, latest) }
        LaunchedEffect(text) {
            delay(NOTE_SAVE_DELAY_MS)
            save()
        }
        DisposableEffect(Unit) { onDispose { save() } }
        TextField(
            text,
            { text = it },
            Modifier.fillMaxWidth().heightIn(min = NOTE_MIN_HEIGHT).onFocusChanged { if (!it.hasFocus) save() },
            placeholder = "Add a note",
            singleLine = false,
        )
    }
}

@Composable
private fun MetadataTags(metadata: Map<String, String>?, modifier: Modifier = Modifier, overImage: Boolean = true) {
    if (metadata?.get(MetadataKeys.COORDINATES).isNullOrBlank() && metadata?.get(MetadataKeys.CHAT).isNullOrBlank()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(OneConfigTheme.current.spacing.xs)) {
        MetadataBadges(metadata, overImage)
    }
}

@Composable
private fun MetadataBadges(metadata: Map<String, String>?, overImage: Boolean) {
    if (!metadata?.get(MetadataKeys.COORDINATES).isNullOrBlank()) Badge("COORDS", overImage = overImage)
    if (!metadata?.get(MetadataKeys.CHAT).isNullOrBlank()) Badge("CHAT", overImage = overImage)
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
    val label = if (uploading) "Uploading..." else if (again) "Upload again to ${defaultProfile.name}" else "Upload to ${defaultProfile.name}"
    if (profiles.size > 1) {
        SplitButton(label, { onUpload(defaultProfile) }, uploaderMenu(profiles, defaultProfile, onUpload), Modifier.fillMaxWidth(), enabled = !uploading, fillWidth = true, icon = "upload")
    } else {
        TextButton(label, "upload", { onUpload(defaultProfile) }, Modifier.fillMaxWidth(), style = ControlStyle.Primary, enabled = !uploading, fillWidth = true)
    }
}

internal fun uploaderMenu(profiles: List<UploaderProfile>, defaultProfile: UploaderProfile, onPick: (UploaderProfile) -> Unit): List<MenuEntry> =
    listOf(MenuHeading("Upload to")) + profiles.map { profile ->
        MenuAction(profile.name, onClick = { onPick(profile) }, icon = "upload", detail = profile.host, badge = if (profile.id == defaultProfile.id) "Default" else null)
    }

@Composable
private fun LinkRow(screenshot: Screenshot, link: UploadRecord) {
    val theme = OneConfigTheme.current
    val spacing = theme.spacing
    Column(
        Modifier
            .fillMaxWidth()
            .themedSurface(SurfaceKind.Field)
            .padding(start = spacing.l, end = spacing.xs, top = spacing.m, bottom = spacing.s),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(link.uploaderName, Modifier.weight(1f), theme.textColor, OneConfigTheme.typography.label)
            Text(relativeTime(link.uploadedAt), Modifier.padding(end = spacing.m), theme.textColorSecondary, OneConfigTheme.typography.caption)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (link.hasLink) link.url.removePrefix("https://").removePrefix("http://") else "Sent, no public link", Modifier.weight(1f), theme.textColorSecondary, OneConfigTheme.typography.small, 1, TextOverflow.Ellipsis, textAlign = TextAlign.Start)
            if (link.hasLink) {
                IconButton("copy", "Copy link", { ClipboardHelper.setString(link.url) }, size = ControlSize.Compact)
                IconButton("external-link", "Open link", { ScreenshotActions.browse(link.url) }, size = ControlSize.Compact)
            }
            RemoteDeleteButton(screenshot, link)
            IconButton("close", "Forget this link", {
                UploadHistory.remove(screenshot, link)
                ChatCards.uploadsChanged(screenshot)
                ScreenshotLibrary.changed()
            }, size = ControlSize.Compact, danger = true)
        }
    }
}

@Composable
internal fun ScreenshotImage(screenshot: Screenshot, width: Int, modifier: Modifier, scale: ContentScale, background: Boolean = true) {
    val theme = OneConfigTheme.current
    val bitmap = rememberImageBitmap(screenshot.file.toPath(), width)
    val fade by animateFloatAsState(if (bitmap != null) 1f else 0f)
    Box(if (background) modifier.background(theme.componentBackground) else modifier) {
        bitmap?.let { Image(it, screenshot.name, Modifier.fillMaxSize().alpha(fade), contentScale = scale) }
    }
}

@Composable
internal fun rememberImageSize(screenshot: Screenshot): IntSize? =
    produceState<IntSize?>(null, screenshot.id, screenshot.modified) {
        value = withContext(Dispatchers.IO) { imageSize(screenshot) }
    }.value

@Composable
private fun RenameDialog(screenshot: Screenshot, onDone: (Screenshot?) -> Unit) {
    val root = remember { ScreenshotLibrary.directory }
    key(screenshot.id) {
        TextPromptDialog(
            title = "Rename screenshot",
            initial = screenshot.id.removeSuffix(".${screenshot.file.extension}"),
            onConfirm = { name -> onDone(ScreenshotActions.rename(screenshot, name)) },
            onDismiss = { onDone(null) },
            confirmLabel = "Rename",
            validate = { name -> (ScreenshotPaths.renameTarget(root, screenshot.file, name) as? RenameCheck.Invalid)?.reason },
            subtitle = "Use / to move it into a folder inside your screenshots folder. The .${screenshot.file.extension} extension is kept.",
            confirmIcon = "text-input",
        )
    }
}

@Composable
private fun NoScreenshots(noScreenshots: Boolean, emptyAlbum: Boolean) {
    EmptyState(
        when {
            noScreenshots -> "No screenshots yet"
            emptyAlbum -> "This album is empty"
            else -> "No screenshots match that search"
        },
        Modifier.fillMaxSize().padding(bottom = 48.dp),
        icon = if (noScreenshots) "/assets/screencapper/screencapper.svg" else "search",
        text = when {
            noScreenshots -> "Press your screenshot key in game and it shows up here."
            emptyAlbum -> "Select screenshots and use Add to album to fill it."
            else -> "Try another word, or a filter like server:, tag:, album: or before:2026-10."
        },
        action = if (noScreenshots) {
            { TextButton("Open screenshots folder", "folder", { ScreenshotActions.openFolder() }) }
        } else {
            null
        },
    )
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
