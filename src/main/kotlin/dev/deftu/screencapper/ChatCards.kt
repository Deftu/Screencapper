package dev.deftu.screencapper

import dev.deftu.screencapper.chat.ChatLines
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.screenshot.Screenshot
import dev.deftu.screencapper.screenshot.UploadRecord
import dev.deftu.screencapper.upload.UploaderProfile
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

object ChatCards {
    private val BAR_INFO = NamedTextColor.AQUA
    private val BAR_SUCCESS = NamedTextColor.GREEN
    private val BAR_DANGER = NamedTextColor.RED
    private val MUTED = NamedTextColor.GRAY
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private const val MAX_CARDS = 128

    private class Card(val details: String, @Volatile var link: UploadRecord? = null)

    private val cards = ConcurrentHashMap<String, Card>()

    private fun headerKey(screenshot: Screenshot) = "header:${screenshot.name}"
    private fun actionsKey(screenshot: Screenshot) = actionsKey(screenshot.name)
    private fun actionsKey(name: String) = "actions:$name"

    fun captured(screenshot: Screenshot) {
        val details = listOfNotNull(
            TIME_FORMAT.format(Instant.ofEpochMilli(screenshot.modified).atZone(ZoneId.systemDefault())),
            resolution(screenshot),
            size(screenshot.size),
        ).joinToString(" · ")
        cards[screenshot.name] = Card(details)
        if (cards.size > MAX_CARDS) cards.keys.removeIf { it != screenshot.name && !ChatLines.isTracked(actionsKey(it)) }
        ChatLines.post(headerKey(screenshot), header(screenshot))
        ChatLines.post(actionsKey(screenshot), actions(screenshot))
    }

    private fun hasCard(screenshot: Screenshot): Boolean = cards.containsKey(screenshot.name) && ChatLines.isTracked(actionsKey(screenshot))

    fun showActions(screenshot: Screenshot) {
        if (hasCard(screenshot)) ChatLines.replace(actionsKey(screenshot), actions(screenshot))
    }

    fun showUploaderPicker(screenshot: Screenshot) {
        if (!hasCard(screenshot)) return
        val choices = ScreencapperConfig.profiles.map { profile ->
            Component.text(profile.name, NamedTextColor.GREEN)
                .hoverEvent(HoverEvent.showText(tooltip(profile.host, "Upload to ${profile.name}")))
                .clickEvent(ClickEvent.runCommand("/screencapper upload-to ${profile.id} ${screenshot.name}")) as Component
        } + back(screenshot, "Cancel")
        ChatLines.replace(actionsKey(screenshot), line(BAR_INFO).append(Component.text("Upload to  ", MUTED)).append(join(choices)))
    }

    fun showDeleteConfirm(screenshot: Screenshot) {
        if (!hasCard(screenshot)) return
        ChatLines.replace(
            actionsKey(screenshot),
            line(BAR_DANGER)
                .append(Component.text("Delete this screenshot?  ", NamedTextColor.WHITE).hoverEvent(HoverEvent.showText(tooltip(screenshot.name, "This can't be undone"))))
                .append(join(listOf(
                    Component.text("Delete", NamedTextColor.RED)
                        .hoverEvent(HoverEvent.showText(Component.text("Delete it for good", NamedTextColor.WHITE)))
                        .clickEvent(ClickEvent.runCommand(command("delete", screenshot))),
                    back(screenshot, "Keep"),
                ))),
        )
    }

    fun uploading(screenshot: Screenshot, profile: UploaderProfile) {
        if (!hasCard(screenshot)) return
        ChatLines.replace(actionsKey(screenshot), line(BAR_INFO).append(Component.text("Uploading to ${profile.name}...", MUTED)))
    }

    fun uploadFinished(screenshot: Screenshot, record: UploadRecord?) {
        val card = cards[screenshot.name] ?: return
        if (!hasCard(screenshot)) return
        if (record != null && ScreencapperConfig.chatUploadLinks) {
            card.link = record
            ChatLines.replace(headerKey(screenshot), header(screenshot))
        }
        ChatLines.replace(actionsKey(screenshot), actions(screenshot))
    }

    fun deleted(screenshot: Screenshot) {
        val card = cards.remove(screenshot.name) ?: return
        ChatLines.replace(
            headerKey(screenshot),
            line(MUTED)
                .append(Component.text("Screenshot deleted", MUTED).decorate(TextDecoration.STRIKETHROUGH))
                .append(Component.text(" · ${card.details}", NamedTextColor.DARK_GRAY)),
        )
        ChatLines.remove(actionsKey(screenshot))
    }

    private fun header(screenshot: Screenshot): Component {
        val card = cards[screenshot.name]
        val link = card?.link
        val title = translatable("screencapper.text.chat.screenshot", "Screenshot saved", NamedTextColor.WHITE)
            .hoverEvent(HoverEvent.showText(tooltip(screenshot.name, listOfNotNull(card?.details, "Click to view it in Screenshots").joinToString("\n"))))
            .clickEvent(ClickEvent.runCommand(command("view", screenshot)))
        val out = line(if (link != null) BAR_SUCCESS else BAR_INFO).append(title)
        if (link == null) return out.append(Component.text(" · ${card?.details.orEmpty()}", MUTED))
        return out.append(Component.text(" · ", MUTED))
            .append(
                Component.text(link.url.removePrefix("https://").removePrefix("http://"), NamedTextColor.AQUA)
                    .decorate(TextDecoration.UNDERLINED)
                    .hoverEvent(HoverEvent.showText(tooltip(link.url, "Uploaded to ${link.uploaderName}. Click to open it")))
                    .clickEvent(ClickEvent.openUrl(link.url)),
            )
            .append(Component.text("  "))
            .append(
                Component.text("Copy link", NamedTextColor.WHITE)
                    .hoverEvent(HoverEvent.showText(Component.text("Copy the link to your clipboard", NamedTextColor.WHITE)))
                    .clickEvent(ClickEvent.runCommand(command("copy-link", screenshot))),
            )
    }

    private fun actions(screenshot: Screenshot): Component {
        val actions = mutableListOf<Component>()
        if (ScreencapperConfig.chatView) actions += action("view", "View", NamedTextColor.AQUA, "Open it in the Screenshots page", command("view", screenshot), underline = true)
        if (ScreencapperConfig.chatCopy) actions += action("copy", "Copy", NamedTextColor.WHITE, "Copy the image to your clipboard", command("copy", screenshot))
        if (ScreencapperConfig.chatUpload) {
            val target = ScreencapperConfig.defaultProfile.name
            val again = cards[screenshot.name]?.link != null
            var upload: Component = action("upload", if (again) "Upload again" else "Upload", NamedTextColor.GREEN, "Upload to $target and copy the link", command("upload", screenshot), translate = !again)
            if (ScreencapperConfig.profiles.size > 1) {
                upload = upload.append(
                    Component.text(" ▾", MUTED)
                        .hoverEvent(HoverEvent.showText(Component.text("Choose another uploader", NamedTextColor.WHITE)))
                        .clickEvent(ClickEvent.runCommand(command("pick", screenshot))),
                )
            }
            actions += upload
        }
        if (ScreencapperConfig.chatOpen) actions += action("open", "Open", NamedTextColor.YELLOW, "Open the image", command("open", screenshot))
        if (ScreencapperConfig.chatOpenFolder) actions += action("open_folder", "Folder", NamedTextColor.GOLD, "Show your screenshots folder", command("folder", screenshot))
        if (ScreencapperConfig.chatDelete) {
            val delete = if (ScreencapperConfig.confirmChatDeletes) command("confirm-delete", screenshot) else command("delete", screenshot)
            actions += action("delete", "Delete", NamedTextColor.RED, "Delete this screenshot", delete)
        }
        return line(BAR_INFO).append(join(actions))
    }

    private fun back(screenshot: Screenshot, label: String): Component =
        Component.text(label, MUTED)
            .hoverEvent(HoverEvent.showText(Component.text("Go back to the actions", NamedTextColor.WHITE)))
            .clickEvent(ClickEvent.runCommand(command("card", screenshot)))

    private fun line(bar: TextColor): TextComponent = Component.text("▌ ", bar)

    private fun join(parts: List<Component>): Component {
        var out: Component = Component.empty()
        parts.forEachIndexed { index, part ->
            if (index > 0) out = out.append(Component.text("  "))
            out = out.append(part)
        }
        return out
    }

    private fun action(
        key: String,
        fallback: String,
        color: NamedTextColor,
        hint: String,
        command: String,
        underline: Boolean = false,
        translate: Boolean = true,
    ): Component {
        var label = if (translate) translatable("screencapper.text.chat.$key", fallback, color) else Component.text(fallback, color)
        if (underline) label = label.decorate(TextDecoration.UNDERLINED)
        return label.hoverEvent(HoverEvent.showText(Component.text(hint, NamedTextColor.WHITE))).clickEvent(ClickEvent.runCommand(command))
    }

    private fun translatable(key: String, fallback: String, color: TextColor): Component =
        Component.translatable().key(key).fallback(fallback).color(color).build()

    private fun tooltip(title: String, hint: String): Component =
        Component.text(title, NamedTextColor.WHITE).append(Component.newline()).append(Component.text(hint, MUTED))

    private fun command(action: String, screenshot: Screenshot): String = "/screencapper $action ${screenshot.name}"

    private fun resolution(screenshot: Screenshot): String? = runCatching {
        ImageIO.createImageInputStream(screenshot.file).use { stream ->
            val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: return@use null
            try {
                reader.input = stream
                "${reader.getWidth(0)}×${reader.getHeight(0)}"
            } finally {
                reader.dispose()
            }
        }
    }.getOrNull()

    private fun size(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
