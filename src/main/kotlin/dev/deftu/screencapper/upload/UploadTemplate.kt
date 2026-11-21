package dev.deftu.screencapper.upload

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import dev.deftu.screencapper.metadata.MetadataKeys
import dev.deftu.screencapper.metadata.PngText
import java.io.File
import java.io.StringReader
import java.net.URI
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

data class ShotDetails(
    val player: String = "",
    val world: String = "",
    val dimension: String = "",
    val biome: String = "",
    val coords: String = "",
    val version: String = "",
    val takenAt: OffsetDateTime? = null,
) {
    companion object {
        val NONE = ShotDetails()

        fun of(metadata: Map<String, String>): ShotDetails = ShotDetails(
            player = metadata[MetadataKeys.PLAYER].orEmpty(),
            world = listOf(MetadataKeys.WORLD, MetadataKeys.SERVER, MetadataKeys.SERVER_ADDRESS).firstNotNullOfOrNull { key -> metadata[key]?.takeIf { it.isNotBlank() } }.orEmpty(),
            dimension = readable(metadata[MetadataKeys.DIMENSION]),
            biome = readable(metadata[MetadataKeys.BIOME]),
            coords = metadata[MetadataKeys.COORDINATES].orEmpty(),
            version = metadata[MetadataKeys.VERSION].orEmpty(),
            takenAt = metadata[MetadataKeys.TIME]?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() },
        )

        private fun readable(id: String?): String = id.orEmpty().removePrefix("minecraft:").replace('_', ' ')
    }
}

class TemplateValues(
    val fileName: String,
    val createdAt: Instant,
    val modifiedAt: Instant,
    val uploadedAt: Instant,
    val uuid: String,
    val details: ShotDetails = ShotDetails.NONE,
) {
    companion object {
        fun now(fileName: String, createdAt: Instant, modifiedAt: Instant, details: ShotDetails = ShotDetails.NONE): TemplateValues =
            TemplateValues(fileName, createdAt, modifiedAt, Instant.now(), UUID.randomUUID().toString(), details)

        fun of(file: File, fileName: String, keepMetadata: Boolean = false): TemplateValues {
            val attributes = runCatching { Files.readAttributes(file.toPath(), BasicFileAttributes::class.java) }.getOrNull()
            val modified = attributes?.lastModifiedTime()?.toInstant() ?: Instant.ofEpochMilli(file.lastModified())
            val created = attributes?.creationTime()?.toInstant()?.takeIf { it.toEpochMilli() > 0 } ?: modified
            val details = if (keepMetadata) ShotDetails.of(PngText.read(file)) else ShotDetails.NONE
            return now(fileName, created, modified, details)
        }
    }
}

object UploadTemplate {
    private val TOKEN = Regex("""\{(uuid|filename|createdat|modifiedat|timestamp|name|player|world|dimension|biome|coords|version|date|time)}""", RegexOption.IGNORE_CASE)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val ELEMENT by lazy { Gson().getAdapter(JsonElement::class.java) }

    fun expand(text: String, values: TemplateValues): String {
        if (!TOKEN.containsMatchIn(text)) return text
        val json = jsonDocument(text) ?: return fill(text, values)
        return expandJson(json, values).toString()
    }

    fun expandField(name: String, value: String, values: TemplateValues): String {
        val expanded = expand(value, values)
        return if (name == DiscordMessage.FIELD) DiscordMessage.tidy(expanded) else expanded
    }

    fun render(text: String, values: TemplateValues, response: String): String = ResponseUrl.fill(expand(text, values), response)

    fun expandUrl(text: String, values: TemplateValues): String =
        fill(text, values) { URLEncoder.encode(it, Charsets.UTF_8).replace("+", "%20") }

    fun renderUrl(text: String, values: TemplateValues, response: String): String = ResponseUrl.fill(expandUrl(text, values), response)

    private fun fill(text: String, values: TemplateValues, encode: (String) -> String = { it }): String = TOKEN.replace(text) { match ->
        val details = values.details
        encode(when (match.groupValues[1].lowercase()) {
            "uuid" -> values.uuid
            "filename" -> values.fileName
            "createdat" -> iso(values.createdAt)
            "modifiedat" -> iso(values.modifiedAt)
            "name" -> values.fileName.substringBeforeLast('.')
            "player" -> details.player
            "world" -> details.world
            "dimension" -> details.dimension
            "biome" -> details.biome
            "coords" -> details.coords
            "version" -> details.version
            "date" -> takenAt(values).toLocalDate().toString()
            "time" -> TIME.format(takenAt(values))
            else -> values.uploadedAt.toEpochMilli().toString()
        })
    }

    private fun takenAt(values: TemplateValues): OffsetDateTime =
        values.details.takenAt ?: values.createdAt.atZone(ZoneId.systemDefault()).toOffsetDateTime()

    private fun jsonDocument(text: String): JsonElement? {
        val trimmed = text.trim()
        if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) return null
        return runCatching {
            val reader = JsonReader(StringReader(trimmed))
            ELEMENT.read(reader).takeIf { reader.peek() == JsonToken.END_DOCUMENT }
        }.getOrNull()
    }

    private fun expandJson(element: JsonElement, values: TemplateValues): JsonElement = when {
        element.isJsonObject -> JsonObject().also { out ->
            for ((key, value) in element.asJsonObject.entrySet()) out.add(fill(key, values), expandJson(value, values))
        }

        element.isJsonArray -> JsonArray().also { out -> element.asJsonArray.forEach { out.add(expandJson(it, values)) } }
        element.isJsonPrimitive && element.asJsonPrimitive.isString -> JsonPrimitive(fill(element.asString, values))
        else -> element
    }

    private fun iso(instant: Instant): String = DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(ChronoUnit.MILLIS))
}

internal fun isWebLink(link: String): Boolean {
    val uri = runCatching { URI(link.trim()) }.getOrNull() ?: return false
    return uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrEmpty()
}
