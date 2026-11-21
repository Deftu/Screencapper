package dev.deftu.screencapper.upload

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class DiscordOptions(
    val content: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val embed: Boolean = false,
    val title: String = "",
    val description: String = "",
    val color: Int = DiscordMessage.DEFAULT_COLOR,
    val footer: String = "",
    val timestamp: Boolean = true,
    val imageInEmbed: Boolean = true,
    val silent: Boolean = false,
    val hideLinkPreviews: Boolean = false,
    val allowMentions: Boolean = false,
    val threadName: String = "",
    val threadId: String = "",
) {
    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("content", content)
        addProperty("username", username)
        addProperty("avatarUrl", avatarUrl)
        addProperty("embed", embed)
        addProperty("title", title)
        addProperty("description", description)
        addProperty("color", color)
        addProperty("footer", footer)
        addProperty("timestamp", timestamp)
        addProperty("imageInEmbed", imageInEmbed)
        addProperty("silent", silent)
        addProperty("hideLinkPreviews", hideLinkPreviews)
        addProperty("allowMentions", allowMentions)
        addProperty("threadName", threadName)
        addProperty("threadId", threadId)
    }

    companion object {
        fun fromJson(json: JsonObject): DiscordOptions {
            val defaults = DiscordOptions()
            fun primitive(key: String): JsonElement? = json.get(key)?.takeIf { it.isJsonPrimitive }
            fun text(key: String, default: String) = primitive(key)?.asString ?: default
            fun flag(key: String, default: Boolean) = primitive(key)?.let { runCatching { it.asBoolean }.getOrNull() } ?: default
            return DiscordOptions(
                content = text("content", defaults.content),
                username = text("username", defaults.username),
                avatarUrl = text("avatarUrl", defaults.avatarUrl),
                embed = flag("embed", defaults.embed),
                title = text("title", defaults.title),
                description = text("description", defaults.description),
                color = primitive("color")?.let { runCatching { it.asInt }.getOrNull() } ?: defaults.color,
                footer = text("footer", defaults.footer),
                timestamp = flag("timestamp", defaults.timestamp),
                imageInEmbed = flag("imageInEmbed", defaults.imageInEmbed),
                silent = flag("silent", defaults.silent),
                hideLinkPreviews = flag("hideLinkPreviews", defaults.hideLinkPreviews),
                allowMentions = flag("allowMentions", defaults.allowMentions),
                threadName = text("threadName", defaults.threadName),
                threadId = text("threadId", defaults.threadId),
            )
        }
    }
}

object DiscordMessage {
    const val FIELD = "payload_json"
    const val DEFAULT_COLOR = 0x5865F2
    const val SUPPRESS_EMBEDS = 1 shl 2
    const val SUPPRESS_NOTIFICATIONS = 1 shl 12

    const val CONTENT_LIMIT = 2000
    const val USERNAME_LIMIT = 80
    const val THREAD_NAME_LIMIT = 100
    const val TITLE_LIMIT = 256
    const val DESCRIPTION_LIMIT = 4096
    const val FOOTER_LIMIT = 2048
    const val EMBEDS_TOTAL_LIMIT = 6000
    private const val AUTHOR_LIMIT = 256
    private const val FIELD_NAME_LIMIT = 256
    private const val FIELD_VALUE_LIMIT = 1024
    private const val FIELD_COUNT_LIMIT = 25

    private val MENTION_TYPES = listOf("users", "roles", "everyone")
    private val BLOCKED_NAMES = listOf("discord", "clyde")

    fun template(options: DiscordOptions): String = JsonObject().apply {
        putText("content", options.content)
        putText("username", options.username)
        putText("avatar_url", options.avatarUrl)
        putText("thread_name", options.threadName)
        if (options.embed) add("embeds", JsonArray().apply { add(embed(options)) })
        add("allowed_mentions", JsonObject().apply {
            add("parse", JsonArray().apply { if (options.allowMentions) MENTION_TYPES.forEach(::add) })
        })
        val flags = (if (options.silent) SUPPRESS_NOTIFICATIONS else 0) or (if (!options.embed && options.hideLinkPreviews) SUPPRESS_EMBEDS else 0)
        if (flags != 0) addProperty("flags", flags)
        if (options.embed && options.imageInEmbed) {
            add("attachments", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("id", 0)
                    addProperty("filename", "{fileName}")
                })
            })
        }
    }.toString()

    fun problem(options: DiscordOptions): String? = when {
        options.avatarUrl.isNotBlank() && !isWebLink(options.avatarUrl) -> "The avatar must be a web link, like https://example.com/avatar.png"
        BLOCKED_NAMES.any { options.username.contains(it, ignoreCase = true) } -> "Discord doesn't allow \"discord\" or \"clyde\" in the bot name."
        options.threadId.isNotBlank() && !options.threadId.trim().all(Char::isDigit) -> "A thread ID is a number. Copy it with Developer Mode on."
        options.threadName.isNotBlank() && options.threadId.isNotBlank() -> "Use a post title or a thread ID, not both."
        else -> null
    }

    fun tidy(payload: String): String {
        val root = runCatching { JsonParser.parseString(payload) }.getOrNull() as? JsonObject ?: return payload
        clampText(root, "content", CONTENT_LIMIT)
        clampText(root, "username", USERNAME_LIMIT)?.let { name ->
            if (BLOCKED_NAMES.any { name.contains(it, ignoreCase = true) }) root.remove("username")
        }
        clampText(root, "avatar_url", Int.MAX_VALUE)
        clampText(root, "thread_name", THREAD_NAME_LIMIT)
        (root.get("embeds") as? JsonArray)?.let { embeds ->
            val kept = JsonArray()
            for (element in embeds) {
                if (element !is JsonObject) continue
                tidyEmbed(element)
                if (hasBody(element)) kept.add(element)
            }
            fitTotal(kept.filterIsInstance<JsonObject>())
            if (kept.size() == 0) root.remove("embeds") else root.add("embeds", kept)
        }
        return root.toString()
    }

    private fun embed(options: DiscordOptions): JsonObject = JsonObject().apply {
        putText("title", options.title)
        putText("description", options.description)
        addProperty("color", options.color and 0xFFFFFF)
        if (options.footer.isNotBlank()) add("footer", JsonObject().apply { addProperty("text", options.footer) })
        if (options.timestamp) addProperty("timestamp", "{createdAt}")
        if (options.imageInEmbed) add("image", JsonObject().apply { addProperty("url", "attachment://{fileName}") })
    }

    private fun JsonObject.putText(key: String, value: String) {
        if (value.isNotBlank()) addProperty(key, value)
    }

    private fun tidyEmbed(embed: JsonObject) {
        clampText(embed, "title", TITLE_LIMIT)
        clampText(embed, "description", DESCRIPTION_LIMIT)
        clampText(embed, "url", Int.MAX_VALUE)
        clampText(embed, "timestamp", Int.MAX_VALUE)
        requireText(embed, "footer", "text", FOOTER_LIMIT)
        requireText(embed, "author", "name", AUTHOR_LIMIT)
        requireText(embed, "image", "url", Int.MAX_VALUE)
        requireText(embed, "thumbnail", "url", Int.MAX_VALUE)
        (embed.get("fields") as? JsonArray)?.let { fields ->
            val kept = JsonArray()
            for (field in fields) {
                if (field !is JsonObject || kept.size() >= FIELD_COUNT_LIMIT) continue
                if (clampText(field, "name", FIELD_NAME_LIMIT) != null && clampText(field, "value", FIELD_VALUE_LIMIT) != null) kept.add(field)
            }
            if (kept.size() == 0) embed.remove("fields") else embed.add("fields", kept)
        }
    }

    private fun requireText(embed: JsonObject, key: String, textKey: String, limit: Int) {
        val part = embed.get(key) as? JsonObject ?: return
        if (clampText(part, textKey, limit) == null) embed.remove(key)
    }

    private fun hasBody(embed: JsonObject): Boolean =
        listOf("title", "description", "footer", "author", "fields", "image", "thumbnail").any { embed.has(it) }

    private fun fitTotal(embeds: List<JsonObject>) {
        val parts = embeds.flatMap { embed ->
            listOfNotNull(
                embed to "description",
                (embed.get("footer") as? JsonObject)?.let { it to "text" },
                embed to "title",
                (embed.get("author") as? JsonObject)?.let { it to "name" },
            )
        }
        val fieldText = embeds.sumOf { embed ->
            (embed.get("fields") as? JsonArray)?.filterIsInstance<JsonObject>()?.sumOf { lengthOf(it, "name") + lengthOf(it, "value") } ?: 0
        }
        var excess = parts.sumOf { (owner, key) -> lengthOf(owner, key) } + fieldText - EMBEDS_TOTAL_LIMIT
        for ((owner, key) in parts) {
            if (excess <= 0) break
            val length = lengthOf(owner, key)
            if (length == 0) continue
            val target = (length - excess).coerceAtLeast(0)
            if (target == 0) owner.remove(key) else owner.addProperty(key, cut(owner.get(key).asString, target))
            excess -= length - lengthOf(owner, key)
        }
        for (embed in embeds) {
            for (key in listOf("footer", "author")) {
                val part = embed.get(key) as? JsonObject ?: continue
                if (!part.has(if (key == "footer") "text" else "name")) embed.remove(key)
            }
        }
    }

    private fun lengthOf(owner: JsonObject, key: String): Int =
        owner.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.length ?: 0

    private fun clampText(owner: JsonObject, key: String, limit: Int): String? {
        val element = owner.get(key) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return null
        val value = element.asString
        if (value.isBlank()) {
            owner.remove(key)
            return null
        }
        val clamped = cut(value, limit)
        if (clamped != value) owner.addProperty(key, clamped)
        return clamped
    }

    private fun cut(text: String, limit: Int): String {
        if (text.length <= limit) return text
        if (limit <= 1) return text.take(limit.coerceAtLeast(0))
        var end = limit - 1
        if (Character.isHighSurrogate(text[end - 1])) end--
        return text.substring(0, end) + "…"
    }
}
