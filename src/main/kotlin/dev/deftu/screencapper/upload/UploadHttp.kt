package dev.deftu.screencapper.upload

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import dev.deftu.screencapper.Screencapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object UploadHttp {
    val TIMEOUT: Duration = Duration.ofSeconds(60)
    val USER_AGENT = "Screencapper/${Screencapper.VERSION}"
    const val MAX_REASON = 200
    private val WHITESPACE = Regex("\\s+")

    val client: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build()
    }

    fun request(url: String): HttpRequest.Builder =
        HttpRequest.newBuilder(uri(url)).timeout(TIMEOUT).header("User-Agent", USER_AGENT)

    fun exchange(request: HttpRequest): HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())

    fun send(request: HttpRequest): String {
        val response = exchange(request)
        if (response.statusCode() !in 200..299) throw UploadException(failure(response.statusCode(), response.body()))
        return response.body()
    }

    fun failure(status: Int, body: String): String {
        val base = "The host answered with HTTP $status"
        val reason = reasonOf(body)?.replace(WHITESPACE, " ")?.trim()?.takeIf { it.isNotEmpty() } ?: return base
        return "$base: ${if (reason.length > MAX_REASON) reason.take(MAX_REASON - 1) + "\u2026" else reason}"
    }

    private fun reasonOf(body: String): String? {
        val text = body.trim()
        if (text.isEmpty() || text.startsWith("<")) return null
        if (!text.startsWith("{")) return text
        val root = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return text
        val data = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        return listOfNotNull(data?.get("error"), root.get("error"), root.get("message")).firstNotNullOfOrNull(::messageOf)
    }

    private fun messageOf(element: JsonElement): String? = when {
        element.isJsonPrimitive -> element.asString
        element.isJsonObject -> element.asJsonObject.get("message")?.takeIf { it.isJsonPrimitive }?.asString
        else -> null
    }

    fun uri(url: String): URI = URI(url.replace(" ", "%20"))
}
