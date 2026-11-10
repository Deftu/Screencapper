package dev.deftu.screencapper.screenshot.capture

/** Opaque pixels of a capture: [width] x [height] ARGB, top row first */
class CapturedImage(val argb: IntArray, val width: Int, val height: Int) {
    init {
        require(argb.size >= width * height) { "Not enough pixels for a ${width}x$height image" }
    }
}

enum class CaptureKind {
    /** The screenshot key's normal shot, or anything vanilla saves as a screenshot */
    NORMAL,

    /** A shot rendered larger than the window */
    HIGH_RES,
}
