package dev.deftu.screencapper.screenshot.naming

object PathSanitizer {
    const val MAX_SEGMENT = 100
    const val MAX_DEPTH = 8
    const val ORIGINALS = "original"
    const val PANORAMAS = "panoramas"

    private val ILLEGAL = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private val SEPARATORS = charArrayOf('/', '\\')
    private val RESERVED = setOf("CON", "PRN", "AUX", "NUL", "CONIN$", "CONOUT$") +
        (1..9).flatMap { listOf("COM$it", "LPT$it") }

    fun segment(raw: String): String {
        var out = trim(raw.replace(ILLEGAL, ""))
        if (out.length > MAX_SEGMENT) out = trim(out.take(MAX_SEGMENT))
        val base = out.substringBefore('.')
        if (base.trimEnd().uppercase() in RESERVED) out = base + "_" + out.substring(base.length)
        return out
    }

    fun folder(raw: String): String {
        val out = segment(raw)
        return if (out.equals(ORIGINALS, ignoreCase = true)) out + "_" else out
    }

    fun path(raw: String): String? {
        val parts = raw.split(*SEPARATORS)
        val file = segment(parts.last())
        if (file.isEmpty()) return null
        return join(parts.dropLast(1).map(::folder), file)
    }

    internal fun join(folders: List<String>, file: String): String {
        val kept = folders.filter { it.isNotEmpty() }.take(MAX_DEPTH - 1).toMutableList()
        if (kept.firstOrNull()?.equals(PANORAMAS, ignoreCase = true) == true) kept[0] += "_"
        return (kept + file).joinToString("/")
    }

    private fun trim(value: String): String = value.trim { it == '.' || it.isWhitespace() }
}
