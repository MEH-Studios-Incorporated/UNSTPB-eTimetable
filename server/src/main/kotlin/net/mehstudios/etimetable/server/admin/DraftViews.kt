// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import io.ktor.http.encodeURLParameter
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import kotlinx.html.*
import net.mehstudios.etimetable.server.Services
import java.util.UUID

/** The query that opens the Ciorne tab of a list. */
internal const val DRAFTS_VIEW = "view=drafts"

private const val UNTITLED = "(fără titlu)"

/** Longer than any field the compose forms accept, so a draft never holds more than a post could. */
private const val MAX_DRAFT_FIELD = 20_000

internal val DraftKind.category: AuditCategory
    get() = if (this == DraftKind.ANNOUNCEMENT) AuditCategory.ANNOUNCEMENTS else AuditCategory.EVENTS

/** "anunțului Program", or "unui anunț fără titlu", for the log. */
internal fun draftSubject(kind: DraftKind, title: String): String {
    val announcement = kind == DraftKind.ANNOUNCEMENT
    return when {
        title.isBlank() -> if (announcement) "unui anunț fără titlu" else "unui eveniment fără titlu"
        announcement -> "anunțului $title"
        else -> "evenimentului $title"
    }
}

/** Why [form] cannot be kept as a draft, or null. A draft needs only a title or a text. */
internal fun draftProblem(form: Map<String, String>): String? = when {
    form["title"].isNullOrBlank() && form["body"].isNullOrBlank() ->
        "Scrie cel puțin un titlu sau un text înainte să salvezi ciorna."
    form.values.any { it.length > MAX_DRAFT_FIELD } -> "Un câmp are peste 20.000 de caractere."
    else -> null
}

/**
 * Saves the compose form as a draft (over [id] when it still exists) and returns to its page.
 * [warning] is shown there too, for a new picture that could not be kept.
 */
internal suspend fun RoutingContext.saveDraft(
    services: Services,
    admin: Admin,
    kind: DraftKind,
    list: String,
    id: UUID?,
    form: Map<String, String>,
    imageHash: String?,
    warning: String?,
) {
    val (saved, isNew) = services.composeDrafts.save(id, kind, form, imageHash, admin.email)
    if (isNew) {
        services.audit.record(admin, kind.category, "a salvat ciorna ${draftSubject(kind, form["title"].orEmpty())}")
    }
    var to = "$list/new?draft=$saved&msg=${"Ciorna a fost salvată.".encodeURLParameter()}"
    if (warning != null) to += "&err=${warning.encodeURLParameter()}"
    call.respondRedirect(to)
}

/** Where `?draft=` leads once that draft is gone: the Ciorne tab, saying why. */
internal suspend fun RoutingContext.draftGone(list: String) {
    val text = "Ciorna nu mai există: cineva a publicat-o sau a șters-o."
    call.respondRedirect("$list?$DRAFTS_VIEW&err=${text.encodeURLParameter()}")
}

/** The draft of [kind] that [text] names, if it still exists. */
internal suspend fun Services.draft(text: String?, kind: DraftKind): ComposeDraft? =
    uuid(text)?.let { composeDrafts.get(it, kind) }

/** `POST <list>/drafts/{id}/delete`; [path] is the list's path under /admin. */
internal fun Route.draftDeleteRoute(services: Services, kind: DraftKind, path: String) {
    action("$path/drafts/{id}/delete", services) { admin ->
        val deleted = uuid(call.parameters["id"])?.let { services.composeDrafts.delete(it, kind) }
        if (deleted != null) {
            services.audit.record(admin, kind.category, "a șters ciorna ${draftSubject(kind, deleted.title)}")
        }
        call.respondRedirect("/admin$path?$DRAFTS_VIEW&msg=${"Ciorna a fost ștearsă.".encodeURLParameter()}")
    }
}

/** The Ciorne tab beside a list's other tabs. */
internal fun NAV.draftsTab(list: String, count: Int, active: Boolean) {
    a(href = "$list?$DRAFTS_VIEW", classes = if (active) "active" else null) {
        +"Ciorne"
        span("n") { +count.toString() }
    }
}

/**
 * The drafts of a list: the columns in [heads] and [cells] first, then who wrote it, the last
 * change, Continuă and Șterge.
 */
internal fun FlowContent.draftTable(
    drafts: List<ComposeDraft>,
    list: String,
    heads: List<String>,
    cells: TR.(ComposeDraft) -> Unit,
) {
    table {
        thead {
            tr {
                heads.forEach { th { +it } }
                th { +"Scrisă de" }
                th { +"Ultima modificare" }
                th { }
            }
        }
        tbody {
            drafts.forEach { d ->
                tr {
                    cells(d)
                    td { +d.createdBy }
                    td("tnum") {
                        +shortTime(d.updatedAt)
                        if (d.updatedBy != d.createdBy) div("hint") { +"de ${d.updatedBy}" }
                    }
                    td("actions") {
                        a(href = "$list/new?draft=${d.id}", classes = "btn secondary") { +"Continuă" }
                        +" "
                        deleteDraftForm(list, d)
                    }
                }
            }
        }
    }
}

/** A draft's title as a link back to its compose page; "(fără titlu)" in grey when it has none. */
internal fun FlowContent.draftTitle(list: String, draft: ComposeDraft) {
    a(href = "$list/new?draft=${draft.id}", classes = if (draft.title.isEmpty()) "untitled" else null) {
        +draft.title.ifEmpty { UNTITLED }
    }
}

internal fun FlowContent.deleteDraftForm(list: String, draft: ComposeDraft, label: String = "Șterge") {
    form(action = "$list/drafts/${draft.id}/delete", method = FormMethod.post, classes = "inline") {
        onSubmit = "return confirm('Ștergi ciorna? Nu se poate anula.')"
        button(classes = "danger") { +label }
    }
}

/** " · Ciornă, salvată azi 10:21 de ana@...", after the list's name above a compose page. */
internal fun FlowContent.draftContext(draft: ComposeDraft) {
    +" · Ciornă, salvată ${shortTime(draft.updatedAt)} de ${draft.updatedBy}"
}

/** Keeps the draft's id in the compose form, so saving again changes the same draft. */
internal fun FlowContent.draftIdField(draft: ComposeDraft?) {
    if (draft != null) hiddenInput(name = "draft") { value = draft.id.toString() }
}

internal val DRAFT_STYLES = """
a.untitled { color: var(--muted); font-style: italic; }
.list-thumb .no-pic { flex: none; width: 96px; aspect-ratio: 16 / 9; border-radius: 4px; background: var(--alt); color: var(--muted);
  display: flex; align-items: center; justify-content: center; }
"""
