// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import kotlinx.html.FlowContent
import kotlinx.html.FlowOrPhrasingContent
import kotlinx.html.a
import kotlinx.html.em
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.strong
import kotlinx.html.ul

// The same subset the app draws (androidApp ui/components/Markdown.kt), so the
// preview shows what students will see: paragraphs, "-" lists, **bold**,
// *italic* and [links](https://...). Anything else shows as written.
private val INLINE = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|\[([^\]]+)]\((https?://[^)\s]+)\)""")
private val LIST_ITEM = Regex("""^\s*[-*]\s+""")

fun FlowContent.markdown(text: String) {
    var list: MutableList<String>? = null
    var paragraph: MutableList<String>? = null
    val blocks = mutableListOf<Pair<Boolean, List<String>>>()
    for (raw in text.trim().lines()) {
        when {
            raw.isBlank() -> {
                list = null
                paragraph = null
            }
            LIST_ITEM.containsMatchIn(raw) -> {
                paragraph = null
                if (list == null) list = mutableListOf<String>().also { blocks += true to it }
                list += raw.replace(LIST_ITEM, "")
            }
            else -> {
                list = null
                if (paragraph == null) paragraph = mutableListOf<String>().also { blocks += false to it }
                paragraph += raw.trim()
            }
        }
    }
    blocks.forEach { (isList, lines) ->
        if (isList) ul { lines.forEach { li { inline(it) } } } else p { inline(lines.joinToString(" ")) }
    }
}

private fun FlowOrPhrasingContent.inline(text: String) {
    var at = 0
    for (match in INLINE.findAll(text)) {
        +text.substring(at, match.range.first)
        val (bold, italic, label, url) = match.destructured
        when {
            bold.isNotEmpty() -> strong { +bold }
            italic.isNotEmpty() -> em { +italic }
            else -> a(href = url) {
                attributes["rel"] = "noopener"
                +label
            }
        }
        at = match.range.last + 1
    }
    +text.substring(at)
}
