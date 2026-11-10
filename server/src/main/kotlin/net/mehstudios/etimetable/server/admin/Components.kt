// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import io.ktor.http.Parameters
import io.ktor.http.encodeURLParameter
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.hiddenInput
import kotlinx.html.id
import kotlinx.html.label
import kotlinx.html.nav
import kotlinx.html.noScript
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.unsafe

/** The rows-per-page choices every dashboard table offers. */
internal val PAGE_SIZES = listOf(10, 25, 50)

/** One page of a table: [page] counts from 1, [total] is the number of rows after filtering. */
internal data class Paging(private val requested: Int, val size: Int, val total: Int) {
    val pages: Int get() = maxOf(1, (total + size - 1) / size)
    val page: Int get() = requested.coerceIn(1, pages)
    val offset: Int get() = (page - 1) * size

    fun <T> slice(rows: List<T>): List<T> = rows.drop(offset).take(size)
}

/** Reads `page` and `size` from the query string; anything odd falls back to page 1 of 10. */
internal fun Parameters.paging(total: Int, defaultSize: Int = PAGE_SIZES.first()): Paging = Paging(
    requested = this["page"]?.toIntOrNull() ?: 1,
    size = this["size"]?.toIntOrNull()?.takeIf { it in PAGE_SIZES } ?: defaultSize,
    total = total,
)

/** [path] with the current filters in [query], replacing or dropping [changes] (null drops). */
internal fun linkTo(path: String, query: Parameters, changes: Map<String, String?>): String {
    val pairs = query.entries()
        .filter { it.key !in changes && it.key !in TRANSIENT }
        .flatMap { (k, vs) -> vs.filter { it.isNotEmpty() }.map { k to it } } +
        changes.mapNotNull { (k, v) -> v?.let { k to it } }
    if (pairs.isEmpty()) return path
    return path + "?" + pairs.joinToString("&") { (k, v) -> "${k.encodeURLParameter()}=${v.encodeURLParameter()}" }
}

/** Messages shown once after a redirect; links must not carry them along. */
private val TRANSIENT = setOf("msg", "err")

/**
 * The footer of a paged table: "11-20 din 243", the rows-per-page choice and the page links.
 * Links keep every filter in [query].
 */
internal fun FlowContent.pager(paging: Paging, path: String, query: Parameters) {
    if (paging.total == 0) return
    div("pager") {
        span("range tnum") {
            val last = minOf(paging.offset + paging.size, paging.total)
            +"${paging.offset + 1}-$last din ${paging.total}"
        }
        if (paging.total > PAGE_SIZES.first()) sizeChoice(paging, path, query)
        if (paging.pages > 1) pageLinks(paging, path, query)
    }
}

private fun FlowContent.sizeChoice(paging: Paging, path: String, query: Parameters) {
    form(action = path, method = FormMethod.get, classes = "size") {
        query.entries().filter { it.key !in setOf("page", "size") && it.key !in TRANSIENT }.forEach { (k, vs) ->
            vs.forEach { v -> hiddenInput(name = k) { value = v } }
        }
        label {
            htmlFor = "page-size"
            +"Pe pagină"
        }
        select {
            id = "page-size"
            name = "size"
            attributes["data-autosubmit"] = ""
            PAGE_SIZES.forEach { n ->
                option {
                    value = n.toString()
                    selected = n == paging.size
                    +n.toString()
                }
            }
        }
        noScript { button(classes = "secondary small") { +"Aplică" } }
    }
}

private fun FlowContent.pageLinks(paging: Paging, path: String, query: Parameters) {
    nav("pages") {
        attributes["aria-label"] = "Pagini"
        pageLink(paging, path, query, paging.page - 1, "Pagina precedentă", icon = ICON_PREV)
        pageNumbers(paging).forEach { n ->
            if (n == null) {
                span("gap") { +"..." }
            } else if (n == paging.page) {
                span("current tnum") {
                    attributes["aria-current"] = "page"
                    +n.toString()
                }
            } else {
                a(href = linkTo(path, query, mapOf("page" to n.toString())), classes = "tnum") { +n.toString() }
            }
        }
        pageLink(paging, path, query, paging.page + 1, "Pagina următoare", icon = ICON_NEXT)
    }
}

private fun FlowContent.pageLink(
    paging: Paging,
    path: String,
    query: Parameters,
    target: Int,
    label: String,
    icon: String,
) {
    if (target < 1 || target > paging.pages) {
        span("step disabled") {
            attributes["aria-hidden"] = "true"
            unsafe { +svgIcon(icon) }
        }
    } else {
        a(href = linkTo(path, query, mapOf("page" to target.toString())), classes = "step") {
            attributes["aria-label"] = label
            attributes["title"] = label
            unsafe { +svgIcon(icon) }
        }
    }
}

/** 1 ... 4 5 6 ... 20: the first, the last and the neighbours of the current page. */
internal fun pageNumbers(paging: Paging): List<Int?> {
    val wanted = (listOf(1, paging.pages) + (paging.page - 1..paging.page + 1))
        .filter { it in 1..paging.pages }.distinct().sorted()
    val out = mutableListOf<Int?>()
    wanted.forEachIndexed { i, n ->
        if (i > 0 && n - wanted[i - 1] > 1) out += if (n - wanted[i - 1] == 2) n - 1 else null
        out += n
    }
    return out
}

/** What a table shows instead of rows: a short title and what to do next. */
internal fun FlowContent.emptyState(title: String, text: String? = null, action: (FlowContent.() -> Unit)? = null) {
    div("empty") {
        strong { +title }
        if (text != null) p { +text }
        if (action != null) div("form-actions") { action() }
    }
}

/** A table inside a card, with an optional filter bar above it and a pager below. */
internal fun FlowContent.tableCard(
    filters: (FlowContent.() -> Unit)? = null,
    footer: (FlowContent.() -> Unit)? = null,
    rows: FlowContent.() -> Unit,
) {
    div("table-card") {
        if (filters != null) div("table-bar") { filters() }
        div("scroll") { rows() }
        if (footer != null) footer()
    }
}

/** Inline stroke icons, 24 px grid, drawn in the text colour. */
internal fun svgIcon(path: String, size: Int = 18) =
    """<svg width="$size" height="$size" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" """ +
        """stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="$path"/></svg>"""

internal const val ICON_PREV = "M15 6l-6 6 6 6"
internal const val ICON_NEXT = "M9 6l6 6-6 6"
internal const val ICON_MOON = "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"
internal const val ICON_SEARCH = "M11 17.5a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13zM16 16l4 4"
