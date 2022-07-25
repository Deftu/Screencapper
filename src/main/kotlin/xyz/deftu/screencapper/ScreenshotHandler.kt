package xyz.deftu.screencapper

import ca.weblite.objc.Client
import dev.isxander.shotify.upload.ImgurUploadTask
import gg.essential.universal.UDesktop
import net.minecraft.client.MinecraftClient
import net.minecraft.client.texture.NativeImage
import net.minecraft.text.ClickEvent
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.apache.commons.lang3.SystemUtils
import xyz.deftu.screencapper.config.ScreencapperConfig
import xyz.deftu.screencapper.config.UploadMode
import xyz.deftu.screencapper.gui.preview.ScreenshotPreview
import xyz.deftu.screencapper.upload.ShareXUploadTask
import xyz.deftu.screencapper.utils.ChatHelper
import xyz.deftu.screencapper.utils.Multithreading
import xyz.deftu.screencapper.utils.Screenshot
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
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

    fun handle(image: NativeImage, bytes: ByteArray, file: File) {
        if (!ScreencapperConfig.toggle) return
        var screenshot = Screenshot(image, bytes, file)
        this.screenshot = screenshot
        ScreenshotPreview.append(screenshot)
        if (ScreencapperConfig.autoCopy) copy()
        if (ScreencapperConfig.uploadToggle) UDesktop.setClipboardString(upload(screenshot).toString())
    }

    fun createText(original: Text): Text {
        if (!ScreencapperConfig.toggle || screenshot == null) return original

        val uploadText = ChatHelper.createTranslatableText("${Screencapper.ID}.text.chat.upload")
            .formatted(Formatting.BOLD, Formatting.UNDERLINE, Formatting.GREEN).apply {
                styled {
                    it.withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, upload(screenshot!!).toString()))
                }
            }
        val copyText = ChatHelper.createTranslatableText("${Screencapper.ID}.text.chat.copy")
            .formatted(Formatting.BOLD, Formatting.UNDERLINE, Formatting.BLUE).apply {
                styled {
                    it.withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/screenshot copy"))
                }
            }
        val openText = ChatHelper.createTranslatableText("${Screencapper.ID}.text.chat.open")
            .formatted(Formatting.BOLD, Formatting.UNDERLINE, Formatting.YELLOW).apply {
                styled {
                    it.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_FILE, screenshot?.file?.absolutePath))
                }
            }
        val openFolderText = ChatHelper.createTranslatableText("${Screencapper.ID}.text.chat.open_folder")
            .formatted(Formatting.BOLD, Formatting.UNDERLINE, Formatting.GOLD).apply {
                styled {
                    it.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_FILE, screenshot?.file?.parentFile?.absolutePath))
                }
            }
        val deleteText = ChatHelper.createTranslatableText("${Screencapper.ID}.text.chat.delete")
            .formatted(Formatting.BOLD, Formatting.UNDERLINE, Formatting.RED).apply {
                styled {
                    it.withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/screenshot delete"))
                }
             }
        return ChatHelper.createTranslatableText("${Screencapper.ID}.text.chat.screenshot").apply {
            formatted(Formatting.WHITE).append(" ")
            if (ScreencapperConfig.chatUpload) append("[").append(uploadText).append("] ")
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
                if (!SystemUtils.IS_OS_MAC) {
                    val selection = ImageSelection(scr)
                    Multithreading.runAsync {
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
                    }
                } else {
                    val client = Client.getInstance()
                    val url = client.sendProxy("NSURL", "fileURLWithPath:", scr.file.absolutePath)
                    val image = client.sendProxy("NSImage", "alloc")
                    image.send("initWithContentsOfURL:", url)
                    var array = client.sendProxy("NSArray", "array")
                    array = array.sendProxy("arrayByAddingObject:", image)
                    val pasteboard = client.sendProxy("NSPasteboard", "generalPasteboard")
                    pasteboard.send("clearContents")
                    val wasSuccessful = pasteboard.sendBoolean("writeObjects:", array)
                    if (!wasSuccessful) throw IllegalStateException("Failed to copy image to clipboard.")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            MinecraftClient.getInstance().inGameHud.chatHud.addMessage(ChatHelper.createTranslatableText("${Screencapper.ID}.text.chat.copy.failed", "${e.javaClass.name}: ${e.message}"))
        }
    }
}

private class ImageSelection(
    val screenshot: Screenshot
) : Transferable {
    override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
    override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.imageFlavor
    override fun getTransferData(flavor: DataFlavor?): Any {
        if (flavor != DataFlavor.imageFlavor) throw UnsupportedFlavorException(flavor)
        val image = ImageIO.read(ByteArrayInputStream(screenshot.bytes))
        return BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB).apply {
            graphics.drawImage(image, 0, 0, null)
        }
    }
}
