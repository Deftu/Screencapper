package dev.deftu.screencapper.metadata

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.util.Locale

/** What a search runs against: the file name, its metadata fields and the day it was taken */
class SearchEntry(val name: String, val fields: Map<String, String>, val date: LocalDate)

/**
 * A parsed search box query. Every term must match.
 *
 * Filters: `server:` (name or address), `world:`, `biome:`, `dimension:`, `player:`, `version:`, `before:` and `after:`
 * (`YYYY`, `YYYY-MM` or `YYYY-MM-DD`, both excluding the named period), `has:coords` and `has:chat`. Values can be quoted
 * to include spaces. Everything else, including unknown filters and unreadable dates, is a free word or quoted phrase
 * matched against the name and every field. Matching ignores case.
 */
class SearchQuery private constructor(private val terms: List<Term>) {
    val isEmpty: Boolean get() = terms.isEmpty()

    fun matches(entry: SearchEntry): Boolean {
        if (terms.isEmpty()) return true
        val lowered = LoweredEntry(entry)
        return terms.all { it.matches(lowered) }
    }

    private class LoweredEntry(val entry: SearchEntry) {
        val name = entry.name.lowercase(Locale.ROOT)
        val fields = entry.fields.mapValues { it.value.lowercase(Locale.ROOT) }
    }

    private sealed interface Term {
        fun matches(entry: LoweredEntry): Boolean
    }

    private class Words(val text: String) : Term {
        override fun matches(entry: LoweredEntry) = text in entry.name || entry.fields.values.any { text in it }
    }

    private class Field(val keys: List<String>, val text: String) : Term {
        override fun matches(entry: LoweredEntry) = keys.any { key -> entry.fields[key]?.contains(text) == true }
    }

    private class Has(val key: String) : Term {
        override fun matches(entry: LoweredEntry) = !entry.entry.fields[key].isNullOrBlank()
    }

    private class Before(val start: LocalDate) : Term {
        override fun matches(entry: LoweredEntry) = entry.entry.date.isBefore(start)
    }

    private class After(val end: LocalDate) : Term {
        override fun matches(entry: LoweredEntry) = entry.entry.date.isAfter(end)
    }

    companion object {
        private val FIELDS = mapOf(
            "server" to listOf(MetadataKeys.SERVER, MetadataKeys.SERVER_ADDRESS),
            "world" to listOf(MetadataKeys.WORLD),
            "biome" to listOf(MetadataKeys.BIOME),
            "dimension" to listOf(MetadataKeys.DIMENSION),
            "player" to listOf(MetadataKeys.PLAYER),
            "version" to listOf(MetadataKeys.VERSION),
        )

        private val HAS = mapOf(
            "coords" to MetadataKeys.COORDINATES,
            "coordinates" to MetadataKeys.COORDINATES,
            "chat" to MetadataKeys.CHAT,
        )

        fun parse(query: String): SearchQuery = SearchQuery(tokens(query).mapNotNull(::term))

        private class Token(val text: String, val phrase: Boolean, val colon: Int)

        private fun term(token: Token): Term? {
            val text = token.text.lowercase(Locale.ROOT)
            if (text.isBlank()) return null
            if (token.phrase || token.colon <= 0) return Words(text)

            val key = text.substring(0, token.colon)
            val value = text.substring(token.colon + 1)
            if (value.isEmpty()) return Words(text)
            FIELDS[key]?.let { return Field(it, value) }
            return when (key) {
                "has" -> HAS[value]?.let(::Has)
                "before" -> period(value)?.let { Before(it.start) }
                "after" -> period(value)?.let { After(it.endInclusive) }
                else -> null
            } ?: Words(text)
        }

        private fun period(value: String): ClosedRange<LocalDate>? = try {
            when {
                Regex("\\d{4}").matches(value) -> LocalDate.of(value.toInt(), 1, 1).let { it..it.withDayOfYear(it.lengthOfYear()) }
                Regex("\\d{4}-\\d{2}").matches(value) -> YearMonth.parse(value).let { it.atDay(1)..it.atEndOfMonth() }
                Regex("\\d{4}-\\d{2}-\\d{2}").matches(value) -> LocalDate.parse(value).let { it..it }
                else -> null
            }
        } catch (_: DateTimeParseException) {
            null
        }

        /**
         * Splits on whitespace outside quotes. A token that opens with a quote is a phrase; inside a token, quotes only
         * group (`world:"my world"`). An unclosed quote runs to the end.
         */
        private fun tokens(query: String): List<Token> {
            val tokens = mutableListOf<Token>()
            var index = 0
            while (index < query.length) {
                while (index < query.length && query[index].isWhitespace()) index++
                if (index >= query.length) break

                val phrase = query[index] == '"'
                val text = StringBuilder()
                var quoted = false
                var colon = -1
                while (index < query.length) {
                    val char = query[index]
                    if (char == '"') {
                        quoted = !quoted
                    } else if (char.isWhitespace() && !quoted) {
                        break
                    } else {
                        if (char == ':' && !quoted && colon < 0) colon = text.length
                        text.append(char)
                    }
                    index++
                }
                tokens += Token(text.toString(), phrase, colon)
            }
            return tokens
        }
    }
}
