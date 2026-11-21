package dev.deftu.screencapper.gui

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZoomGeometryTest {
    private val wide = ZoomGeometry(1000f, 500f, 4000, 2000)

    @Test
    fun `fit scale shrinks the image to the view`() {
        val geometry = ZoomGeometry(1000f, 1000f, 4000, 2000)
        assertEquals(0.25f, geometry.fitScale)
        assertEquals(1000f, geometry.contentWidth)
        assertEquals(500f, geometry.contentHeight)
    }

    @Test
    fun `at fit the image stays centred`() {
        assertEquals(ZoomTransform(), wide.clamp(ZoomTransform(1f, 50f, -50f)))
    }

    @Test
    fun `zoom is limited to fit and four screen pixels per image pixel`() {
        assertEquals(1f, wide.clamp(ZoomTransform(0.5f)).zoom)
        assertEquals(16f, wide.maxZoom)
        assertEquals(16f, wide.clamp(ZoomTransform(1000f)).zoom)
    }

    @Test
    fun `small images can still zoom past fit`() {
        assertEquals(2f, ZoomGeometry(1000f, 1000f, 100, 100).maxZoom)
    }

    @Test
    fun `zooming keeps the image point under the pointer in place`() {
        val before = ZoomTransform()
        val after = wide.zoomBy(before, 2f, 800f, 400f)
        assertEquals(2f, after.zoom)
        assertEquals(wide.imageX(before, 800f), wide.imageX(after, 800f), 0.01f)
        assertEquals(wide.imageY(before, 400f), wide.imageY(after, 400f), 0.01f)
        assertEquals(3200f, wide.imageX(after, 800f), 0.01f)
        assertEquals(1600f, wide.imageY(after, 400f), 0.01f)
    }

    @Test
    fun `zooming in and back out returns to fit`() {
        val zoomed = wide.zoomBy(ZoomTransform(), 3f, 900f, 100f)
        assertEquals(ZoomTransform(), wide.zoomBy(zoomed, 1f / 3f, 200f, 300f))
    }

    @Test
    fun `panning cannot drag the image edge into the view`() {
        val zoomed = wide.clamp(ZoomTransform(2f))
        assertEquals(ZoomTransform(2f, 500f, 250f), wide.panBy(zoomed, 10_000f, 10_000f))
        assertEquals(ZoomTransform(2f, -500f, -250f), wide.panBy(zoomed, -10_000f, -10_000f))
    }

    @Test
    fun `panning at fit does nothing`() {
        assertEquals(ZoomTransform(), wide.panBy(ZoomTransform(), 40f, 40f))
    }

    @Test
    fun `an axis smaller than the view stays centred while zoomed`() {
        val geometry = ZoomGeometry(1000f, 1000f, 4000, 2000)
        assertEquals(0f, geometry.panBy(geometry.clamp(ZoomTransform(1.5f)), 0f, 300f).y)
    }

    @Test
    fun `double click zooms to native pixels at the point and back to fit`() {
        val zoomed = wide.toggle(ZoomTransform(), 750f, 250f)
        assertEquals(4f, zoomed.zoom)
        assertEquals(1f, wide.pixelScale(zoomed))
        assertEquals(3000f, wide.imageX(zoomed, 750f), 0.01f)
        assertEquals(ZoomTransform(), wide.toggle(zoomed, 10f, 10f))
    }

    @Test
    fun `double click on an image already shown past native zooms by two`() {
        val small = ZoomGeometry(1000f, 500f, 500, 250)
        assertEquals(2f, small.toggle(ZoomTransform(), 500f, 250f).zoom)
    }

    @Test
    fun `wheel notches zoom by a fixed factor`() {
        assertEquals(1.25f, ZoomGeometry.wheelFactor(-150f), 0.0001f)
        assertEquals(1f / 1.25f, ZoomGeometry.wheelFactor(150f), 0.0001f)
        assertEquals(sqrt(1.25f), ZoomGeometry.wheelFactor(-75f), 0.0001f)
        assertEquals(1f, ZoomGeometry.wheelFactor(0f))
    }

    @Test
    fun `no sharper region is needed while the base bitmap is detailed enough`() {
        assertNull(wide.sharpRegion(ZoomTransform(), 2000))
        assertNull(wide.sharpRegion(wide.clamp(ZoomTransform(16f)), 4000))
    }

    @Test
    fun `native zoom asks for the visible area plus a margin at full resolution`() {
        val region = assertNotNull(wide.sharpRegion(wide.clamp(ZoomTransform(4f)), 2000))
        assertEquals(ImageRegion(1350, 675, 1300, 650, 1), region)
    }

    @Test
    fun `the region stays inside the image`() {
        val atLeftEdge = wide.panBy(wide.clamp(ZoomTransform(4f)), 10_000f, 10_000f)
        val region = assertNotNull(wide.sharpRegion(atLeftEdge, 2000))
        assertEquals(0, region.x)
        assertEquals(0, region.y)
        assertEquals(1150, region.width)
        assertEquals(575, region.height)
    }

    @Test
    fun `a zoom below native skips source pixels`() {
        val region = assertNotNull(wide.sharpRegion(wide.clamp(ZoomTransform(2f)), 1000))
        assertEquals(2, region.subsampling)
    }

    @Test
    fun `regions never decode more than the pixel budget`() {
        val huge = ZoomGeometry(4000f, 4000f, 16000, 16000)
        val region = assertNotNull(huge.sharpRegion(huge.clamp(ZoomTransform(2.2f)), 2560))
        val pixels = ((region.width + region.subsampling - 1) / region.subsampling).toLong() *
            ((region.height + region.subsampling - 1) / region.subsampling)
        assertTrue(pixels <= ZoomGeometry.MAX_REGION_PIXELS, "decoded $pixels pixels")
    }

    @Test
    fun `a region covers the view it was made for until it pans away`() {
        val zoomed = wide.clamp(ZoomTransform(4f))
        val region = assertNotNull(wide.sharpRegion(zoomed, 2000))
        assertTrue(wide.covers(region, zoomed))
        assertTrue(wide.covers(region, wide.panBy(zoomed, 100f, 50f)))
        assertFalse(wide.covers(region, wide.panBy(zoomed, 400f, 0f)))
    }

    @Test
    fun `a coarse region does not cover a closer zoom`() {
        val coarse = assertNotNull(wide.sharpRegion(wide.clamp(ZoomTransform(2f)), 1000))
        assertFalse(wide.covers(coarse, wide.clamp(ZoomTransform(4f))))
    }
}
