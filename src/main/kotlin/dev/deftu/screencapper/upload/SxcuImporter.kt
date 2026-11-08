package dev.deftu.screencapper.upload

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.deftu.screencapper.config.RequestBody
import dev.deftu.screencapper.config.RequestMethod
import java.io.File
import java.net.URLEncoder

object SxcuImporter {
    fun read(file: File): UploaderProfile {
        val json = JsonParser.parseString(file.readText()).asJsonObject
        val destination = json.string("DestinationType").orEmpty()
        require(destination.isEmpty() || destination.contains("ImageUploader", true) || destination.contains("FileUploader", true)) {
            "This uploader does not accept images."
        }
        val body = json.string("Body").orEmpty()
        require(body.isEmpty() || body.equals("Binary", true) || body.equals("MultipartFormData", true)) {
            "Uploaders that send a $body body aren't supported."
        }
        val url = withQuery(json.string("RequestURL") ?: throw IllegalArgumentException("The file has no RequestURL."), json.pairs("Parameters"))

        return UploaderProfile(
            name = json.string("Name")?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
            url = url,
            method = RequestMethod.entries.firstOrNull { it.name.equals(json.string("RequestMethod"), true) } ?: RequestMethod.POST,
            body = if (body.equals("Binary", ignoreCase = true)) RequestBody.RAW else RequestBody.MULTIPART,
            fileField = json.string("FileFormName") ?: "file",
            responseUrl = ResponseUrl.normalize(json.string("URL").orEmpty()),
            headers = json.pairs("Headers"),
            formFields = json.pairs("Arguments"),
        )
    }

    private fun withQuery(url: String, parameters: List<Pair<String, String>>): String {
        if (parameters.isEmpty()) return url
        val query = parameters.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        return url + (if ('?' in url) "&" else "?") + query
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    private fun JsonObject.string(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.pairs(key: String): List<Pair<String, String>> =
        getAsJsonObject(key)?.entrySet()?.filter { it.value.isJsonPrimitive }?.map { it.key to it.value.asString }.orEmpty()
}
