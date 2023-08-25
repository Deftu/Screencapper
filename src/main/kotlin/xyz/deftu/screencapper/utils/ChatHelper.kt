package xyz.deftu.screencapper.utils

import net.minecraft.util.Formatting
import xyz.deftu.lib.utils.TextHelper

object ChatHelper {
    fun createLineDivider(number: Int) = TextHelper.createLiteralText("-".repeat(number)).formatted(Formatting.STRIKETHROUGH)
}
