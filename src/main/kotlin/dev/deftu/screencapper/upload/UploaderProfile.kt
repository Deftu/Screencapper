package dev.deftu.screencapper.upload

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.deftu.screencapper.config.LinkMode
import dev.deftu.screencapper.config.RequestBody
import dev.deftu.screencapper.config.RequestMethod
import dev.deftu.screencapper.upload.integration.IntegrationSettings
import dev.deftu.screencapper.upload.integration.Integrations
import java.net.URI
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
    val sizeLimitMb: String = "",
    val keepMetadata: Boolean = false,
    val linkMode: LinkMode = LinkMode.LINK,
    val deletion: DeletionRequest = DeletionRequest(),
    val discord: DiscordOptions? = null,
    val integration: IntegrationSettings? = null,
) {
    val isBuiltIn: Boolean get() = BUILT_IN.any { it.id == id }

    val host: String
        get() = when (id) {
            IMGUR_ID -> "imgur.com"
            CATBOX_ID -> "catbox.moe"
            else -> integration?.let { settings -> Integrations.find(settings.kind)?.host(settings) } ?: displayHost(url)
        }

    private fun displayHost(url: String): String {
        if (url.isBlank()) return "No URL set"
        return runCatching { URI(url.trim().replace(Regex("""\{[^{}]*}"""), "x")).host }.getOrNull() ?: "Custom host"
    }

    val sizeLimitBytes: Long?
        get() = when (id) {
            IMGUR_ID -> 20_000_000L
            CATBOX_ID -> 200_000_000L
            else -> sizeLimitMb.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { (it * 1_000_000).toLong() }
        }

    companion object {
        const val IMGUR_ID = "imgur"
        const val CATBOX_ID = "catbox"
        internal const val IMGUR_CLIENT_ID = "fb714c908164e90"

        val IMGUR = UploaderProfile(id = IMGUR_ID, name = "Imgur", url = "https://api.imgur.com/3/image")

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

        fun decodeList(json: String, cipher: SecretCipher): List<UploaderProfile> = decodeList(json).map { UploaderSecrets.open(it, cipher) }

        fun encodeList(profiles: List<UploaderProfile>, cipher: SecretCipher): String = encodeList(profiles.map { UploaderSecrets.seal(it, cipher) })

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
            headers = jsonToPairs(json.get("headers") as? JsonArray),
            formFields = jsonToPairs(json.get("formFields") as? JsonArray),
            sizeLimitMb = json.get("sizeLimitMb")?.asString.orEmpty(),
            keepMetadata = json.get("keepMetadata")?.asBoolean ?: false,
            linkMode = LinkMode.entries.firstOrNull { it.name == json.get("linkMode")?.asString } ?: LinkMode.LINK,
            deletion = (json.get("deletion") as? JsonObject)?.let { runCatching { DeletionRequest.fromJson(it) }.getOrNull() } ?: DeletionRequest(),
            discord = (json.get("discord") as? JsonObject)?.let { runCatching { DiscordOptions.fromJson(it) }.getOrNull() },
            integration = (json.get("integration") as? JsonObject)?.let { runCatching { IntegrationSettings.fromJson(it) }.getOrNull() },
        )

        private fun encode(profile: UploaderProfile): JsonObject = JsonObject().apply {
            addProperty("id", profile.id)
            addProperty("name", profile.name)
            addProperty("url", profile.url)
            addProperty("method", profile.method.name)
            addProperty("body", profile.body.name)
            addProperty("fileField", profile.fileField)
            addProperty("responseUrl", profile.responseUrl)
            add("headers", pairsToJson(profile.headers))
            add("formFields", pairsToJson(profile.formFields))
            addProperty("sizeLimitMb", profile.sizeLimitMb)
            addProperty("keepMetadata", profile.keepMetadata)
            addProperty("linkMode", profile.linkMode.name)
            add("deletion", profile.deletion.toJson())
            profile.discord?.let { add("discord", it.toJson()) }
            profile.integration?.let { add("integration", it.toJson()) }
        }
    }
}
