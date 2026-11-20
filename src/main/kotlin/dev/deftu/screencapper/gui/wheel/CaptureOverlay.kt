@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.gui.wheel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.deftu.screencapper.screenshot.capture.HighResCapture
import dev.deftu.screencapper.screenshot.capture.input.InputBridge
import net.minecraft.client.Minecraft
import org.polyfrost.compose.minecraft.hud.HudLayer
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.OneConfigPrefs
import org.polyfrost.oneconfig.api.ui.v1.components.RadialMenu
import org.polyfrost.oneconfig.api.ui.v1.components.RadialMenuDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.RadialMenuInput
import org.polyfrost.oneconfig.api.ui.v1.components.RadialMenuItem
import org.polyfrost.oneconfig.api.ui.v1.components.RadialMenuState
import org.polyfrost.oneconfig.api.ui.v1.components.SurfaceKind
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.themedSurface
import org.polyfrost.oneconfig.api.ui.v1.platform.OneConfigComposeOptions
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigUITheme
import org.polyfrost.oneconfig.api.ui.v1.theme.ThemeStyle
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?} else if >=1.21 {
/*import net.minecraft.client.gui.GuiGraphics
*///?}

//? if >=26.1 {
internal typealias WheelGraphics = GuiGraphicsExtractor
//?} else if >=1.21 {
/*internal typealias WheelGraphics = GuiGraphics
*///?} else {
/*internal typealias WheelGraphics = Any
*///?}

private const val GLYPH_EM = 10

private val OneConfigUITheme.isSquare: Boolean get() = style == ThemeStyle.Pixel

private val animationsOn: Boolean get() = !OneConfigPrefs.reducedMotion

@Composable
private fun countdownFont(size: TextUnit, square: Boolean, ratio: Float): TextUnit {
    if (!square) return size
    val density = LocalDensity.current
    val pxPerSp = density.density * density.fontScale * ratio
    val scale = max(1, (size.value * pxPerSp / GLYPH_EM).roundToInt())
    return (scale * GLYPH_EM / pxPerSp).sp
}

@Composable
private fun CountdownText(text: String, size: TextUnit, color: Color, weight: FontWeight) {
    Text(text, Modifier, color, OneConfigTheme.typography.body.copy(fontSize = size, fontWeight = weight), 1, TextOverflow.Ellipsis, textAlign = TextAlign.Center)
}

// Adapted from PolyPlus's radial wheel (Polyfrost, GPL-3.0)
internal class WheelSession(val items: List<RadialMenuItem>) {
    val state = RadialMenuState()
}

internal object CaptureOverlay {
    private const val TITLE = "Capture"
    private const val CANCEL_LABEL = "Cancel"
    private const val CANCEL_DETAIL = "Release here to take nothing"
    private const val COUNTDOWN_CAPTION = "Screenshot in"

    private var session: WheelSession? = null
    private var shown by mutableStateOf<WheelSession?>(null)
    private var fadeEndsAt = 0L
    private var countdownEndsAt by mutableLongStateOf(-1L)

    private val host = HudLayer("screencapper:capture_overlay", OneConfigComposeOptions) { Content() }.apply { acceptsInput = false }

    val isOpen: Boolean get() = session != null

    fun canStayOpen(): Boolean = Minecraft.getInstance().player != null && !InputBridge.screenOpen

    fun open(items: List<RadialMenuItem>): Boolean {
        if (session != null || !canStayOpen()) return false
        val next = WheelSession(items)
        session = next
        shown = next
        return true
    }

    fun confirm(): Int {
        val pick = session?.state?.highlighted ?: return -1
        val current = finish(instant = pick >= 0) ?: return -1
        if (pick >= 0) current.state.select(instant = true) else current.state.dismiss()
        return pick
    }

    fun pick(index: Int) {
        val current = finish(instant = true) ?: return
        current.state.highlight(index)
        current.state.select(instant = true)
    }

    fun dismiss(instant: Boolean) {
        val current = finish(instant) ?: return
        current.state.dismiss(instant)
    }

    private fun finish(instant: Boolean): WheelSession? {
        val current = session ?: return null
        session = null
        endFade(instant)
        return current
    }

    private fun endFade(instant: Boolean) {
        fadeEndsAt = if (instant || !animationsOn) 0L else System.currentTimeMillis() + RadialMenuDefaults.fadeMillis
        if (fadeEndsAt == 0L) shown = null
    }

    fun steer(dx: Double, dy: Double) {
        val current = session ?: return
        val scale = steerScale(InputBridge.sensitivity(), host.density)
        current.state.steer((dx * scale).toFloat(), (dy * scale).toFloat())
    }

    fun showCountdown(endsAt: Long) {
        countdownEndsAt = endsAt
    }

    @JvmStatic
    fun render(graphics: WheelGraphics?) {
        if (session == null && shown != null && System.currentTimeMillis() >= fadeEndsAt) shown = null
        if (shown == null && countdownEndsAt < 0) return
        if (HighResCapture.isCapturing) return
        //? if >=1.21 {
        if (graphics != null) host.draw(graphics)
        //?} else {
        /*host.draw()
        *///?}
    }

    @Composable
    private fun Content() {
        val current = shown
        if (current != null) key(current) {
            RadialMenu(
                current.items,
                current.state,
                title = CANCEL_LABEL,
                subtitle = CANCEL_DETAIL,
                eyebrow = TITLE,
                input = RadialMenuInput.Steered,
                hint = RadialMenuDefaults.numberHint(current.items.size),
                showNumbers = true,
                sounds = RadialMenuDefaults.silent,
                pixelRatio = host.pixelRatio,
            )
        }
        val endsAt = countdownEndsAt
        if (endsAt >= 0) Countdown(endsAt, host.pixelRatio)
    }

    @Composable
    private fun Countdown(endsAt: Long, ratio: Float) {
        val theme = OneConfigTheme.current
        val square = theme.isSquare
        val spacing = theme.spacing
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(endsAt) {
            while (true) withFrameMillis { now = System.currentTimeMillis() }
        }
        val left = ceil((endsAt - now) / 1000.0).toInt().coerceAtLeast(1)
        val appear = remember { Animatable(if (animationsOn) 0f else 1f) }
        LaunchedEffect(Unit) { if (animationsOn) appear.animateTo(1f, tween(RadialMenuDefaults.fadeMillis)) }
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .offset(y = maxHeight * 0.3f - 36.dp)
                    .graphicsLayer { alpha = appear.value }
                    .widthIn(min = 96.dp)
                    .themedSurface(SurfaceKind.Popup)
                    .padding(horizontal = spacing.xl, vertical = spacing.m),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    CountdownText("$left", countdownFont(28.sp, square, ratio), theme.textColor, FontWeight.SemiBold)
                    CountdownText(COUNTDOWN_CAPTION, countdownFont(11.sp, square, ratio), theme.textColorSecondary, FontWeight.Medium)
                }
            }
        }
    }
}
