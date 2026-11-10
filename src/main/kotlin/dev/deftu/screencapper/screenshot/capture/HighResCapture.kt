package dev.deftu.screencapper.screenshot.capture

/**
 * Renders one frame larger than the window and hands back its pixels.
 *
 * Contract between the capture input (which decides when and at what size) and the version-specific high-res renderer.
 * Call [capture] on the render thread. It spreads the work over the next few frames and calls [onDone] on the render
 * thread with the image, or null if the capture failed or was cancelled.
 */
object HighResCapture {
    /** Whether a high-res capture is in progress; the window should look normal again once this is false */
    @JvmStatic
    val isCapturing: Boolean
        get() = false

    @JvmStatic
    fun capture(width: Int, height: Int, hideHud: Boolean, onDone: (CapturedImage?) -> Unit) {
        onDone(null)
    }
}
