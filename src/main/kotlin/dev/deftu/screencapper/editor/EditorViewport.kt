package dev.deftu.screencapper.editor

import dev.deftu.screencapper.gui.ZoomGeometry
import dev.deftu.screencapper.gui.ZoomTransform

internal class EditorViewport(val viewWidth: Float, val viewHeight: Float, val area: PixelRect) {
    val zoom: ZoomGeometry = ZoomGeometry(viewWidth, viewHeight, area.width, area.height)

    fun scale(transform: ZoomTransform): Float = zoom.pixelScale(transform)

    fun toImage(transform: ZoomTransform, x: Float, y: Float): ImagePoint =
        ImagePoint(area.left + zoom.imageX(transform, x), area.top + zoom.imageY(transform, y))

    fun toViewX(transform: ZoomTransform, x: Float): Float =
        (x - area.left - area.width / 2f) * scale(transform) + viewWidth / 2f + transform.x

    fun toViewY(transform: ZoomTransform, y: Float): Float =
        (y - area.top - area.height / 2f) * scale(transform) + viewHeight / 2f + transform.y
}
