package dev.deftu.screencapper.upload.integration.nextcloud

import dev.deftu.screencapper.upload.UploadException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NextcloudRepliesTest {
    private val loginStart = """
        {"poll":{"token":"mQUYQdffOSAMJYtm8pVpkOsVqXt5hglnuSpO5EMbgJMNEPFGaiDe8OUjvrJ2WcYc","endpoint":"https:\/\/cloud.example.com\/login\/v2\/poll"},
         "login":"https:\/\/cloud.example.com\/login\/v2\/flow\/guyjGtcKPTKCi4epIRIupIexgJ8wNInMFSfHabACRPZU"}
    """.trimIndent()

    private val loginGrant = """
        {"server":"https:\/\/cloud.example.com","loginName":"username","appPassword":"yKTVA4zgxjfivy52WqD8kW3M2pKGQr6srmUXMipRdunxjPFripJn0GMfmtNOqOolYSuJ6sCN"}
    """.trimIndent()

    private val currentUser = """
        {"ocs":{"meta":{"status":"ok","statuscode":200,"message":"OK"},"data":{"enabled":true,"id":"sam","display-name":"Sam Smith","displayname":"Sam Smith","email":null}}}
    """.trimIndent()

    private val share = """
        {"ocs":{"meta":{"status":"ok","statuscode":200,"message":"OK"},"data":{"id":"42","share_type":3,"uid_owner":"sam","permissions":17,"path":"\/Screenshots\/shot.png","item_type":"file","token":"yxcFKRWBJqYYzp4","url":"https:\/\/cloud.example.com\/s\/yxcFKRWBJqYYzp4","mimetype":"image\/png"}}}
    """.trimIndent()

    @Test
    fun `login start reads the browser link and poll details`() {
        val start = NextcloudReplies.loginStart(Reply(200, loginStart))
        assertEquals("https://cloud.example.com/login/v2/flow/guyjGtcKPTKCi4epIRIupIexgJ8wNInMFSfHabACRPZU", start.loginUrl)
        assertEquals("https://cloud.example.com/login/v2/poll", start.pollEndpoint)
        assertEquals("mQUYQdffOSAMJYtm8pVpkOsVqXt5hglnuSpO5EMbgJMNEPFGaiDe8OUjvrJ2WcYc", start.pollToken)
    }

    @Test
    fun `login start on a server without the flow says to use an app password`() {
        val error = assertFailsWith<UploadException> { NextcloudReplies.loginStart(Reply(404, "<html>Not found</html>")) }
        assertTrue("app password" in error.message.orEmpty())
        assertFailsWith<UploadException> { NextcloudReplies.loginStart(Reply(200, "<html>Welcome to nginx</html>")) }
        assertFailsWith<UploadException> { NextcloudReplies.loginStart(Reply(200, """{"poll":{"token":"t","endpoint":"javascript:alert(1)"},"login":"https://x.example"}""")) }
    }

    @Test
    fun `login poll is pending on 404 and returns the credentials on 200`() {
        assertNull(NextcloudReplies.loginPoll(Reply(404, "[]")))
        val grant = NextcloudReplies.loginPoll(Reply(200, loginGrant))!!
        assertEquals("https://cloud.example.com", grant.server)
        assertEquals("username", grant.loginName)
        assertEquals("yKTVA4zgxjfivy52WqD8kW3M2pKGQr6srmUXMipRdunxjPFripJn0GMfmtNOqOolYSuJ6sCN", grant.appPassword)
        assertTrue(grant.appPassword !in grant.toString())
        assertFailsWith<UploadException> { NextcloudReplies.loginPoll(Reply(500, "")) }
    }

    @Test
    fun `current user reads the user id and display name`() {
        assertEquals(NextcloudUser("sam", "Sam Smith"), NextcloudReplies.currentUser(Reply(200, currentUser)))
    }

    @Test
    fun `share reads the id and public link`() {
        assertEquals(NextcloudShare("42", "https://cloud.example.com/s/yxcFKRWBJqYYzp4"), NextcloudReplies.share(Reply(200, share)))
        val numericId = share.replace("\"id\":\"42\"", "\"id\":42")
        assertEquals("42", NextcloudReplies.share(Reply(200, numericId)).id)
    }

    @Test
    fun `share failure carries the server message`() {
        val body = """{"ocs":{"meta":{"status":"failure","statuscode":403,"message":"Public link sharing is disabled by the administrator"},"data":[]}}"""
        val error = assertFailsWith<UploadException> { NextcloudReplies.share(Reply(403, body)) }
        assertTrue("Public link sharing is disabled by the administrator" in error.message.orEmpty())
    }

    @Test
    fun `failures explain auth, storage and WebDAV errors plainly`() {
        assertEquals("Wrong username or app password.", NextcloudReplies.describe(Reply(401, "")))
        assertEquals("Your Nextcloud storage is full.", NextcloudReplies.describe(Reply(507, "")))
        val dav = """<?xml version="1.0" encoding="utf-8"?>
            <d:error xmlns:d="DAV:" xmlns:s="http://sabredav.org/ns"><s:exception>Sabre\DAV\Exception\Forbidden</s:exception><s:message>Invalid path &quot;a&quot;</s:message></d:error>"""
        assertEquals("Nextcloud answered with HTTP 403: Invalid path \"a\"", NextcloudReplies.describe(Reply(403, dav)))
        assertEquals("Nextcloud answered with HTTP 500", NextcloudReplies.describe(Reply(500, "<html>oops</html>")))
    }

    @Test
    fun `connection check tells bad passwords and other servers apart`() {
        assertEquals(ConnectionCheck.Ok(NextcloudUser("sam", "Sam Smith")), NextcloudReplies.check(Reply(200, currentUser)))
        assertEquals("Wrong username or app password.", assertIs<ConnectionCheck.Failed>(NextcloudReplies.check(Reply(401, currentUser.take(10)))).message)
        assertTrue("doesn't look like a Nextcloud server" in assertIs<ConnectionCheck.Failed>(NextcloudReplies.check(Reply(404, "<html></html>"))).message)
        assertTrue("doesn't look like a Nextcloud server" in assertIs<ConnectionCheck.Failed>(NextcloudReplies.check(Reply(200, "<html></html>"))).message)
        assertTrue("maintenance" in assertIs<ConnectionCheck.Failed>(NextcloudReplies.check(Reply(503, ""))).message)
    }
}
