package dev.deftu.screencapper.upload

import dev.deftu.screencapper.Screencapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object UploadHttp {
    val TIMEOUT: Duration = Duration.ofSeconds(60)
    val USER_AGENT = "Screencapper/${Screencapper.VERSION}"

    val client: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build()
    }

    fun request(url: String): HttpRequest.Builder =
        HttpRequest.newBuilder(uri(url)).timeout(TIMEOUT).header("User-Agent", USER_AGENT)

    fun exchange(request: HttpRequest): HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())

    fun send(request: HttpRequest): String {
        val response = exchange(request)
        if (response.statusCode() !in 200..299) throw UploadException("The host answered with HTTP ${response.statusCode()}")
        return response.body()
    }

    fun uri(url: String): URI = URI(url.replace(" ", "%20"))
}
