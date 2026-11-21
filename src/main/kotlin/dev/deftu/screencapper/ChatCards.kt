package dev.deftu.screencapper

import dev.deftu.screencapper.chat.ChatLines
import dev.deftu.screencapper.config.DeletionKind
import dev.deftu.screencapper.config.ScreencapperConfig
import dev.deftu.screencapper.screenshot.Panoramas
import dev.deftu.screencapper.screenshot.Screenshot
import dev.deftu.screencapper.screenshot.SharedUploads
import dev.deftu.screencapper.screenshot.UploadHistory
import dev.deftu.screencapper.screenshot.UploadRecord
import dev.deftu.screencapper.upload.UploaderProfile
import dev.deftu.screencapper.upload.Uploaders
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
import java.util.concurrent.atomic.AtomicLong
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
    private val renames = ConcurrentHashMap<String, String>()
    private val panoramas = ConcurrentHashMap<String, String>()
    private val reusedKeys = AtomicLong()

    private fun cardKey(screenshot: Screenshot) = renames[screenshot.id] ?: screenshot.id
    private fun headerKey(screenshot: Screenshot) = headerKey(cardKey(screenshot))
    private fun headerKey(key: String) = "header:$key"
    private fun actionsKey(screenshot: Screenshot) = actionsKey(cardKey(screenshot))
    private fun actionsKey(key: String) = "actions:$key"

    fun captured(screenshot: Screenshot) {
        val details = listOfNotNull(
            TIME_FORMAT.format(Instant.ofEpochMilli(screenshot.modified).atZone(ZoneId.systemDefault())),
            resolution(screenshot),
            size(screenshot.size),
        ).joinToString(" · ")
        renames.remove(screenshot.id)
        val key = if (renames.containsValue(screenshot.id)) "${screenshot.id}#${reusedKeys.incrementAndGet()}" else screenshot.id
        if (key != screenshot.id) renames[screenshot.id] = key
        cards[key] = Card(details)
        if (cards.size > MAX_CARDS) {
            cards.keys.removeIf { it != key && !ChatLines.isTracked(actionsKey(it)) }
            renames.values.retainAll(cards.keys)
        }
        ChatLines.post(headerKey(screenshot), header(screenshot))
        ChatLines.post(actionsKey(screenshot), actions(screenshot))
    }

    fun panoramaSaved(id: String, faceSize: Int) {
        val details = "${TIME_FORMAT.format(Instant.now().atZone(ZoneId.systemDefault()))} · $faceSize×$faceSize"
        panoramas[id] = details
        if (panoramas.size > MAX_CARDS) panoramas.keys.removeIf { it != id && !ChatLines.isTracked(panoramaKey(it)) }
        ChatLines.post(panoramaKey(id), panorama(id, details))
    }

    fun panoramasChanged() {
        panoramas.forEach { (id, details) -> if (ChatLines.isTracked(panoramaKey(id))) ChatLines.replace(panoramaKey(id), panorama(id, details)) }
    }

    private fun panorama(id: String, details: String): Component {
        val folder = "/screencapper panorama-folder $id"
        val title = translatable("screencapper.text.chat.panorama", "Panorama saved", NamedTextColor.WHITE)
            .hoverEvent(HoverEvent.showText(tooltip(id.substringAfterLast('/'), "Saved in screenshots/${id.substringBeforeLast('/')}. Click to open its folder")))
            .clickEvent(ClickEvent.runCommand(folder))
        val background = if (Panoramas.isTitleBackground(id)) {
            action("panorama_reset_title", "Reset title background", NamedTextColor.YELLOW, "Show Minecraft's panorama on the title screen again", "/screencapper panorama-reset-title $id")
        } else {
            action("panorama_set_title", "Set as title background", NamedTextColor.GREEN, "Show this panorama on the title screen", "/screencapper panorama-set-title $id")
        }
        val open = action("panorama_folder", "Open folder", NamedTextColor.GOLD, "Show the folder with its six faces", folder)
        return line(BAR_INFO).append(title).append(Component.text(" · $details  ", MUTED)).append(join(listOf(background, open)))
    }

    private fun panoramaKey(id: String) = "panorama:$id"

    private fun hasCard(screenshot: Screenshot): Boolean = cards.containsKey(cardKey(screenshot)) && ChatLines.isTracked(actionsKey(screenshot))

    fun renamed(from: Screenshot, to: Screenshot) {
        val key = cardKey(from)
        if (!cards.containsKey(key)) return
        renames.remove(from.id)
        renames[to.id] = key
        if (!ChatLines.isTracked(actionsKey(key))) return
        ChatLines.replace(headerKey(to), header(to))
        ChatLines.replace(actionsKey(to), actions(to))
    }

    fun showActions(screenshot: Screenshot) {
        if (hasCard(screenshot)) ChatLines.replace(actionsKey(screenshot), actions(screenshot))
    }

    fun showUploaderPicker(screenshot: Screenshot) {
        if (!hasCard(screenshot)) return
        val choices = ScreencapperConfig.profiles.map { profile ->
            Component.text(profile.name, NamedTextColor.GREEN)
                .hoverEvent(HoverEvent.showText(tooltip(profile.host, "Upload to ${profile.name}")))
                .clickEvent(ClickEvent.runCommand("/screencapper upload-to ${profile.id} ${screenshot.id}")) as Component
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

    fun showDeleteUploadConfirm(screenshot: Screenshot, record: UploadRecord) {
        if (!hasCard(screenshot)) return
        val host = record.uploaderName
        val sharedWith = SharedUploads.describe(UploadHistory.sharing(screenshot, record), screenshot.id)
        val (question, label, hint) = when {
            sharedWith != null -> Triple("Remove this link?  ", "Remove link", "Still used by $sharedWith, so it stays on $host")
            record.deletion?.kind == DeletionKind.BROWSER -> Triple("Delete this upload?  ", "Open $host's deletion page", "Finish deleting it in your browser. The link is removed from your history")
            else -> Triple("Delete this upload?  ", "Delete from $host", "Your local copy stays")
        }
        ChatLines.replace(
            actionsKey(screenshot),
            line(BAR_DANGER)
                .append(Component.text(question, NamedTextColor.WHITE).hoverEvent(HoverEvent.showText(tooltip(record.url.ifBlank { host }, hint))))
                .append(join(listOf(
                    Component.text(label, NamedTextColor.RED)
                        .hoverEvent(HoverEvent.showText(Component.text(hint, NamedTextColor.WHITE)))
                        .clickEvent(ClickEvent.runCommand(command("delete-upload ${record.uploadedAt}", screenshot))),
                    back(screenshot, "Cancel"),
                ))),
        )
    }

    fun uploadsChanged(screenshot: Screenshot) {
        val card = cards[cardKey(screenshot)] ?: return
        card.link?.let { if (it !in UploadHistory.of(screenshot)) card.link = null }
        if (!hasCard(screenshot)) return
        ChatLines.replace(headerKey(screenshot), header(screenshot))
        ChatLines.replace(actionsKey(screenshot), actions(screenshot))
    }

    fun uploading(screenshot: Screenshot, profile: UploaderProfile) {
        if (!hasCard(screenshot)) return
        ChatLines.replace(actionsKey(screenshot), line(BAR_INFO).append(Component.text("Uploading to ${profile.name}...", MUTED)))
    }

    fun uploadFinished(screenshot: Screenshot, record: UploadRecord?) {
        val card = cards[cardKey(screenshot)] ?: return
        if (!hasCard(screenshot)) return
        if (record != null && ScreencapperConfig.chatUploadLinks) {
            card.link = record
            ChatLines.replace(headerKey(screenshot), header(screenshot))
        }
        ChatLines.replace(actionsKey(screenshot), actions(screenshot))
    }

    fun deleted(screenshot: Screenshot) {
        val key = cardKey(screenshot)
        renames.remove(screenshot.id)
        val card = cards.remove(key) ?: return
        ChatLines.replace(
            headerKey(key),
            line(MUTED)
                .append(Component.text("Screenshot deleted", MUTED).decorate(TextDecoration.STRIKETHROUGH))
                .append(Component.text(" · ${card.details}", NamedTextColor.DARK_GRAY)),
        )
        ChatLines.remove(actionsKey(key))
    }

    private fun header(screenshot: Screenshot): Component {
        val card = cards[cardKey(screenshot)]
        val link = card?.link
        val title = translatable("screencapper.text.chat.screenshot", "Screenshot saved", NamedTextColor.WHITE)
            .hoverEvent(HoverEvent.showText(tooltip(screenshot.name, listOfNotNull(card?.details, "Click to view it in Screenshots").joinToString("\n"))))
            .clickEvent(ClickEvent.runCommand(command("view", screenshot)))
        val out = line(if (link != null) BAR_SUCCESS else BAR_INFO).append(title)
        if (link == null) return out.append(Component.text(" · ${card?.details.orEmpty()}", MUTED))
        val delete = deleteUpload(screenshot, link)
        if (!link.hasLink) {
            val uploaded = out.append(Component.text(" · Uploaded to ${link.uploaderName}", MUTED))
            return if (delete == null) uploaded else uploaded.append(Component.text("  ")).append(delete)
        }
        val linked = out.append(Component.text(" · ", MUTED))
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
        return if (delete == null) linked else linked.append(Component.text("  ")).append(delete)
    }

    private fun deleteUpload(screenshot: Screenshot, link: UploadRecord): Component? {
        val kind = link.deletion?.kind ?: DeletionKind.NONE
        if (kind == DeletionKind.NONE || link !in UploadHistory.of(screenshot)) return null
        if (Uploaders.isDeleting(link)) return Component.text("Deleting...", MUTED)
        return Component.text("Delete upload", NamedTextColor.RED)
            .hoverEvent(HoverEvent.showText(Component.text("Delete it from ${link.uploaderName}", NamedTextColor.WHITE)))
            .clickEvent(ClickEvent.runCommand(command("confirm-delete-upload ${link.uploadedAt}", screenshot)))
    }

    private fun actions(screenshot: Screenshot): Component {
        val actions = mutableListOf<Component>()
        if (ScreencapperConfig.chatView) actions += action("view", "View", NamedTextColor.AQUA, "Open it in the Screenshots page", command("view", screenshot), underline = true)
        if (ScreencapperConfig.chatCopy) actions += action("copy", "Copy", NamedTextColor.WHITE, "Copy the image to your clipboard", command("copy", screenshot))
        if (ScreencapperConfig.chatUpload) {
            val target = ScreencapperConfig.defaultProfile.name
            val again = cards[cardKey(screenshot)]?.link != null
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

    private fun command(action: String, screenshot: Screenshot): String = "/screencapper $action ${screenshot.id}"

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
