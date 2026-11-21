package dev.deftu.screencapper.upload.integration.nextcloud

import dev.deftu.screencapper.upload.UploadException
import java.io.IOException
import java.net.http.HttpRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class NextcloudSignInTest {
    private val start = LoginStart("https://cloud.example.com/login/v2/flow/abc", "https://cloud.example.com/login/v2/poll", "token")
    private val grant = """{"server":"https:\/\/cloud.example.com","loginName":"sam","appPassword":"Ab3dE-fGh1J-kLm2N-oPq3R-sTu4V"}"""

    private class Clock {
        var now = 0L
    }

    @Test
    fun `waits while the poll is pending and returns the grant`() {
        val replies = ArrayDeque(listOf(Reply(404, "[]"), Reply(404, "[]"), Reply(200, grant)))
        val polled = mutableListOf<HttpRequest>()
        val clock = Clock()
        val signIn = NextcloudSignIn({ polled += it; replies.removeFirst() }, { clock.now += it }, { clock.now })
        assertEquals(LoginGrant("https://cloud.example.com", "sam", "Ab3dE-fGh1J-kLm2N-oPq3R-sTu4V"), signIn.await(start))
        assertEquals(3, polled.size)
        assertEquals(3 * NextcloudSignIn.POLL_MILLIS, clock.now)
    }

    @Test
    fun `gives up when the sign in link expires`() {
        val clock = Clock()
        val signIn = NextcloudSignIn({ Reply(404, "[]") }, { clock.now += it }, { clock.now })
        assertFailsWith<UploadException> { signIn.await(start) }
        assertEquals(NextcloudSignIn.TIMEOUT_MILLIS, clock.now)
    }

    @Test
    fun `begin reads the login link from the server`() {
        val body = """{"poll":{"token":"token","endpoint":"https:\/\/cloud.example.com\/login\/v2\/poll"},"login":"https:\/\/cloud.example.com\/login\/v2\/flow\/abc"}"""
        val signIn = NextcloudSignIn({ Reply(200, body) }, {}, { 0L })
        assertEquals(start, signIn.begin("https://cloud.example.com"))
    }

    @Test
    fun `check reports an unreachable server instead of throwing`() {
        val signIn = NextcloudSignIn({ throw IOException("Connection refused") }, {}, { 0L })
        val result = assertIs<ConnectionCheck.Failed>(signIn.check(NextcloudCredentials("https://cloud.example.com", "sam", "pw")))
        assertEquals(NextcloudReplies.UNREACHABLE, result.message)
    }
}
