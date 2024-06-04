package dev.deftu.screencapper.utils

import net.minecraft.util.Formatting
import dev.deftu.lib.utils.TextHelper

object ChatHelper {
    fun createLineDivider(number: Int) = TextHelper.createLiteralText("-".repeat(number)).formatted(Formatting.STRIKETHROUGH)
}
