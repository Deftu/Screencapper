@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.gui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.deftu.screencapper.MainThread
import dev.deftu.screencapper.Screencapper
import dev.deftu.screencapper.ScreenshotActions
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.editor.Badge
import dev.deftu.screencapper.editor.CropAspect
import dev.deftu.screencapper.editor.Drawing
import dev.deftu.screencapper.editor.EditRenderer
import dev.deftu.screencapper.editor.EditedCopies
import dev.deftu.screencapper.editor.EditorViewport
import dev.deftu.screencapper.editor.ImagePoint
import dev.deftu.screencapper.editor.Item
import dev.deftu.screencapper.editor.Layer
import dev.deftu.screencapper.editor.Orientation
import dev.deftu.screencapper.editor.PixelRect
import dev.deftu.screencapper.editor.Shape
import dev.deftu.screencapper.editor.ShapeKind
import dev.deftu.screencapper.editor.TextBox
import dev.deftu.screencapper.editor.TextEffect
import dev.deftu.screencapper.editor.TextLayout
import dev.deftu.screencapper.editor.Turn
import dev.deftu.screencapper.editor.drawItem
import dev.deftu.screencapper.editor.toImageBitmap
import dev.deftu.screencapper.gui.DoubleClickDetector
import dev.deftu.screencapper.gui.ZoomGeometry
import dev.deftu.screencapper.gui.ZoomTransform
import dev.deftu.screencapper.gui.uploaderMenu
import dev.deftu.screencapper.screenshot.Screenshot
import dev.deftu.screencapper.screenshot.ScreenshotLibrary
import dev.deftu.screencapper.screenshot.capture.CapturedImage
import dev.deftu.screencapper.screenshot.capture.compression.CompressionProfile
import dev.deftu.screencapper.screenshot.capture.compression.ImageCompressor
import dev.deftu.screencapper.screenshot.capture.compression.ResizeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import org.polyfrost.compose.host.controller.ControllerButton
import org.polyfrost.compose.host.controller.Controllers
import org.polyfrost.compose.host.controller.controllerAction
import org.polyfrost.compose.host.controller.controllerPan
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.ColorPickerPopup
import org.polyfrost.oneconfig.api.ui.v1.components.Confirm
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.EmptyState
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.PanelHeader
import org.polyfrost.oneconfig.api.ui.v1.components.Placement
import org.polyfrost.oneconfig.api.ui.v1.components.Select
import org.polyfrost.oneconfig.api.ui.v1.components.SplitButton
import org.polyfrost.oneconfig.api.ui.v1.components.SurfaceKind
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.components.controllerShortcut
import org.polyfrost.oneconfig.api.ui.v1.components.rememberColorPickerModel
import org.polyfrost.oneconfig.api.ui.v1.components.themedSurface
import org.polyfrost.oneconfig.api.ui.v1.page.PageText
import org.polyfrost.oneconfig.api.ui.v1.page.resolve
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.utils.v1.Multithreading
import kotlin.math.floor

private const val PREVIEW_WIDTH = 2560
private const val TEXT_FIELD_MAX_WIDTH = 520
private const val SWATCH_SIZE = 14
private const val HINT_ICON = 12
private const val ASPECT_WIDTH = 116
private const val EFFECT_WIDTH = 104
private const val DOUBLE_CLICK_MILLIS = 400L
private const val DOUBLE_CLICK_SLOP = 6f
private val ROW_HEIGHT = ControlSize.Regular.height
private const val OUTLINE = 2f
private const val HANDLE = 8f
private const val TINT_ALPHA = 0.2f
private const val FAINT_ALPHA = 0.5f
private const val CONTROLLER_ZOOM_STEP = 1.5f
private val SAVE_LABEL = PageText.translatable("screencapper.controller.save", "Save copy")

private val PALETTE = listOf(
    "White" to 0xFFFFFFFF.toInt(),
    "Black" to 0xFF000000.toInt(),
    "Red" to 0xFFE35B5B.toInt(),
    "Yellow" to 0xFFFFC53D.toInt(),
    "Green" to 0xFF3CAF77.toInt(),
    "Blue" to 0xFF5B9BE3.toInt(),
)

private fun editorIcon(name: String): String = EditorIcons.path(name)

private val EditTool.iconPath: String?
    get() = icon ?: when (this) {
        EditTool.SHAPES -> editorIcon("shapes")
        EditTool.STEPS -> editorIcon("steps")
        else -> null
    }

private val Pen.icon: String
    get() = when (this) {
        Pen.PEN -> editorIcon("pen")
        Pen.MARKER -> editorIcon("marker")
    }

private val ShapeKind.icon: String
    get() = when (this) {
        ShapeKind.ARROW -> editorIcon("arrow")
        ShapeKind.RECTANGLE -> editorIcon("rectangle")
        ShapeKind.ELLIPSE -> editorIcon("ellipse")
        ShapeKind.LINE -> editorIcon("line")
    }

private val TURNS = listOf(
    Triple(Turn.LEFT, "rotate-left", "Rotate left"),
    Triple(Turn.RIGHT, "rotate-right", "Rotate right"),
    Triple(Turn.FLIP_HORIZONTAL, "flip-horizontal", "Flip left to right"),
    Triple(Turn.FLIP_VERTICAL, "flip-vertical", "Flip upside down"),
)

private class BaseImage(val bitmap: ImageBitmap, val full: CapturedImage, val count: Int)

private class FloatingPreview(val selection: Selection.Area, val under: BaseImage, val patch: ImageBitmap)

private data class Bake(val source: CapturedImage?, val orientation: Orientation, val items: List<Item>)

private enum class SourceLoad { LOADING, FAILED, READY }


@Composable
internal fun ScreenshotEditor(screenshot: Screenshot, onClose: () -> Unit, onSaved: (Screenshot) -> Unit) {
    val theme = OneConfigTheme.current
    val spacing = theme.spacing
    val state = remember(screenshot.id) { EditorState() }
    var load by remember(screenshot.id) { mutableStateOf(SourceLoad.LOADING) }
    val textFocus = remember { FocusRequester() }
    var base by remember(screenshot.id) { mutableStateOf<BaseImage?>(null) }
    var floating by remember(screenshot.id) { mutableStateOf<FloatingPreview?>(null) }
    val latestClose by rememberUpdatedState(onClose)
    val latestSaved by rememberUpdatedState(onSaved)

    LaunchedEffect(screenshot.id) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { ImageCompressor.fromImage(screenshot.readImage()) }
                .onFailure { Screencapper.LOGGER.error("Couldn't open {} for editing", screenshot.name, it) }
                .getOrNull()
        }
        state.source = loaded
        load = if (loaded != null) SourceLoad.READY else SourceLoad.FAILED
    }

    LaunchedEffect(state) {
        snapshotFlow { state.displayItems().let { Bake(state.source, state.doc.orientation, it.take(EditRenderer.layered(it))) } }
            .distinctUntilChanged()
            .conflate()
            .collect { bake ->
                val source = bake.source ?: return@collect
                val full = rendered(source, bake.orientation, bake.items)
                base = withContext(Dispatchers.Default) { BaseImage(previewScaled(full).toImageBitmap(), full, bake.items.size) }
            }
    }

    LaunchedEffect(state.pendingLift) {
        val rect = state.pendingLift ?: return@LaunchedEffect
        val source = state.source ?: return@LaunchedEffect
        val items = state.displayItems()
        val orientation = state.doc.orientation
        val full = rendered(source, orientation, items)
        val lifted = withContext(Dispatchers.Default) {
            val patch = EditRenderer.region(full, rect)
            FloatingPreview(Selection.Area(rect, EditRenderer.ringAverage(full, rect), patch), BaseImage(previewScaled(full).toImageBitmap(), full, items.size), patch.toImageBitmap())
        }
        if (state.pendingLift != rect) return@LaunchedEffect
        floating = lifted
        state.lifted(lifted.selection)
    }

    val finish: () -> Unit = remember(state) { { state.saved?.let { latestSaved(it.screenshot) } ?: latestClose() } }

    val cancel: () -> Unit = remember(state) {
        {
            when {
                state.escape() -> Unit
                state.saving -> Unit
                state.canSave -> Confirm.ask(
                    "Discard your edits?",
                    if (state.saved != null) "Changes since your last save are thrown away." else "Your changes are thrown away. ${screenshot.name} stays as it is.",
                    confirmLabel = "Discard",
                    onConfirm = { finish() },
                )

                else -> finish()
            }
        }
    }

    val save: () -> Unit = remember(state) {
        save@{
            val source = state.source ?: return@save
            state.commitDraft()
            if (!state.canSave || state.saving) return@save
            state.saving = true
            val doc = state.doc
            Multithreading.submit {
                val result = runCatching { EditedCopies.save(screenshot.file, source, doc) }
                MainThread.run {
                    state.saving = false
                    result.onSuccess { file ->
                        ScreenshotLibrary.changed()
                        val copy = Screenshot(file)
                        ScreenshotActions.notifySuccess("Saved a copy", "${copy.name} is next to the original.")
                        state.markSaved(copy, doc)
                    }.onFailure {
                        Screencapper.LOGGER.error("Couldn't save the edited copy of {}", screenshot.name, it)
                        ScreenshotActions.notifyError("Couldn't save", "The edited copy of ${screenshot.name} couldn't be saved.")
                    }
                }
            }
        }
    }

    val latestBase by rememberUpdatedState(base)
    val pick: (ImagePoint) -> Unit = remember(state) {
        pick@{ point ->
            val shown = latestBase ?: return@pick
            val live = state.displayItems(moving = false).drop(shown.count)
            Multithreading.submit {
                val color = EditRenderer.sample(shown.full, live, floor(point.x).toInt(), floor(point.y).toInt())
                MainThread.run { state.sampled(color) }
            }
        }
    }

    EscapeCloses(cancel)

    val canSave = load == SourceLoad.READY && state.canSave && !state.saving
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = spacing.xxl, end = spacing.xxl, top = spacing.l, bottom = spacing.xxl)
            .controllerAction(ControllerButton.START, SAVE_LABEL.resolve(), canSave, requireFocus = false, onPress = save)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !(event.isCtrlPressed || event.isMetaPressed) || state.draft != null) return@onKeyEvent false
                when {
                    event.key == Key.Z && !event.isShiftPressed -> state.undo()
                    event.key == Key.Y || (event.key == Key.Z && event.isShiftPressed) -> state.redo()
                    event.key == Key.Zero -> state.fitView()
                    event.key == Key.One -> state.actualSize()
                    else -> return@onKeyEvent false
                }
                true
            },
        verticalArrangement = Arrangement.spacedBy(spacing.l),
    ) {
        PanelHeader(
            title = screenshot.file.nameWithoutExtension,
            subtitle = "Saved as a copy. The original isn't changed.",
            onBack = cancel,
            backLabel = "Back",
            actions = {
                IconButton("undo", "Undo", state::undo, enabled = state.history.canUndo || state.draft != null)
                IconButton("redo", "Redo", state::redo, enabled = state.history.canRedo)
            },
            primaryAction = {
                TextButton("Cancel", cancel, enabled = !state.saving)
                TextButton(
                    if (state.saving) "Saving..." else "Save copy", "tick", save,
                    style = ControlStyle.Primary,
                    enabled = canSave,
                )
            },
        )
        ToolRow(state)
        OptionsRow(state, textFocus, finish)
        when (load) {
            SourceLoad.READY -> EditorCanvas(state, base, floating, pick, textFocus, Modifier.weight(1f).fillMaxWidth())
            SourceLoad.LOADING -> Spacer(Modifier.weight(1f))
            SourceLoad.FAILED -> EmptyState(
                "Can't edit this screenshot",
                Modifier.weight(1f).fillMaxWidth(),
                icon = "warning",
                text = "${screenshot.name} couldn't be opened. It may be damaged or too large.",
            )
        }
    }
}

@Composable
private fun ToolRow(state: EditorState) {
    val theme = OneConfigTheme.current
    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(theme.spacing.m)) {
        for (option in EditTool.entries) {
            Chip(option.label, state.tool == option, { state.select(option) }, icon = option.iconPath)
        }
    }
}

@Composable
private fun OptionsRow(state: EditorState, focus: FocusRequester, onDone: () -> Unit) {
    val theme = OneConfigTheme.current
    val spacing = theme.spacing
    val draft = state.draft
    LaunchedEffect(draft?.id) { if (draft != null) runCatching { focus.requestFocus() } }
    val result = state.result

    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
        val selected = (state.selection as? Selection.Object)?.let { state.doc.item(it.id) }
        when {
            result != null -> SavedActions(result, onDone)
            draft != null -> {
                Colours(state)
                EffectOptions(state)
                TextField(
                    draft.text,
                    state::typeText,
                    Modifier.weight(1f, fill = false).widthIn(max = TEXT_FIELD_MAX_WIDTH.dp),
                    placeholder = "Type your text",
                    maxLength = TextLayout.MAX_LENGTH,
                    focusRequester = focus,
                    onSubmit = state::commitDraft,
                )
                DeleteButton(state)
            }

            selected != null && state.tool == EditTool.SELECT -> {
                ItemOptions(state, selected)
                DeleteButton(state)
            }

            selected != null -> {
                ToolOptions(state)
                DeleteButton(state)
            }

            state.selection != null -> DeleteButton(state)
            else -> ToolOptions(state)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(spacing.s, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (result != null) "check-circle" else "info", null, Modifier.size(HINT_ICON.dp), theme.textColorSecondary)
            Text(if (result != null) "Saved ${result.name} next to the original." else state.hint, Modifier, theme.textColorSecondary, OneConfigTheme.typography.caption, 1, TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DeleteButton(state: EditorState) {
    TextButton("Delete", "trash", state::deleteSelected, style = ControlStyle.Danger, size = ControlSize.Compact)
}

@Composable
private fun ToolOptions(state: EditorState) {
    when (state.tool) {
        EditTool.CROP -> CropOptions(state)
        EditTool.DRAW -> {
            IconGroup {
                for (pen in Pen.entries) IconButton(pen.icon, pen.label, { state.pen = pen }, size = ControlSize.Compact, selected = state.pen == pen)
            }
            Colours(state)
            SizeChips(state)
        }

        EditTool.SHAPES -> {
            ShapePicker(state, state.shape)
            if (state.shape.fillable) Chip("Fill", state.filled, { state.pickFilled(!state.filled) }, size = ControlSize.Compact)
            Colours(state)
            SizeChips(state)
        }

        EditTool.STEPS -> {
            Colours(state)
            SizeChips(state)
        }

        EditTool.TEXT -> {
            Colours(state)
            EffectOptions(state)
            if (state.activeBox != null) TextButton("Edit text", "text-input", state::editText, size = ControlSize.Compact)
        }

        EditTool.SELECT, EditTool.BLUR -> Unit
    }
}

@Composable
private fun ItemOptions(state: EditorState, item: Item) {
    when (item) {
        is TextBox -> {
            Colours(state)
            EffectOptions(state)
            TextButton("Edit text", "text-input", state::editText, size = ControlSize.Compact)
        }

        is Shape -> {
            ShapePicker(state, item.kind)
            if (item.kind.fillable) Chip("Fill", item.filled, { state.pickFilled(!item.filled) }, size = ControlSize.Compact)
            Colours(state)
            SizeChips(state)
        }

        is Drawing, is Badge -> {
            Colours(state)
            SizeChips(state)
        }

        is Layer -> Unit
    }
}

@Composable
private fun ShapePicker(state: EditorState, current: ShapeKind) {
    IconGroup {
        for (kind in ShapeKind.entries) {
            IconButton(kind.icon, kind.label, { state.pickShape(kind) }, size = ControlSize.Compact, selected = current == kind)
        }
    }
}

@Composable
private fun IconGroup(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(OneConfigTheme.current.spacing.xxs), verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable
private fun CropOptions(state: EditorState) {
    Select(
        state.aspect, CropAspect.entries, { state.aspect = it },
        Modifier.width(ASPECT_WIDTH.dp),
        label = { it.label },
        size = ControlSize.Compact,
        contentDescription = "Crop shape",
    )
    IconGroup {
        for ((turn, icon, label) in TURNS) IconButton(editorIcon(icon), label, { state.turn(turn) }, size = ControlSize.Compact)
    }
}

@Composable
private fun EffectOptions(state: EditorState) {
    val box = state.activeBox
    val effect = box?.effect ?: state.effect
    Select(
        effect, TextEffect.entries, state::pickEffect,
        Modifier.width(EFFECT_WIDTH.dp),
        label = { it.label },
        size = ControlSize.Compact,
        contentDescription = "Text style",
    )
    if (effect == TextEffect.BOX) {
        val backdrop = if (box != null) box.backdrop else state.backdrop
        ColourButton("Box colour", backdrop, state::pickBackdrop)
        if (backdrop != null) IconButton("close", "Use the automatic box colour", { state.pickBackdrop(null) }, size = ControlSize.Compact)
    }
}

@Composable
private fun SavedActions(copy: Screenshot, onDone: () -> Unit) {
    val revision = ScreenshotLibrary.revision
    val uploading = remember(revision, copy.id) { ScreenshotActions.isUploading(copy) }
    val profiles = remember(revision) { ScreencapperConfig.profiles }
    val defaultProfile = remember(revision) { ScreencapperConfig.defaultProfile }
    val label = if (uploading) "Uploading..." else "Upload to ${defaultProfile.name}"
    TextButton("Copy", "copy", { ScreenshotActions.copy(copy) }, size = ControlSize.Compact)
    if (profiles.size > 1) {
        SplitButton(label, { ScreenshotActions.upload(copy, defaultProfile) }, uploaderMenu(profiles, defaultProfile) { ScreenshotActions.upload(copy, it) }, style = ControlStyle.Secondary, size = ControlSize.Compact, enabled = !uploading, icon = "upload")
    } else {
        TextButton(label, "upload", { ScreenshotActions.upload(copy, defaultProfile) }, size = ControlSize.Compact, enabled = !uploading)
    }
    TextButton("Done", "tick", onDone, style = ControlStyle.Primary, size = ControlSize.Compact)
}

@Composable
private fun SizeChips(state: EditorState) {
    for (option in EditSize.entries) Chip(option.label, state.size == option, { state.pickSize(option) }, size = ControlSize.Compact)
}

@Composable
private fun Colours(state: EditorState) {
    val theme = OneConfigTheme.current
    val current = state.selectedItem?.takeUnless { it is Layer }?.color ?: state.color
    val swatches = listOf("Theme colour" to OneConfigTheme.accent.toArgb()) + PALETTE + listOfNotNull(state.custom?.let { "Your colour" to it })
    ColourButton("Colour", current, state::pickCustom)
    Row(horizontalArrangement = Arrangement.spacedBy(theme.spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        for ((name, color) in swatches) {
            IconButton(name, { state.pickColor(color) }, size = ControlSize.Compact, selected = current == color) { Dot(color) }
        }
        IconButton(editorIcon("eyedropper"), "Pick from image", { state.picking = !state.picking }, size = ControlSize.Compact, selected = state.picking)
    }
}

@Composable
private fun Dot(color: Int?) {
    val theme = OneConfigTheme.current
    Box(Modifier.size(SWATCH_SIZE.dp).background(color?.let(::Color) ?: theme.textColorSecondary.copy(alpha = FAINT_ALPHA), theme.circleShape).border(1.dp, theme.borderColor, theme.circleShape))
}

@Composable
private fun ColourButton(label: String, current: Int?, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val model = rememberColorPickerModel(Color(current ?: EditorState.WHITE), hasAlpha = false)
    Box {
        TextButton(
            label,
            {
                if (!open) model.set(Color(current ?: EditorState.WHITE))
                open = !open
            },
            size = ControlSize.Compact,
            leading = { Dot(current) },
            contentDescription = "$label, opens the colour picker",
        )
        if (open) {
            ColorPickerPopup(
                model,
                onColorChanged = {},
                onDismiss = { open = false },
                placement = Placement.BelowStart,
                onCommit = { onPick(it.toArgb()) },
            )
        }
    }
}

@Composable
private fun EditorCanvas(
    state: EditorState,
    base: BaseImage?,
    floating: FloatingPreview?,
    onPick: (ImagePoint) -> Unit,
    textFocus: FocusRequester,
    modifier: Modifier,
) {
    val theme = OneConfigTheme.current
    val accent = OneConfigTheme.accent
    val scrim = theme.scrimColor
    val handleFill = theme.popupBackground
    val focus = remember { FocusRequester() }

    Box(modifier.themedSurface(SurfaceKind.Card)) {
        Box(
            Modifier
                .matchParentSize()
                .clipToBounds()
                .onSizeChanged { state.viewSize = it }
                .controllerPan { delta -> state.panView(-delta.x, -delta.y) }
                .controllerShortcut(ControllerButton.LEFT_BUMPER, "screencapper.controller.undo", "Undo", state.history.canUndo || state.draft != null, onPress = state::undo)
                .controllerShortcut(ControllerButton.RIGHT_BUMPER, "screencapper.controller.redo", "Redo", state.history.canRedo, onPress = state::redo)
                .controllerShortcut(ControllerButton.RIGHT_TRIGGER, "screencapper.controller.zoom_in", "Zoom in", state.viewport != null) { state.zoomView(CONTROLLER_ZOOM_STEP) }
                .controllerShortcut(ControllerButton.LEFT_TRIGGER, "screencapper.controller.zoom_out", "Zoom out", state.transform.zoomed) { state.zoomView(1f / CONTROLLER_ZOOM_STEP) }
                .controllerShortcut(ControllerButton.X, "screencapper.controller.delete", "Delete", state.selection != null && state.draft == null, onPress = state::deleteSelected)
                .focusRequester(focus)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || state.draft != null || (event.key != Key.Delete && event.key != Key.Backspace)) return@onKeyEvent false
                    state.deleteSelected()
                    true
                }
                .pointerHoverIcon(
                    when (state.hover) {
                        Hover.ITEM -> PointerIcon.Hand
                        Hover.HANDLE, Hover.PICK -> PointerIcon.Crosshair
                        Hover.TEXT -> PointerIcon.Text
                        Hover.NONE -> PointerIcon.Default
                    },
                )
                .editorGestures(state, onPick) { runCatching { if (state.draft != null) textFocus.requestFocus() else focus.requestFocus() } },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val viewport = state.viewport ?: return@Canvas
                val transform = state.transform
                val area = viewport.area
                val scale = viewport.scale(transform)
                val gesture = state.gesture
                fun x(value: Float) = viewport.toViewX(transform, value)
                fun y(value: Float) = viewport.toViewY(transform, value)
                fun rectOf(rect: PixelRect) = Offset(x(rect.left.toFloat()), y(rect.top.toFloat())) to Size(rect.width * scale, rect.height * scale)
                fun image(shown: BaseImage) = drawBitmap(shown.bitmap, x(0f), y(0f), shown.full.width * scale / shown.bitmap.width, shown.full.height * scale / shown.bitmap.height, FilterQuality.Low)

                val selectedArea = state.selection as? Selection.Area
                val areaDrag = gesture as? Gesture.MoveArea
                val lifted = floating?.takeIf { selectedArea != null && selectedArea.rect == it.selection.rect && (selectedArea.cutId != null || areaDrag != null) }

                clipRect(x(area.left.toFloat()), y(area.top.toFloat()), x(area.right.toFloat()), y(area.bottom.toFloat())) {
                    if (lifted != null && selectedArea != null) {
                        image(lifted.under)
                        val (holeAt, holeSize) = rectOf(selectedArea.rect)
                        drawRect(Color(selectedArea.fill), holeAt, holeSize)
                        val patchAt = selectedArea.current.offset(areaDrag?.dx ?: 0, areaDrag?.dy ?: 0)
                        drawBitmap(lifted.patch, x(patchAt.left.toFloat()), y(patchAt.top.toFloat()), scale, scale, FilterQuality.Low)
                        return@clipRect
                    }
                    if (base != null) image(base)
                    inImage(viewport, transform) {
                        for (item in state.displayItems().drop(base?.count ?: 0)) if (item !is Layer) drawItem(item)
                        (gesture as? Gesture.Stroke)?.let { drawItem(Drawing(0, it.points, state.color, it.width, state.pen == Pen.MARKER)) }
                        state.pendingShape()?.let { drawItem(it) }
                    }
                }

                if (gesture is Gesture.Marquee && state.tool != EditTool.SHAPES && state.tool != EditTool.STEPS) {
                    val rect = state.marqueeRect(gesture)
                    val (topLeft, size) = rectOf(rect)
                    when (state.tool) {
                        EditTool.CROP -> dimOutside(viewport, transform, rect, scrim)
                        EditTool.BLUR -> drawRect(accent.copy(alpha = TINT_ALPHA), topLeft, size)
                        else -> Unit
                    }
                    drawRect(accent, topLeft, size, style = Stroke(OUTLINE))
                }

                if (state.tool == EditTool.TEXT) {
                    for (box in state.displayItems().filterIsInstance<TextBox>()) {
                        val (topLeft, size) = rectOf(box.area)
                        drawRect(accent.copy(alpha = FAINT_ALPHA), topLeft, size, style = Stroke(1f))
                    }
                }

                selectedArea?.let { selected ->
                    val (topLeft, size) = rectOf(selected.current.offset(areaDrag?.dx ?: 0, areaDrag?.dy ?: 0))
                    drawRect(accent, topLeft, size, style = Stroke(OUTLINE))
                }

                val selectedId = state.draft?.id ?: (state.selection as? Selection.Object)?.id
                state.displayItems().firstOrNull { it.id == selectedId }?.let { item ->
                    if (item !is Shape) {
                        val (topLeft, size) = rectOf(item.bounds())
                        drawRect(accent, topLeft, size, style = Stroke(OUTLINE))
                    }
                    for ((_, at) in state.handlesOf(item)) handle(Offset(x(at.x), y(at.y)), accent, handleFill)
                }
            }
        }
        ZoomButtons(state)
    }
}

@Composable
private fun BoxScope.ZoomButtons(state: EditorState) {
    val theme = OneConfigTheme.current
    if (state.viewport == null) return
    Row(Modifier.align(Alignment.BottomStart).padding(theme.spacing.m), horizontalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
        TextButton("Fit", "maximise", state::fitView, style = ControlStyle.Glass, size = ControlSize.Compact, enabled = state.transform.zoomed, contentDescription = "Fit to view")
        TextButton("100%", state::actualSize, style = ControlStyle.Glass, size = ControlSize.Compact, enabled = !state.atActualSize, contentDescription = "Show at actual size")
    }
}

private fun Modifier.editorGestures(state: EditorState, onPick: (ImagePoint) -> Unit, onPress: () -> Unit): Modifier = pointerInput(state) {
    var drawing = false
    var panning = false
    var last = Offset.Zero
    val clicks = DoubleClickDetector(DOUBLE_CLICK_MILLIS, DOUBLE_CLICK_SLOP)
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: continue
            val viewport = state.viewport ?: continue
            val position = change.position
            val point = state.pointer(position.x, position.y) ?: continue
            val scale = state.pointerScale
            state.shift = event.keyboardModifiers.isShiftPressed
            when (event.type) {
                PointerEventType.Scroll -> {
                    if (Controllers.isDispatching) continue
                    val factor = ZoomGeometry.wheelFactor(change.scrollDelta.y)
                    if (factor != 1f) state.transform = viewport.zoom.zoomBy(state.transform, factor, position.x, position.y)
                    event.changes.forEach { it.consume() }
                }

                PointerEventType.Press -> {
                    if (event.buttons.isPrimaryPressed && state.picking && !drawing) {
                        onPick(point)
                        change.consume()
                    } else if (event.buttons.isPrimaryPressed && !drawing && !state.saving) {
                        drawing = true
                        state.press(point, scale, clicks.press(change.uptimeMillis, position.x, position.y))
                        onPress()
                        change.consume()
                    } else if (event.buttons.isSecondaryPressed || event.buttons.isTertiaryPressed) {
                        panning = true
                        last = position
                        change.consume()
                    }
                }

                PointerEventType.Move -> {
                    if (drawing) {
                        state.move(point, scale)
                        change.consume()
                    } else if (panning) {
                        state.transform = viewport.zoom.panBy(state.transform, position.x - last.x, position.y - last.y)
                        last = position
                        change.consume()
                    } else {
                        state.hoverAt(point, scale)
                    }
                }

                PointerEventType.Release -> {
                    if (drawing && !event.buttons.isPrimaryPressed) {
                        drawing = false
                        state.release(point, scale)
                        change.consume()
                    }
                    if (panning && !event.buttons.isSecondaryPressed && !event.buttons.isTertiaryPressed) panning = false
                }
            }
        }
    }
}

private fun EditorState.zoomView(factor: Float) {
    val viewport = viewport ?: return
    transform = viewport.zoom.zoomBy(transform, factor, viewport.viewWidth / 2f, viewport.viewHeight / 2f)
}

private fun EditorState.panView(dx: Float, dy: Float) {
    val viewport = viewport ?: return
    transform = viewport.zoom.panBy(transform, dx, dy)
}

private fun DrawScope.inImage(viewport: EditorViewport, transform: ZoomTransform, block: DrawScope.() -> Unit) {
    val zoom = viewport.scale(transform)
    withTransform({
        translate(viewport.toViewX(transform, 0f), viewport.toViewY(transform, 0f))
        scale(zoom, zoom, Offset.Zero)
    }, block)
}

private fun DrawScope.handle(center: Offset, color: Color, fill: Color) {
    val at = center - Offset(HANDLE / 2f, HANDLE / 2f)
    drawRect(fill, at, Size(HANDLE, HANDLE))
    drawRect(color, at, Size(HANDLE, HANDLE), style = Stroke(OUTLINE / 2f))
}

private fun DrawScope.dimOutside(viewport: EditorViewport, transform: ZoomTransform, keep: PixelRect, color: Color) {
    val area = viewport.area
    fun x(value: Int) = viewport.toViewX(transform, value.toFloat())
    fun y(value: Int) = viewport.toViewY(transform, value.toFloat())
    fun band(left: Float, top: Float, right: Float, bottom: Float) {
        if (right > left && bottom > top) drawRect(color, Offset(left, top), Size(right - left, bottom - top))
    }
    band(x(area.left), y(area.top), x(area.right), y(keep.top))
    band(x(area.left), y(keep.bottom), x(area.right), y(area.bottom))
    band(x(area.left), y(keep.top), x(keep.left), y(keep.bottom))
    band(x(keep.right), y(keep.top), x(area.right), y(keep.bottom))
}

private fun DrawScope.drawBitmap(bitmap: ImageBitmap, left: Float, top: Float, scaleX: Float, scaleY: Float, quality: FilterQuality) {
    val size = IntSize(bitmap.width, bitmap.height)
    withTransform({
        translate(left, top)
        scale(scaleX, scaleY, Offset.Zero)
    }) {
        drawImage(bitmap, IntOffset.Zero, size, IntOffset.Zero, size, filterQuality = quality)
    }
}

private fun previewScaled(image: CapturedImage): CapturedImage =
    if (image.width <= PREVIEW_WIDTH) image else ImageCompressor.resize(image, CompressionProfile(resize = ResizeMode.MAX_SIZE, maxWidth = PREVIEW_WIDTH))

private suspend fun rendered(source: CapturedImage, orientation: Orientation, items: List<Item>): CapturedImage {
    return withContext(Dispatchers.Default) { EditRenderer.render(orientation.apply(source), items) }
}
