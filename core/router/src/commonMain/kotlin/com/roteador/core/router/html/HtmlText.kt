package com.roteador.core.router.html

/** Small helpers for the static parts of router pages. */
object HtmlText {
    /**
     * Finds the text right after a label, skipping any tags in between:
     * `<strong>Modelo:</strong></td><td>RTF8225VW</td>` -> `RTF8225VW` for label `Modelo:`.
     */
    fun valueAfterLabel(html: String, label: String): String? {
        val regex = Regex(Regex.escape(label) + """\s*(?:<[^>]*>\s*)*([^<]+)""")
        return regex.find(html)?.groupValues?.get(1)?.let(::decodeEntities)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        return ENTITY.replace(text) { match ->
            val body = match.groupValues[1]
            when {
                body.startsWith("#x") || body.startsWith("#X") ->
                    body.drop(2).toIntOrNull(16)?.let(::codePointToString) ?: match.value
                body.startsWith("#") -> body.drop(1).toIntOrNull()?.let(::codePointToString) ?: match.value
                else -> NAMED[body] ?: match.value
            }
        }
    }

    private fun codePointToString(code: Int): String =
        if (code in 0..0xFFFF) code.toChar().toString() else "�"

    private val ENTITY = Regex("""&(#?[A-Za-z0-9]+);""")
    private val NAMED = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "#39" to "'", "nbsp" to " ")
}
