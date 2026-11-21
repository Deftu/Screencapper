package dev.deftu.screencapper.editor

import kotlin.math.floor
import kotlin.math.hypot

data class Badge(override val id: Int, val center: ImagePoint, val number: Int, override val color: Int, val radius: Float) : Item {
    override val left: Int get() = floor(center.x - radius).toInt() - 1
    override val top: Int get() = floor(center.y - radius).toInt() - 1
    override val look: Badge get() = moved(-left.toFloat(), -top.toFloat()).copy(id = 0)

    override fun moved(dx: Float, dy: Float): Badge = copy(center = center.offset(dx, dy))

    override fun hits(point: ImagePoint, slop: Float): Boolean = hypot(point.x - center.x, point.y - center.y) <= radius + slop

    override fun bounds(): PixelRect = PixelRect.spanning(center.x - radius, center.y - radius, center.x + radius, center.y + radius)

    override fun recoloured(color: Int): Badge = copy(color = color)

    override fun turned(turn: Turn, width: Int, height: Int): Badge = copy(center = turn.point(center, width, height))
}
