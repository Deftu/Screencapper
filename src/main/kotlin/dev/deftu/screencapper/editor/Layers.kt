package dev.deftu.screencapper.editor

import kotlin.math.roundToInt

sealed interface Layer : Item {
    override val color: Int get() = 0
    override val left: Int get() = bounds().left
    override val top: Int get() = bounds().top

    override fun recoloured(color: Int): Item = this
}

data class BlurArea(override val id: Int, val area: PixelRect, val block: Int) : Layer {
    override val look: BlurArea get() = copy(id = 0)

    override fun moved(dx: Float, dy: Float): BlurArea = copy(area = area.offset(dx.roundToInt(), dy.roundToInt()))

    override fun hits(point: ImagePoint, slop: Float): Boolean = area.contains(point.x, point.y, slop)

    override fun bounds(): PixelRect = area

    override fun turned(turn: Turn, width: Int, height: Int): BlurArea = copy(area = turn.rect(area, width, height))
}

data class CutArea(override val id: Int, val area: PixelRect, val dx: Int, val dy: Int, val fill: Int, val dropped: Boolean = false) : Layer {
    override val look: CutArea get() = copy(id = 0)

    override fun moved(dx: Float, dy: Float): CutArea = copy(dx = this.dx + dx.roundToInt(), dy = this.dy + dy.roundToInt())

    override fun hits(point: ImagePoint, slop: Float): Boolean = bounds().contains(point.x, point.y, slop)

    override fun bounds(): PixelRect = if (dropped) area else area.offset(dx, dy)

    override fun turned(turn: Turn, width: Int, height: Int): CutArea {
        val (x, y) = turn.vector(dx, dy)
        return copy(area = turn.rect(area, width, height), dx = x, dy = y)
    }
}
