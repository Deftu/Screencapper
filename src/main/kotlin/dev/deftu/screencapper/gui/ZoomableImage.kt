@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.gui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.polyfrost.compose.backend.asComposeImageBitmap
import org.polyfrost.compose.host.controller.ControllerButton
import org.polyfrost.compose.host.controller.Controllers
import org.polyfrost.compose.host.controller.controllerPan
import org.polyfrost.compose.host.controller.isFromController
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.controllerShortcut
import org.polyfrost.oneconfig.api.ui.v1.components.rememberImageBitmap
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import java.io.File
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class ZoomTransform(val zoom: Float = 1f, val x: Float = 0f, val y: Float = 0f) {
    val zoomed: Boolean get() = zoom > 1f + ZoomGeometry.ZOOM_EPSILON
}

internal data class ImageRegion(val x: Int, val y: Int, val width: Int, val height: Int, val subsampling: Int)

internal class ZoomGeometry(val viewWidth: Float, val viewHeight: Float, val imageWidth: Int, val imageHeight: Int) {
    val fitScale: Float = min(viewWidth / imageWidth, viewHeight / imageHeight)
    val contentWidth: Float = imageWidth * fitScale
    val contentHeight: Float = imageHeight * fitScale
    val maxZoom: Float = max(MAX_PIXEL_SCALE / fitScale, MIN_MAX_ZOOM)
    private val doubleClickZoom: Float = max(1f / fitScale, DOUBLE_CLICK_MIN_ZOOM).coerceAtMost(maxZoom)

    fun clamp(transform: ZoomTransform): ZoomTransform {
        val zoom = transform.zoom.coerceIn(1f, maxZoom)
        if (zoom <= 1f + ZOOM_EPSILON) return ZoomTransform()
        val limitX = max(0f, (contentWidth * zoom - viewWidth) / 2f)
        val limitY = max(0f, (contentHeight * zoom - viewHeight) / 2f)
        return ZoomTransform(zoom, transform.x.coerceIn(-limitX, limitX), transform.y.coerceIn(-limitY, limitY))
    }

    fun zoomBy(transform: ZoomTransform, factor: Float, px: Float, py: Float): ZoomTransform =
        zoomTo(transform, transform.zoom * factor, px, py)

    fun panBy(transform: ZoomTransform, dx: Float, dy: Float): ZoomTransform =
        clamp(transform.copy(x = transform.x + dx, y = transform.y + dy))

    fun toggle(transform: ZoomTransform, px: Float, py: Float): ZoomTransform =
        if (transform.zoomed) ZoomTransform() else zoomTo(transform, doubleClickZoom, px, py)

    fun pixelScale(transform: ZoomTransform): Float = fitScale * transform.zoom

    fun imageX(transform: ZoomTransform, px: Float): Float =
        (px - viewWidth / 2f - transform.x) / pixelScale(transform) + imageWidth / 2f

    fun imageY(transform: ZoomTransform, py: Float): Float =
        (py - viewHeight / 2f - transform.y) / pixelScale(transform) + imageHeight / 2f

    fun sharpRegion(transform: ZoomTransform, baseWidth: Int): ImageRegion? {
        if (imageWidth <= baseWidth) return null
        if (pixelScale(transform) * imageWidth / baseWidth <= SHARPEN_THRESHOLD) return null
        return regionFor(transform)
    }

    fun covers(region: ImageRegion, transform: ZoomTransform): Boolean {
        if (region.subsampling > regionFor(transform).subsampling) return false
        val left = imageX(transform, 0f).coerceAtLeast(0f)
        val top = imageY(transform, 0f).coerceAtLeast(0f)
        val right = imageX(transform, viewWidth).coerceAtMost(imageWidth.toFloat())
        val bottom = imageY(transform, viewHeight).coerceAtMost(imageHeight.toFloat())
        return left >= region.x && top >= region.y && right <= region.x + region.width && bottom <= region.y + region.height
    }

    private fun zoomTo(transform: ZoomTransform, target: Float, px: Float, py: Float): ZoomTransform {
        val zoom = target.coerceIn(1f, maxZoom)
        val anchorX = px - viewWidth / 2f
        val anchorY = py - viewHeight / 2f
        val ratio = zoom / transform.zoom
        return clamp(ZoomTransform(zoom, anchorX - (anchorX - transform.x) * ratio, anchorY - (anchorY - transform.y) * ratio))
    }

    private fun regionFor(transform: ZoomTransform): ImageRegion {
        val left = imageX(transform, 0f).coerceAtLeast(0f)
        val top = imageY(transform, 0f).coerceAtLeast(0f)
        val right = imageX(transform, viewWidth).coerceAtMost(imageWidth.toFloat())
        val bottom = imageY(transform, viewHeight).coerceAtMost(imageHeight.toFloat())
        val marginX = (right - left) * REGION_MARGIN
        val marginY = (bottom - top) * REGION_MARGIN
        val x0 = floor(left - marginX).toInt().coerceAtLeast(0)
        val y0 = floor(top - marginY).toInt().coerceAtLeast(0)
        val x1 = ceil(right + marginX).toInt().coerceAtMost(imageWidth)
        val y1 = ceil(bottom + marginY).toInt().coerceAtMost(imageHeight)
        val width = (x1 - x0).coerceAtLeast(1)
        val height = (y1 - y0).coerceAtLeast(1)
        var subsampling = floor(1f / pixelScale(transform)).toInt().coerceAtLeast(1)
        while (((width + subsampling - 1) / subsampling).toLong() * ((height + subsampling - 1) / subsampling) > MAX_REGION_PIXELS) subsampling++
        return ImageRegion(x0, y0, width, height, subsampling)
    }

    companion object {
        const val ZOOM_EPSILON = 0.001f
        const val MAX_PIXEL_SCALE = 4f
        const val MIN_MAX_ZOOM = 2f
        const val DOUBLE_CLICK_MIN_ZOOM = 2f
        const val SHARPEN_THRESHOLD = 1.2f
        const val REGION_MARGIN = 0.15f
        const val MAX_REGION_PIXELS = 8L * 1024 * 1024
        private const val WHEEL_NOTCH = 150f
        private const val NOTCH_ZOOM = 1.25f

        fun wheelFactor(scrollY: Float): Float = exp(-scrollY / WHEEL_NOTCH * ln(NOTCH_ZOOM))
    }
}

@Stable
internal class ZoomState {
    var transform: ZoomTransform by mutableStateOf(ZoomTransform())

    var geometry: ZoomGeometry? by mutableStateOf(null)

    fun reset() {
        transform = ZoomTransform()
    }

    fun zoomBy(factor: Float) {
        val layout = geometry ?: return
        transform = layout.zoomBy(transform, factor, layout.viewWidth / 2f, layout.viewHeight / 2f)
    }

    fun toggle() {
        val layout = geometry ?: return
        transform = layout.toggle(transform, layout.viewWidth / 2f, layout.viewHeight / 2f)
    }

    fun panBy(dx: Float, dy: Float) {
        val layout = geometry ?: return
        transform = layout.panBy(transform, dx, dy)
    }
}

internal fun Modifier.controllerZoom(state: ZoomState, onPress: (() -> Unit)? = null): Modifier {
    val zoomed = state.transform.zoomed
    return this
        .controllerPan(zoomed) { delta -> state.panBy(-delta.x, -delta.y) }
        .controllerShortcut(ControllerButton.RIGHT_TRIGGER, "screencapper.controller.zoom_in", "Zoom in", state.geometry != null) { state.zoomBy(CONTROLLER_ZOOM_STEP) }
        .controllerShortcut(ControllerButton.LEFT_TRIGGER, "screencapper.controller.zoom_out", "Zoom out", zoomed) { state.zoomBy(1f / CONTROLLER_ZOOM_STEP) }
        .onKeyEvent { event ->
            if (!event.isFromController || (event.key != Key.Enter && event.key != Key.NumPadEnter)) return@onKeyEvent false
            if (event.type == KeyEventType.KeyDown) (onPress ?: state::toggle)()
            true
        }
}

@Composable
internal fun rememberZoomState(key: Any?): ZoomState = remember(key) { ZoomState() }

@Composable
internal fun ZoomableImage(
    file: File,
    imageSize: IntSize?,
    baseWidths: IntArray,
    modifier: Modifier = Modifier,
    state: ZoomState = rememberZoomState(file),
    doubleClickZooms: Boolean = true,
) {
    val path = remember(file) { file.toPath() }
    val bases = baseWidths.map { rememberImageBitmap(path, it) }
    val fallback = bases.lastOrNull { it != null }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val sourceSize = imageSize ?: fallback?.let { IntSize(it.width, it.height) }
    val geometry = remember(viewSize, sourceSize) {
        if (viewSize.width <= 0 || viewSize.height <= 0 || sourceSize == null || sourceSize.width <= 0 || sourceSize.height <= 0) null
        else ZoomGeometry(viewSize.width.toFloat(), viewSize.height.toFloat(), sourceSize.width, sourceSize.height)
    }
    val latestGeometry by rememberUpdatedState(geometry)
    LaunchedEffect(geometry) {
        state.geometry = geometry
        geometry?.let { state.transform = it.clamp(state.transform) }
    }
    val sharp = rememberSharpRegion(file, state, geometry.takeIf { imageSize != null }, baseWidths.maxOrNull() ?: 0)
    val fade by animateFloatAsState(if (fallback != null) 1f else 0f)
    val crisp by remember(geometry, state) { derivedStateOf { geometry != null && geometry.pixelScale(state.transform) >= CRISP_PIXEL_SCALE } }

    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .clipToBounds()
                .onSizeChanged { viewSize = it }
                .zoomGestures(state, { latestGeometry }, doubleClickZooms),
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val transform = state.transform
                        scaleX = transform.zoom
                        scaleY = transform.zoom
                        translationX = transform.x
                        translationY = transform.y
                        alpha = fade
                    },
            ) {
                val layout = geometry ?: return@Canvas
                val left = (size.width - layout.contentWidth) / 2f
                val top = (size.height - layout.contentHeight) / 2f
                for (base in bases) {
                    if (base != null) drawBitmap(base, left, top, layout.contentWidth / base.width, layout.contentHeight / base.height, FilterQuality.Low)
                }
                if (sharp != null) {
                    val scale = layout.fitScale * sharp.region.subsampling
                    drawBitmap(
                        sharp.bitmap,
                        left + sharp.region.x * layout.fitScale,
                        top + sharp.region.y * layout.fitScale,
                        scale,
                        scale,
                        if (crisp) FilterQuality.None else FilterQuality.Low,
                    )
                }
            }
        }
        if (geometry != null) ZoomChip(state, geometry, Modifier.align(Alignment.BottomStart).padding(OneConfigTheme.current.spacing.m))
    }
}

@Composable
private fun ZoomChip(state: ZoomState, geometry: ZoomGeometry, modifier: Modifier) {
    val transform = state.transform
    if (!transform.zoomed) return
    TextButton(
        "${(geometry.pixelScale(transform) * 100f).roundToInt()}%",
        state::reset,
        modifier,
        style = ControlStyle.Glass,
        size = ControlSize.Compact,
        contentDescription = "Reset zoom to fit",
    )
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

private class SharpBitmap(val region: ImageRegion, val bitmap: ImageBitmap)

private val sharpDecodes = Mutex()

@Composable
private fun rememberSharpRegion(file: File, state: ZoomState, geometry: ZoomGeometry?, baseWidth: Int): SharpBitmap? {
    var sharp by remember(file) { mutableStateOf<SharpBitmap?>(null) }
    LaunchedEffect(file, geometry, baseWidth) {
        if (geometry == null) {
            sharp = null
            return@LaunchedEffect
        }
        snapshotFlow { state.transform }.collectLatest { transform ->
            val wanted = geometry.sharpRegion(transform, baseWidth)
            if (wanted == null) {
                sharp = null
                return@collectLatest
            }
            if (sharp?.let { geometry.covers(it.region, transform) } == true) return@collectLatest
            delay(SHARPEN_DELAY_MILLIS)
            val bitmap = sharpDecodes.withLock {
                withContext(Dispatchers.IO) {
                    Thumbnails.decodeRegion(file, wanted.x, wanted.y, wanted.width, wanted.height, wanted.subsampling)
                        ?.let { PolyBitmap.fromAwt(it).asComposeImageBitmap() }
                }
            }
            if (bitmap != null) sharp = SharpBitmap(wanted, bitmap)
        }
    }
    return sharp
}

private fun Modifier.zoomGestures(state: ZoomState, geometry: () -> ZoomGeometry?, doubleClickZooms: Boolean): Modifier =
    pointerInput(state, doubleClickZooms) {
        val drag = TapTracker(viewConfiguration.touchSlop)
        val doubleClick = DoubleClickDetector(viewConfiguration.doubleTapTimeoutMillis, viewConfiguration.touchSlop)
        var last = Offset.Zero
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: continue
                val layout = geometry()
                when (event.type) {
                    PointerEventType.Scroll -> {
                        if (Controllers.isDispatching) continue
                        val factor = ZoomGeometry.wheelFactor(change.scrollDelta.y)
                        if (layout != null && factor != 1f) state.transform = layout.zoomBy(state.transform, factor, change.position.x, change.position.y)
                        event.changes.forEach { it.consume() }
                    }

                    PointerEventType.Press -> {
                        if (!event.buttons.isPrimaryPressed || change.isConsumed) {
                            doubleClick.reset()
                            continue
                        }
                        drag.press(change.position.x, change.position.y)
                        last = change.position
                        if (doubleClickZooms && layout != null && doubleClick.press(change.uptimeMillis, change.position.x, change.position.y)) {
                            state.transform = layout.toggle(state.transform, change.position.x, change.position.y)
                            change.consume()
                        }
                    }

                    PointerEventType.Move -> {
                        if (drag.move(change.position.x, change.position.y) && layout != null && state.transform.zoomed) {
                            state.transform = layout.panBy(state.transform, change.position.x - last.x, change.position.y - last.y)
                            change.consume()
                        }
                        last = change.position
                    }

                    PointerEventType.Release -> {
                        if (drag.dragging) {
                            doubleClick.reset()
                            change.consume()
                        }
                        drag.release()
                    }
                }
            }
        }
    }

private const val CRISP_PIXEL_SCALE = 2f
private const val CONTROLLER_ZOOM_STEP = 1.5f
private const val SHARPEN_DELAY_MILLIS = 150L
