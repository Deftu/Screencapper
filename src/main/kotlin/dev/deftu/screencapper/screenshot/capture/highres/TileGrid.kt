package dev.deftu.screencapper.screenshot.capture.highres

class TileGrid(val width: Int, val height: Int, val tileWidth: Int, val tileHeight: Int) {
    init {
        require(width > 0 && height > 0 && tileWidth > 0 && tileHeight > 0) { "Sizes must be positive" }
    }

    val columns: Int = (width + tileWidth - 1) / tileWidth
    val rows: Int = (height + tileHeight - 1) / tileHeight

    val aspect: Float
        get() = width.toFloat() / height

    fun projection(column: Int, row: Int): TileProjection {
        val scaleX = width.toFloat() / tileWidth
        val scaleY = height.toFloat() / tileHeight
        return TileProjection(scaleX, scaleY, scaleX - 2 * column - 1, 2 * row + 1 - scaleY)
    }

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
