// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.encodeURLParameter
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.*
import net.mehstudios.etimetable.model.Announcement
import net.mehstudios.etimetable.schedule.FacultyTimeZone
import net.mehstudios.etimetable.server.Services
import kotlin.time.Clock
import kotlin.time.Instant

private const val LIST = "/admin/announcements"
private const val MAX_TITLE = 200
private const val MAX_BODY = 20_000
private const val MAX_AUTHOR = 100

internal fun Route.announcementRoutes(services: Services) {
    page("/announcements", services) { admin ->
        val paging = call.parameters.paging(services.announcements.count())
        val announcements = services.announcements.list(paging.offset, paging.size)
        val here = call.request.uri
        call.respondHtml {
            adminPage(
                "Anunțuri",
                admin,
                Section.ANNOUNCEMENTS,
                context = { +"Ajung la toți studenții, la următoarea sincronizare a aplicației." },
                actions = { a(href = "$LIST/new", classes = "btn") { +"Anunț nou" } },
            ) {
                notice(call.parameters["msg"])
                notice(call.parameters["err"], error = true)
                tableCard(footer = { pager(paging, LIST, call.request.queryParameters) }) {
                    if (announcements.isEmpty()) {
                        emptyState(
                            "Niciun anunț publicat",
                            "Anunțurile apar în fila Anunțuri a aplicației și ajung la toți studenții.",
                        ) { a(href = "$LIST/new", classes = "btn") { +"Scrie primul anunț" } }
                    } else {
                        announcementTable(announcements, here)
                    }
                }
            }
        }
    }
    page("/announcements/new", services) { admin ->
        call.respondHtml { composeAnnouncement(admin, null, Draft.empty()) }
    }
    action("/announcements", services) { admin ->
        val draft = Draft.from(call.receiveParameters())
        if (draft.preview || draft.error != null) {
            return@action call.respondHtml { composeAnnouncement(admin, null, draft) }
        }
        val announcement = with(draft) {
            services.announcements.publish(title, body, author, pinned = pinned)
        }
        services.audit.record(admin, AuditCategory.ANNOUNCEMENTS, "a publicat anunțul ${announcement.title}")
        // every message would fail, so the admin hears why instead of phones staying silent
        val drift = services.push.clockDrift()
        if (drift != null) {
            return@action call.respondRedirect("$LIST?err=${clockNotice(drift).encodeURLParameter()}")
        }
        services.push.announce(announcement)
        call.respondRedirect("$LIST?msg=${"Anunțul a fost publicat.".encodeURLParameter()}")
    }
    page("/announcements/{id}", services) { admin ->
        val announcement =
            services.announcements.byId(call.parameters["id"].orEmpty())
                ?: return@page call.respond(HttpStatusCode.NotFound)
        call.respondHtml { composeAnnouncement(admin, announcement, Draft.of(announcement)) }
    }
    action("/announcements/{id}", services) { admin ->
        val announcement =
            services.announcements.byId(call.parameters["id"].orEmpty())
                ?: return@action call.respond(HttpStatusCode.NotFound)
        val draft = Draft.from(call.receiveParameters())
        if (draft.preview || draft.error != null) {
            return@action call.respondHtml { composeAnnouncement(admin, announcement, draft) }
        }
        services.announcements.update(announcement.id, draft.title, draft.body, draft.author, draft.pinned)
        services.audit.record(admin, AuditCategory.ANNOUNCEMENTS, "a modificat anunțul ${draft.title}")
        call.respondRedirect("$LIST?msg=${"Anunțul a fost salvat.".encodeURLParameter()}")
    }
    action("/announcements/{id}/pin", services) { admin ->
        val id = call.parameters["id"].orEmpty()
        val form = call.receiveParameters()
        val pinned = form["pinned"] == "true"
        services.announcements.setPinned(id, pinned)
        services.announcements.byId(id)?.let {
            services.audit.record(
                admin,
                AuditCategory.ANNOUNCEMENTS,
                "${if (pinned) "a fixat" else "nu mai fixează"} anunțul ${it.title}",
            )
        }
        call.respondRedirect(backTo(form))
    }
    action("/announcements/{id}/delete", services) { admin ->
        val id = call.parameters["id"].orEmpty()
        services.announcements.byId(id)?.let {
            services.audit.record(admin, AuditCategory.ANNOUNCEMENTS, "a șters anunțul ${it.title}")
        }
        services.announcements.delete(id)
        call.respondRedirect("$LIST?msg=${"Anunțul a fost șters.".encodeURLParameter()}")
    }
}

/** The list page the person came from, so pinning keeps their page; never another site. */
private fun backTo(form: Parameters): String {
    val back = form["back"].orEmpty()
    return if (back == LIST || back.startsWith("$LIST?")) back else LIST
}

private fun FlowContent.announcementTable(announcements: List<Announcement>, here: String) {
    table {
        thead {
            tr {
                th { +"Titlu" }
                th { +"Semnat de" }
                th { +"Publicat" }
                th { +"Fixat" }
                th { }
            }
        }
        tbody {
            announcements.forEach { a ->
                tr {
                    td("excerpt") {
                        a(href = "$LIST/${a.id}") { +a.title }
                        div("hint clip") { +markdownPreview(a.body, 160) }
                    }
                    td { +a.author }
                    td("tnum") { +shortTime(a.publishedAt) }
                    td { if (a.pinned) span("tag pinned") { +"Fixat" } }
                    td("actions") {
                        a(href = "$LIST/${a.id}", classes = "btn secondary") { +"Editează" }
                        +" "
                        form(action = "$LIST/${a.id}/pin", method = FormMethod.post) {
                            hiddenInput(name = "pinned") { value = (!a.pinned).toString() }
                            hiddenInput(name = "back") { value = here }
                            button(classes = "secondary") { +(if (a.pinned) "Nu mai fixa" else "Fixează") }
                        }
                        +" "
                        form(action = "$LIST/${a.id}/delete", method = FormMethod.post) {
                            onSubmit = "return confirm('Ștergi anunțul? Dispare și din aplicații.')"
                            button(classes = "danger") { +"Șterge" }
                        }
                    }
                }
            }
        }
    }
}

/** An announcement being written: what the form sent, and whether to only show the preview. */
private class Draft(
    val title: String,
    val body: String,
    val author: String,
    val pinned: Boolean,
    val preview: Boolean,
    /** False for a page opened fresh, where nothing was sent yet and nothing is wrong. */
    val sent: Boolean = true,
) {
    val error: String? get() = when {
        !sent -> null
        title.length > MAX_TITLE || body.length > MAX_BODY || author.length > MAX_AUTHOR ->
            "Titlul poate avea 200 de caractere, textul 20.000 și semnătura 100."
        title.isBlank() || body.isBlank() || author.isBlank() -> "Titlul, textul și semnătura sunt obligatorii."
        else -> null
    }

    companion object {
        fun from(form: Parameters) = Draft(
            form["title"].orEmpty().trim(),
            form["body"].orEmpty().trim(),
            form["author"].orEmpty().trim(),
            form["pinned"] == "on",
            preview = form["preview"] != null,
        )

        fun of(a: Announcement) = Draft(a.title, a.body, a.author, a.pinned, preview = false, sent = false)

        fun empty() = Draft("", "", "", pinned = false, preview = false, sent = false)
    }
}

/** The compose page for a new announcement, or for [existing] when editing one. */
private fun HTML.composeAnnouncement(admin: Admin, existing: Announcement?, draft: Draft) {
    val action = if (existing == null) LIST else "$LIST/${existing.id}"
    adminPage(
        if (existing == null) "Anunț nou" else "Editează anunțul",
        admin,
        Section.ANNOUNCEMENTS,
        context = {
            a(href = LIST) { +"Anunțuri" }
            if (existing != null) +" · publicat ${shortTime(existing.publishedAt)}"
        },
        actions = existing?.let {
            {
                form(action = "$LIST/${it.id}/delete", method = FormMethod.post, classes = "inline") {
                    onSubmit = "return confirm('Ștergi anunțul? Dispare și din aplicații.')"
                    button(classes = "danger") { +"Șterge" }
                }
            }
        },
    ) {
        if (draft.error != null && !draft.preview) notice(draft.error, error = true)
        composeLayout(
            form = {
                form(action = action, method = FormMethod.post, classes = "card") {
                    attributes["data-compose"] = ""
                    div("fields") { announcementFields(draft) }
                    composeActions(if (existing == null) "Publică" else "Salvează", LIST)
                }
            },
            phone = {
                phonePreview(
                    dark = admin.shell?.theme == "dark",
                    card = { announcementCard(draft, existing?.publishedAt) },
                    page = { announcementOpened(draft, existing?.publishedAt) },
                    below = {
                        p {
                            +if (existing == null) {
                                "La publicare, toți studenții primesc o notificare cu titlul anunțului."
                            } else {
                                "Data publicării rămâne aceeași; aplicațiile primesc textul nou la următoarea " +
                                    "sincronizare, fără notificare."
                            }
                        }
                    },
                )
            },
        )
    }
}

private fun FlowContent.announcementFields(draft: Draft) {
    countedField("title", "Titlu", draft.title.length, MAX_TITLE) {
        textInput(name = "title") {
            id = "title"
            required = true
            maxLength = MAX_TITLE.toString()
            value = draft.title
        }
    }
    countedField("body", "Text", draft.body.length, MAX_BODY) {
        textArea {
            id = "body"
            name = "body"
            required = true
            rows = "10"
            +draft.body
        }
        p("hint") {
            +"Un rând liber începe un paragraf nou. **îngroșat**, *cursiv*, [link](https://faima.upb.ro). "
            +"Pentru liste, scrie fiecare element pe un rând nou, cu spațiu după cratimă: - primul element."
        }
    }
    field("author", "Semnat de") {
        textInput(name = "author") {
            id = "author"
            required = true
            maxLength = MAX_AUTHOR.toString()
            placeholder = "Secretariat"
            value = draft.author
        }
    }
    label("check") {
        checkBoxInput(name = "pinned") { checked = draft.pinned }
        +"Fixat sus în listă"
    }
}

/** "Marți, 10 noiembrie, 14:30 · Secretariat", as the app writes it under a post. */
private fun FlowContent.announcementMeta(draft: Draft, publishedAt: Instant?) {
    div("a-meta") {
        val at = (publishedAt ?: Clock.System.now()).toLocalDateTime(FacultyTimeZone)
        +"${dayLabel(at.date)}, ${at.time.toString().take(5)} · "
        span { previewText("author", draft.author, "Semnătura") }
    }
}

private fun FlowContent.pinnedLabel(draft: Draft) {
    div("app-pinned") {
        attributes["data-preview-show"] = "pinned"
        if (!draft.pinned) attributes["hidden"] = "hidden"
        unsafe { +svgIcon(ICON_PIN, 14) }
        +"Fixat"
    }
}

/** The post in the Anunțuri tab, unread, as a student sees it first. */
private fun FlowContent.announcementCard(draft: Draft, publishedAt: Instant?) {
    appBrandBar()
    div("app-heading") { +"Anunțuri" }
    div("scroller") {
        div("a-item") {
            span("unread")
            div("text") {
                pinnedLabel(draft)
                div("title") { previewText("title", draft.title, "Titlul anunțului") }
                div("excerpt") {
                    attributes["data-preview-plain"] = "body"
                    attributes["data-empty"] = "Primele rânduri ale textului"
                    val excerpt = markdownPreview(draft.body, 240)
                    if (excerpt.isBlank()) {
                        classes = classes + "is-empty"
                        +"Primele rânduri ale textului"
                    } else {
                        +excerpt
                    }
                }
                announcementMeta(draft, publishedAt)
            }
        }
    }
    appTabBar(Section.ANNOUNCEMENTS)
}

/** The announcement opened in full. */
private fun FlowContent.announcementOpened(draft: Draft, publishedAt: Instant?) {
    appPageBar("Anunț")
    div("scroller") {
        div("a-page") {
            pinnedLabel(draft)
            div("title") { previewText("title", draft.title, "Titlul anunțului") }
            announcementMeta(draft, publishedAt)
            hr()
            div("app-md") {
                attributes["data-preview-markdown"] = "body"
                markdown(draft.body)
            }
        }
    }
}
