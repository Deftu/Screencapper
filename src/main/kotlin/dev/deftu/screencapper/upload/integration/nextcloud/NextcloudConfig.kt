package dev.deftu.screencapper.upload.integration.nextcloud

import dev.deftu.screencapper.upload.integration.IntegrationSettings

enum class NextcloudLink(val id: String) {
    PUBLIC("public"),
    NONE("none"),
}

data class NextcloudCredentials(val server: String, val user: String, val appPassword: String) {
    override fun toString(): String = "NextcloudCredentials(server=$server, user=$user)"
}

data class NextcloudConfig(
    val server: String,
    val user: String,
    val appPassword: String,
    val userId: String = "",
    val folder: String = DEFAULT_FOLDER,
    val link: NextcloudLink = NextcloudLink.PUBLIC,
) {
    val credentials: NextcloudCredentials get() = NextcloudCredentials(server, user, appPassword)

    override fun toString(): String = "NextcloudConfig(server=$server, user=$user, userId=$userId, folder=$folder, link=$link)"

    fun toSettings(): IntegrationSettings = IntegrationSettings(
        KIND,
        mapOf(
            SERVER to server,
            USER to user,
            APP_PASSWORD to appPassword,
            USER_ID to userId,
            FOLDER to folder,
            LINK to link.id,
        ),
    )

    companion object {
        const val KIND = "nextcloud"
        const val DEFAULT_FOLDER = "Screenshots"
        const val USER_ID = "userId"

        private const val SERVER = "server"
        private const val USER = "user"
        private const val APP_PASSWORD = "appPassword"
        private const val FOLDER = "folder"
        private const val LINK = "link"

        fun from(settings: IntegrationSettings): NextcloudConfig = NextcloudConfig(
            server = settings[SERVER],
            user = settings[USER],
            appPassword = settings[APP_PASSWORD],
            userId = settings[USER_ID],
            folder = settings.values[FOLDER] ?: DEFAULT_FOLDER,
            link = NextcloudLink.entries.firstOrNull { it.id == settings[LINK] } ?: NextcloudLink.PUBLIC,
        )
    }
}
