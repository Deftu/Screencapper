package dev.deftu.screencapper.upload.integration.flickr

import dev.deftu.screencapper.upload.DeletionRequest
import dev.deftu.screencapper.upload.UploadException
import dev.deftu.screencapper.upload.UploadHttp
import dev.deftu.screencapper.upload.UploadResult
import dev.deftu.screencapper.upload.integration.HostIntegration
import dev.deftu.screencapper.upload.integration.IntegrationSettings
import dev.deftu.screencapper.upload.integration.IntegrationUpload
import java.net.http.HttpRequest
import java.util.UUID

object FlickrIntegration : HostIntegration {
    override val kind: String = FlickrSettings.KIND
    override val displayName: String = "Flickr"

    override fun host(settings: IntegrationSettings): String = "flickr.com"

    override fun upload(upload: IntegrationUpload): UploadResult {
        val settings = upload.settings
        val credentials = FlickrSettings.credentials(settings) ?: throw UploadException("Sign in to Flickr in the uploader settings first.")
        val fields = FlickrApi.uploadFields(settings, upload.values)
        val boundary = "----Screencapper${UUID.randomUUID().toString().replace("-", "")}"
        val request = UploadHttp.request(FlickrApi.UPLOAD_URL)
            .header("Authorization", FlickrApi.authorization("POST", FlickrApi.UPLOAD_URL, credentials, fields))
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(FlickrApi.multipart(boundary, fields, upload.fileName, upload.contentType, upload.bytes)))
            .build()
        val response = UploadHttp.exchange(request)
        val photoId = FlickrApi.photoId(response.statusCode(), response.body())
        val nsid = settings[FlickrSettings.USER_NSID]
        val link = if (nsid.isBlank()) "" else FlickrApi.photoUrl(nsid, photoId)
        return UploadResult(link, FlickrApi.deletion(settings, photoId))
    }

    override fun delete(request: DeletionRequest) {
        val credentials = FlickrSettings.credentials(request.headers.toMap()) ?: throw UploadException("This Flickr upload has no saved sign-in to delete it with.")
        val photoId = request.url.trim()
        if (photoId.isEmpty() || !photoId.all { it.isDigit() }) throw UploadException("This Flickr upload has no photo id.")
        val fields = FlickrApi.deleteFields(credentials.consumerKey, photoId)
        val response = UploadHttp.exchange(
            UploadHttp.request(FlickrApi.REST_URL)
                .header("Authorization", FlickrApi.authorization("POST", FlickrApi.REST_URL, credentials, fields))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(OAuth1.query(fields)))
                .build(),
        )
        FlickrApi.checkDeleted(response.statusCode(), response.body())
    }
}
