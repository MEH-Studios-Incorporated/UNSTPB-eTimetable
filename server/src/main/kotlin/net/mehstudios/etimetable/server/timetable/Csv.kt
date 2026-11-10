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

    fun parse(text: String): List<List<String>> = records(text).map { it.cells }

    /** A row of the file and where it sits in the text, from its first character to its line break. */
    class Record(val cells: List<String>, val start: Int, val end: Int)

    /** The separator [parse] uses for [text]: the one the header row uses most. */
    fun separator(text: String): Char {
        val firstLine = text.removePrefix(BOM).substringBefore('\n')
        return if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
    }

    /** The rows [parse] gives, with their place in [text]; blank rows are skipped as there. */
    fun records(text: String): List<Record> {
        val offset = if (text.startsWith(BOM)) BOM.length else 0
        val body = text.substring(offset)
        val separator = separator(text)
        val rows = mutableListOf<Record>()
        var row = mutableListOf<String>()
        var start = 0
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
                    val end = i
                    if (ch == '\r' && body.getOrNull(i + 1) == '\n') i++
                    row += field.toString()
                    field.clear()
                    if (row.any { it.isNotBlank() }) rows += Record(row, start + offset, end + offset)
                    row = mutableListOf()
                    start = i + 1
                }
                else -> field.append(ch)
            }
            i++
        }
        row += field.toString()
        if (row.any { it.isNotBlank() }) rows += Record(row, start + offset, body.length + offset)
        return rows
    }

    /**
     * [text] with its row number [index] (as [parse] counts them) replaced by [cells], in
     * the file's own separator. The rest of the text stays as it was.
     */
    fun replaceRow(text: String, index: Int, cells: List<String>): String {
        val record = records(text)[index]
        val line = encode(cells, separator(text))
        return text.substring(0, record.start) + line + text.substring(record.end)
    }

    private fun encode(row: List<String>, separator: Char) = row.joinToString(separator.toString()) { value ->
        if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' || it == ',' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    /** Semicolon separated with a byte order mark, which Excel opens correctly with diacritics. */
    fun write(rows: List<List<String>>): String = BOM + rows.joinToString("\r\n", postfix = "\r\n") { encode(it, ';') }
}
