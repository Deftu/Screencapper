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
