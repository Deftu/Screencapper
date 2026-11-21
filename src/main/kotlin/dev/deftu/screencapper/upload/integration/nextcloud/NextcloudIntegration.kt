package dev.deftu.screencapper.upload.integration.nextcloud

import dev.deftu.screencapper.upload.DeletionRequest
import dev.deftu.screencapper.upload.UploadException
import dev.deftu.screencapper.upload.UploadResult
import dev.deftu.screencapper.upload.integration.HostIntegration
import dev.deftu.screencapper.upload.integration.IntegrationSettings
import dev.deftu.screencapper.upload.integration.IntegrationUpload
import dev.deftu.screencapper.upload.integration.Integrations
import java.net.URI
import java.util.UUID

object NextcloudIntegration : HostIntegration {
    override val kind: String = NextcloudConfig.KIND
    override val displayName: String = "Nextcloud"

    private val uploader = NextcloudUploader(NextcloudTransport.HTTP) { UUID.randomUUID().toString().take(8) }

    override fun host(settings: IntegrationSettings): String {
        val server = NextcloudConfig.from(settings).server
        return runCatching { URI(server).host }.getOrNull() ?: server.ifBlank { "No server set" }
    }

    override fun upload(upload: IntegrationUpload): UploadResult {
        var config = NextcloudConfig.from(upload.settings)
        NextcloudPaths.serverProblem(config.server)?.let { throw UploadException(it) }
        NextcloudPaths.folderProblem(config.folder)?.let { throw UploadException(it) }
        if (config.user.isBlank() || config.appPassword.isBlank()) throw UploadException("Sign in to Nextcloud in the uploader settings first.")
        if (config.userId.isBlank()) {
            val userId = uploader.currentUser(config.credentials).id
            Integrations.update(upload.profile.id) { it.with(NextcloudConfig.USER_ID to userId) }
            config = config.copy(userId = userId)
        }
        val uploaded = uploader.upload(config, upload.bytes, upload.fileName, upload.contentType)
        return UploadResult(uploaded.share?.url.orEmpty(), NextcloudDeletions.request(config, uploaded))
    }

    override fun delete(request: DeletionRequest) {
        uploader.delete(NextcloudDeletions.target(request))
    }
}
