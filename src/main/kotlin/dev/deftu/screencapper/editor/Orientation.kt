package dev.deftu.screencapper.editor

import dev.deftu.screencapper.screenshot.capture.CapturedImage

enum class Turn {
    RIGHT,
    LEFT,
    FLIP_HORIZONTAL,
    FLIP_VERTICAL;

    private val quarter: Boolean get() = this == RIGHT || this == LEFT

    fun size(width: Int, height: Int): PixelRect = if (quarter) PixelRect.of(height, width) else PixelRect.of(width, height)

    fun point(point: ImagePoint, width: Int, height: Int): ImagePoint = when (this) {
        RIGHT -> ImagePoint(height - point.y, point.x)
        LEFT -> ImagePoint(point.y, width - point.x)
        FLIP_HORIZONTAL -> ImagePoint(width - point.x, point.y)
        FLIP_VERTICAL -> ImagePoint(point.x, height - point.y)
    }

    fun rect(rect: PixelRect, width: Int, height: Int): PixelRect = when (this) {
        RIGHT -> PixelRect(height - rect.bottom, rect.left, height - rect.top, rect.right)
        LEFT -> PixelRect(rect.top, width - rect.right, rect.bottom, width - rect.left)
        FLIP_HORIZONTAL -> PixelRect(width - rect.right, rect.top, width - rect.left, rect.bottom)
        FLIP_VERTICAL -> PixelRect(rect.left, height - rect.bottom, rect.right, height - rect.top)
    }

    fun vector(dx: Int, dy: Int): Pair<Int, Int> = when (this) {
        RIGHT -> -dy to dx
        LEFT -> dy to -dx
        FLIP_HORIZONTAL -> -dx to dy
        FLIP_VERTICAL -> dx to -dy
    }
}

data class Orientation(val turns: Int = 0, val mirrored: Boolean = false) {
    fun then(turn: Turn): Orientation = when (turn) {
        Turn.RIGHT -> Orientation((turns + 1) % 4, mirrored)
        Turn.LEFT -> Orientation((turns + 3) % 4, mirrored)
        Turn.FLIP_HORIZONTAL -> Orientation((4 - turns) % 4, !mirrored)
        Turn.FLIP_VERTICAL -> Orientation((6 - turns) % 4, !mirrored)
    }

    fun size(width: Int, height: Int): PixelRect = if (turns % 2 == 1) PixelRect.of(height, width) else PixelRect.of(width, height)

    fun apply(image: CapturedImage): CapturedImage {
        if (this == Orientation()) return image
        val width = image.width
        val height = image.height
        val size = size(width, height)
        val out = IntArray(width * height)
        for (y in 0 until height) {
            for (sourceX in 0 until width) {
                val x = if (mirrored) width - 1 - sourceX else sourceX
                val index = when (turns) {
                    1 -> x * size.width + (height - 1 - y)
                    2 -> (height - 1 - y) * size.width + (width - 1 - x)
                    3 -> (width - 1 - x) * size.width + y
                    else -> y * size.width + x
                }
                out[index] = image.argb[y * width + sourceX]
            }
        }
        return CapturedImage(out, size.width, size.height)
    }
}
