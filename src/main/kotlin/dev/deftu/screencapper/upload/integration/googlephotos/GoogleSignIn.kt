package dev.deftu.screencapper.upload.integration.googlephotos

import dev.deftu.screencapper.upload.UploadException
import dev.deftu.screencapper.upload.integration.LoopbackReceiver
import org.polyfrost.oneconfig.api.platform.v1.DesktopHelper
import java.net.URI
import java.util.concurrent.TimeoutException

class GoogleSignIn(private val clientId: String, private val clientSecret: String) {
    private val receiver = LoopbackReceiver.open()
    private val verifier = GoogleOAuth.randomToken()
    private val state = GoogleOAuth.randomToken()

    fun run(): SignedIn {
        val url = GoogleOAuth.authorizationUrl(clientId, receiver.redirectUri, GoogleOAuth.challenge(verifier), state)
        if (!DesktopHelper.browse(URI(url))) {
            receiver.cancel()
            throw UploadException("Couldn't open your browser.")
        }
        val reply = try {
            receiver.await()
        } catch (_: TimeoutException) {
            throw UploadException("Sign-in timed out. Try again.")
        }
        val code = GoogleOAuth.codeFrom(reply, state)
        val response = GooglePhotosIntegration.send(GoogleOAuth.exchange(clientId, clientSecret, code, verifier, receiver.redirectUri))
        if (response.statusCode() !in 200..299) {
            throw UploadException(
                when (GoogleOAuth.error(response.body())) {
                    "invalid_client", "unauthorized_client" -> GoogleOAuth.CLIENT_REJECTED
                    else -> "Google sign-in answered with HTTP ${response.statusCode()}"
                },
            )
        }
        val tokens = GoogleOAuth.parseTokens(response.body(), System.currentTimeMillis())
        if (!tokens.canUpload) throw UploadException("Screencapper needs permission to add photos. Sign in again and allow it.")
        if (tokens.refreshToken.isEmpty()) throw UploadException("Google didn't keep you signed in. Remove the app's access in your Google Account, then sign in again.")
        return SignedIn(clientId, tokens)
    }

    fun cancel() {
        receiver.cancel()
    }
}
