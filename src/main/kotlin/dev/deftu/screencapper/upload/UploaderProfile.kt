package dev.deftu.screencapper.upload

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.deftu.screencapper.config.RequestBody
import dev.deftu.screencapper.config.RequestMethod
import java.util.UUID

data class UploaderProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "New uploader",
    val url: String = "",
    val method: RequestMethod = RequestMethod.POST,
    val body: RequestBody = RequestBody.MULTIPART,
    val fileField: String = "file",
    val responseUrl: String = "url",
    val headers: List<Pair<String, String>> = emptyList(),
    val formFields: List<Pair<String, String>> = emptyList(),
    /** Largest upload the host takes, in megabytes; blank for no limit */
    val sizeLimitMb: String = "",
    val keepMetadata: Boolean = false,
) {
    val isBuiltIn: Boolean get() = BUILT_IN.any { it.id == id }

    val host: String
        get() = when (id) {
            IMGUR_ID -> "imgur.com"
            CATBOX_ID -> "catbox.moe"
            else -> runCatching { java.net.URI(url).host }.getOrNull() ?: url.ifBlank { "No URL set" }
        }

    /** The host's upload limit in bytes, or null for none */
    val sizeLimitBytes: Long?
        get() = when (id) {
            IMGUR_ID -> 20_000_000L
            CATBOX_ID -> 200_000_000L
            else -> sizeLimitMb.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { (it * 1_000_000).toLong() }
        }

    companion object {
        const val IMGUR_ID = "imgur"
        const val CATBOX_ID = "catbox"

        val IMGUR = UploaderProfile(id = IMGUR_ID, name = "Imgur", url = "https://api.imgur.com/3/image")

        /** catbox.moe's anonymous upload API answers with the file's link as plain text */
        val CATBOX = UploaderProfile(
            id = CATBOX_ID,
            name = "Catbox",
            url = "https://catbox.moe/user/api.php",
            fileField = "fileToUpload",
            responseUrl = "",
            formFields = listOf("reqtype" to "fileupload"),
        )

        val BUILT_IN = listOf(IMGUR, CATBOX)

        fun decodeList(json: String): List<UploaderProfile> = runCatching {
            JsonParser.parseString(json).asJsonArray.mapNotNull { runCatching { decode(it.asJsonObject) }.getOrNull() }
        }.getOrDefault(emptyList())

        fun encodeList(profiles: List<UploaderProfile>): String = JsonArray().apply {
            profiles.filterNot { it.isBuiltIn }.forEach { add(encode(it)) }
        }.toString()

        private fun decode(json: JsonObject): UploaderProfile = UploaderProfile(
            id = json.get("id")?.asString ?: UUID.randomUUID().toString(),
            name = json.get("name")?.asString ?: "Uploader",
            url = json.get("url")?.asString.orEmpty(),
            method = RequestMethod.entries.firstOrNull { it.name == json.get("method")?.asString } ?: RequestMethod.POST,
            body = RequestBody.entries.firstOrNull { it.name == json.get("body")?.asString } ?: RequestBody.MULTIPART,
            fileField = json.get("fileField")?.asString ?: "file",
            responseUrl = json.get("responseUrl")?.asString ?: "url",
            headers = pairs(json.getAsJsonArray("headers")),
            formFields = pairs(json.getAsJsonArray("formFields")),
            sizeLimitMb = json.get("sizeLimitMb")?.asString.orEmpty(),
            keepMetadata = json.get("keepMetadata")?.asBoolean ?: false,
        )

        private fun encode(profile: UploaderProfile): JsonObject = JsonObject().apply {
            addProperty("id", profile.id)
            addProperty("name", profile.name)
            addProperty("url", profile.url)
            addProperty("method", profile.method.name)
            addProperty("body", profile.body.name)
            addProperty("fileField", profile.fileField)
            addProperty("responseUrl", profile.responseUrl)
            add("headers", pairsJson(profile.headers))
            add("formFields", pairsJson(profile.formFields))
            addProperty("sizeLimitMb", profile.sizeLimitMb)
            addProperty("keepMetadata", profile.keepMetadata)
        }

        private fun pairs(array: JsonArray?): List<Pair<String, String>> =
            array?.mapNotNull { entry -> entry.asJsonArray.takeIf { it.size() == 2 }?.let { it[0].asString to it[1].asString } }.orEmpty()

        private fun pairsJson(pairs: List<Pair<String, String>>): JsonArray = JsonArray().apply {
            pairs.forEach { (k, v) -> add(JsonArray().apply { add(k); add(v) }) }
        }
    }
}
