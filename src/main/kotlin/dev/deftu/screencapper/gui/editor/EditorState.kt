package dev.deftu.screencapper.gui.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import dev.deftu.screencapper.editor.Badge
import dev.deftu.screencapper.editor.BlurArea
import dev.deftu.screencapper.editor.CropAspect
import dev.deftu.screencapper.editor.CutArea
import dev.deftu.screencapper.editor.DocHistory
import dev.deftu.screencapper.editor.Drawing
import dev.deftu.screencapper.editor.EditDoc
import dev.deftu.screencapper.editor.EditRenderer
import dev.deftu.screencapper.editor.EditorText
import dev.deftu.screencapper.editor.EditorViewport
import dev.deftu.screencapper.editor.Handle
import dev.deftu.screencapper.editor.ImagePoint
import dev.deftu.screencapper.editor.Item
import dev.deftu.screencapper.editor.Layer
import dev.deftu.screencapper.editor.PixelRect
import dev.deftu.screencapper.editor.Shape
import dev.deftu.screencapper.editor.ShapeGeometry
import dev.deftu.screencapper.editor.ShapeKind
import dev.deftu.screencapper.editor.TextBox
import dev.deftu.screencapper.editor.TextEffect
import dev.deftu.screencapper.editor.TextLayout
import dev.deftu.screencapper.editor.TextMetrics
import dev.deftu.screencapper.editor.Turn
import dev.deftu.screencapper.gui.ZoomTransform
import dev.deftu.screencapper.screenshot.Screenshot
import dev.deftu.screencapper.screenshot.capture.CapturedImage
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class EditTool(val label: String, val icon: String?, val hint: String) {
    SELECT("Select", "move", "Click anything you added to move it, or drag to cut out part of the screenshot. Double-click text to change it."),
    CROP("Crop", "scissors", "Drag to pick the part to keep."),
    DRAW("Draw", "paintbrush", "Drag to draw."),
    SHAPES("Shapes", null, "Drag to draw a shape. Hold Shift to keep it straight or even."),
    STEPS("Steps", null, "Click to add the next number."),
    TEXT("Text", "text", "Click to start typing, or drag to set how wide the text can be."),
    BLUR("Blur", "eye-off", "Drag over anything you want hidden, including things you added."),
}

internal enum class Pen(val label: String, val hint: String) {
    PEN("Pen", "Drag to draw."),
    MARKER("Marker", "Drag to highlight."),
}

internal enum class EditSize(val label: String, val stroke: Float, val badge: Float) {
    SMALL("Small", 0.004f, 0.018f),
    MEDIUM("Medium", 0.008f, 0.026f),
    LARGE("Large", 0.016f, 0.04f),
}

internal val ShapeKind.label: String
    get() = when (this) {
        ShapeKind.ARROW -> "Arrow"
        ShapeKind.RECTANGLE -> "Rectangle"
        ShapeKind.ELLIPSE -> "Ellipse"
        ShapeKind.LINE -> "Line"
    }

internal val ShapeKind.fillable: Boolean get() = this == ShapeKind.RECTANGLE || this == ShapeKind.ELLIPSE

internal val TextEffect.label: String
    get() = when (this) {
        TextEffect.SHADOW -> "Plain"
        TextEffect.OUTLINE -> "Outline"
        TextEffect.BOX -> "Box"
    }

internal enum class Hover { NONE, ITEM, HANDLE, TEXT, PICK }

internal object EditorMemory {
    var custom: Int? = null
}

internal class SavedCopy(val screenshot: Screenshot, val doc: EditDoc)

internal sealed interface Selection {
    data class Object(val id: Int) : Selection

    class Area(val rect: PixelRect, val fill: Int, val patch: CapturedImage, val dx: Int = 0, val dy: Int = 0, val cutId: Int? = null) : Selection {
        val current: PixelRect get() = rect.offset(dx, dy)
    }
}

internal sealed interface Gesture {
    class Marquee(val start: ImagePoint, val end: ImagePoint) : Gesture
    class Stroke(val points: List<ImagePoint>, val width: Float) : Gesture
    class MoveItem(val id: Int, val start: ImagePoint, val dx: Float = 0f, val dy: Float = 0f) : Gesture
    class MoveArea(val start: ImagePoint, val dx: Int = 0, val dy: Int = 0) : Gesture
    class Resize(val handle: Handle, val start: Item, val item: Item) : Gesture
}

@Stable
internal class EditorState(private val metrics: TextMetrics = EditorText) {
    var history by mutableStateOf(DocHistory())
        private set
    var tool by mutableStateOf(EditTool.CROP)
        private set
    var color by mutableIntStateOf(WHITE)
        private set
    var custom by mutableStateOf(EditorMemory.custom)
        private set
    var size by mutableStateOf(EditSize.MEDIUM)
    var pen by mutableStateOf(Pen.PEN)
    var shape by mutableStateOf(ShapeKind.ARROW)
        private set
    var filled by mutableStateOf(false)
        private set
    var effect by mutableStateOf(TextEffect.SHADOW)
        private set
    var backdrop by mutableStateOf<Int?>(null)
        private set
    var aspect by mutableStateOf(CropAspect.FREE)
    var picking by mutableStateOf(false)
    var shift = false
    var source by mutableStateOf<CapturedImage?>(null)
    var transform by mutableStateOf(ZoomTransform())
    var viewSize by mutableStateOf(IntSize.Zero)
    var saving by mutableStateOf(false)
    var saved by mutableStateOf<SavedCopy?>(null)
        private set
    private var resultOpen by mutableStateOf(false)
    var selection by mutableStateOf<Selection?>(null)
    var gesture by mutableStateOf<Gesture?>(null)
        private set
    var draft by mutableStateOf<TextBox?>(null)
        private set
    var pendingLift by mutableStateOf<PixelRect?>(null)
        private set
    var hover by mutableStateOf(Hover.NONE)
        private set

    val doc: EditDoc get() = history.current

    val bounds: PixelRect
        get() = source?.let { doc.orientation.size(it.width, it.height) } ?: PixelRect(0, 0, 0, 0)

    val area: PixelRect
        get() = bounds.takeUnless { it.isEmpty }?.let { EditRenderer.visibleArea(doc, it.width, it.height) } ?: PixelRect(0, 0, 0, 0)

    val viewport: EditorViewport?
        get() {
            val area = area
            if (area.isEmpty || viewSize.width <= 0 || viewSize.height <= 0) return null
            return EditorViewport(viewSize.width.toFloat(), viewSize.height.toFloat(), area)
        }

    val pointerScale: Float get() = viewport?.scale(transform) ?: 1f

    fun pointer(x: Float, y: Float): ImagePoint? = viewport?.toImage(transform, x, y)

    val dirty: Boolean get() = !doc.isEmpty || draft?.text?.isNotBlank() == true

    val canSave: Boolean get() = dirty && (saved?.doc != doc || draft?.text?.isNotBlank() == true)

    val result: Screenshot? get() = saved?.takeIf { resultOpen && it.doc == doc && draft == null }?.screenshot

    val hint: String
        get() = when {
            picking -> "Click the picture to pick a colour."
            draft != null -> "Type, then press Enter. Corners scale the text, sides change the width."
            selection is Selection.Area -> "Drag the area to move it. Delete clears it."
            selection is Selection.Object && tool == EditTool.SELECT -> "Drag to move it. Delete removes it."
            selection is Selection.Object -> "Drag it to move it, or start somewhere else to add another. Delete removes it."
            tool == EditTool.DRAW -> pen.hint
            else -> tool.hint
        }

    val activeBox: TextBox?
        get() = draft ?: (selection as? Selection.Object)?.let { doc.item(it.id) as? TextBox }

    val selectedItem: Item?
        get() = draft ?: (selection as? Selection.Object)?.let { doc.item(it.id) }

    private val shortSide: Int get() = source?.let { min(it.width, it.height) } ?: 0

    fun strokeWidth(): Float = max(MIN_STROKE, shortSide * size.stroke)

    private fun markerWidth(): Float = strokeWidth() * MARKER_WIDTH

    private fun badgeRadius(): Float = max(MIN_BADGE, shortSide * size.badge)

    private fun textSize(): Float = max(TextLayout.MIN_SIZE, shortSide * TEXT_SIZE)

    private fun maxTextSize(): Float = max(TextLayout.MIN_SIZE, shortSide * MAX_TEXT)

    fun displayItems(moving: Boolean = true): List<Item> {
        val gesture = gesture
        var items = doc.items
        draft?.let { box -> items = if (items.any { it.id == box.id }) items.map { if (it.id == box.id) box else it } else items + box }
        return items.map { item ->
            when {
                moving && gesture is Gesture.MoveItem && gesture.id == item.id -> item.moved(gesture.dx, gesture.dy)
                gesture is Gesture.Resize && gesture.item.id == item.id -> gesture.item
                else -> item
            }
        }
    }

    fun pendingShape(): Shape? = (gesture as? Gesture.Marquee)?.takeIf { tool == EditTool.SHAPES }?.let { shapeFrom(it.start, it.end) }

    fun marqueeRect(marquee: Gesture.Marquee): PixelRect =
        if (tool == EditTool.CROP) CropAspect.lock(marquee.start, marquee.end, aspect.ratio(bounds), area)
        else PixelRect.spanning(marquee.start.x, marquee.start.y, marquee.end.x, marquee.end.y).clampTo(area)

    fun handlesOf(item: Item): List<Pair<Handle, ImagePoint>> = when (item) {
        is TextBox -> {
            val rect = item.area
            val middle = (rect.top + rect.bottom) / 2f
            listOf(
                Handle.TOP_LEFT to ImagePoint(rect.left.toFloat(), rect.top.toFloat()),
                Handle.TOP_RIGHT to ImagePoint(rect.right.toFloat(), rect.top.toFloat()),
                Handle.BOTTOM_LEFT to ImagePoint(rect.left.toFloat(), rect.bottom.toFloat()),
                Handle.BOTTOM_RIGHT to ImagePoint(rect.right.toFloat(), rect.bottom.toFloat()),
                Handle.LEFT to ImagePoint(rect.left.toFloat(), middle),
                Handle.RIGHT to ImagePoint(rect.right.toFloat(), middle),
            )
        }

        is Shape -> listOf(Handle.START to item.start, Handle.END to item.end)
        is BlurArea -> {
            val rect = item.area
            listOf(
                Handle.TOP_LEFT to ImagePoint(rect.left.toFloat(), rect.top.toFloat()),
                Handle.TOP_RIGHT to ImagePoint(rect.right.toFloat(), rect.top.toFloat()),
                Handle.BOTTOM_LEFT to ImagePoint(rect.left.toFloat(), rect.bottom.toFloat()),
                Handle.BOTTOM_RIGHT to ImagePoint(rect.right.toFloat(), rect.bottom.toFloat()),
            )
        }

        else -> emptyList()
    }

    private fun handleAt(item: Item, point: ImagePoint, slop: Float): Handle? =
        handlesOf(item).firstOrNull { (_, at) -> hypot(point.x - at.x, point.y - at.y) <= slop }?.first

    fun select(next: EditTool) {
        commitDraft()
        selection = null
        gesture = null
        pendingLift = null
        picking = false
        resultOpen = false
        tool = next
    }

    fun pickSize(value: EditSize) {
        size = value
        val item = selectedItem ?: return
        if (item is TextBox || item is Layer) return
        changeItem(item.id) { resized(it) }
    }

    private fun resized(item: Item): Item = when (item) {
        is Shape -> item.copy(width = strokeWidth())
        is Badge -> item.copy(radius = badgeRadius())
        is Drawing -> item.copy(width = if (item.marker) markerWidth() else strokeWidth())
        else -> item
    }

    fun pickColor(value: Int) {
        color = value
        draft?.let {
            draft = it.copy(color = value)
            return
        }
        val item = selectedItem ?: return
        if (item !is Layer && item.color != value) history = history.push(doc.replacing(item.recoloured(value)))
    }

    fun pickCustom(value: Int) {
        val opaque = value or OPAQUE
        custom = opaque
        EditorMemory.custom = opaque
        pickColor(opaque)
    }

    fun pickShape(kind: ShapeKind) {
        shape = kind
        val item = selectedItem as? Shape ?: return
        if (item.kind != kind) history = history.push(doc.replacing(item.copy(kind = kind, filled = item.filled && kind.fillable)))
    }

    fun pickFilled(value: Boolean) {
        filled = value
        val item = selectedItem as? Shape ?: return
        if (item.kind.fillable && item.filled != value) history = history.push(doc.replacing(item.copy(filled = value)))
    }

    fun pickEffect(value: TextEffect) {
        effect = value
        val box = activeBox ?: return
        changeItem(box.id) { (it as TextBox).copy(effect = value) }
    }

    fun pickBackdrop(value: Int?) {
        backdrop = value
        val box = activeBox ?: return
        changeItem(box.id) { (it as TextBox).copy(backdrop = value) }
    }

    fun sampled(value: Int) {
        picking = false
        pickCustom(value)
    }

    fun editText() {
        val box = (selection as? Selection.Object)?.let { doc.item(it.id) as? TextBox } ?: return
        selection = null
        draft = box
    }

    fun turn(turn: Turn) {
        commitDraft()
        settle()
        val size = bounds
        if (size.isEmpty) return
        history = history.push(doc.turned(turn, size.width, size.height))
        transform = ZoomTransform()
    }

    fun fitView() {
        transform = ZoomTransform()
    }

    fun actualSize() {
        val viewport = viewport ?: return
        transform = viewport.zoom.zoomBy(ZoomTransform(), 1f / viewport.zoom.fitScale, viewport.viewWidth / 2f, viewport.viewHeight / 2f)
    }

    val atActualSize: Boolean get() = viewport?.let { abs(it.scale(transform) - 1f) < ACTUAL_EPSILON } ?: true

    fun markSaved(copy: Screenshot, doc: EditDoc) {
        saved = SavedCopy(copy, doc)
        resultOpen = true
    }

    fun undo() {
        if (draft != null) {
            draft = null
            return
        }
        settle()
        history = history.undo()
        transform = ZoomTransform()
    }

    fun redo() {
        settle()
        history = history.redo()
        transform = ZoomTransform()
    }

    fun typeText(value: String) {
        draft = draft?.let { TextLayout.fit(it.copy(text = value), metrics) }
    }

    fun commitDraft() {
        val box = draft ?: return
        draft = null
        val existing = doc.item(box.id)
        val next = when {
            box.text.isBlank() -> if (existing != null) doc.without(box.id) else doc
            existing == null -> doc.copy(items = doc.items + box, nextId = max(doc.nextId, box.id + 1))
            else -> doc.replacing(box)
        }
        history = history.push(next)
        if (box.text.isNotBlank()) selection = Selection.Object(box.id)
    }

    fun escape(): Boolean = when {
        picking -> {
            picking = false
            true
        }

        draft != null -> {
            draft = null
            true
        }

        selection != null -> {
            selection = null
            true
        }

        else -> false
    }

    fun deleteSelected() {
        draft?.let { box ->
            draft = null
            if (doc.item(box.id) != null) history = history.push(doc.without(box.id))
            return
        }
        when (val current = selection) {
            is Selection.Object -> history = history.push(doc.without(current.id))
            is Selection.Area -> {
                val cut = current.cutId
                history = history.push(
                    if (cut != null) doc.replacing(CutArea(cut, current.rect, current.dx, current.dy, current.fill, dropped = true))
                    else doc.adding { CutArea(it, current.rect, current.dx, current.dy, current.fill, dropped = true) }.first,
                )
            }

            null -> return
        }
        selection = null
    }

    fun lifted(selection: Selection.Area) {
        pendingLift = null
        this.selection = selection
    }

    fun press(point: ImagePoint, scale: Float, double: Boolean = false) {
        val slop = HIT_SLOP / scale
        val active = selectedItem?.let { item -> displayItems().firstOrNull { it.id == item.id } }
        if (active != null) {
            handleAt(active, point, HANDLE_SLOP / scale)?.let { handle ->
                gesture = Gesture.Resize(handle, active, active)
                return
            }
            if (tool != EditTool.SELECT && tool != EditTool.TEXT && tool != EditTool.CROP && active.hits(point, slop)) {
                gesture = Gesture.MoveItem(active.id, point)
                return
            }
        }
        when (tool) {
            EditTool.SELECT -> {
                val area = selection as? Selection.Area
                if (area != null && area.current.contains(point.x, point.y)) {
                    gesture = Gesture.MoveArea(point)
                    return
                }
                val hit = displayItems().lastOrNull { it.hits(point, slop) }
                if (hit is TextBox && double) {
                    commitDraft()
                    selection = null
                    draft = doc.item(hit.id) as? TextBox ?: hit
                    gesture = Gesture.MoveItem(hit.id, point)
                } else if (hit != null) {
                    commitDraft()
                    selection = Selection.Object(hit.id)
                    gesture = Gesture.MoveItem(hit.id, point)
                } else {
                    commitDraft()
                    selection = null
                    gesture = Gesture.Marquee(point, point)
                }
            }

            EditTool.TEXT -> {
                val hit = displayItems().lastOrNull { it.hits(point, slop) }
                when {
                    hit is TextBox -> {
                        if (draft?.id != hit.id) {
                            commitDraft()
                            selection = null
                            draft = doc.item(hit.id) as? TextBox ?: hit
                        }
                        gesture = Gesture.MoveItem(hit.id, point)
                    }

                    hit != null && hit.id == active?.id -> gesture = Gesture.MoveItem(hit.id, point)
                    else -> {
                        commitDraft()
                        selection = null
                        gesture = Gesture.Marquee(point, point)
                    }
                }
            }

            EditTool.DRAW -> {
                commitDraft()
                selection = null
                gesture = Gesture.Stroke(listOf(point), if (pen == Pen.MARKER) markerWidth() else strokeWidth())
            }

            EditTool.SHAPES, EditTool.STEPS, EditTool.BLUR -> {
                commitDraft()
                selection = null
                gesture = Gesture.Marquee(point, point)
            }

            EditTool.CROP -> gesture = Gesture.Marquee(point, point)
        }
    }

    fun move(point: ImagePoint, scale: Float) {
        gesture = when (val current = gesture) {
            is Gesture.Marquee -> Gesture.Marquee(current.start, point)
            is Gesture.Stroke -> {
                val last = current.points.last()
                if (hypot(point.x - last.x, point.y - last.y) * scale >= MIN_STEP) Gesture.Stroke(current.points + point, current.width) else current
            }

            is Gesture.MoveItem -> Gesture.MoveItem(current.id, current.start, (point.x - current.start.x).roundToInt().toFloat(), (point.y - current.start.y).roundToInt().toFloat())
            is Gesture.MoveArea -> Gesture.MoveArea(current.start, (point.x - current.start.x).roundToInt(), (point.y - current.start.y).roundToInt())
            is Gesture.Resize -> Gesture.Resize(current.handle, current.start, reshaped(current, point))
            null -> null
        }
    }

    fun release(point: ImagePoint, scale: Float) {
        val image = source
        val current = gesture
        gesture = null
        if (image == null || current == null) return
        when (current) {
            is Gesture.Marquee -> finishMarquee(image, Gesture.Marquee(current.start, point), scale)
            is Gesture.Stroke -> {
                val marker = pen == Pen.MARKER
                val stroke = Drawing(0, current.points, color, current.width, marker)
                add { stroke.copy(id = it) }
            }

            is Gesture.MoveItem -> if (current.dx != 0f || current.dy != 0f) changeItem(current.id) { it.moved(current.dx, current.dy) }
            is Gesture.MoveArea -> (selection as? Selection.Area)?.let { area ->
                if (current.dx == 0 && current.dy == 0) return
                val dx = area.dx + current.dx
                val dy = area.dy + current.dy
                val cut = area.cutId
                val id = if (cut != null) {
                    history = history.push(doc.replacing(CutArea(cut, area.rect, dx, dy, area.fill)))
                    cut
                } else {
                    val (next, added) = doc.adding { CutArea(it, area.rect, dx, dy, area.fill) }
                    history = history.push(next)
                    added.id
                }
                selection = Selection.Area(area.rect, area.fill, area.patch, dx, dy, id)
            }

            is Gesture.Resize -> changeItem(current.item.id) { current.item }
        }
    }

    private fun add(make: (Int) -> Item) {
        val (next, item) = doc.adding(make)
        history = history.push(next)
        selection = Selection.Object(item.id)
    }

    private fun clamped(point: ImagePoint): ImagePoint {
        val bounds = bounds
        return ImagePoint(point.x.coerceIn(bounds.left.toFloat(), bounds.right.toFloat()), point.y.coerceIn(bounds.top.toFloat(), bounds.bottom.toFloat()))
    }

    private fun shapeFrom(start: ImagePoint, end: ImagePoint): Shape {
        val from = clamped(start)
        val to = clamped(if (shift) ShapeGeometry.constrained(shape, from, end) else end)
        return Shape(0, shape, from, to, color, strokeWidth(), filled && shape.fillable)
    }

    private fun changeItem(id: Int, change: (Item) -> Item) {
        val box = draft
        if (box != null && box.id == id) {
            draft = change(box) as TextBox
            return
        }
        val item = doc.item(id) ?: return
        history = history.push(doc.replacing(change(item)))
    }

    private fun finishMarquee(image: CapturedImage, marquee: Gesture.Marquee, scale: Float) {
        val rect = marqueeRect(marquee)
        val big = rect.width >= MIN_AREA && rect.height >= MIN_AREA
        when (tool) {
            EditTool.CROP -> if (big) {
                history = history.push(doc.withCrop(rect))
                transform = ZoomTransform()
            }

            EditTool.BLUR -> if (big) {
                val block = EditRenderer.blurBlock(image.width, image.height)
                add { BlurArea(it, rect, block) }
            }

            EditTool.SELECT -> if (big) pendingLift = rect
            EditTool.TEXT -> {
                val size = textSize()
                val dragged = rect.width >= max(MIN_BOX, (MIN_BOX_VIEW / scale).roundToInt())
                val left = if (dragged) rect.left else marquee.start.x.roundToInt().coerceIn(area.left, area.right)
                val top = if (dragged) rect.top else (marquee.start.y - metrics.lineHeight(size) / 2f).roundToInt()
                val maxWidth = max((TextLayout.MIN_WIDTH_EM * size).roundToInt(), min(area.right - left, (area.width * AUTO_WIDTH).roundToInt()))
                draft = TextLayout.create(doc.nextId, left, top, "", color, size, if (dragged) rect.width else null, maxWidth, metrics)
                    .copy(effect = effect, backdrop = backdrop)
            }

            EditTool.SHAPES -> {
                val shape = shapeFrom(marquee.start, marquee.end)
                if (hypot(shape.end.x - shape.start.x, shape.end.y - shape.start.y) * scale >= MIN_SHAPE_VIEW) add { shape.copy(id = it) }
            }

            EditTool.STEPS -> {
                val radius = badgeRadius()
                add { Badge(it, clamped(marquee.end), 0, color, radius) }
            }

            EditTool.DRAW -> Unit
        }
    }

    private fun settle() {
        draft = null
        selection = null
        gesture = null
        pendingLift = null
        picking = false
    }

    private fun reshaped(resize: Gesture.Resize, point: ImagePoint): Item = when (val start = resize.start) {
        is TextBox -> resizedText(start, resize.handle, point)
        is Shape -> {
            val fixed = if (resize.handle == Handle.START) start.end else start.start
            val moved = clamped(if (shift) ShapeGeometry.constrained(start.kind, fixed, point) else point)
            if (resize.handle == Handle.START) start.copy(start = moved) else start.copy(end = moved)
        }

        is BlurArea -> {
            val rect = start.area
            val leftSide = resize.handle == Handle.TOP_LEFT || resize.handle == Handle.BOTTOM_LEFT
            val topSide = resize.handle == Handle.TOP_LEFT || resize.handle == Handle.TOP_RIGHT
            val fixedX = if (leftSide) rect.right else rect.left
            val fixedY = if (topSide) rect.bottom else rect.top
            val next = PixelRect.spanning(fixedX.toFloat(), fixedY.toFloat(), point.x, point.y).clampTo(bounds)
            if (next.width < MIN_AREA || next.height < MIN_AREA) start else start.copy(area = next)
        }

        else -> start
    }

    private fun resizedText(start: TextBox, handle: Handle, point: ImagePoint): TextBox {
        val area = start.area
        return when (handle) {
            Handle.LEFT -> TextLayout.widened(start, area.right - point.x, Handle.LEFT, metrics)
            Handle.RIGHT -> TextLayout.widened(start, point.x - area.left, Handle.RIGHT, metrics)
            else -> {
                val leftSide = handle == Handle.TOP_LEFT || handle == Handle.BOTTOM_LEFT
                val topSide = handle == Handle.TOP_LEFT || handle == Handle.TOP_RIGHT
                val width = if (leftSide) area.right - point.x else point.x - area.left
                val height = if (topSide) area.bottom - point.y else point.y - area.top
                val factor = max(width / area.width.coerceAtLeast(1), height / area.height.coerceAtLeast(1)).coerceAtLeast(MIN_FACTOR)
                TextLayout.scaled(start, factor, handle, maxTextSize(), metrics)
            }
        }
    }

    fun hoverAt(point: ImagePoint, scale: Float) {
        val active = selectedItem
        val slop = HIT_SLOP / scale
        hover = when {
            gesture != null -> hover
            picking -> Hover.PICK
            active != null && handleAt(active, point, HANDLE_SLOP / scale) != null -> Hover.HANDLE
            tool == EditTool.SELECT && displayItems().any { it.hits(point, slop) } -> Hover.ITEM
            tool == EditTool.TEXT && displayItems().any { it is TextBox && it.hits(point, slop) } -> Hover.ITEM
            active != null && tool != EditTool.CROP && active.hits(point, slop) -> Hover.ITEM
            tool == EditTool.TEXT -> Hover.TEXT
            else -> Hover.NONE
        }
    }

    companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val HIT_SLOP = 6f
        const val HANDLE_SLOP = 10f
        private const val OPAQUE = 0xFF000000.toInt()
        private const val TEXT_SIZE = 0.05f
        private const val MIN_STROKE = 2f
        private const val MIN_BADGE = 8f
        private const val MARKER_WIDTH = 4f
        private const val MAX_TEXT = 0.3f
        private const val AUTO_WIDTH = 0.6f
        private const val MIN_FACTOR = 0.01f
        private const val MIN_STEP = 2f
        private const val MIN_AREA = 2
        private const val MIN_BOX = 8
        private const val MIN_BOX_VIEW = 16f
        private const val MIN_SHAPE_VIEW = 4f
        private const val ACTUAL_EPSILON = 0.001f
    }
}
