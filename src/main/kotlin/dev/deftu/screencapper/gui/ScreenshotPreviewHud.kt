@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import dev.deftu.screencapper.ScreenshotActions
import dev.deftu.screencapper.screenshot.Screenshot
import org.polyfrost.compose.composables.NodeCanvas
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.render.FilterQuality
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.compose.render.RenderContext
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.HudIcons
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.oneconfig.api.hud.v1.HudResize
import org.polyfrost.oneconfig.api.hud.v1.clickRegion
import org.polyfrost.oneconfig.api.hud.v1.hoverRegion
import org.polyfrost.oneconfig.api.hud.v1.removeClickRegion
import org.polyfrost.compose.render.PolyFont
import org.polyfrost.compose.layout.PolyAlign
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Weight
import org.polyfrost.oneconfig.api.hud.v1.toPolyColor
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.theme.ThemeStyle
import org.polyfrost.oneconfig.utils.v1.Multithreading
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.min
import kotlin.math.pow

private const val DEFAULT_WIDTH = 256f
private const val ASPECT = 9f / 16f
private const val ENTER_MS = 320f
private const val FLASH_MS = 260f
private const val EXIT_MS = 300f
private const val FEEDBACK_MS = 1200L
private const val TEXT_RATIO = 0.05f
private const val MIN_TEXT_RATIO = 0.025f
private const val PILL_CHROME = 0.9f * 2f + 1.25f + 0.55f
private const val PILL_GAP = 0.55f

private enum class PreviewAction(val label: String, val doneLabel: String, val icon: String) {
    COPY("Copy", "Copied", "copy"),
    UPLOAD("Upload", "Link copied", "upload"),
    DELETE("Delete", "Deleted", "trash"),
    VIEW("View", "View", "external-link"),
}

private class ActivePreview(val screenshot: Screenshot, val bitmap: PolyBitmap, val shownAt: Long) {
    @Volatile var pausedFor = 0L
    @Volatile var pausedSince = -1L
    @Volatile var dismissedAt = -1L
    @Volatile var feedback: PreviewAction? = null
    @Volatile var feedbackAt = 0L
}

private class PreviewColors(val accent: PolyColor, val danger: PolyColor, val onMedia: PolyColor, val shade: PolyColor)

class ScreenshotPreviewHud : Hud("screenshot_preview.json", "Screenshot Preview", Category.INFO) {
    override val description: String = "Shows each new screenshot with quick actions."

    @Slider(title = "Duration", description = "How long the preview stays on screen, in seconds.", min = 1f, max = 30f, step = 1f)
    var duration = 6f

    @Switch(title = "Hover actions", description = "With chat open, hover the preview for copy, upload and delete buttons, or click it to open it in Screenshots.")
    var hoverActions = true

    @Switch(title = "Pause on hover", description = "Keeps the preview on screen while your mouse is over it.")
    var pauseOnHover = true

    @Switch(title = "Flash", description = "Flashes the preview when a screenshot is taken.")
    var flash = true

    @Switch(title = "Countdown bar", description = "Shows how long is left before the preview hides.")
    var countdown = true

    private var _staticW = mutableStateOf(DEFAULT_WIDTH)
    override var staticW: Float get() = _staticW.value; set(v) { _staticW.value = v }

    override var staticH: Float
        get() = staticW * ASPECT
        set(_) {}

    override val resizeAxes: HudResize get() = HudResize.Width

    init {
        showBackground = false
        bgRadius = 8f
        font = Font.Poppins
    }

    override fun setup() {
        super.setup()
        staticWidth = true
    }

    override fun clone(): Hud = (super.clone() as ScreenshotPreviewHud).apply {
        _staticW = mutableStateOf(this@ScreenshotPreviewHud.staticW)
    }

    override fun update(): Boolean = false

    override fun shouldShow(): Boolean = visible()

    override fun showByDefault(): Boolean = true

    override fun multipleInstancesAllowed(): Boolean = false

    override fun minimumSize(): Pair<Float, Float> = 128f to 72f

    override val alwaysRedraw: Boolean get() = active != null

    private val square: Boolean get() = OneConfigTheme.active?.style == ThemeStyle.Pixel

    private fun visibleMs(preview: ActivePreview, now: Long): Long {
        val paused = preview.pausedFor + if (preview.pausedSince >= 0) now - preview.pausedSince else 0L
        return now - preview.shownAt - paused
    }

    private fun exitStart(preview: ActivePreview, now: Long): Long =
        if (preview.dismissedAt >= 0) preview.dismissedAt else preview.shownAt + (duration * 1000f).toLong() + (now - preview.shownAt - visibleMs(preview, now))

    private fun visible(): Boolean {
        val preview = active ?: return false
        val now = System.currentTimeMillis()
        if (now - exitStart(preview, now) > EXIT_MS) {
            if (active === preview) active = null
            return false
        }
        return true
    }

    @Composable
    override fun Content() {
        val theme = OneConfigTheme.current
        val colors = PreviewColors(
            accent = OneConfigTheme.accent.toPolyColor(),
            danger = theme.error.toPolyColor(),
            onMedia = theme.onMedia.toPolyColor(),
            shade = theme.onMediaShadow.toPolyColor(),
        )
        val icons = ICON_NAMES.associateWith { HudIcons.bitmap(it, ICON_PX) }
        NodeCanvas(PolyModifier.size(staticW, staticH), live = true) { x, y, w, h -> draw(x, y, w, h, colors, icons) }
    }

    private fun RenderContext.draw(x: Float, y: Float, width: Float, height: Float, colors: PreviewColors, icons: Map<String, PolyBitmap?>) {
        val preview = active?.takeIf { visible() }
        if (preview == null) clearRegions()
        if (preview == null && isReal && !HudManager.isEditing) return

        val unit = if (staticW > 0f) width / staticW else 1f
        if (showBackground) rect(x, y, width, height, bgPolyColor, bgRadius * unit)
        val ix = x + padLeft * unit
        val iy = y + padTop * unit
        val iw = (width - (padLeft + padRight) * unit).coerceAtLeast(1f)
        val ih = (height - (padTop + padBottom) * unit).coerceAtLeast(1f)
        val innerRadius = if (square) 0f else (if (showBackground) bgRadius - minOf(padLeft, padTop, padRight, padBottom) else bgRadius).coerceAtLeast(0f) * unit

        if (preview == null) {
            rect(ix, iy, iw, ih, colors.onMedia.withAlpha(0.25f), innerRadius)
            rectStroke(ix, iy, iw, ih, colors.onMedia.withAlpha(0.5f), 1f, innerRadius)
            val sample = "Screenshot preview"
            val size = fitLabel(listOf(sample), ih * TEXT_RATIO * textScale, iw * 0.9f) { _, _ -> 0f }
            drawLabel(sample, ix + (iw - measureLabel(sample, size)) / 2f, iy + ih / 2f, size, 1f)
            return
        }

        val now = System.currentTimeMillis()
        val sinceShown = (now - preview.shownAt).toFloat()
        val sinceExit = (now - exitStart(preview, now)).toFloat()
        val enter = easeOut((sinceShown / ENTER_MS).coerceIn(0f, 1f))
        val exit = if (sinceExit > 0f) easeIn((sinceExit / EXIT_MS).coerceIn(0f, 1f)) else 0f
        val alpha = enter * (1f - exit)
        val scale = (0.9f + 0.1f * enter) * (1f - 0.04f * exit)
        val drift = if (onRightHalf()) 1f else -1f

        val bitmap = preview.bitmap
        val fit = min(iw / bitmap.width, ih / bitmap.height)
        val bw = bitmap.width * fit
        val bh = bitmap.height * fit
        val (ax, ay) = alignmentFractions()
        val cx = ix + (iw - bw) * ax + bw / 2f + drift * bw * 0.1f * exit
        val cy = iy + (ih - bh) * ay + bh / 2f + bh * 0.08f * (1f - enter)
        val dw = bw * scale
        val dh = bh * scale
        val dx = cx - dw / 2f
        val dy = cy - dh / 2f
        val radius = innerRadius.coerceAtMost(min(dw, dh) / 2f)

        val inside = if (isReal && exit == 0f) {
            val hovered = this@ScreenshotPreviewHud.hoverRegion(PREVIEW_REGION, dx, dy, dw, dh, false)
            if (hoverActions) {
                this@ScreenshotPreviewHud.clickRegion(VIEW_REGION, dx, dy, dw, dh, false) { run(preview, PreviewAction.VIEW) }
            } else {
                removeClickRegion(VIEW_REGION)
            }
            hovered
        } else {
            clearRegions()
            false
        }
        updatePause(preview, inside && pauseOnHover, now)

        save()
        clipRRect(dx, dy, dw, dh, radius)
        image(bitmap, dx, dy, dw, dh, 0f, 0f, 1f, 1f, PolyColor.WHITE.withAlpha(alpha), FilterQuality.Low)
        if (flash && sinceShown < FLASH_MS) {
            rect(dx, dy, dw, dh, PolyColor.WHITE.withAlpha(0.85f * (1f - sinceShown / FLASH_MS) * alpha))
        }
        if (countdown && preview.dismissedAt < 0) {
            val left = 1f - (visibleMs(preview, now) / (duration * 1000f)).coerceIn(0f, 1f)
            val bar = (dh * 0.015f).coerceAtLeast(1f)
            rect(dx, dy + dh - bar, dw * left, bar, colors.accent.withAlpha(alpha))
        }
        if (inside && hoverActions) drawActions(preview, dx, dy, dw, dh, alpha, colors, icons) else clearActionRegions()
        restore()
        rectStroke(dx, dy, dw, dh, colors.onMedia.withAlpha((if (inside) 0.55f else 0.25f) * alpha), 1f, radius)
    }

    private fun RenderContext.drawActions(
        preview: ActivePreview,
        dx: Float, dy: Float, dw: Float, dh: Float,
        alpha: Float,
        colors: PreviewColors,
        icons: Map<String, PolyBitmap?>,
    ): PreviewAction {
        rect(dx, dy, dw, dh, colors.shade.withAlpha(0.35f * alpha))

        val now = System.currentTimeMillis()
        val actions = ACTIONS
        fun label(action: PreviewAction) =
            if (preview.feedback == action && now - preview.feedbackAt < FEEDBACK_MS) action.doneLabel else action.label

        val labels = actions.map(::label)
        val margin = dh * 0.06f
        val labelSize = fitLabel(labels, dh * TEXT_RATIO * textScale, dw - margin * 2f) { size, count -> size * PILL_CHROME * count + size * PILL_GAP * (count - 1) }
        val withLabels = labelSize >= dh * MIN_TEXT_RATIO
        val pillH = if (withLabels) min(labelSize * 2.4f, dh * 0.32f) else dh * 0.2f
        val iconSize = if (withLabels) labelSize * 1.25f else pillH * 0.52f
        val padX = if (withLabels) labelSize * 0.9f else 0f
        val gap = if (withLabels) labelSize * PILL_GAP else pillH * 0.25f
        val widths = actions.mapIndexed { i, _ -> if (withLabels) padX * 2 + iconSize + gap + measureLabel(labels[i], labelSize) else pillH }
        var px = dx + (dw - widths.sum() - gap * (actions.size - 1)) / 2f
        val py = dy + dh - pillH - margin
        var hit = PreviewAction.VIEW

        for ((index, action) in actions.withIndex()) {
            val w = widths[index]
            val over = this@ScreenshotPreviewHud.clickRegion(action.name, px, py, w, pillH, false) { run(preview, action) }
            if (over) hit = action
            val done = preview.feedback == action && now - preview.feedbackAt < FEEDBACK_MS
            val fill = when {
                done -> colors.accent
                over && action == PreviewAction.DELETE -> colors.danger
                else -> colors.shade.withAlpha(0.9f)
            }
            rect(px, py, w, pillH, fill.withAlpha(fill.alpha / 255f * alpha), pillRadius(pillH))
            if (over && !done && action != PreviewAction.DELETE) rect(px, py, w, pillH, colors.onMedia.withAlpha(0.12f * alpha), pillRadius(pillH))
            rectStroke(px, py, w, pillH, colors.onMedia.withAlpha((if (over) 0.3f else 0.12f) * alpha), 1f, pillRadius(pillH))
            val ix = if (withLabels) px + padX else px + (w - iconSize) / 2f
            icons[if (done) "tick" else action.icon]?.let {
                image(it, ix, py + (pillH - iconSize) / 2f, iconSize, iconSize, 0f, 0f, 1f, 1f, textTint(alpha), FilterQuality.Low)
            }
            if (withLabels) drawLabel(labels[index], ix + iconSize + gap, py + pillH / 2f, labelSize, alpha)
            px += w + gap
        }

        if (hit == PreviewAction.VIEW) {
            val hint = "Click to view"
            val hintSize = fitLabel(listOf(hint), dh * TEXT_RATIO * 0.8f * textScale, dw * 0.7f) { _, _ -> 0f }
            val hintPad = hintSize * 0.8f
            val hw = measureLabel(hint, hintSize) + hintPad * 2f
            val hh = labelHeight(hintSize) + hintPad
            rect(dx + (dw - hw) / 2f, dy + margin, hw, hh, colors.shade.withAlpha(0.65f * alpha), pillRadius(hh))
            drawLabel(hint, dx + (dw - hw) / 2f + hintPad, dy + margin + hh / 2f, hintSize, alpha)
        }
        return hit
    }

    private fun alignmentFractions(): Pair<Float, Float> = when (alignment) {
        PolyAlign.TopLeft -> 0f to 0f
        PolyAlign.Top -> 0.5f to 0f
        PolyAlign.TopRight -> 1f to 0f
        PolyAlign.Left -> 0f to 0.5f
        PolyAlign.Center -> 0.5f to 0.5f
        PolyAlign.Right -> 1f to 0.5f
        PolyAlign.BottomLeft -> 0f to 1f
        PolyAlign.Bottom -> 0.5f to 1f
        PolyAlign.BottomRight -> 1f to 1f
    }

    private fun cased(text: String): String = when (caseType) {
        1 -> text.uppercase()
        2 -> text.lowercase()
        else -> text
    }

    private fun textTint(alpha: Float): PolyColor {
        val color = textPolyColor
        return color.withAlpha(color.alpha / 255f * alpha)
    }

    private fun shadowTint(alpha: Float): PolyColor {
        val color = shadowPolyColor
        return color.withAlpha(color.alpha / 255f * alpha)
    }

    private fun pillRadius(height: Float) = if (square) 0f else height / 2f

    private fun fitLabel(labels: List<String>, preferred: Float, available: Float, chrome: (Float, Int) -> Float): Float {
        var size = preferred
        repeat(3) {
            val total = labels.sumOf { measureLabel(it, size).toDouble() }.toFloat() + chrome(size, labels.size)
            if (total <= available || total <= 0f) return size
            size *= available / total
        }
        return size
    }

    private fun labelFont(size: Float): PolyFont = when (font) {
        Font.Poppins -> PolyFont(getPoppinsFontName(), size)
        Font.Minecraft -> PolyFont(if (textBold || textWeight == Weight.Bold || textWeight == Weight.Black) "minecraft-bold" else "minecraft", size)
    }

    private fun labelHeight(size: Float): Float = labelFont(size).metrics.let { it.descent - it.ascent }

    private fun measureLabel(text: String, size: Float): Float = labelFont(size).measureTextWidth(cased(text))

    private fun RenderContext.drawLabel(text: String, x: Float, centerY: Float, size: Float, alpha: Float) {
        val value = cased(text)
        val font = labelFont(size)
        val metrics = font.metrics
        val baseline = centerY - (metrics.descent + metrics.ascent) / 2f
        val offset = font.size / 8f
        when {
            showShadow && fullShadow -> textOutline(value, x, baseline, textTint(alpha), font, offset)
            showShadow -> {
                text(value, x + shadowOffsetX / 2f * offset, baseline + shadowOffsetY / 2f * offset, shadowTint(alpha), font)
                text(value, x, baseline, textTint(alpha), font)
            }
            else -> text(value, x, baseline, textTint(alpha), font)
        }
        if (textUnderline) {
            rect(x, baseline + metrics.descent * 0.35f, font.measureTextWidth(value), offset.coerceAtLeast(1f), textTint(alpha))
        }
    }

    private fun updatePause(preview: ActivePreview, paused: Boolean, now: Long) {
        if (paused && preview.pausedSince < 0) {
            preview.pausedSince = now
        } else if (!paused && preview.pausedSince >= 0) {
            preview.pausedFor += now - preview.pausedSince
            preview.pausedSince = -1
        }
    }

    private fun clearActionRegions() {
        for (action in ACTIONS) removeClickRegion(action.name)
    }

    private fun clearRegions() {
        removeClickRegion(PREVIEW_REGION)
        removeClickRegion(VIEW_REGION)
        clearActionRegions()
    }

    private fun onRightHalf(): Boolean = x + scaledWidth / 2f > HudManager.guiScreenWidth / 2f

    companion object {
        private const val MAX_WIDTH = 960
        private const val ICON_PX = 48
        private const val PREVIEW_REGION = "preview"
        private const val VIEW_REGION = "view"

        private val ACTIONS = listOf(PreviewAction.COPY, PreviewAction.UPLOAD, PreviewAction.DELETE)
        private val ICON_NAMES = ACTIONS.map { it.icon } + "tick"

        @Volatile
        private var active: ActivePreview? = null

        private val order = PreviewOrder()

        private fun easeOut(t: Float) = 1f - (1f - t).pow(3)
        private fun easeIn(t: Float) = t * t * t

        private fun run(preview: ActivePreview, action: PreviewAction) {
            if (preview !== active) return
            val screenshot = preview.screenshot
            fun done() {
                preview.feedback = action
                preview.feedbackAt = System.currentTimeMillis()
            }
            when (action) {
                PreviewAction.COPY -> ScreenshotActions.copy(screenshot) { if (it) done() }
                PreviewAction.UPLOAD -> ScreenshotActions.upload(screenshot) { if (it != null) done() }
                PreviewAction.VIEW -> {
                    ScreenshotActions.openManager(screenshot)
                    preview.dismissedAt = System.currentTimeMillis()
                }
                PreviewAction.DELETE -> if (ScreenshotActions.delete(screenshot)) preview.dismissedAt = System.currentTimeMillis()
            }
        }

        fun showCaptured(file: File, argb: IntArray, width: Int, height: Int) {
            val screenshot = Screenshot(file)
            val ticket = order.claim(screenshot.id, System.currentTimeMillis(), fromPixels = true) ?: return
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            image.raster.setDataElements(0, 0, width, height, argb)
            val bitmap = PolyBitmap.fromAwt(Thumbnails.downscale(image, MAX_WIDTH))
            order.commit(ticket) { active = ActivePreview(screenshot, bitmap, System.currentTimeMillis()) }
        }

        fun show(screenshot: Screenshot) {
            val ticket = order.claim(screenshot.id, screenshot.modified, fromPixels = false) ?: return
            Multithreading.submit {
                var image: BufferedImage? = null
                for (attempt in 0 until 5) {
                    image = runCatching { screenshot.readImage() }.getOrNull()
                    if (image != null || !order.isCurrent(ticket)) break
                    Thread.sleep(100)
                }
                val bitmap = PolyBitmap.fromAwt(Thumbnails.downscale(image ?: return@submit, MAX_WIDTH))
                order.commit(ticket) { active = ActivePreview(screenshot, bitmap, System.currentTimeMillis()) }
            }
        }

        fun dismiss(screenshot: Screenshot) {
            order.forget(screenshot.id)
            val preview = active ?: return
            if (preview.screenshot.id == screenshot.id && preview.dismissedAt < 0) preview.dismissedAt = System.currentTimeMillis()
        }
    }
}
