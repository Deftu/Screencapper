package dev.deftu.screencapper.screenshot.naming

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class NameValues(
    val time: LocalDateTime,
    val world: String? = null,
    val dimension: String? = null,
    val player: String? = null,
    val version: String? = null,
)

object NameTemplate {
    const val DEFAULT = "%time%"
    const val COUNT = "%count%"

    private val TOKEN = Regex("%([a-zA-Z]+)%")
    private val SEPARATORS = charArrayOf('/', '\\')
    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT)
    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    private val CLOCK = DateTimeFormatter.ofPattern("HH.mm.ss", Locale.ROOT)
    private val YEAR = DateTimeFormatter.ofPattern("yyyy", Locale.ROOT)
    private val MONTH = DateTimeFormatter.ofPattern("MM", Locale.ROOT)
    private val DAY = DateTimeFormatter.ofPattern("dd", Locale.ROOT)

    fun expand(template: String, values: NameValues, fallback: String = DEFAULT): String {
        val parts = template.split(*SEPARATORS).map { fill(it, values) }
        val file = PathSanitizer.segment(parts.last()).ifEmpty {
            PathSanitizer.segment(fill(fallback, values)).ifEmpty { values.time.format(TIME) }
        }
        return PathSanitizer.join(parts.dropLast(1).map(PathSanitizer::folder), file)
    }

    private fun fill(segment: String, values: NameValues): String =
        TOKEN.replace(segment) { match -> value(match.groupValues[1].lowercase(Locale.ROOT), values) ?: match.value }

    private fun value(token: String, values: NameValues): String? = when (token) {
        "time" -> values.time.format(TIME)
        "date" -> values.time.format(DATE)
        "clock" -> values.time.format(CLOCK)
        "year" -> values.time.format(YEAR)
        "month" -> values.time.format(MONTH)
        "day" -> values.time.format(DAY)
        "world" -> values.world?.takeIf { it.isNotBlank() } ?: "no_world"
        "dimension" -> values.dimension?.takeIf { it.isNotBlank() }?.removePrefix("minecraft:")?.replace(':', '_') ?: "no_dimension"
        "player" -> values.player?.takeIf { it.isNotBlank() } ?: "no_player"
        "version" -> values.version?.takeIf { it.isNotBlank() } ?: "unknown"
        else -> null
    }
}
