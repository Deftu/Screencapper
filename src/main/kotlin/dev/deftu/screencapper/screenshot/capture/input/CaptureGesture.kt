package dev.deftu.screencapper.screenshot.capture.input

/** Tells a tap of the screenshot key from a hold, from the key state seen each frame */
internal class HoldGesture(private val thresholdMs: () -> Long) {
    enum class Outcome { NONE, TAP, HOLD, RELEASED }

    private var pressedAt = -1L
    private var held = false

    val active: Boolean get() = pressedAt >= 0

    fun press(now: Long) {
        pressedAt = now
        held = false
    }

    /** [HOLD] fires once, when the key has been down for the threshold; [RELEASED] follows it, [TAP] replaces it */
    fun update(now: Long, down: Boolean): Outcome {
        if (pressedAt < 0) return Outcome.NONE
        if (!down) {
            val wasHeld = held
            reset()
            return if (wasHeld) Outcome.RELEASED else Outcome.TAP
        }
        if (!held && now - pressedAt >= thresholdMs()) {
            held = true
            return Outcome.HOLD
        }
        return Outcome.NONE
    }

    fun reset() {
        pressedAt = -1L
        held = false
    }
}

/**
 * A capture whose fate is decided separately from its arrival.
 *
 * The pixels can arrive after the decision (GPU readback finishes a frame or two later) or before it. Whichever comes
 * second applies the decision, so each capture reaches exactly one of [keep] or [drop].
 */
internal class PendingShot<T : Any>(private val keep: (T) -> Unit, private val drop: (T) -> Unit) {
    private var capture: T? = null
    private var decision: Boolean? = null

    @Synchronized
    fun arrive(capture: T) {
        when (decision) {
            true -> keep(capture)
            false -> drop(capture)
            null -> this.capture = capture
        }
    }

    /** Returns false when this shot was already decided */
    @Synchronized
    fun decide(save: Boolean): Boolean {
        if (decision != null) return false
        decision = save
        val ready = capture ?: return true
        capture = null
        if (save) keep(ready) else drop(ready)
        return true
    }
}
