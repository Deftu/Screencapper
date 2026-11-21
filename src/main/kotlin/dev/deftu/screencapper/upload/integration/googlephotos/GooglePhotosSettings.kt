package dev.deftu.screencapper.upload.integration.googlephotos

import dev.deftu.screencapper.upload.integration.IntegrationSettings

data class GooglePhotosForm(val clientId: String, val clientSecret: String, val useAlbum: Boolean, val copyLink: Boolean)

class SignedIn(val clientId: String, val tokens: GoogleTokens)

class GooglePhotosSettings(val settings: IntegrationSettings) {
    val clientId: String get() = settings[CLIENT_ID].trim()
    val clientSecret: String get() = settings[CLIENT_SECRET].trim()
    val refreshToken: String get() = settings[REFRESH_TOKEN]
    val accessToken: String get() = settings[ACCESS_TOKEN]
    val albumId: String get() = settings[ALBUM_ID]
    val useAlbum: Boolean get() = settings[ALBUM] == "true"
    val copyLink: Boolean get() = settings[COPY_LINK] == "true"
    val isSignedIn: Boolean get() = refreshToken.isNotBlank()

    fun hasFreshAccess(now: Long): Boolean {
        val expiresAt = settings[EXPIRES_AT].toLongOrNull() ?: return false
        return accessToken.isNotBlank() && now < expiresAt - EXPIRY_MARGIN_MS
    }

    companion object {
        const val KIND = "google-photos"
        const val ALBUM_ID = "albumId"
        private const val CLIENT_ID = "clientId"
        private const val CLIENT_SECRET = "clientSecret"
        private const val REFRESH_TOKEN = "refreshToken"
        private const val ACCESS_TOKEN = "accessToken"
        private const val EXPIRES_AT = "expiresAt"
        private const val ALBUM = "album"
        private const val COPY_LINK = "copyLink"
        private const val EXPIRY_MARGIN_MS = 60_000L
        private val TOKENS = setOf(REFRESH_TOKEN, ACCESS_TOKEN, EXPIRES_AT)

        fun saved(latest: IntegrationSettings?, form: GooglePhotosForm, signedIn: SignedIn?): IntegrationSettings {
            val clientId = form.clientId.trim()
            var values = latest?.takeIf { it.kind == KIND }?.values.orEmpty()
            if (values[CLIENT_ID].orEmpty().trim() != clientId) values = values - TOKENS - ALBUM_ID
            if (!form.useAlbum) values = values - ALBUM_ID
            values = values + mapOf(
                CLIENT_ID to clientId,
                CLIENT_SECRET to form.clientSecret.trim(),
                ALBUM to form.useAlbum.toString(),
                COPY_LINK to form.copyLink.toString(),
            )
            val settings = IntegrationSettings(KIND, values)
            return signedIn?.takeIf { it.clientId == clientId }?.let { withTokens(settings, it.tokens) } ?: settings
        }

        fun signedOut(settings: IntegrationSettings): IntegrationSettings = settings.copy(values = settings.values - TOKENS)

        fun withTokens(settings: IntegrationSettings, tokens: GoogleTokens): IntegrationSettings = settings.with(
            REFRESH_TOKEN to tokens.refreshToken,
            ACCESS_TOKEN to tokens.accessToken,
            EXPIRES_AT to tokens.expiresAt.toString(),
        )
    }
}
