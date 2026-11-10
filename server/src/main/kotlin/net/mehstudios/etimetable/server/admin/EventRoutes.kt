// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.encodeURLParameter
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.utils.io.toByteArray
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.*
import net.mehstudios.etimetable.model.Event
import net.mehstudios.etimetable.schedule.FacultyTimeZone
import net.mehstudios.etimetable.server.Services
import net.mehstudios.etimetable.server.events.EventFields
import net.mehstudios.etimetable.server.events.EventImages
import net.mehstudios.etimetable.server.events.EventRepository
import net.mehstudios.etimetable.server.events.ImageRejected

private const val LIST = "/admin/events"
private const val MAX_TITLE = 200
private const val MAX_BODY = 20_000

private val WEEKDAYS = listOf("Luni", "Marți", "Miercuri", "Joi", "Vineri", "Sâmbătă", "Duminică")
private val MONTHS_RO = listOf(
    "ianuarie", "februarie", "martie", "aprilie", "mai", "iunie",
    "iulie", "august", "septembrie", "octombrie", "noiembrie", "decembrie",
)

/** The app's backdrop on an event's page: square for a photo, up to 3:4 for a poster. */
private const val HERO_MIN_RATIO = 0.75f
private const val HERO_MAX_RATIO = 1f

/** "Joi, 20 noiembrie", as the app writes a day. */
internal fun dayLabel(d: LocalDate) =
    "${WEEKDAYS[d.dayOfWeek.isoDayNumber - 1]}, ${d.day} ${MONTHS_RO[d.month.ordinal]}"

/** "Joi, 20 noiembrie, 10:00 - 12:00 · Aula BN", as the app's card writes it in Romanian. */
internal fun whenAndWhere(event: Event, withPlace: Boolean = true): String = timeSpan(
    event.startsAt.toLocalDateTime(FacultyTimeZone),
    event.endsAt?.toLocalDateTime(FacultyTimeZone),
    event.place?.takeIf { withPlace },
)

private fun timeSpan(start: LocalDateTime, end: LocalDateTime?, place: String?): String {
    fun time(t: LocalTime) = t.toString().take(5)
    val span = when {
        end == null -> "${dayLabel(start.date)}, ${time(start.time)}"
        end.date == start.date -> "${dayLabel(start.date)}, ${time(start.time)} - ${time(end.time)}"
        else -> "${dayLabel(start.date)}, ${time(start.time)} - ${dayLabel(end.date)}, ${time(end.time)}"
    }
    return listOfNotNull(span, place).joinToString(" · ")
}

internal fun Route.eventRoutes(services: Services) {
    page("/events", services) { admin ->
        val showDrafts = call.parameters["view"] == "drafts"
        val past = !showDrafts && call.parameters["when"] == "past"
        val upcomingCount = services.events.count(past = false)
        val pastCount = services.events.count(past = true)
        val draftCount = services.composeDrafts.count(DraftKind.EVENT)
        val paging = call.parameters.paging(
            when {
                showDrafts -> draftCount
                past -> pastCount
                else -> upcomingCount
            },
        )
        val events = if (showDrafts) emptyList() else services.events.list(past, paging.offset, paging.size)
        val drafts = if (showDrafts) {
            services.composeDrafts.list(
                DraftKind.EVENT,
                paging.offset,
                paging.size,
            )
        } else {
            emptyList()
        }
        call.respondHtml {
            adminPage(
                "Evenimente",
                admin,
                Section.EVENTS,
                context = { +"Cardurile cu imagine din fila Evenimente a aplicației; le văd toți studenții." },
                actions = { a(href = "$LIST/new", classes = "btn") { +"Eveniment nou" } },
            ) {
                notice(call.parameters["msg"])
                notice(call.parameters["err"], error = true)
                tableCard(
                    filters = {
                        nav("tabs") {
                            attributes["aria-label"] = "Când"
                            a(href = LIST, classes = if (past || showDrafts) null else "active") {
                                +"Următoare"
                                span("n") { +upcomingCount.toString() }
                            }
                            a(href = "$LIST?when=past", classes = if (past) "active" else null) {
                                +"Trecute"
                                span("n") { +pastCount.toString() }
                            }
                            draftsTab(LIST, draftCount, showDrafts)
                        }
                    },
                    footer = { pager(paging, LIST, call.request.queryParameters) },
                ) {
                    if (showDrafts) {
                        if (drafts.isEmpty()) {
                            emptyState(
                                "Nicio ciornă",
                                "Salvează ciorna din pagina unui eveniment nou, ca să-l termini mai târziu, " +
                                    "tu sau un coleg.",
                            )
                        } else {
                            eventDraftTable(drafts)
                        }
                    } else if (events.isEmpty()) {
                        if (past) {
                            emptyState(
                                "Niciun eveniment trecut",
                                "Evenimentele ajung aici după ce se termină. Aplicația le mai arată " +
                                    "${EventRepository.KEPT_DAYS} de zile, la Trecute.",
                            )
                        } else {
                            emptyState(
                                "Niciun eveniment programat",
                                "Un eveniment are o imagine, o dată și, dacă vrei, un loc, o descriere și un link.",
                            ) { a(href = "$LIST/new", classes = "btn") { +"Adaugă un eveniment" } }
                        }
                    } else {
                        eventTable(events, past)
                    }
                }
                if (past && events.isNotEmpty()) {
                    p("hint") {
                        +"Aplicația le arată încă ${EventRepository.KEPT_DAYS} de zile după ce se termină, la Trecute."
                    }
                }
            }
        }
    }
    page("/events/new", services) { admin ->
        val saved = call.parameters["draft"]?.let {
            services.draft(it, DraftKind.EVENT) ?: return@page draftGone(LIST)
        }
        val draft = saved?.let {
            val size = it.imageHash?.let { hash -> services.events.imageSize(hash) }
            EventDraft.of(it, size?.let { (w, h) -> w.toFloat() / h })
        }
        call.respondHtml {
            composeEvent(
                admin,
                null,
                draft ?: EventDraft.empty(),
                saved,
                call.parameters["msg"],
                call.parameters["err"],
            )
        }
    }
    action("/events", services) { admin ->
        val draft = receiveDraft(services, existing = null)
        val saved = services.draft(draft.value("draft"), DraftKind.EVENT)
        if (draft.value("save") == "draft") {
            val kept = draft.kept()
            val problem = draftProblem(kept) ?: return@action saveDraft(
                services,
                admin,
                DraftKind.EVENT,
                LIST,
                saved?.id,
                kept,
                draft.imageHash,
                draft.imageError,
            )
            return@action call.respondHtml { composeEvent(admin, null, draft, saved, err = problem) }
        }
        if (draft.preview || draft.error != null) {
            return@action call.respondHtml { composeEvent(admin, null, draft, saved) }
        }
        val event = services.events.publish(draft.fields!!, draft.imageHash!!)
        val fromDraft = saved?.let { services.composeDrafts.delete(it.id, DraftKind.EVENT) } != null
        services.audit.record(
            admin,
            AuditCategory.EVENTS,
            "a publicat evenimentul ${event.title}" + if (fromDraft) " dintr-o ciornă" else "",
        )
        if (draft.notify) {
            // every message would fail, so the admin hears why instead of phones staying silent
            val drift = services.push.clockDrift()
            if (drift != null) {
                return@action call.respondRedirect("$LIST?err=${clockNotice(drift).encodeURLParameter()}")
            }
            services.push.announce(event, whenAndWhere(event))
        }
        call.respondRedirect("$LIST?msg=${"Evenimentul a fost publicat.".encodeURLParameter()}")
    }
    page("/events/{id}", services) { admin ->
        val event = services.events.byId(call.parameters["id"].orEmpty())
            ?: return@page call.respond(HttpStatusCode.NotFound)
        call.respondHtml { composeEvent(admin, event, EventDraft.of(event), null) }
    }
    action("/events/{id}", services) { admin ->
        val event = services.events.byId(call.parameters["id"].orEmpty())
            ?: return@action call.respond(HttpStatusCode.NotFound)
        val draft = receiveDraft(services, existing = event)
        if (draft.preview || draft.error != null) {
            return@action call.respondHtml { composeEvent(admin, event, draft, null) }
        }
        services.events.update(event.id, draft.fields!!, draft.imageHash!!)
        services.audit.record(admin, AuditCategory.EVENTS, "a modificat evenimentul ${draft.fields!!.title}")
        call.respondRedirect("$LIST?msg=${"Evenimentul a fost salvat.".encodeURLParameter()}")
    }
    action("/events/{id}/delete", services) { admin ->
        val id = call.parameters["id"].orEmpty()
        services.events.byId(id)?.let {
            services.audit.record(admin, AuditCategory.EVENTS, "a șters evenimentul ${it.title}")
        }
        services.events.delete(id)
        call.respondRedirect("$LIST?msg=${"Evenimentul a fost șters.".encodeURLParameter()}")
    }
    draftDeleteRoute(services, DraftKind.EVENT, "/events")
}

private fun FlowContent.eventDraftTable(drafts: List<ComposeDraft>) {
    draftTable(drafts, LIST, listOf("Eveniment", "Când")) { d ->
        val draft = EventDraft.of(d, null)
        td {
            div("list-thumb") {
                if (d.imageHash != null) {
                    img(src = EventRepository.imagePath(d.imageHash), alt = "") { attributes["loading"] = "lazy" }
                } else {
                    div("no-pic") { unsafe { +svgIcon(ICON_PICTURE, 20) } }
                }
                div {
                    draftTitle(LIST, d)
                    draft.value("place").takeIf { it.isNotBlank() }?.let { div("hint") { +it } }
                }
            }
        }
        td("tnum") { +(draft.timeLine(withPlace = false) ?: "-") }
    }
}

private fun FlowContent.eventTable(events: List<Event>, past: Boolean) {
    table {
        thead {
            tr {
                th { +"Eveniment" }
                th { +"Când" }
                th { +"Publicat" }
                th { }
            }
        }
        tbody {
            events.forEach { e ->
                tr {
                    td {
                        div(if (past) "list-thumb past" else "list-thumb") {
                            img(src = e.image, alt = "") { attributes["loading"] = "lazy" }
                            div {
                                a(href = "$LIST/${e.id}") { +e.title }
                                e.place?.let { div("hint") { +it } }
                            }
                        }
                    }
                    td("tnum") { +whenAndWhere(e, withPlace = false) }
                    td("tnum") { +shortTime(e.publishedAt) }
                    td("actions") {
                        a(href = "$LIST/${e.id}", classes = "btn secondary") { +"Editează" }
                        +" "
                        form(action = "$LIST/${e.id}/delete", method = FormMethod.post) {
                            onSubmit = "return confirm('Ștergi evenimentul? Dispare și din aplicații.')"
                            button(classes = "danger") { +"Șterge" }
                        }
                    }
                }
            }
        }
    }
}

/**
 * An event being written. The picture is kept on the server as soon as it is uploaded and
 * travels in the form as [imageHash], since a browser empties a file field on every new page.
 */
private class EventDraft(
    val form: Map<String, String>,
    val imageHash: String?,
    val imageRatio: Float?,
    val notify: Boolean,
    val preview: Boolean,
    val error: String?,
    /** A new picture that could not be kept; a draft is saved without it. */
    val imageError: String? = null,
) {
    val fields: EventFields? get() = if (error == null) parse(form).getOrNull() else null

    fun value(name: String) = form[name].orEmpty()

    /** What a saved draft keeps: the fields that are filled in, the picture apart. */
    fun kept(): Map<String, String> = form.filterKeys { it in DRAFT_FIELDS }.filterValues { it.isNotEmpty() }

    /** The date line from whatever the form holds so far, or null until it has a day and a start. */
    fun timeLine(withPlace: Boolean): String? {
        val date = runCatching { LocalDate.parse(value("start_date").trim()) }.getOrNull() ?: return null
        val time = runCatching { LocalTime.parse(value("start_time").trim()) }.getOrNull() ?: return null
        val endDate = runCatching { LocalDate.parse(value("end_date").trim()) }.getOrNull() ?: date
        val end = runCatching {
            LocalTime.parse(value("end_time").trim())
        }.getOrNull()?.let { LocalDateTime(endDate, it) }
        return timeSpan(LocalDateTime(date, time), end, value("place").trim().ifEmpty { null }?.takeIf { withPlace })
    }

    companion object {
        private val DRAFT_FIELDS =
            setOf("title", "body", "place", "link", "start_date", "start_time", "end_date", "end_time", "notify")

        fun empty() = EventDraft(emptyMap(), null, null, notify = true, preview = false, error = null)

        fun of(d: ComposeDraft, imageRatio: Float?) = EventDraft(
            d.form,
            d.imageHash,
            imageRatio,
            notify = d.form["notify"] == "on",
            preview = false,
            error = null,
        )

        fun of(e: Event): EventDraft {
            val start = e.startsAt.toLocalDateTime(FacultyTimeZone)
            val end = e.endsAt?.toLocalDateTime(FacultyTimeZone)
            val form = mapOf(
                "title" to e.title,
                "body" to e.body,
                "place" to e.place.orEmpty(),
                "link" to e.link.orEmpty(),
                "start_date" to start.date.toString(),
                "start_time" to start.time.toString().take(5),
                "end_date" to (end?.date?.takeIf { it != start.date }?.toString() ?: ""),
                "end_time" to (end?.time?.toString()?.take(5) ?: ""),
            )
            return EventDraft(
                form,
                e.image.substringAfterLast('/').removeSuffix(".jpg"),
                e.imageRatio,
                false,
                false,
                null,
            )
        }

        /** The text fields as an event, or the first thing wrong with them. */
        fun parse(form: Map<String, String>): Result<EventFields> = runCatching {
            fun text(name: String) = form[name].orEmpty().trim()
            val title = text("title")
            val link = text("link").ifEmpty { null }
            when {
                title.isEmpty() -> fail("Titlul este obligatoriu.")
                title.length > MAX_TITLE -> fail("Titlul are peste 200 de caractere.")
                text("place").length > 200 -> fail("Locul are peste 200 de caractere.")
                text("body").length > MAX_BODY -> fail("Descrierea are peste 20000 de caractere.")
                link != null && (link.length > 500 || !Regex("^https?://\\S+$").matches(link)) ->
                    fail("Linkul trebuie să înceapă cu https:// și să nu aibă spații.")
            }
            val startDate = runCatching { LocalDate.parse(text("start_date")) }.getOrNull()
                ?: fail("Alege data evenimentului.")
            val startTime = runCatching { LocalTime.parse(text("start_time")) }.getOrNull()
                ?: fail("Alege ora de început.")
            val endDate = text("end_date").ifEmpty { null }?.let {
                runCatching { LocalDate.parse(it) }.getOrNull() ?: fail("Data de sfârșit nu este validă.")
            }
            val endTime = text("end_time").ifEmpty { null }?.let {
                runCatching { LocalTime.parse(it) }.getOrNull() ?: fail("Ora de sfârșit nu este validă.")
            }
            val startsAt = LocalDateTime(startDate, startTime).toInstant(FacultyTimeZone)
            val endsAt = when {
                endTime != null -> LocalDateTime(endDate ?: startDate, endTime).toInstant(FacultyTimeZone)
                endDate != null -> fail("Alege și ora de sfârșit.")
                else -> null
            }
            if (endsAt != null && endsAt <= startsAt) fail("Sfârșitul trebuie să fie după început.")
            EventFields(title, text("body"), text("place").ifEmpty { null }, link, startsAt, endsAt)
        }

        private fun fail(message: String): Nothing = throw IllegalArgumentException(message)
    }
}

/** Reads the multipart form; a new picture is checked, re-encoded and kept right away. */
private suspend fun RoutingContext.receiveDraft(services: Services, existing: Event?): EventDraft {
    val form = mutableMapOf<String, String>()
    var upload: ByteArray? = null
    var tooBig = false
    call.receiveMultipart(formFieldLimit = EventImages.MAX_UPLOAD_BYTES + 1L).forEachPart { part ->
        when (part) {
            is PartData.FormItem -> form[part.name.orEmpty()] = part.value
            is PartData.FileItem -> if (part.name == "image" && !part.originalFileName.isNullOrEmpty()) {
                val bytes = part.provider().toByteArray()
                if (bytes.size > EventImages.MAX_UPLOAD_BYTES) tooBig = true else upload = bytes
            }
            else -> Unit
        }
        part.dispose()
    }
    var imageError: String? = if (tooBig) "Imaginea are peste 8 MB." else null
    // an edit without a new picture keeps the event's own
    var hash = form["image_hash"]?.takeIf { Regex("[0-9a-f]{64}").matches(it) }
        ?: existing?.image?.substringAfterLast('/')?.removeSuffix(".jpg")
    upload?.let { bytes ->
        try {
            hash = services.events.stage(EventImages.process(bytes))
        } catch (e: ImageRejected) {
            imageError = e.message
        }
    }
    val size = hash?.let { services.events.imageSize(it) }
    if (size == null) hash = null
    val preview = form["preview"] != null
    val error = imageError
        ?: EventDraft.parse(form).exceptionOrNull()?.message
        ?: if (hash == null) "Alege o imagine pentru eveniment." else null
    return EventDraft(
        form,
        hash,
        size?.let { it.first.toFloat() / it.second },
        notify = existing == null && form["notify"] == "on",
        preview = preview,
        error = error,
        imageError = imageError,
    )
}

/** The compose page for a new event, or for [existing] when editing one. */
private fun HTML.composeEvent(
    admin: Admin,
    existing: Event?,
    draft: EventDraft,
    saved: ComposeDraft?,
    msg: String? = null,
    err: String? = null,
) {
    val isNew = existing == null
    adminPage(
        if (isNew) "Eveniment nou" else "Editează evenimentul",
        admin,
        Section.EVENTS,
        context = {
            a(href = LIST) { +"Evenimente" }
            if (existing != null) +" · ${whenAndWhere(existing)}"
            if (saved != null) draftContext(saved)
        },
        actions = when {
            existing != null -> {
                {
                    form(action = "$LIST/${existing.id}/delete", method = FormMethod.post, classes = "inline") {
                        onSubmit = "return confirm('Ștergi evenimentul? Dispare și din aplicații.')"
                        button(classes = "danger") { +"Șterge" }
                    }
                }
            }
            saved != null -> {
                { deleteDraftForm(LIST, saved, "Șterge ciorna") }
            }
            else -> null
        },
    ) {
        notice(msg)
        notice(err ?: draft.error?.takeUnless { draft.preview }, error = true)
        composeLayout(
            form = {
                form(
                    action = existing?.let { "$LIST/${it.id}" } ?: LIST,
                    method = FormMethod.post,
                    encType = FormEncType.multipartFormData,
                    classes = "card",
                ) {
                    attributes["data-compose"] = ""
                    draftIdField(saved)
                    div("fields") { eventFields(draft, isNew) }
                    composeActions(if (isNew) "Publică" else "Salvează", LIST, saveDraft = isNew)
                }
            },
            phone = {
                phonePreview(
                    dark = admin.shell?.theme == "dark",
                    card = { eventCard(draft) },
                    page = { eventOpened(draft) },
                    below = {
                        if (draft.preview && draft.error != null) p { +"Înainte de publicare: ${draft.error}" }
                        if (isNew) {
                            p {
                                attributes["data-preview-show"] = "notify"
                                if (!draft.notify) attributes["hidden"] = "hidden"
                                +"La publicare, studenții primesc o notificare cu titlul și data evenimentului."
                            }
                            p {
                                attributes["data-preview-hide"] = "notify"
                                if (draft.notify) attributes["hidden"] = "hidden"
                                +"Fără notificare. Evenimentul apare în aplicație la următoarea sincronizare."
                            }
                        } else {
                            p { +"Aplicațiile primesc schimbările la următoarea sincronizare, fără notificare." }
                        }
                    },
                )
            },
        )
    }
}

private fun FlowContent.eventFields(draft: EventDraft, isNew: Boolean) {
    countedField("title", "Titlu", draft.value("title").length, MAX_TITLE) {
        textInput(name = "title") {
            id = "title"
            required = true
            maxLength = MAX_TITLE.toString()
            placeholder = "Ziua Carierei FAIMA"
            value = draft.value("title")
        }
        p("hint") { +"Apare sub imagine, pe card, pe un singur rând." }
    }
    div("row3") {
        field("start_date", "Data") {
            dateInput(name = "start_date") {
                id = "start_date"
                required = true
                value = draft.value("start_date")
            }
        }
        field("start_time", "Începe la") {
            timeInput(name = "start_time") {
                id = "start_time"
                required = true
                value = draft.value("start_time")
            }
        }
        field("end_time", "Se termină la (opțional)") {
            timeInput(name = "end_time") {
                id = "end_time"
                value = draft.value("end_time")
            }
        }
    }
    field("end_date", "Se termină în altă zi (opțional)") {
        dateInput(name = "end_date") {
            id = "end_date"
            style = "max-width: 220px"
            value = draft.value("end_date")
        }
    }
    field("place", "Locul (opțional)") {
        textInput(name = "place") {
            id = "place"
            maxLength = "200"
            placeholder = "Aula BN"
            value = draft.value("place")
        }
    }
    div("field") {
        label {
            htmlFor = "image"
            +(if (draft.imageHash == null) "Imagine" else "Imagine nouă (opțional)")
        }
        div("image-pick") {
            div("thumb") {
                eventPicture(draft, empty = { unsafe { +svgIcon(ICON_PICTURE, 22) } })
            }
            div("pick") {
                if (draft.imageHash != null) hiddenInput(name = "image_hash") { value = draft.imageHash }
                fileInput(name = "image") {
                    id = "image"
                    accept = "image/jpeg,image/png"
                    if (draft.imageHash == null) required = true
                }
                p("hint") {
                    +"JPEG sau PNG, cel mult 8 MB. Se micșorează la 1600 px. Cardul o taie la 16:9, "
                    +"pagina evenimentului o arată mai mare; ține textul unui afiș spre mijloc."
                }
            }
        }
    }
    countedField("body", "Descriere (opțional)", draft.value("body").length, MAX_BODY) {
        textArea {
            id = "body"
            name = "body"
            rows = "8"
            +draft.value("body")
        }
        p("hint") {
            +"Un rând liber începe un paragraf nou. **îngroșat**, *cursiv*, [link](https://faima.upb.ro), "
            +"liste cu - la început de rând."
        }
    }
    field("link", "Link (opțional)") {
        urlInput(name = "link") {
            id = "link"
            placeholder = "https://faima.upb.ro/..."
            value = draft.value("link")
        }
        p("hint") { +"Înscriere, program sau pagina evenimentului; aplicația îl arată ca buton." }
    }
    if (isNew) {
        label("check notify") {
            checkBoxInput(name = "notify") { checked = draft.notify }
            span {
                +"Trimite notificare pe telefoane"
                span("hint") {
                    +"Ajunge la toți studenții care au notificările pornite. Evenimentele nu se filtrează pe grupe."
                }
            }
        }
    }
}

/** The kept picture, or [empty] until there is one; admin.js swaps in a newly chosen file. */
private fun FlowContent.eventPicture(draft: EventDraft, empty: DIV.() -> Unit) {
    img(src = draft.imageHash?.let(EventRepository::imagePath), alt = "") {
        attributes["data-preview-image"] = ""
        if (draft.imageHash == null) attributes["hidden"] = "hidden"
    }
    div("e-none") {
        attributes["data-preview-image-empty"] = ""
        if (draft.imageHash != null) attributes["hidden"] = "hidden"
        empty()
    }
}

private fun DIV.pictureHint() {
    unsafe { +svgIcon(ICON_PICTURE, 24) }
    span { +"Imaginea evenimentului" }
}

/** The card in the Evenimente tab: the picture cut to 16:9, the title, the day and place. */
private fun FlowContent.eventCard(draft: EventDraft) {
    appBrandBar()
    div("app-heading") { +"Evenimente" }
    div("scroller") {
        div("e-list") {
            div("e-card") {
                div("e-pic") { eventPicture(draft) { pictureHint() } }
                div("text") {
                    div("title") { previewText("title", draft.value("title"), "Titlul evenimentului") }
                    div("when") { previewWhen(draft, withPlace = true) }
                }
            }
        }
    }
    appTabBar(Section.EVENTS)
}

/** The event's page: the picture fading into the page, then the title rising into it. */
private fun FlowContent.eventOpened(draft: EventDraft) {
    div("scroller") {
        val ratio = (draft.imageRatio ?: 1f).coerceIn(HERO_MIN_RATIO, HERO_MAX_RATIO)
        div("e-hero") {
            style = "aspect-ratio: $ratio"
            attributes["data-preview-hero"] = ""
            eventPicture(draft) { pictureHint() }
            div("fade")
            span("app-back") { unsafe { +svgIcon(ICON_PREV, 18) } }
        }
        div("e-page") {
            div("title") { previewText("title", draft.value("title"), "Titlul evenimentului") }
            div("when") { previewWhen(draft, withPlace = false) }
            div("place") {
                attributes["data-preview-show"] = "place"
                if (draft.value("place").isBlank()) attributes["hidden"] = "hidden"
                previewText("place", draft.value("place").trim(), null)
            }
            div("app-md") {
                attributes["data-preview-markdown"] = "body"
                if (draft.value("body").isBlank()) attributes["hidden"] = "hidden"
                markdown(draft.value("body"))
            }
            span("e-link") {
                attributes["data-preview-show"] = "link"
                if (draft.value("link").isBlank()) attributes["hidden"] = "hidden"
                +"Deschide linkul"
            }
        }
    }
}

private fun HtmlBlockTag.previewWhen(draft: EventDraft, withPlace: Boolean) {
    attributes["data-preview-when"] = if (withPlace) "full" else "time"
    attributes["data-empty"] = "Alege data și ora"
    val line = draft.timeLine(withPlace)
    if (line == null) {
        classes = classes + "is-empty"
        +"Alege data și ora"
    } else {
        +line
    }
}
