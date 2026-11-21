package dev.deftu.screencapper.upload.integration.flickr

import kotlin.test.Test
import kotlin.test.assertEquals

class OAuth1Test {
    @Test
    fun `percent encoding keeps only unreserved characters and uses uppercase hex`() {
        assertEquals("AZaz09-._~", OAuth1.encode("AZaz09-._~"))
        assertEquals("Ladies%20%2B%20Gentlemen%2A%21", OAuth1.encode("Ladies + Gentlemen*!"))
        assertEquals("21207597%40N07", OAuth1.encode("21207597@N07"))
        assertEquals("%E2%98%83", OAuth1.encode("☃"))
    }

    @Test
    fun `base string matches the rfc 5849 section 3_4_1_1 example`() {
        val params = listOf(
            "b5" to "=%3D",
            "a3" to "a",
            "c@" to "",
            "a2" to "r b",
            "oauth_consumer_key" to "9djdj82h48djs9d2",
            "oauth_token" to "kkk9d7dh3k39sjv7",
            "oauth_signature_method" to "HMAC-SHA1",
            "oauth_timestamp" to "137131201",
            "oauth_nonce" to "7d8f3e4a",
            "c2" to "",
            "a3" to "2 q",
        )
        val expected = "POST&http%3A%2F%2Fexample.com%2Frequest&a2%3Dr%2520b%26a3%3D2%2520q%26a3%3Da%26b5%3D%253D%25253D%26c%2540%3D%26c2%3D" +
            "%26oauth_consumer_key%3D9djdj82h48djs9d2%26oauth_nonce%3D7d8f3e4a%26oauth_signature_method%3DHMAC-SHA1" +
            "%26oauth_timestamp%3D137131201%26oauth_token%3Dkkk9d7dh3k39sjv7"
        assertEquals(expected, OAuth1.baseString("POST", "http://example.com/request", params))
    }

    @Test
    fun `signature matches the rfc 5849 section 1_2 photo request`() {
        val params = listOf(
            "file" to "vacation.jpg",
            "size" to "original",
            "oauth_consumer_key" to "dpf43f3p2l4k3l03",
            "oauth_token" to "nnch734d00sl2jdk",
            "oauth_signature_method" to "HMAC-SHA1",
            "oauth_timestamp" to "137131202",
            "oauth_nonce" to "chapoH",
        )
        val base = OAuth1.baseString("GET", "http://photos.example.net/photos", params)
        assertEquals("MdpQcU8iPSUjWoN/UDMsK2sui9I=", OAuth1.sign(base, "kd94hf93k423kf44", "pfkkdhi9sl3r4s00"))
    }

    @Test
    fun `request token base string matches the flickr docs example`() {
        val oauth = OAuth1.oauthParams(OAuthCredentials("653e7a6ecc1d528c516cc8f92cf98611", ""), "95613465", "1305586162") +
            ("oauth_callback" to "http://www.example.com")
        val expected = "GET&https%3A%2F%2Fwww.flickr.com%2Fservices%2Foauth%2Frequest_token&oauth_callback%3Dhttp%253A%252F%252Fwww.example.com" +
            "%26oauth_consumer_key%3D653e7a6ecc1d528c516cc8f92cf98611%26oauth_nonce%3D95613465%26oauth_signature_method%3DHMAC-SHA1" +
            "%26oauth_timestamp%3D1305586162%26oauth_version%3D1.0"
        assertEquals(expected, OAuth1.baseString("GET", FlickrApi.REQUEST_TOKEN_URL, oauth))
    }

    @Test
    fun `signed params add the token and a signature over every given param`() {
        val credentials = OAuthCredentials("key", "secret", "tok", "toksecret")
        val signed = OAuth1.signed("POST", "https://example.com/x", credentials, listOf("title" to "a b"), nonce = "n", timestamp = "1")
        val unsigned = signed.filterNot { it.first == "oauth_signature" }
        assertEquals("tok", signed.toMap()["oauth_token"])
        assertEquals(null, signed.toMap()["title"])
        assertEquals(OAuth1.sign(OAuth1.baseString("POST", "https://example.com/x", unsigned + ("title" to "a b")), "secret", "toksecret"), signed.toMap()["oauth_signature"])
    }

    @Test
    fun `authorization header quotes encoded values`() {
        assertEquals("OAuth oauth_token=\"a%2Fb\", oauth_signature=\"x%3D\"", OAuth1.header(listOf("oauth_token" to "a/b", "oauth_signature" to "x=")))
    }
}
