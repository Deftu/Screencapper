package dev.deftu.screencapper.editor

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import org.polyfrost.compose.text.Font

object EditorText : TextMetrics {
    private const val FONT = "assets/oneconfig/fonts/Poppins/Poppins-SemiBold.ttf"
    private const val PROBE = "Ag"
    private const val CACHE = 256

    private val family = FontFamily(Font(FONT, FontWeight.SemiBold))

    private val measurer by lazy { TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, CACHE) }

    fun line(text: String, size: Float): TextLayoutResult = measurer.measure(
        text,
        TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = TextUnit(size, TextUnitType.Sp)),
        TextOverflow.Clip,
        softWrap = false,
        maxLines = 1,
    )

    fun TextLayoutResult.advance(): Float = multiParagraph.intrinsics.maxIntrinsicWidth

    override fun width(text: String, size: Float): Float = line(text, size).advance()

    override fun ascent(size: Float): Float = line(PROBE, size).firstBaseline

    override fun lineHeight(size: Float): Float = line(PROBE, size).multiParagraph.let { it.getLineBottom(0) - it.getLineTop(0) }
}
