package dev.deftu.screencapper.upload.integration.nextcloud

import dev.deftu.screencapper.config.DeletionKind
import dev.deftu.screencapper.config.DeletionMethod
import dev.deftu.screencapper.upload.UploadException
import java.io.ByteArrayOutputStream
import java.net.http.HttpRequest
import java.nio.ByteBuffer
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NextcloudUploaderTest {
    private val password = "Ab3dE-fGh1J-kLm2N-oPq3R-sTu4V"
    private val config = NextcloudConfig("https://cloud.example.com", "sam@example.com", password, userId = "sam", folder = "Screenshots")
    private val bytes = byteArrayOf(1, 2, 3)
    private val shareJson = """{"ocs":{"meta":{"status":"ok","statuscode":200,"message":"OK"},"data":{"id":"42","share_type":3,"token":"yxcFKRWBJqYYzp4","url":"https:\/\/cloud.example.com\/s\/yxcFKRWBJqYYzp4"}}}"""
    private val fileBase = "https://cloud.example.com/remote.php/dav/files/sam"
    private val sharesUrl = "https://cloud.example.com/ocs/v2.php/apps/files_sharing/api/v1/shares"

    private class Script(vararg replies: Reply) : NextcloudTransport {
        val requests = mutableListOf<HttpRequest>()
        private val queue = ArrayDeque(replies.toList())

        override fun send(request: HttpRequest): Reply {
            requests += request
            return queue.removeFirstOrNull() ?: error("Unexpected ${request.method()} ${request.uri()}")
        }
    }

    private fun uploader(script: Script) = NextcloudUploader(script) { "a1b2c3d4" }

    private fun HttpRequest.line(): String = "${method()} ${uri()}"

    private fun HttpRequest.header(name: String): String? = headers().firstValue(name).orElse(null)

    private fun HttpRequest.body(): String {
        val publisher = bodyPublisher().orElse(null) ?: return ""
        val out = ByteArrayOutputStream()
        val done = CompletableFuture<Unit>()
        publisher.subscribe(object : Flow.Subscriber<ByteBuffer> {
            override fun onSubscribe(subscription: Flow.Subscription) = subscription.request(Long.MAX_VALUE)
            override fun onNext(item: ByteBuffer) {
                val chunk = ByteArray(item.remaining())
                item.get(chunk)
                out.write(chunk)
            }
            override fun onError(throwable: Throwable) {
                done.completeExceptionally(throwable)
            }
            override fun onComplete() {
                done.complete(Unit)
            }
        })
        done.get(5, TimeUnit.SECONDS)
        return out.toString(Charsets.UTF_8)
    }

    @Test
    fun `upload puts the file without overwriting and makes a public link`() {
        val script = Script(Reply(201, ""), Reply(200, shareJson))
        val uploaded = uploader(script).upload(config, bytes, "shot.png", "image/png")

        assertEquals(listOf("PUT $fileBase/Screenshots/shot.png", "POST $sharesUrl"), script.requests.map { it.line() })
        val put = script.requests[0]
        assertEquals("Basic " + Base64.getEncoder().encodeToString("sam@example.com:$password".toByteArray()), put.header("Authorization"))
        assertEquals("*", put.header("If-None-Match"))
        assertEquals("image/png", put.header("Content-Type"))
        assertEquals(3L, put.bodyPublisher().get().contentLength())

        val share = script.requests[1]
        assertEquals("true", share.header("OCS-APIRequest"))
        assertEquals("application/json", share.header("Accept"))
        assertEquals("application/x-www-form-urlencoded", share.header("Content-Type"))
        assertEquals("path=%2FScreenshots%2Fshot.png&shareType=3", share.body())

        assertEquals(NextcloudUploaded("$fileBase/Screenshots/shot.png", NextcloudShare("42", "https://cloud.example.com/s/yxcFKRWBJqYYzp4")), uploaded)
    }

    @Test
    fun `no link skips sharing`() {
        val script = Script(Reply(201, ""))
        val uploaded = uploader(script).upload(config.copy(link = NextcloudLink.NONE), bytes, "shot.png", "image/png")
        assertEquals(1, script.requests.size)
        assertNull(uploaded.share)
    }

    @Test
    fun `an existing file makes it pick the next free name`() {
        val script = Script(Reply(412, ""), Reply(201, ""), Reply(200, shareJson))
        uploader(script).upload(config, bytes, "shot.png", "image/png")
        assertEquals("PUT $fileBase/Screenshots/shot%20%282%29.png", script.requests[1].line())
        assertEquals("path=%2FScreenshots%2Fshot+%282%29.png&shareType=3", script.requests[2].body())
    }

    @Test
    fun `gives up after too many taken names`() {
        val script = Script(*Array(NextcloudPaths.MAX_ATTEMPTS) { Reply(412, "") })
        assertFailsWith<UploadException> { uploader(script).upload(config, bytes, "shot.png", "image/png") }
        assertEquals(NextcloudPaths.MAX_ATTEMPTS, script.requests.size)
        assertEquals("PUT $fileBase/Screenshots/shot-a1b2c3d4.png", script.requests.last().line())
    }

    @Test
    fun `a missing folder is created one level at a time then the upload is retried`() {
        val script = Script(Reply(409, ""), Reply(405, ""), Reply(201, ""), Reply(201, ""))
        uploader(script).upload(config.copy(folder = "Games/Minecraft", link = NextcloudLink.NONE), bytes, "shot.png", "image/png")
        assertEquals(
            listOf(
                "PUT $fileBase/Games/Minecraft/shot.png",
                "MKCOL $fileBase/Games",
                "MKCOL $fileBase/Games/Minecraft",
                "PUT $fileBase/Games/Minecraft/shot.png",
            ),
            script.requests.map { it.line() },
        )
        assertTrue(script.requests[1].header("Authorization")!!.startsWith("Basic "))
    }

    @Test
    fun `a folder that can't be created stops the upload`() {
        val script = Script(Reply(409, ""), Reply(403, ""))
        val error = assertFailsWith<UploadException> { uploader(script).upload(config.copy(link = NextcloudLink.NONE), bytes, "shot.png", "image/png") }
        assertTrue("Screenshots" in error.message.orEmpty())
    }

    @Test
    fun `a failed share removes the uploaded file and reports why`() {
        val refused = """{"ocs":{"meta":{"status":"failure","statuscode":403,"message":"Public link sharing is disabled by the administrator"},"data":[]}}"""
        val script = Script(Reply(201, ""), Reply(403, refused), Reply(204, ""))
        val error = assertFailsWith<UploadException> { uploader(script).upload(config, bytes, "shot.png", "image/png") }
        assertTrue("Public link sharing is disabled by the administrator" in error.message.orEmpty())
        assertEquals("DELETE $fileBase/Screenshots/shot.png", script.requests[2].line())
    }

    @Test
    fun `wrong password is reported plainly`() {
        val script = Script(Reply(401, ""))
        val error = assertFailsWith<UploadException> { uploader(script).upload(config, bytes, "shot.png", "image/png") }
        assertTrue("Wrong username or app password." in error.message.orEmpty())
        assertTrue(password !in error.message.orEmpty())
    }

    @Test
    fun `current user is read from the OCS endpoint`() {
        val script = Script(Reply(200, """{"ocs":{"meta":{"status":"ok"},"data":{"id":"sam","display-name":"Sam"}}}"""))
        assertEquals(NextcloudUser("sam", "Sam"), uploader(script).currentUser(config.credentials))
        val request = script.requests.single()
        assertEquals("GET https://cloud.example.com/ocs/v2.php/cloud/user", request.line())
        assertEquals("true", request.header("OCS-APIRequest"))
        assertEquals("application/json", request.header("Accept"))
    }

    @Test
    fun `deletion request carries the exact password under a secret name and reads back`() {
        val uploaded = NextcloudUploaded("$fileBase/Screenshots/shot.png", NextcloudShare("42", "https://cloud.example.com/s/x"))
        val request = NextcloudDeletions.request(config, uploaded)
        assertEquals(DeletionKind.REQUEST, request.kind)
        assertEquals(DeletionMethod.DELETE, request.method)
        assertEquals("nextcloud", request.integration)
        assertEquals("$fileBase/Screenshots/shot.png", request.url)
        assertTrue("password" to password in request.headers)
        assertEquals(NextcloudDeleteTarget(config.credentials, "$fileBase/Screenshots/shot.png", "42"), NextcloudDeletions.target(request))
        assertEquals("", NextcloudDeletions.target(NextcloudDeletions.request(config, uploaded.copy(share = null))).shareId)
    }

    @Test
    fun `deletion refuses a file outside the server's WebDAV folder`() {
        val request = NextcloudDeletions.request(config, NextcloudUploaded("$fileBase/Screenshots/shot.png", null))
        assertFailsWith<UploadException> { NextcloudDeletions.target(request.copy(url = "https://evil.example/remote.php/dav/files/sam/x.png")) }
        assertFailsWith<UploadException> { NextcloudDeletions.target(request.copy(headers = request.headers.filterNot { it.first == "password" })) }
    }

    @Test
    fun `delete removes the share then the file and treats gone as done`() {
        val script = Script(Reply(200, ""), Reply(204, ""))
        uploader(script).delete(NextcloudDeleteTarget(config.credentials, "$fileBase/Screenshots/shot.png", "42"))
        assertEquals(listOf("DELETE $sharesUrl/42", "DELETE $fileBase/Screenshots/shot.png"), script.requests.map { it.line() })
        assertEquals("true", script.requests[0].header("OCS-APIRequest"))

        uploader(Script(Reply(404, ""), Reply(404, ""))).delete(NextcloudDeleteTarget(config.credentials, "$fileBase/Screenshots/shot.png", "42"))

        val noShare = Script(Reply(204, ""))
        uploader(noShare).delete(NextcloudDeleteTarget(config.credentials, "$fileBase/Screenshots/shot.png", ""))
        assertEquals(1, noShare.requests.size)

        assertFailsWith<UploadException> { uploader(Script(Reply(401, ""))).delete(NextcloudDeleteTarget(config.credentials, "$fileBase/Screenshots/shot.png", "")) }
    }

    @Test
    fun `browser sign in starts anonymously and polls with the token as a form field`() {
        val start = NextcloudRequests.loginStart("https://cloud.example.com")
        assertEquals("POST https://cloud.example.com/index.php/login/v2", start.line())
        assertNull(start.header("Authorization"))

        val poll = NextcloudRequests.loginPoll(LoginStart("https://cloud.example.com/login/v2/flow/abc", "https://cloud.example.com/login/v2/poll", "t+k/n="))
        assertEquals("POST https://cloud.example.com/login/v2/poll", poll.line())
        assertEquals("application/x-www-form-urlencoded", poll.header("Content-Type"))
        assertEquals("token=t%2Bk%2Fn%3D", poll.body())
    }
}
