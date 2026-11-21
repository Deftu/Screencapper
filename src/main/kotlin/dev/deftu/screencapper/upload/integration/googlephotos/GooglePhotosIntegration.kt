package dev.deftu.screencapper.upload.integration.googlephotos

import dev.deftu.screencapper.upload.UploadException
import dev.deftu.screencapper.upload.UploadHttp
import dev.deftu.screencapper.upload.UploadResult
import dev.deftu.screencapper.upload.integration.HostIntegration
import dev.deftu.screencapper.upload.integration.IntegrationSettings
import dev.deftu.screencapper.upload.integration.IntegrationUpload
import dev.deftu.screencapper.upload.integration.Integrations
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object GooglePhotosIntegration : HostIntegration {
    const val SIGN_IN_AGAIN = "Sign in to Google Photos again."
    private const val ALBUM_TITLE = "Screencapper"

    override val kind: String = GooglePhotosSettings.KIND
    override val displayName: String = "Google Photos"

    private val tokenLock = Any()
    private val createLock = Any()

    override fun host(settings: IntegrationSettings): String = "photos.google.com"

    override fun upload(upload: IntegrationUpload): UploadResult {
        val profileId = upload.profile.id
        val config = GooglePhotosSettings(latest(profileId, upload.settings))
        if (!config.isSignedIn) throw UploadException(SIGN_IN_AGAIN)
        val uploadToken = success(authorized(profileId, upload.settings) { GooglePhotosApi.uploadBytes(it, upload.bytes, upload.contentType) }).trim()
        if (uploadToken.isEmpty()) throw UploadException("Google Photos didn't return an upload token.")
        val item = synchronized(createLock) {
            val albumId = if (config.useAlbum) album(profileId, upload.settings) else ""
            val response = authorized(profileId, upload.settings) { GooglePhotosApi.batchCreate(it, uploadToken, upload.fileName, albumId) }
            if (albumId.isNotEmpty() && response.statusCode() in 400..404) {
                throw UploadException("${GooglePhotosApi.describe(response.statusCode(), response.body()).trimEnd('.')}. If you deleted the Screencapper album, turn the album option off and on again.")
            }
            GooglePhotosApi.parseCreated(success(response))
        }
        return UploadResult(if (config.copyLink) item.productUrl else "", null)
    }

    private fun album(profileId: String, fallback: IntegrationSettings): String {
        GooglePhotosSettings(latest(profileId, fallback)).albumId.takeIf { it.isNotEmpty() }?.let { return it }
        val id = GooglePhotosApi.parseAlbumId(success(authorized(profileId, fallback) { GooglePhotosApi.createAlbum(it, ALBUM_TITLE) }))
        Integrations.update(profileId) { it.with(GooglePhotosSettings.ALBUM_ID to id) }
        return id
    }

    private fun authorized(profileId: String, fallback: IntegrationSettings, build: (String) -> ApiRequest): HttpResponse<String> {
        val token = accessToken(profileId, fallback, rejected = null)
        val first = send(build(token))
        if (first.statusCode() != 401) return first
        return send(build(accessToken(profileId, fallback, rejected = token)))
    }

    private fun accessToken(profileId: String, fallback: IntegrationSettings, rejected: String?): String = synchronized(tokenLock) {
        val config = GooglePhotosSettings(latest(profileId, fallback))
        if (!config.isSignedIn) throw UploadException(SIGN_IN_AGAIN)
        val now = System.currentTimeMillis()
        if (config.hasFreshAccess(now) && config.accessToken != rejected) return config.accessToken
        val response = send(GoogleOAuth.refresh(config.clientId, config.clientSecret, config.refreshToken))
        if (response.statusCode() !in 200..299) {
            when (GoogleOAuth.error(response.body())) {
                "invalid_grant" -> {
                    Integrations.update(profileId) { GooglePhotosSettings.signedOut(it) }
                    throw UploadException(SIGN_IN_AGAIN)
                }
                "invalid_client", "unauthorized_client" -> throw UploadException(GoogleOAuth.CLIENT_REJECTED)
                else -> throw UploadException("Google sign-in answered with HTTP ${response.statusCode()}")
            }
        }
        val tokens = GoogleOAuth.parseTokens(response.body(), now, config.refreshToken)
        Integrations.update(profileId) { GooglePhotosSettings.withTokens(it, tokens) }
        return tokens.accessToken
    }

    private fun latest(profileId: String, fallback: IntegrationSettings): IntegrationSettings =
        Integrations.current(profileId)?.takeIf { it.kind == kind } ?: fallback

    private fun success(response: HttpResponse<String>): String {
        if (response.statusCode() !in 200..299) throw UploadException(GooglePhotosApi.describe(response.statusCode(), response.body()))
        return response.body()
    }

    internal fun send(request: ApiRequest): HttpResponse<String> {
        val builder = UploadHttp.request(request.url)
        for ((name, value) in request.headers) builder.header(name, value)
        builder.POST(HttpRequest.BodyPublishers.ofByteArray(request.body))
        return UploadHttp.exchange(builder.build())
    }
}
