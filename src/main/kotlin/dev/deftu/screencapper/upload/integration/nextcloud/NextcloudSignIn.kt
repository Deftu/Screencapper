package dev.deftu.screencapper.upload.integration.nextcloud

import dev.deftu.screencapper.upload.UploadException
import java.io.IOException

class NextcloudSignIn(
    private val transport: NextcloudTransport = NextcloudTransport.HTTP,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    fun begin(server: String): LoginStart = NextcloudReplies.loginStart(transport.send(NextcloudRequests.loginStart(server)))

    fun await(start: LoginStart): LoginGrant {
        val deadline = clock() + TIMEOUT_MILLIS
        while (clock() < deadline) {
            sleep(POLL_MILLIS)
            NextcloudReplies.loginPoll(transport.send(NextcloudRequests.loginPoll(start)))?.let { return it }
        }
        throw UploadException("The sign-in link expired. Try again.")
    }

    fun check(credentials: NextcloudCredentials): ConnectionCheck = try {
        NextcloudReplies.check(transport.send(NextcloudRequests.currentUser(credentials)))
    } catch (e: IOException) {
        ConnectionCheck.Failed(NextcloudReplies.UNREACHABLE)
    }

    companion object {
        const val POLL_MILLIS = 2_000L
        const val TIMEOUT_MILLIS = 20 * 60 * 1_000L
    }
}
