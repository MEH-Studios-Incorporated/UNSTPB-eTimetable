// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.timetable

/**
 * CSV as spreadsheets write it: comma or semicolon separated (Romanian
 * Excel uses semicolons), fields quoted with double quotes, an optional
 * UTF-8 byte order mark.
 */
object Csv {
    // the UTF-8 byte order mark, written as a code so editors cannot drop it
    private val BOM = Char(0xFEFF).toString()

    fun parse(text: String): List<List<String>> {
        val body = text.removePrefix(BOM)
        val firstLine = body.substringBefore('\n')
        val separator = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < body.length) {
            val ch = body[i]
            when {
                quoted && ch == '"' && body.getOrNull(i + 1) == '"' -> {
                    field.append('"')
                    i++
                }
                ch == '"' && (quoted || field.isEmpty()) -> quoted = !quoted
                quoted -> field.append(ch)
                ch == separator -> {
                    row += field.toString()
                    field.clear()
                }
                ch == '\n' || ch == '\r' -> {
                    if (ch == '\r' && body.getOrNull(i + 1) == '\n') i++
                    row += field.toString()
                    field.clear()
                    if (row.any { it.isNotBlank() }) rows += row
                    row = mutableListOf()
                }
                else -> field.append(ch)
            }
            i++
        }
        row += field.toString()
        if (row.any { it.isNotBlank() }) rows += row
        return rows
    }

    /** Semicolon separated with a byte order mark, which Excel opens correctly with diacritics. */
    fun write(rows: List<List<String>>): String = BOM + rows.joinToString("\r\n", postfix = "\r\n") { row ->
        row.joinToString(";") { value ->
            if (value.any {
                    it == ';' || it == '"' || it == '\n' || it == ','
                }
            ) {
                "\"${value.replace("\"", "\"\"")}\""
            } else {
                value
            }
        }
    }
}
