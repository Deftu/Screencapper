package dev.deftu.screencapper.upload.integration.nextcloud

import dev.deftu.screencapper.config.DeletionKind
import dev.deftu.screencapper.config.DeletionMethod
import dev.deftu.screencapper.upload.DeletionRequest
import dev.deftu.screencapper.upload.UploadException

data class NextcloudUploaded(val fileUrl: String, val share: NextcloudShare?)

data class NextcloudDeleteTarget(val credentials: NextcloudCredentials, val fileUrl: String, val shareId: String)

class NextcloudUploader(private val transport: NextcloudTransport, private val random: () -> String) {
    fun currentUser(credentials: NextcloudCredentials): NextcloudUser =
        NextcloudReplies.currentUser(transport.send(NextcloudRequests.currentUser(credentials)))

    fun upload(config: NextcloudConfig, bytes: ByteArray, fileName: String, contentType: String): NextcloudUploaded {
        val credentials = config.credentials
        val folder = NextcloudPaths.folderSegments(config.folder)
        var foldersCreated = false
        var attempt = 0
        while (attempt < NextcloudPaths.MAX_ATTEMPTS) {
            val path = folder + NextcloudPaths.candidateName(fileName, attempt, random)
            val url = NextcloudPaths.davUrl(config.server, config.userId, path)
            val reply = transport.send(NextcloudRequests.put(credentials, url, bytes, contentType))
            when {
                reply.ok -> return NextcloudUploaded(url, if (config.link == NextcloudLink.PUBLIC) share(credentials, url, path) else null)
                reply.status == 412 -> attempt++
                reply.status == 409 && !foldersCreated && folder.isNotEmpty() -> {
                    createFolders(config, folder)
                    foldersCreated = true
                }
                else -> throw UploadException(NextcloudReplies.describe(reply))
            }
        }
        throw UploadException("Couldn't find a free file name for $fileName in ${NextcloudPaths.sharePath(folder)}.")
    }

    fun delete(target: NextcloudDeleteTarget) {
        if (target.shareId.isNotEmpty()) expectGone(transport.send(NextcloudRequests.deleteShare(target.credentials, target.shareId)))
        expectGone(transport.send(NextcloudRequests.deleteFile(target.credentials, target.fileUrl)))
    }

    private fun expectGone(reply: Reply) {
        if (!reply.ok && reply.status != 404) throw UploadException(NextcloudReplies.describe(reply))
    }

    private fun createFolders(config: NextcloudConfig, folder: List<String>) {
        for (depth in 1..folder.size) {
            val reply = transport.send(NextcloudRequests.mkcol(config.credentials, NextcloudPaths.davUrl(config.server, config.userId, folder.take(depth))))
            if (reply.status != 201 && reply.status != 405) {
                throw UploadException("Couldn't create the folder ${NextcloudPaths.sharePath(folder.take(depth))}. ${NextcloudReplies.describe(reply)}")
            }
        }
    }

    private fun share(credentials: NextcloudCredentials, url: String, path: List<String>): NextcloudShare = try {
        NextcloudReplies.share(transport.send(NextcloudRequests.createShare(credentials, NextcloudPaths.sharePath(path))))
    } catch (e: UploadException) {
        runCatching { transport.send(NextcloudRequests.deleteFile(credentials, url)) }
        throw UploadException("Couldn't make a public link, so the upload was removed. ${e.message} To skip links, pick No link in the uploader settings.")
    }
}

object NextcloudDeletions {
    private const val SERVER = "server"
    private const val USER = "user"
    private const val PASSWORD = "password"
    private const val SHARE = "share"

    fun request(config: NextcloudConfig, uploaded: NextcloudUploaded): DeletionRequest = DeletionRequest(
        DeletionKind.REQUEST,
        DeletionMethod.DELETE,
        uploaded.fileUrl,
        listOfNotNull(
            SERVER to config.server,
            USER to config.user,
            PASSWORD to config.appPassword,
            uploaded.share?.let { SHARE to it.id },
        ),
        integration = NextcloudConfig.KIND,
    )

    fun target(request: DeletionRequest): NextcloudDeleteTarget {
        fun header(name: String): String = request.headers.firstOrNull { it.first == name }?.second.orEmpty()
        val credentials = NextcloudCredentials(header(SERVER), header(USER), header(PASSWORD))
        val missing = credentials.server.isEmpty() || credentials.user.isEmpty() || credentials.appPassword.isEmpty()
        if (missing || !request.url.startsWith("${credentials.server}/remote.php/dav/files/")) {
            throw UploadException("This Nextcloud upload is missing details, so it can't be deleted from here.")
        }
        return NextcloudDeleteTarget(credentials, request.url, header(SHARE))
    }
}
