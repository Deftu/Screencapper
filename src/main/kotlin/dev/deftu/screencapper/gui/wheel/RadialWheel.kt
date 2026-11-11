@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.gui.wheel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigUITheme
import org.polyfrost.oneconfig.internal.ThemeConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

internal class WheelEntry(val icon: String, val label: String, val detail: String?)

internal const val FADE_MILLIS = 120

private const val CANCEL_LABEL = "Cancel"
private const val CANCEL_DETAIL = "Release here to take nothing"

private const val WINDOW_SHARE = 0.34f
private const val HUB_SHARE = 0.44f
private const val SURFACE_ALPHA_FLOOR = 0.78f
private const val SHADOW_ALPHA = 0.22f
private const val HALO_ALPHA = 0.05f
private const val HALO_RINGS = 6
private const val ARC_STEP_DEG = 3.0
private const val HOVER_MILLIS = 110
private const val HINT_ALPHA = 0.85f
private const val GLYPH_EM = 10
private const val PIXEL_ICON_CELLS = 12
private const val PIXEL_ICON_ROUND_UP = 0.6f
private const val CURSOR_SHADOW_ALPHA = 0.35f
private const val CURSOR_OUTLINE_ALPHA = 0.85f
private const val CURSOR_ARM_UNITS = 3
private const val QUARTER = PI / 2
private const val EIGHTH = PI / 4
private const val DEAD_ZONE_SHARE = 0.45f
private const val KNOB_OMEGA = 95f
private const val MAX_SPRING_STEP = 0.1f
private const val SETTLE_EPSILON = 1e-4f
private const val BAND_ALPHA = 0.55f
private const val BAND_THIN = 0.35f
private const val BAND_PIXEL_ROOT = 4f
private const val BAND_PIXEL_TIP = 2f

private val MIN_DIAMETER = 240.dp
private val MAX_DIAMETER = 340.dp
private val WEDGE_GAP = 3.dp
private val HUB_GAP = 8.dp
private val HOVER_POP = 3.dp
private val ICON_SIZE = 22.dp
private val HALO_STEP = 1.5.dp
private val SHADOW_DROP = 2.dp
private val CURSOR_DOT = 4.dp
private val CURSOR_RING = 1.5.dp
private val CURSOR_SHADOW = 7.dp
private val BAND_ROOT = 7.dp
private val BAND_TIP = 2.5.dp

private val PopEasing = CubicBezierEasing(0.0f, 0.55f, 0.45f, 1.0f)

internal val OneConfigUITheme.isSquare: Boolean get() = !shadowEnabled

internal val animationsOn: Boolean get() = runCatching { ThemeConfig.animations }.getOrDefault(true)

@Composable
internal fun wheelSurface(theme: OneConfigUITheme): Color {
    val base = theme.popupBackground
    return if (theme.isSquare || base.alpha >= SURFACE_ALPHA_FLOOR) base else base.copy(alpha = SURFACE_ALPHA_FLOOR)
}

@Composable
internal fun wheelFont(size: TextUnit, square: Boolean, ratio: Float): TextUnit {
    if (!square) return size
    val density = LocalDensity.current
    val pxPerSp = density.density * density.fontScale * ratio
    val scale = max(1, (size.value * pxPerSp / GLYPH_EM).roundToInt())
    return (scale * GLYPH_EM / pxPerSp).sp
}

@Composable
internal fun WheelText(
    text: String,
    size: TextUnit,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
    modifier: Modifier = Modifier,
) {
    Text(text, modifier, color, size, null, BaselineShift.None, weight, TextAlign.Center, maxLines, TextOverflow.Ellipsis)
}

// Adapted from PolyPlus's radial wheel (Polyfrost, GPL-3.0)
@Composable
internal fun RadialWheel(session: WheelSession, pixelRatio: Float, title: String) {
    val entries = session.entries
    val animate = animationsOn
    val visibility = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(session.closing) {
        val target = if (session.closing) 0f else 1f
        if (!animationsOn || session.instantClose) visibility.snapTo(target)
        else visibility.animateTo(target, tween(FADE_MILLIS, easing = PopEasing))
    }
    val theme = OneConfigTheme.current
    val square = theme.isSquare
    val shape: Shape = theme.circleShape

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val count = entries.size
        val sector = if (count == 0) 0f else 360f / count
        val diameter = (min(maxWidth, maxHeight) * WINDOW_SHARE).coerceIn(MIN_DIAMETER, MAX_DIAMETER)
        val hub = diameter * HUB_SHARE
        val outer = diameter / 2
        val inner = hub / 2 + HUB_GAP
        val dead = hub.value / 2f * DEAD_ZONE_SHARE
        val active = session.selected
        SideEffect { session.configure(outer.value, square, dead) }
        Box(
            Modifier.size(diameter).graphicsLayer { alpha = visibility.value },
            contentAlignment = Alignment.Center,
        ) {
            val pops = hoverPops(count, active, animate)
            WheelRing(entries, pops, sector, outer, inner, theme)
            entries.forEachIndexed { i, entry ->
                WheelSlot(entry, i, i == active, sector, pops[i], outer, inner, square, pixelRatio)
            }
            WheelHub(entries.getOrNull(active), title, hub, shape, square, pixelRatio, count)
            WheelCursor(session, square, hub / 2, outer, animate, Modifier.matchParentSize())
        }
    }
}

@Composable
private fun hoverPops(count: Int, active: Int, animate: Boolean): List<State<Float>> = (0 until count).map { i ->
    animateFloatAsState(if (i == active) 1f else 0f, if (animate) tween(HOVER_MILLIS) else snap())
}

private fun midAngle(index: Int, sector: Float): Double = Math.toRadians(index * sector - 90.0)

private fun squareReach(angle: Double): Float = max(abs(cos(angle)), abs(sin(angle))).toFloat()

@Composable
private fun WheelRing(entries: List<WheelEntry>, pops: List<State<Float>>, sector: Float, outer: Dp, inner: Dp, theme: OneConfigUITheme) {
    val accent = OneConfigTheme.accent
    val surface = wheelSurface(theme)
    val border = theme.borderColor
    val shadow = theme.shadowColor
    val square = theme.isSquare
    val shadows = theme.shadowEnabled
    val cornerShape = theme.buttonShape as? CornerBasedShape
    Spacer(
        Modifier.size(outer * 2).drawWithCache {
            val corner = cornerShape?.topStart?.toPx(size, this) ?: 0f
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerPx = if (square) outer.toPx().roundToInt().toFloat() else outer.toPx()
            val innerPx = if (square) inner.toPx().roundToInt().toFloat() else inner.toPx()
            val drop = Offset(0f, SHADOW_DROP.toPx())
            val halo = HALO_STEP.toPx()
            val haloStroke = Stroke(halo)
            val line = if (square) max(1f, 1.dp.toPx().roundToInt().toFloat()) else 1.dp.toPx()
            val stroke = Stroke(line)
            val gap = if (entries.size > 1) WEDGE_GAP.toPx() else 0f
            val pop = HOVER_POP.toPx()
            val shadowFill = shadow.copy(alpha = SHADOW_ALPHA)
            val haloColor = shadow.copy(alpha = HALO_ALPHA)
            val paths = List(entries.size) { i ->
                val mid = midAngle(i, sector)
                val start = mid - Math.toRadians(sector / 2.0)
                val end = mid + Math.toRadians(sector / 2.0)
                if (square) squareWedgePath(center, innerPx, outerPx, start, end, gap / 2f)
                else wedgePath(center, innerPx, outerPx, start, end, gap / 2f, corner)
            }
            val shifts = List(entries.size) { i ->
                val mid = midAngle(i, sector)
                Offset((cos(mid) * pop).toFloat(), (sin(mid) * pop).toFloat())
            }
            onDrawBehind {
                if (shadows) {
                    drawCircle(shadowFill, outerPx, center + drop)
                    for (ring in 1..HALO_RINGS) drawCircle(haloColor, outerPx + ring * halo, center + drop, style = haloStroke)
                }
                for (i in paths.indices) {
                    val p = pops[i].value
                    val fill = lerp(surface, accent, p)
                    val shift = shifts[i]
                    translate(shift.x * p, shift.y * p) {
                        drawPath(paths[i], fill)
                        drawPath(paths[i], if (p > 0.5f) accent else border, style = stroke)
                    }
                }
            }
        },
    )
}

private fun wedgePath(center: Offset, inner: Float, outer: Float, start: Double, end: Double, halfGap: Float, corner: Float): Path {
    fun polar(r: Float, a: Double) = Offset(center.x + (cos(a) * r).toFloat(), center.y + (sin(a) * r).toFloat())
    fun startEdge(r: Float) = polar(r, start + asin((halfGap / r).coerceAtMost(1f).toDouble()))
    fun endEdge(r: Float) = polar(r, end - asin((halfGap / r).coerceAtMost(1f).toDouble()))

    val outerStart = start + asin((halfGap / outer).toDouble())
    val outerEnd = end - asin((halfGap / outer).toDouble())
    val innerStart = start + asin((halfGap / inner).coerceAtMost(1f).toDouble())
    val innerEnd = end - asin((halfGap / inner).coerceAtMost(1f).toDouble())
    val innerArc = (innerEnd - innerStart).coerceAtLeast(0.0) * inner
    val c = corner.coerceAtMost((outer - inner) / 3f).coerceAtMost((innerArc / 3.0).toFloat()).coerceAtLeast(0f)

    val path = Path()
    fun arc(r: Float, from: Double, to: Double) {
        val steps = max(1, ceil(Math.toDegrees(abs(to - from)) / ARC_STEP_DEG).toInt())
        for (s in 1..steps) {
            val point = polar(r, from + (to - from) * s / steps)
            path.lineTo(point.x, point.y)
        }
    }

    val begin = startEdge(inner + c)
    path.moveTo(begin.x, begin.y)
    startEdge(outer - c).let { path.lineTo(it.x, it.y) }
    val outerA = outerStart + c / outer
    val outerB = outerEnd - c / outer
    startEdge(outer).let { control -> polar(outer, outerA).let { path.quadraticTo(control.x, control.y, it.x, it.y) } }
    arc(outer, outerA, outerB)
    endEdge(outer).let { control -> endEdge(outer - c).let { path.quadraticTo(control.x, control.y, it.x, it.y) } }
    endEdge(inner + c).let { path.lineTo(it.x, it.y) }
    val innerB = innerEnd - c / inner
    val innerA = innerStart + c / inner
    endEdge(inner).let { control -> polar(inner, innerB).let { path.quadraticTo(control.x, control.y, it.x, it.y) } }
    arc(inner, innerB, innerA)
    startEdge(inner).let { control -> path.quadraticTo(control.x, control.y, begin.x, begin.y) }
    path.close()
    return path
}

private fun squareWedgePath(center: Offset, inner: Float, outer: Float, start: Double, end: Double, halfGap: Float): Path {
    fun cut(half: Float, angle: Double, shift: Float): Offset {
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        val nx = -dy * shift
        val ny = dx * shift
        var t = Float.MAX_VALUE
        if (abs(dx) > 1e-6f) t = minOf(t, ((if (dx > 0f) half else -half) - nx) / dx)
        if (abs(dy) > 1e-6f) t = minOf(t, ((if (dy > 0f) half else -half) - ny) / dy)
        return Offset(center.x + nx + dx * t, center.y + ny + dy * t)
    }
    fun corner(half: Float, angle: Double) =
        Offset(center.x + sign(cos(angle)).toFloat() * half, center.y + sign(sin(angle)).toFloat() * half)

    val first = floor((start - EIGHTH) / QUARTER).toInt() + 1
    val corners = generateSequence(first) { it + 1 }.map { EIGHTH + it * QUARTER }.takeWhile { it < end }.toList()

    val path = Path()
    cut(outer, start, halfGap).let { path.moveTo(it.x, it.y) }
    for (a in corners) corner(outer, a).let { path.lineTo(it.x, it.y) }
    cut(outer, end, -halfGap).let { path.lineTo(it.x, it.y) }
    cut(inner, end, -halfGap).let { path.lineTo(it.x, it.y) }
    for (a in corners.asReversed()) corner(inner, a).let { path.lineTo(it.x, it.y) }
    cut(inner, start, halfGap).let { path.lineTo(it.x, it.y) }
    path.close()
    return path
}

@Composable
private fun WheelSlot(
    entry: WheelEntry,
    index: Int,
    hovered: Boolean,
    sector: Float,
    pop: State<Float>,
    outer: Dp,
    inner: Dp,
    square: Boolean,
    ratio: Float,
) {
    val theme = OneConfigTheme.current
    val ink = if (hovered) theme.accentTextColor else theme.textColor
    val mid = midAngle(index, sector)
    val dirX = cos(mid).toFloat()
    val dirY = sin(mid).toFloat()
    val base = (outer + inner) / 2
    val radius = if (square) base / squareReach(mid) else base
    val chord = base * (2.0 * sin(PI / max(2.0, 360.0 / sector))).toFloat()
    val width = min(chord - WEDGE_GAP * 2 - 10.dp, (outer - inner) * 1.4f).coerceAtLeast(40.dp)
    Column(
        modifier = Modifier
            .offset {
                val r = radius.toPx()
                IntOffset((dirX * r).roundToInt(), (dirY * r).roundToInt())
            }
            .width(width)
            .graphicsLayer {
                val shift = HOVER_POP.toPx() * pop.value
                translationX = dirX * shift
                translationY = dirY * shift
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(entry.icon, ink, Modifier.size(if (square) crispPixelSize(ICON_SIZE) else ICON_SIZE))
        WheelText(entry.label, wheelFont(12.sp, square, ratio), ink, FontWeight.Medium, maxLines = 2, modifier = Modifier.widthIn(max = width))
        WheelText("${index + 1}", wheelFont(10.sp, square, ratio), ink.copy(alpha = ink.alpha * 0.7f))
    }
}

@Composable
private fun crispPixelSize(size: Dp): Dp = with(LocalDensity.current) {
    val scale = (size.toPx() / PIXEL_ICON_CELLS + PIXEL_ICON_ROUND_UP).toInt().coerceAtLeast(1)
    (scale * PIXEL_ICON_CELLS).toDp()
}

@Composable
private fun WheelHub(hovered: WheelEntry?, title: String, hub: Dp, shape: Shape, square: Boolean, ratio: Float, count: Int) {
    val theme = OneConfigTheme.current
    val headline = hovered?.label ?: CANCEL_LABEL
    val detail = if (hovered != null) hovered.detail else CANCEL_DETAIL
    Box(
        modifier = Modifier
            .size(hub)
            .clip(shape)
            .background(wheelSurface(theme))
            .border(1.dp, theme.borderColor, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            WheelText(title, wheelFont(10.sp, square, ratio), theme.textColorSecondary, FontWeight.Medium)
            WheelText(headline, wheelFont(14.sp, square, ratio), theme.textColor, FontWeight.SemiBold, maxLines = 2)
            if (detail != null) WheelText(detail, wheelFont(11.sp, square, ratio), theme.textColorSecondary, maxLines = 2)
            val hint = theme.textColorSecondary.let { it.copy(alpha = it.alpha * HINT_ALPHA) }
            WheelText("Keys 1-$count pick, Esc cancels", wheelFont(9.sp, square, ratio), hint, maxLines = 2)
        }
    }
}

private class KnobSpring {
    var position by mutableStateOf(Offset.Zero)
    private var velocity = Offset.Zero

    fun step(target: Offset, dt: Float) {
        val y = position - target
        if (y.getDistanceSquared() < SETTLE_EPSILON && velocity.getDistanceSquared() < SETTLE_EPSILON) {
            if (position != target) position = target
            velocity = Offset.Zero
            return
        }
        val decay = exp(-KNOB_OMEGA * dt)
        val drive = velocity + y * KNOB_OMEGA
        position = target + (y + drive * dt) * decay
        velocity = (velocity - drive * (KNOB_OMEGA * dt)) * decay
    }
}

@Composable
private fun WheelCursor(session: WheelSession, square: Boolean, hubRadius: Dp, outer: Dp, animate: Boolean, modifier: Modifier) {
    val accent = OneConfigTheme.accent
    val theme = OneConfigTheme.current
    val shadow = theme.shadowColor
    val shadows = theme.shadowEnabled
    val outline = theme.textColor.copy(alpha = CURSOR_OUTLINE_ALPHA)
    val band = accent.copy(alpha = accent.alpha * BAND_ALPHA)
    val knob = remember(session) { KnobSpring() }
    val path = remember { Path() }
    LaunchedEffect(session, animate) {
        if (!animate) return@LaunchedEffect
        var last = System.nanoTime()
        while (true) {
            withFrameNanos {
                val now = System.nanoTime()
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, MAX_SPRING_STEP)
                last = now
                knob.step(if (session.closing) Offset.Zero else session.pointer, dt)
            }
        }
    }
    Spacer(
        modifier.drawBehind {
            val at = if (animate) knob.position else session.pointer
            val center = Offset(size.width / 2f, size.height / 2f)
            val tip = Offset(at.x.dp.toPx(), at.y.dp.toPx())
            val hubPx = hubRadius.toPx()
            val stretch = if (!animate) 0f else ((tip.getDistance() - hubPx) / (outer.toPx() - hubPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
            if (square) drawPixelBand(path, center, tip, hubPx, stretch, band) else drawRoundBand(path, center, tip, hubPx, stretch, band)
            val x = center.x + tip.x
            val y = center.y + tip.y
            if (square) drawPixelCursor(x, y, accent, outline, theme.popupBackground) else drawRoundCursor(x, y, accent, outline, shadow.takeIf { shadows })
        },
    )
}

private fun bandStart(tip: Offset, hubPx: Float, square: Boolean): Offset? {
    val length = tip.getDistance()
    if (length < 1e-3f) return null
    val dir = tip / length
    val edge = if (square) hubPx / max(abs(dir.x), abs(dir.y)) else hubPx
    return if (length > edge) dir * edge else null
}

private fun DrawScope.drawRoundBand(path: Path, center: Offset, tip: Offset, hubPx: Float, stretch: Float, color: Color) {
    val start = bandStart(tip, hubPx, false) ?: return
    val dir = tip / tip.getDistance()
    val normal = Offset(-dir.y, dir.x)
    val root = BAND_ROOT.toPx() * (1f - BAND_THIN * stretch) / 2f
    val end = BAND_TIP.toPx() / 2f
    val a = center + start
    val b = center + tip
    path.reset()
    path.moveTo(a.x + normal.x * root, a.y + normal.y * root)
    path.lineTo(b.x + normal.x * end, b.y + normal.y * end)
    path.lineTo(b.x - normal.x * end, b.y - normal.y * end)
    path.lineTo(a.x - normal.x * root, a.y - normal.y * root)
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawPixelBand(path: Path, center: Offset, tip: Offset, hubPx: Float, stretch: Float, color: Color) {
    val start = bandStart(tip, hubPx, true) ?: return
    val u = max(1f, 1.dp.toPx().roundToInt().toFloat())
    val a = center + start
    val b = center + tip
    val length = (b - a).getDistance()
    val steps = max(1, (length / u).toInt())
    val root = BAND_PIXEL_ROOT * (1f - BAND_THIN * stretch)
    path.reset()
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val units = max(1, (root + (BAND_PIXEL_TIP - root) * t).roundToInt())
        val side = units * u
        val p = a + (b - a) * t
        val x = ((p.x - side / 2f) / u).roundToInt() * u
        val y = ((p.y - side / 2f) / u).roundToInt() * u
        path.addRect(Rect(x, y, x + side, y + side))
    }
    drawPath(path, color)
}

private fun DrawScope.drawRoundCursor(x: Float, y: Float, fill: Color, outline: Color, shadow: Color?) {
    val center = Offset(x, y)
    val dot = CURSOR_DOT.toPx()
    val ring = CURSOR_RING.toPx()
    if (shadow != null) {
        drawCircle(shadow.copy(alpha = CURSOR_SHADOW_ALPHA * 0.5f), CURSOR_SHADOW.toPx(), center + Offset(0f, ring))
        drawCircle(shadow.copy(alpha = CURSOR_SHADOW_ALPHA), dot + ring * 2f, center + Offset(0f, ring * 0.5f))
    }
    drawCircle(outline, dot + ring, center)
    drawCircle(fill, dot, center)
}

private fun DrawScope.drawPixelCursor(x: Float, y: Float, fill: Color, ink: Color, backing: Color) {
    val u = max(1f, 1.dp.toPx().roundToInt().toFloat())
    val cx = (x / u).roundToInt() * u
    val cy = (y / u).roundToInt() * u
    val arm = CURSOR_ARM_UNITS * u
    fun cross(ox: Float, oy: Float, thick: Float, reach: Float, color: Color) {
        drawRect(color, Offset(ox - reach, oy - thick / 2f), Size(reach * 2, thick))
        drawRect(color, Offset(ox - thick / 2f, oy - reach), Size(thick, reach * 2))
    }
    cross(cx, cy, u * 4, arm + u * 2, backing.copy(alpha = 1f))
    cross(cx, cy, u * 2, arm + u, ink.copy(alpha = 1f))
    drawRect(fill, Offset(cx - u, cy - u), Size(u * 2, u * 2))
}
