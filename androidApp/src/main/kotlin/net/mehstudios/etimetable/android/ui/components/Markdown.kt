// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

// The Markdown the dashboard documents (docs/admin.md): paragraphs, "-" lists,
// **bold**, *italic* and [links](https://...). Anything else shows as written.
private val INLINE = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|\[([^\]]+)]\((https?://[^)\s]+)\)""")
private val LIST_ITEM = Regex("""^\s*[-*]\s+""")

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val linkColor = MaterialTheme.colorScheme.primary
    val style = MaterialTheme.typography.bodyLarge
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks(text).forEach { block ->
            if (block.list) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    block.lines.forEach { line ->
                        Row {
                            Text("•", style = style, modifier = Modifier.width(18.dp).padding(start = 4.dp))
                            Text(inline(line, linkColor), style = style)
                        }
                    }
                }
            } else {
                Text(inline(block.lines.joinToString(" "), linkColor), style = style)
            }
        }
    }
}

private class Block(val list: Boolean, val lines: MutableList<String> = mutableListOf())

/** Paragraphs and lists; a list may follow a line of text without a blank line between. */
private fun blocks(text: String): List<Block> {
    val result = mutableListOf<Block>()
    var current: Block? = null
    for (raw in text.trim().lines()) {
        if (raw.isBlank()) {
            current = null
            continue
        }
        val item = LIST_ITEM.containsMatchIn(raw)
        if (current == null || current.list != item) current = Block(item).also { result += it }
        current.lines += if (item) raw.replace(LIST_ITEM, "") else raw.trim()
    }
    return result
}

/** The text without Markdown marks, for one- or two-line previews. */
fun markdownPreview(text: String): String =
    INLINE.replace(text) { it.groupValues[1].ifEmpty { it.groupValues[2] }.ifEmpty { it.groupValues[3] } }
        .lines().joinToString(" ") { it.replace(LIST_ITEM, "").trim() }.replace(Regex("""\s+"""), " ").trim()

private fun inline(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var at = 0
    for (match in INLINE.findAll(text)) {
        append(text.substring(at, match.range.first))
        val (bold, italic, label, url) = match.destructured
        when {
            bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }
            italic.isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
            else -> withLink(
                LinkAnnotation.Url(
                    url,
                    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                ),
            ) {
                append(label)
            }
        }
        at = match.range.last + 1
    }
    append(text.substring(at))
}
