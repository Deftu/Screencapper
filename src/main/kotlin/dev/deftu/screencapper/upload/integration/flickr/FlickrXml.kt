package dev.deftu.screencapper.upload.integration.flickr

import org.w3c.dom.Element
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

internal sealed interface FlickrReply {
    class Ok(private val root: Element) : FlickrReply {
        fun text(tag: String): String = root.getElementsByTagName(tag).item(0)?.textContent?.trim().orEmpty()
    }

    class Fail(val code: Int, val message: String) : FlickrReply
}

internal object FlickrXml {
    private val QUIET = object : ErrorHandler {
        override fun warning(exception: SAXParseException) {}
        override fun error(exception: SAXParseException) = throw exception
        override fun fatalError(exception: SAXParseException) = throw exception
    }

    fun parse(body: String): FlickrReply? {
        val root = runCatching { builder().parse(InputSource(StringReader(body.trim()))).documentElement }.getOrNull() ?: return null
        if (root.tagName != "rsp") return null
        return when (root.getAttribute("stat")) {
            "ok" -> FlickrReply.Ok(root)
            "fail" -> {
                val error = root.getElementsByTagName("err").item(0) as? Element
                FlickrReply.Fail(error?.getAttribute("code")?.toIntOrNull() ?: -1, error?.getAttribute("msg").orEmpty())
            }
            else -> null
        }
    }

    private fun builder(): DocumentBuilder = DocumentBuilderFactory.newInstance().apply {
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }.newDocumentBuilder().apply { setErrorHandler(QUIET) }
}
