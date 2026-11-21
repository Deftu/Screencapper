package dev.deftu.screencapper.editor

object TextFit {
    fun wrap(text: String, maxWidth: Float, width: (String) -> Float): List<String> {
        val lines = mutableListOf<String>()
        for (paragraph in text.lines()) {
            var line = ""
            for (word in paragraph.split(' ', '\t').filter { it.isNotEmpty() }) {
                val joined = if (line.isEmpty()) word else "$line $word"
                if (width(joined) <= maxWidth) {
                    line = joined
                    continue
                }
                if (line.isNotEmpty()) lines += line
                var rest = word
                while (rest.length > 1 && width(rest) > maxWidth) {
                    var cut = 1
                    while (cut < rest.length - 1 && width(rest.substring(0, cut + 1)) <= maxWidth) cut++
                    lines += rest.substring(0, cut)
                    rest = rest.substring(cut)
                }
                line = rest
            }
            if (line.isNotEmpty()) lines += line
        }
        return lines
    }
}
