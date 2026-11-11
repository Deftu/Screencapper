@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.gui.wheel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.deftu.screencapper.screenshot.capture.HighResCapture
import dev.deftu.screencapper.screenshot.capture.input.InputBridge
import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.theme.ProvideOneConfigTheme
import kotlin.math.ceil

// Adapted from PolyPlus's radial wheel (Polyfrost, GPL-3.0)
internal class WheelSession(val entries: List<WheelEntry>) {
    var pointer by mutableStateOf(Offset.Zero)
        private set
    var selected by mutableStateOf(-1)
        private set
    var closing by mutableStateOf(false)
        private set

    var instantClose = false
        private set

    private val steering = WheelSteering()
    private var configured = false

    val hovered: Int get() = selected

    fun configure(radius: Float, square: Boolean, deadZone: Float) {
        if (!steering.configure(radius, square, deadZone, List(entries.size) { true }) && configured) return
        configured = true
        steer(0f, 0f)
    }

    fun steer(dx: Float, dy: Float) {
        if (closing) return
        selected = steering.steer(dx, dy)
        val (x, y) = steering.pointer
        pointer = Offset(x, y)
    }

    fun close(instant: Boolean) {
        instantClose = instant
        closing = true
    }
}

internal object CaptureOverlay {
    private const val TITLE = "Capture"
    private const val COUNTDOWN_CAPTION = "Screenshot in"

    private var session: WheelSession? = null
    private var shown by mutableStateOf<WheelSession?>(null)
    private var fadeEndsAt = 0L
    private var countdownEndsAt by mutableLongStateOf(-1L)

    private val host = OverlayComposeHost("capture_overlay") { Content() }

    val isOpen: Boolean get() = session != null

    val hovered: Int get() = session?.hovered ?: -1

    fun canStayOpen(): Boolean = Minecraft.getInstance().player != null && !InputBridge.screenOpen

    fun open(entries: List<WheelEntry>): Boolean {
        if (session != null || !canStayOpen()) return false
        val next = WheelSession(entries)
        session = next
        shown = next
        return true
    }

    fun close(instant: Boolean): Int {
        val current = session ?: return -1
        session = null
        val pick = current.hovered
        current.close(instant)
        fadeEndsAt = if (instant || !animationsOn) 0L else System.currentTimeMillis() + FADE_MILLIS
        if (fadeEndsAt == 0L) shown = null
        return pick
    }

    fun steer(dx: Double, dy: Double) {
        val current = session ?: return
        val scale = steerScale(InputBridge.sensitivity(), host.density)
        current.steer((dx * scale).toFloat(), (dy * scale).toFloat())
    }

    fun showCountdown(endsAt: Long) {
        countdownEndsAt = endsAt
    }

    @JvmStatic
    fun render(graphics: WheelGraphics?) {
        if (session == null && shown != null && System.currentTimeMillis() >= fadeEndsAt) shown = null
        if (shown == null && countdownEndsAt < 0) return
        if (HighResCapture.isCapturing) return
        host.draw(graphics)
    }

    @Composable
    private fun Content() {
        ProvideOneConfigTheme {
            val current = shown
            if (current != null) key(current) { RadialWheel(current, host.pixelRatio, TITLE) }
            val endsAt = countdownEndsAt
            if (endsAt >= 0) Countdown(endsAt, host.pixelRatio)
        }
    }

    @Composable
    private fun Countdown(endsAt: Long, ratio: Float) {
        val theme = OneConfigTheme.current
        val square = theme.isSquare
        val shape = theme.popupShape
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(endsAt) {
            while (true) withFrameMillis { now = System.currentTimeMillis() }
        }
        val left = ceil((endsAt - now) / 1000.0).toInt().coerceAtLeast(1)
        val appear = remember { Animatable(if (animationsOn) 0f else 1f) }
        LaunchedEffect(Unit) { if (animationsOn) appear.animateTo(1f, tween(FADE_MILLIS)) }
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .offset(y = maxHeight * 0.3f - 36.dp)
                    .graphicsLayer { alpha = appear.value }
                    .widthIn(min = 96.dp)
                    .clip(shape)
                    .background(wheelSurface(theme))
                    .border(1.dp, theme.borderColor, shape)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    WheelText("$left", wheelFont(28.sp, square, ratio), theme.textColor, FontWeight.SemiBold)
                    WheelText(COUNTDOWN_CAPTION, wheelFont(11.sp, square, ratio), theme.textColorSecondary, FontWeight.Medium)
                }
            }
        }
    }
}
