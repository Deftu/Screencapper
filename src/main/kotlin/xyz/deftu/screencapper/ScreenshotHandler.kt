package xyz.deftu.screencapper

import dev.isxander.shotify.upload.ImgurUploadTask
import gg.essential.universal.UDesktop
import net.minecraft.client.texture.NativeImage
import net.minecraft.text.ClickEvent
import net.minecraft.text.Text
import net.minecraft.text.TranslatableText
import net.minecraft.util.Formatting
import xyz.deftu.screencapper.config.ScreencapperConfig
import xyz.deftu.screencapper.config.UploadMode
import xyz.deftu.screencapper.gui.preview.ScreenshotPreview
import xyz.deftu.screencapper.upload.ShareXUploadTask
import xyz.deftu.screencapper.utils.ChatHelper
import xyz.deftu.screencapper.utils.Multithreading
import xyz.deftu.screencapper.utils.Screenshot
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.net.URL
import javax.imageio.ImageIO

object ScreenshotHandler {
    private var screenshot: Screenshot? = null

    private fun upload(screenshot: Screenshot): URL {
        var screenshot = screenshot
        if (screenshot.url != null) return screenshot.url!!
        screenshot = when (ScreencapperConfig.uploadMode) {
            UploadMode.IMGUR -> ImgurUploadTask.upload(screenshot)
            UploadMode.SHAREX -> ShareXUploadTask.upload(screenshot)
        }
        return screenshot.url!!
    }

    fun handle(image: NativeImage, file: File) {
        if (!ScreencapperConfig.toggle) return
        var screenshot = Screenshot(image, file)
        this.screenshot = screenshot
        ScreenshotPreview.append(screenshot)
        if (ScreencapperConfig.uploadToggle) UDesktop.setClipboardString(upload(screenshot).toString())
    }

    fun createText(original: Text): Text {
        if (!ScreencapperConfig.toggle || screenshot == null) return original

        val uploadText = TranslatableText("${Screencapper.ID}.text.chat.upload")
            .formatted(Formatting.BOLD, Formatting.UNDERLINE, Formatting.GREEN).apply {
                styled {
                    it.withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, upload(screenshot!!).toString()))
                }
            }
        val copyText = TranslatableText("${Screencapper.ID}.text.chat.copy")
            .formatted(Formatting.BOLD, Formatting.UNDERLINE, Formatting.BLUE).apply {
                styled {
                    it.withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/screenshot copy"))
                }
            }
        val openText = TranslatableText("${Screencapper.ID}.text.chat.open")
            .formatted(Formatting.BOLD, Formatting.UNDERLINE, Formatting.YELLOW).apply {
                styled {
                    it.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_FILE, screenshot?.file?.absolutePath))
                }
            }
        val openFolderText = TranslatableText("${Screencapper.ID}.text.chat.open_folder")
            .formatted(Formatting.BOLD, Formatting.UNDERLINE, Formatting.GOLD).apply {
                styled {
                    it.withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, screenshot?.file?.parentFile?.absolutePath))
                }
            }
        val deleteText = TranslatableText("${Screencapper.ID}.text.chat.delete")
            .formatted(Formatting.BOLD, Formatting.UNDERLINE, Formatting.RED).apply {
                styled {
                    it.withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/screenshot delete"))
                }
             }
        return TranslatableText("${Screencapper.ID}.text.chat.screenshot").apply {
            formatted(Formatting.WHITE).append(" ")
            if (ScreencapperConfig.chatUpload) screenshot?.url?.let { append("[").append(uploadText).append("] ") }
            if (ScreencapperConfig.chatCopy) append("[").append(copyText).append("] ")
            if (ScreencapperConfig.chatOpen) screenshot?.file?.let { append("[").append(openText).append("] ") }
            if (ScreencapperConfig.chatOpenFolder) screenshot?.file?.parent?.let { append("[").append(openFolderText).append("] ") }
            if (ScreencapperConfig.chatDelete) append("[").append(deleteText).append("] ")
        }
    }

    internal fun delete() =
        screenshot?.file?.delete()

    internal fun copy() {
        try {
            screenshot?.let { scr ->
                val selection = ImageSelection(scr)
                Multithreading.runAsync {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

private class ImageSelection(
    val screenshot: Screenshot
) : Transferable {
    override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
    override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.imageFlavor
    override fun getTransferData(flavor: DataFlavor?): Any {
        if (flavor == DataFlavor.imageFlavor) return ImageIO.read(screenshot.file)
        throw UnsupportedFlavorException(flavor)
    }
}
