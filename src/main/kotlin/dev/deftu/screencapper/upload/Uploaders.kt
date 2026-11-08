package dev.deftu.screencapper.upload

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import dev.deftu.screencapper.config.RequestBody
import dev.deftu.screencapper.screenshot.Screenshot
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import java.util.UUID

class UploadException(message: String) : IOException(message)

object Uploaders {
    private const val IMGUR_CLIENT_ID = "fb714c908164e90"
    private val TIMEOUT: Duration = Duration.ofSeconds(60)
    // HttpClient rejects these, and Content-Type is always set from the body below.
    private val MANAGED_HEADERS = setOf("content-type", "content-length", "host", "connection", "expect", "upgrade")

    private val client: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build()
    }

    fun upload(screenshot: Screenshot, profile: UploaderProfile): String =
        if (profile.isBuiltIn) uploadToImgur(screenshot) else uploadToCustomHost(screenshot, profile)

    private fun uploadToImgur(screenshot: Screenshot): String {
        val base64 = Base64.getEncoder().encodeToString(screenshot.readBytes())
        val request = HttpRequest.newBuilder(URI("https://api.imgur.com/3/image"))
            .timeout(TIMEOUT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", "Client-ID $IMGUR_CLIENT_ID")
            .POST(HttpRequest.BodyPublishers.ofString("image=${URLEncoder.encode(base64, Charsets.UTF_8)}"))
            .build()
        val body = send(request)
        val json = JsonParser.parseString(body).asJsonObject
        if (!json.get("success").asBoolean) throw UploadException("Imgur returned status ${json.get("status")}")
        return checkLink(json.getAsJsonObject("data").get("link").asString)
    }

    private fun uploadToCustomHost(screenshot: Screenshot, profile: UploaderProfile): String {
        val url = profile.url.trim()
        if (url.isEmpty()) throw UploadException("${profile.name} has no upload URL set.")

        val builder = HttpRequest.newBuilder(URI(url)).timeout(TIMEOUT)
        for ((key, value) in profile.headers) {
            if (key.trim().lowercase() !in MANAGED_HEADERS) builder.header(key.trim(), value)
        }

        val publisher = when (profile.body) {
            RequestBody.RAW -> {
                builder.header("Content-Type", contentType(screenshot))
                HttpRequest.BodyPublishers.ofByteArray(screenshot.readBytes())
            }

            RequestBody.MULTIPART -> {
                val boundary = "----Screencapper${UUID.randomUUID().toString().replace("-", "")}"
                builder.header("Content-Type", "multipart/form-data; boundary=$boundary")
                HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, screenshot, profile))
            }
        }
        builder.method(profile.method.name, publisher)

        val body = send(builder.build())
        return checkLink(ResponseUrl.extract(body, profile.responseUrl))
    }

    private fun checkLink(link: String): String {
        val trimmed = link.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull()
        if (uri == null || uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrEmpty()) {
            throw UploadException("The host didn't return a web link")
        }
        return trimmed
    }

    private fun multipart(boundary: String, screenshot: Screenshot, profile: UploaderProfile): ByteArray {
        val out = ByteArrayOutputStream()
        fun write(text: String) = out.write(text.toByteArray(Charsets.UTF_8))
        for ((key, value) in profile.formFields) {
            write("--$boundary\r\nContent-Disposition: form-data; name=\"${escape(key)}\"\r\n\r\n$value\r\n")
        }
        val field = profile.fileField.ifBlank { "file" }
        write("--$boundary\r\nContent-Disposition: form-data; name=\"${escape(field)}\"; filename=\"${escape(screenshot.name)}\"\r\n")
        write("Content-Type: ${contentType(screenshot)}\r\n\r\n")
        out.write(screenshot.readBytes())
        write("\r\n--$boundary--\r\n")
        return out.toByteArray()
    }

    private fun send(request: HttpRequest): String {
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) throw UploadException("The host answered with HTTP ${response.statusCode()}")
        return response.body()
    }

    private fun contentType(screenshot: Screenshot): String =
        if (screenshot.file.extension.lowercase() in setOf("jpg", "jpeg")) "image/jpeg" else "image/png"

    private fun escape(value: String): String = value.replace("\"", "%22").replace("\r", "").replace("\n", "")
}

/**
 * Reads the link out of an upload response.
 *
 * The path is either a plain JSON path (`data.link`, `files[0].url`), empty for a plain-text response, or a template
 * using ShareX's `{json:path}`, `$json:path$` and `{response}` tokens, such as `https://i.example.com/{json:id}`.
 */
object ResponseUrl {
    private val TOKEN = Regex("""\{json:([^}]+)}|\${'$'}json:([^${'$'}]+)\${'$'}|\{response}""")
    private val SEGMENT = Regex("""([^.\[\]]+)|\[(\d+)]""")

    /** Turns a lone token into its plain path. Templates with surrounding text are kept as they are */
    fun normalize(path: String): String {
        val trimmed = path.trim()
        val match = TOKEN.matchEntire(trimmed) ?: return trimmed
        if (match.value == "{response}") return ""
        return match.groupValues[1].ifEmpty { match.groupValues[2] }
    }

    fun extract(body: String, path: String): String {
        val normalized = normalize(path)
        if (normalized.isEmpty()) return body.trim()
        if (TOKEN.containsMatchIn(normalized)) {
            val json by lazy { parse(body) }
            return TOKEN.replace(normalized) { match ->
                val jsonPath = match.groupValues[1].ifEmpty { match.groupValues[2] }
                if (jsonPath.isEmpty()) body.trim() else valueAt(json, jsonPath)
            }
        }
        return valueAt(parse(body), normalized)
    }

    private fun parse(body: String): JsonElement =
        runCatching { JsonParser.parseString(body) }.getOrNull() ?: throw UploadException("The response is not JSON")

    private fun valueAt(root: JsonElement, path: String): String {
        var element = root
        for (match in SEGMENT.findAll(path)) {
            val (key, index) = match.destructured
            element = when {
                key.isNotEmpty() && element.isJsonObject -> element.asJsonObject.get(key)
                index.isNotEmpty() && element.isJsonArray -> element.asJsonArray.takeIf { index.toInt() < it.size() }?.get(index.toInt())
                else -> null
            } ?: throw UploadException("No value at \"$path\" in the response")
        }
        if (!element.isJsonPrimitive) throw UploadException("The value at \"$path\" is not a link")
        return element.asString
    }
}
