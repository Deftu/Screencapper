package dev.deftu.screencapper.upload.integration.flickr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class FlickrXmlTest {
    @Test
    fun `ok reply exposes its payload`() {
        val reply = assertIs<FlickrReply.Ok>(FlickrXml.parse("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n<rsp stat=\"ok\">\n\t<photoid>1234</photoid>\n</rsp>"))
        assertEquals("1234", reply.text("photoid"))
    }

    @Test
    fun `fail reply gives the error code and message`() {
        val reply = assertIs<FlickrReply.Fail>(FlickrXml.parse("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n<rsp stat=\"fail\">\n\t<err code=\"96\" msg=\"Invalid signature\" />\n</rsp>"))
        assertEquals(96, reply.code)
        assertEquals("Invalid signature", reply.message)
    }

    @Test
    fun `documents with a doctype are refused`() {
        val xxe = "<?xml version=\"1.0\"?><!DOCTYPE rsp [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><rsp stat=\"ok\"><photoid>&x;</photoid></rsp>"
        assertNull(FlickrXml.parse(xxe))
    }

    @Test
    fun `non rsp or broken documents are unreadable`() {
        assertNull(FlickrXml.parse("<html><body>Bad gateway</body></html>"))
        assertNull(FlickrXml.parse("oauth_problem=signature_invalid"))
        assertNull(FlickrXml.parse(""))
    }
}
