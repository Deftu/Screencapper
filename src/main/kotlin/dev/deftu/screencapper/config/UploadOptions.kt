package dev.deftu.screencapper.config

enum class RequestMethod {
    POST,
    PUT,
    PATCH
}

enum class RequestBody(val displayName: String) {
    MULTIPART("Multipart form"),
    RAW("Raw image");

    override fun toString(): String = displayName
}

enum class LinkMode(val displayName: String) {
    LINK("Link from response"),
    NONE("No link");

    override fun toString(): String = displayName
}

enum class DeletionKind(val displayName: String) {
    NONE("None"),
    BROWSER("Open in browser"),
    REQUEST("Send request");

    override fun toString(): String = displayName
}

enum class DeletionMethod {
    DELETE,
    POST,
    GET
}
