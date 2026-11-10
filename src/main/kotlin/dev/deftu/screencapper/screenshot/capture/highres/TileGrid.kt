package dev.deftu.screencapper.screenshot.capture.highres

/**
 * Splits a [width] x [height] image into window-sized tiles, row 0 at the top.
 *
 * Edge tiles may reach past the image; [stitch] drops whatever falls outside it.
 */
class TileGrid(val width: Int, val height: Int, val tileWidth: Int, val tileHeight: Int) {
    init {
        require(width > 0 && height > 0 && tileWidth > 0 && tileHeight > 0) { "Sizes must be positive" }
    }

    val columns: Int = (width + tileWidth - 1) / tileWidth
    val rows: Int = (height + tileHeight - 1) / tileHeight

    /** The aspect ratio the full frustum must be rendered at, so the stitched image isn't stretched */
    val aspect: Float
        get() = width.toFloat() / height

    /**
     * The clip-space transform that zooms the full frustum onto one tile.
     *
     * Applied on the left of the projection: x' = scaleX * x + offsetX * w, and the same for y.
     */
    fun projection(column: Int, row: Int): TileProjection {
        val scaleX = width.toFloat() / tileWidth
        val scaleY = height.toFloat() / tileHeight
        return TileProjection(scaleX, scaleY, scaleX - 2 * column - 1, 2 * row + 1 - scaleY)
    }

    /** Copies one tile into [out] (a [width] x [height] image, top row first); [bottomUp] tiles store their bottom row first */
    fun stitch(out: IntArray, tile: IntArray, tileStride: Int, column: Int, row: Int, bottomUp: Boolean) {
        val left = column * tileWidth
        val top = row * tileHeight
        val copyWidth = minOf(tileWidth, width - left)
        val copyHeight = minOf(tileHeight, height - top)
        if (copyWidth <= 0 || copyHeight <= 0) return
        for (y in 0 until copyHeight) {
            val sourceRow = if (bottomUp) tileHeight - 1 - y else y
            System.arraycopy(tile, sourceRow * tileStride, out, (top + y) * width + left, copyWidth)
        }
    }
}

class TileProjection(val scaleX: Float, val scaleY: Float, val offsetX: Float, val offsetY: Float)
