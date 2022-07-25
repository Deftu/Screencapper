package xyz.deftu.screencapper.utils

import net.minecraft.util.Formatting

object ChatHelper {
    fun createLiteralText(text: String) =
        //#if MC>=11900
        net.minecraft.text.Text.literal(text)
        //#else
        //$$ net.minecraft.text.LiteralText(text)
        //#endif
    fun createTranslatableText(key: String, vararg args: Any) =
        //#if MC>=11900
        net.minecraft.text.Text.translatable(key, *args)
        //#else
        //$$ net.minecraft.text.TranslatableText(key, *args)
        //#endif
    fun createLineDivider(number: Int) = createLiteralText("-".repeat(number)).formatted(Formatting.STRIKETHROUGH)
}
