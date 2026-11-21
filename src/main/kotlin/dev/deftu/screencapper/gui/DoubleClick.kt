package dev.deftu.screencapper.gui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.hypot

internal class DoubleClickDetector(private val timeoutMillis: Long, private val slop: Float) {
    private var armed = false
    private var lastTime = 0L
    private var lastX = 0f
    private var lastY = 0f

    fun press(timeMillis: Long, x: Float, y: Float): Boolean {
        val double = armed && timeMillis - lastTime in 0..timeoutMillis && hypot(x - lastX, y - lastY) <= slop
        armed = !double
        lastTime = timeMillis
        lastX = x
        lastY = y
        return double
    }

    fun reset() {
        armed = false
    }
}

internal class TapTracker(private val slop: Float) {
    private var pressed = false
    private var startX = 0f
    private var startY = 0f

    var dragging = false
        private set

    fun press(x: Float, y: Float) {
        pressed = true
        dragging = false
        startX = x
        startY = y
    }

    fun move(x: Float, y: Float): Boolean {
        if (pressed && !dragging && hypot(x - startX, y - startY) > slop) dragging = true
        return dragging
    }

    fun release(): Boolean {
        val tap = pressed && !dragging
        pressed = false
        dragging = false
        return tap
    }
}

internal fun Modifier.onDoubleClick(onDoubleClick: () -> Unit): Modifier = composed {
    val latest by rememberUpdatedState(onDoubleClick)
    pointerInput(Unit) {
        val detector = DoubleClickDetector(viewConfiguration.doubleTapTimeoutMillis, viewConfiguration.touchSlop)
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Press) continue
                val change = event.changes.firstOrNull() ?: continue
                if (!event.buttons.isPrimaryPressed) {
                    detector.reset()
                    continue
                }
                if (detector.press(change.uptimeMillis, change.position.x, change.position.y)) {
                    change.consume()
                    latest()
                }
            }
        }
    }
}

internal fun Modifier.onTap(onTap: () -> Unit): Modifier = composed {
    val latest by rememberUpdatedState(onTap)
    pointerInput(Unit) {
        val tracker = TapTracker(viewConfiguration.touchSlop)
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: continue
                when (event.type) {
                    PointerEventType.Press -> if (event.buttons.isPrimaryPressed && !change.isConsumed) tracker.press(change.position.x, change.position.y)
                    PointerEventType.Move -> tracker.move(change.position.x, change.position.y)
                    PointerEventType.Release -> if (tracker.release() && !change.isConsumed) {
                        change.consume()
                        latest()
                    }
                }
            }
        }
    }
}
