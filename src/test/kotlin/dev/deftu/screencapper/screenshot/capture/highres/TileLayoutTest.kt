package dev.deftu.screencapper.screenshot.capture.highres

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TileLayoutTest {
    @Test
    fun gridCoversTargetWithWholeTiles() {
        val exact = TileGrid(3840, 2160, 1920, 1080)
        assertEquals(2, exact.columns)
        assertEquals(2, exact.rows)

        val partial = TileGrid(4000, 2200, 1920, 1080)
        assertEquals(3, partial.columns)
        assertEquals(3, partial.rows)

        val single = TileGrid(800, 600, 1920, 1080)
        assertEquals(1, single.columns)
        assertEquals(1, single.rows)
    }

    @Test
    fun eachTileProjectionMapsItsPixelsOntoTheWholeViewport() {
        val grid = TileGrid(5000, 2500, 1920, 1080)
        for (row in 0 until grid.rows) for (column in 0 until grid.columns) {
            val tile = grid.projection(column, row)
            val left = column * grid.tileWidth
            val top = row * grid.tileHeight

            val ndcLeft = -1.0 + 2.0 * left / grid.width
            val ndcRight = -1.0 + 2.0 * (left + grid.tileWidth) / grid.width
            val ndcTop = 1.0 - 2.0 * top / grid.height
            val ndcBottom = 1.0 - 2.0 * (top + grid.tileHeight) / grid.height

            assertEquals(-1.0, tile.scaleX * ndcLeft + tile.offsetX, 1e-4, "left edge of $column,$row")
            assertEquals(1.0, tile.scaleX * ndcRight + tile.offsetX, 1e-4, "right edge of $column,$row")
            assertEquals(1.0, tile.scaleY * ndcTop + tile.offsetY, 1e-4, "top edge of $column,$row")
            assertEquals(-1.0, tile.scaleY * ndcBottom + tile.offsetY, 1e-4, "bottom edge of $column,$row")
        }
    }

    @Test
    fun singleTileProjectionIsIdentity() {
        val tile = TileGrid(1920, 1080, 1920, 1080).projection(0, 0)
        assertEquals(1f, tile.scaleX)
        assertEquals(1f, tile.scaleY)
        assertEquals(0f, tile.offsetX)
        assertEquals(0f, tile.offsetY)
    }

    @Test
    fun stitchingTopDownTilesRebuildsTheImage() = assertStitches(bottomUp = false)

    @Test
    fun stitchingBottomUpTilesRebuildsTheImage() = assertStitches(bottomUp = true)

    private fun assertStitches(bottomUp: Boolean) {
        val width = 23
        val height = 17
        val grid = TileGrid(width, height, 10, 7)
        val expected = IntArray(width * height) { it + 1 }
        val out = IntArray(width * height)

        for (row in 0 until grid.rows) for (column in 0 until grid.columns) {
            val stride = grid.tileWidth + 3
            val tile = IntArray(stride * grid.tileHeight) { -1 }
            for (y in 0 until grid.tileHeight) for (x in 0 until grid.tileWidth) {
                val sourceX = column * grid.tileWidth + x
                val sourceY = row * grid.tileHeight + y
                val value = if (sourceX < width && sourceY < height) expected[sourceY * width + sourceX] else -1
                val storedY = if (bottomUp) grid.tileHeight - 1 - y else y
                tile[storedY * stride + x] = value
            }
            grid.stitch(out, tile, stride, column, row, bottomUp)
        }

        assertContentEquals(expected, out)
    }
}
