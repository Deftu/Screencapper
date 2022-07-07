package xyz.deftu.screencapper.gui.preview

import dev.isxander.shotify.util.ofNativeImage
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.UIImage
import gg.essential.elementa.constraints.ChildBasedMaxSizeConstraint
import gg.essential.elementa.constraints.ImageAspectConstraint
import gg.essential.elementa.constraints.XConstraint
import gg.essential.elementa.constraints.YConstraint
import gg.essential.elementa.constraints.animation.Animations
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.OutlineEffect
import gg.essential.elementa.utils.withAlpha
import xyz.deftu.screencapper.config.ScreencapperConfig
import xyz.deftu.screencapper.utils.Screenshot
import java.awt.Color

class ScreenshotPreviewComponent(
    val screenshot: Screenshot
) : UIContainer() {
    companion object {
        @JvmStatic
        val COLLAPSE_ANIMATION = Animations.IN_OUT_SIN
        const val COLLAPSE_DURATION = 0.5f
    }

    private val image by UIImage.ofNativeImage(screenshot.image).constrain {
        width = 100.percent
        height = ImageAspectConstraint()
    } childOf this
    private val flash by UIBlock(Color.WHITE.withAlpha(0)).constrain {
        width = 100.percent
        height = 100.percent boundTo image
    } childOf this

    init {
        constrain {
            width = 100.percent
            height = 100.percent
        } effect OutlineEffect(Color.WHITE, 0.75f)
    }

    fun animate() {
        flash.animate {
            setColorAnimation(Animations.IN_SIN, 0.1f, Color(255, 255, 255, 200).toConstraint()).onComplete {
                flash.animate {
                    setColorAnimation(Animations.OUT_SIN, 0.1f, Color(255, 255, 255, 0).toConstraint()).onComplete {
                        collapse()
                    }
                }
            }
        }
    }

    private fun collapse() {
        animate {
            val positions: Pair<XConstraint, YConstraint> = when (ScreencapperConfig.previewPosition) {
                PreviewPosition.TOP_LEFT -> 2.percent to basicYConstraint { 2.percent.getXPositionImpl(it) }
                PreviewPosition.TOP_RIGHT -> 68.percent to basicYConstraint { 2.percent.getXPositionImpl(it) }
                PreviewPosition.BOTTOM_LEFT -> 2.percent to 0.pixels(alignOpposite = true) - basicYConstraint { 2.percent.getXPositionImpl(it) }
                PreviewPosition.BOTTOM_RIGHT -> 68.percent to 0.pixels(alignOpposite = true) - basicYConstraint { 2.percent.getXPositionImpl(it) }
            }
            setXAnimation(COLLAPSE_ANIMATION, COLLAPSE_DURATION, positions.first)
            setYAnimation(COLLAPSE_ANIMATION, COLLAPSE_DURATION, positions.second)
            setWidthAnimation(COLLAPSE_ANIMATION, COLLAPSE_DURATION, 30.percent)
            setHeightAnimation(COLLAPSE_ANIMATION, COLLAPSE_DURATION, ChildBasedMaxSizeConstraint())

            onComplete {
                remove()
            }
        }
    }

    private fun remove() {
        animate {
            val x = if (ScreencapperConfig.previewPosition.isLeft()) (-30).percent else 100.percent
            setXAnimation(Animations.OUT_SIN, 0.5f, x, ScreencapperConfig.previewTime.toFloat()).onComplete {
                hide()
            }
        }
    }
}
