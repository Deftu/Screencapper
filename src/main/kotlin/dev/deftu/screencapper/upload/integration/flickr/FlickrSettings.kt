package dev.deftu.screencapper.upload.integration.flickr

import dev.deftu.screencapper.upload.integration.IntegrationSettings

internal data class FlickrAccount(val token: String, val tokenSecret: String, val nsid: String, val username: String)

internal object FlickrSettings {
    const val KIND = "flickr"
    const val API_KEY = "apiKey"
    const val API_SECRET = "apiSecret"
    const val OAUTH_TOKEN = "oauthToken"
    const val TOKEN_SECRET = "tokenSecret"
    const val USER_NSID = "userNsid"
    const val USERNAME = "username"
    const val VISIBILITY = "visibility"
    const val PUBLIC = "public"
    const val PRIVATE = "private"
    const val FRIENDS = "friends"
    const val FAMILY = "family"
    const val TITLE = "title"
    const val TAGS = "tags"

    private val ACCOUNT = listOf(OAUTH_TOKEN, TOKEN_SECRET, USER_NSID, USERNAME)

    fun initial(): IntegrationSettings = IntegrationSettings(KIND, mapOf(VISIBILITY to PUBLIC, TITLE to "{name}"))

    fun signedIn(settings: IntegrationSettings): Boolean = credentials(settings) != null

    fun isPublic(settings: IntegrationSettings): Boolean = settings[VISIBILITY] != PRIVATE

    fun flag(settings: IntegrationSettings, key: String): Boolean = settings[key] == "true"

    fun credentials(settings: IntegrationSettings): OAuthCredentials? = credentials(settings.values)

    fun credentials(values: Map<String, String>): OAuthCredentials? {
        val credentials = OAuthCredentials(values[API_KEY].orEmpty(), values[API_SECRET].orEmpty(), values[OAUTH_TOKEN].orEmpty(), values[TOKEN_SECRET].orEmpty())
        val complete = listOf(credentials.consumerKey, credentials.consumerSecret, credentials.token, credentials.tokenSecret).all { it.isNotBlank() }
        return credentials.takeIf { complete }
    }

    fun withKeys(settings: IntegrationSettings, key: String, secret: String): IntegrationSettings {
        if (settings[API_KEY] == key && settings[API_SECRET] == secret) return settings
        return settings.copy(values = settings.values - ACCOUNT + mapOf(API_KEY to key, API_SECRET to secret))
    }

    fun withAccount(settings: IntegrationSettings, account: FlickrAccount): IntegrationSettings = settings.with(
        OAUTH_TOKEN to account.token,
        TOKEN_SECRET to account.tokenSecret,
        USER_NSID to account.nsid,
        USERNAME to account.username,
    )
}
