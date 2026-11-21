package dev.deftu.screencapper.upload.integration.nextcloud

import dev.deftu.screencapper.upload.UploadHttp
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.util.Base64

fun interface NextcloudTransport {
    fun send(request: HttpRequest): Reply

    companion object {
        val HTTP = NextcloudTransport { request -> UploadHttp.exchange(request).let { Reply(it.statusCode(), it.body()) } }
    }
}

object NextcloudRequests {
    private const val SHARES = "/ocs/v2.php/apps/files_sharing/api/v1/shares"
    private const val PUBLIC_LINK = "3"
    private const val FORM = "application/x-www-form-urlencoded"

    fun loginStart(server: String): HttpRequest =
        UploadHttp.request("$server/index.php/login/v2").POST(HttpRequest.BodyPublishers.noBody()).build()

    fun loginPoll(start: LoginStart): HttpRequest = UploadHttp.request(start.pollEndpoint)
        .header("Content-Type", FORM)
        .POST(HttpRequest.BodyPublishers.ofString(form("token" to start.pollToken)))
        .build()

    fun currentUser(credentials: NextcloudCredentials): HttpRequest = ocs(credentials, "${credentials.server}/ocs/v2.php/cloud/user").GET().build()

    fun put(credentials: NextcloudCredentials, url: String, bytes: ByteArray, contentType: String): HttpRequest = authed(credentials, url)
        .header("Content-Type", contentType)
        .header("If-None-Match", "*")
        .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
        .build()

    fun mkcol(credentials: NextcloudCredentials, url: String): HttpRequest =
        authed(credentials, url).method("MKCOL", HttpRequest.BodyPublishers.noBody()).build()

    fun createShare(credentials: NextcloudCredentials, path: String): HttpRequest = ocs(credentials, "${credentials.server}$SHARES")
        .header("Content-Type", FORM)
        .POST(HttpRequest.BodyPublishers.ofString(form("path" to path, "shareType" to PUBLIC_LINK)))
        .build()

    fun deleteShare(credentials: NextcloudCredentials, id: String): HttpRequest =
        ocs(credentials, "${credentials.server}$SHARES/${NextcloudPaths.encodeSegment(id)}").DELETE().build()

    fun deleteFile(credentials: NextcloudCredentials, url: String): HttpRequest = authed(credentials, url).DELETE().build()

    private fun authed(credentials: NextcloudCredentials, url: String): HttpRequest.Builder {
        val pair = "${credentials.user}:${credentials.appPassword}".toByteArray(Charsets.UTF_8)
        return UploadHttp.request(url).header("Authorization", "Basic " + Base64.getEncoder().encodeToString(pair))
    }

    private fun ocs(credentials: NextcloudCredentials, url: String): HttpRequest.Builder =
        authed(credentials, url).header("OCS-APIRequest", "true").header("Accept", "application/json")

    private fun form(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (key, value) -> URLEncoder.encode(key, Charsets.UTF_8) + "=" + URLEncoder.encode(value, Charsets.UTF_8) }
}
