package dev.deftu.screencapper.gui.wheel

internal enum class WheelClick { PICK, CANCEL, NONE }

// Adapted from PolyPlus's radial wheel (Polyfrost, GPL-3.0)
internal fun wheelClick(button: Int, left: Int, right: Int, swapped: Boolean): WheelClick {
    if (button != left && button != right) return WheelClick.NONE
    return if ((button == left) != swapped) WheelClick.PICK else WheelClick.CANCEL
}

private const val PULL_GAIN = 1.9

internal fun steerScale(sensitivity: Double, density: Float): Double {
    val f = sensitivity * 0.6 + 0.2
    return PULL_GAIN * (f * f * f * 8.0) / density.coerceAtLeast(0.01f)
}
