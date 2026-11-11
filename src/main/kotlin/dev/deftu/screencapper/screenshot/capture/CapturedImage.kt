package dev.deftu.screencapper.screenshot.capture

class CapturedImage(val argb: IntArray, val width: Int, val height: Int) {
    init {
        require(argb.size >= width * height) { "Not enough pixels for a ${width}x$height image" }
    }
}

enum class CaptureKind {
    NORMAL,

    HIGH_RES,
}
