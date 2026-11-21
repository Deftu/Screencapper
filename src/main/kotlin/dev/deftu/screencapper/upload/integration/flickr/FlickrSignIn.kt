package dev.deftu.screencapper.upload.integration.flickr

import dev.deftu.screencapper.upload.UploadException
import dev.deftu.screencapper.upload.UploadHttp
import dev.deftu.screencapper.upload.integration.LoopbackReceiver
import java.net.URI

internal object FlickrSignIn {
    fun run(consumerKey: String, consumerSecret: String, receiver: LoopbackReceiver, browse: (URI) -> Boolean): FlickrAccount {
        val requestToken = FlickrApi.parseRequestToken(get(FlickrApi.requestTokenUrl(consumerKey, consumerSecret, receiver.redirectUri, OAuth1.nonce(), OAuth1.timestamp())))
        if (!browse(URI(FlickrApi.authorizeUrl(requestToken.token)))) throw UploadException("Couldn't open your browser.")
        val reply = receiver.await()
        val verifier = reply["oauth_verifier"].orEmpty()
        if (verifier.isEmpty() || reply["oauth_token"] != requestToken.token) throw UploadException("Flickr didn't finish the sign-in. Try again.")
        return FlickrApi.parseAccessToken(get(FlickrApi.accessTokenUrl(consumerKey, consumerSecret, requestToken, verifier, OAuth1.nonce(), OAuth1.timestamp())))
    }

    private fun get(url: String): String {
        val response = UploadHttp.exchange(UploadHttp.request(url).GET().build())
        if (response.statusCode() !in 200..299) throw UploadException(FlickrApi.oauthProblem(response.statusCode(), response.body()))
        return response.body()
    }
}
