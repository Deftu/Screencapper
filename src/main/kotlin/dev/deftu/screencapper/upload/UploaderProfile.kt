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
) {
    val isBuiltIn: Boolean get() = id == IMGUR_ID

    val host: String
        get() = if (isBuiltIn) "imgur.com" else runCatching { java.net.URI(url).host }.getOrNull() ?: url.ifBlank { "No URL set" }

    companion object {
        const val IMGUR_ID = "imgur"

        val IMGUR = UploaderProfile(id = IMGUR_ID, name = "Imgur", url = "https://api.imgur.com/3/image")

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
        }

        private fun pairs(array: JsonArray?): List<Pair<String, String>> =
            array?.mapNotNull { entry -> entry.asJsonArray.takeIf { it.size() == 2 }?.let { it[0].asString to it[1].asString } }.orEmpty()

        private fun pairsJson(pairs: List<Pair<String, String>>): JsonArray = JsonArray().apply {
            pairs.forEach { (k, v) -> add(JsonArray().apply { add(k); add(v) }) }
        }
    }
}
