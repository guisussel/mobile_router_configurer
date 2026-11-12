package com.roteador.core.router.html

/**
 * Reads the `var name = '…'` assignments that router web UIs embed in their
 * pages. Most consumer routers render state into inline JavaScript instead of
 * exposing an API, so this is the main parsing tool for drivers.
 *
 * Supported value forms: `'text'`, `"text"`, `htmlDecode('text')`, `parseInt('12')`.
 * Assignments are kept in page order because some pages repeat the same variable
 * once per radio (see [blocks]).
 */
class PageScript(source: String) {
    val assignments: List<Pair<String, String>> = ASSIGNMENT.findAll(source).map { match ->
        val (name, wrapper, single, double) = match.destructured
        val raw = if (match.groups[3] != null) single else double
        val value = unescapeJs(raw).let { if (wrapper == "htmlDecode") HtmlText.decodeEntities(it) else it }
        name to value
    }.toList()

    operator fun get(name: String): String? = assignments.firstOrNull { it.first == name }?.second

    fun all(name: String): List<String> = assignments.filter { it.first == name }.map { it.second }

    fun require(name: String): String =
        get(name) ?: throw NoSuchElementException("variável '$name' não encontrada")

    /**
     * Splits the assignments into groups, a new group starting at every
     * assignment of [startVar]. Useful for pages that print the same set of
     * variables once per Wi-Fi radio.
     */
    fun blocks(startVar: String): List<Map<String, String>> {
        val result = mutableListOf<MutableMap<String, String>>()
        for ((name, value) in assignments) {
            if (name == startVar) result += mutableMapOf<String, String>()
            result.lastOrNull()?.putIfAbsent(name, value)
        }
        return result
    }

    companion object {
        private val ASSIGNMENT = Regex(
            """var\s+([A-Za-z_]\w*)\s*=\s*(?:(htmlDecode|parseInt)\(\s*)?(?:'((?:[^'\\\n]|\\.)*)'|"((?:[^"\\\n]|\\.)*)")""",
        )

        private fun unescapeJs(value: String): String {
            if ('\\' !in value) return value
            val out = StringBuilder(value.length)
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '\\' && i + 1 < value.length) {
                    out.append(
                        when (val next = value[i + 1]) {
                            'n' -> '\n'
                            't' -> '\t'
                            else -> next
                        },
                    )
                    i += 2
                } else {
                    out.append(c)
                    i++
                }
            }
            return out.toString()
        }

        /**
         * Parses a JS array literal of string arrays, e.g. `[['1','a'],['0','b']]`,
         * as printed by `var name = [[…]];`. Returns an empty list when absent.
         */
        fun stringRows(source: String, varName: String): List<List<String>> {
            val start = Regex("""var\s+$varName\s*=\s*\[""").find(source) ?: return emptyList()
            val end = source.indexOf("];", start.range.last)
            if (end < 0) return emptyList()
            val body = source.substring(start.range.last + 1, end)
            return ROW.findAll(body).map { row ->
                CELL.findAll(row.groupValues[1]).map { HtmlText.decodeEntities(it.groupValues[1]) }.toList()
            }.toList()
        }

        private val ROW = Regex("""\[((?:\s*'(?:[^'\\]|\\.)*'\s*,?)*)]""")
        private val CELL = Regex("""'((?:[^'\\]|\\.)*)'""")
    }
}
