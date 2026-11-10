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
import kotlin.time.Clock

private val WEEKDAYS = listOf("Luni", "Marți", "Miercuri", "Joi", "Vineri", "Sâmbătă", "Duminică")
private val MONTHS_RO = listOf(
    "ianuarie", "februarie", "martie", "aprilie", "mai", "iunie",
    "iulie", "august", "septembrie", "octombrie", "noiembrie", "decembrie",
)

/** "Joi, 20 noiembrie, 10:00 - 12:00 · Aula BN", as the app's card writes it in Romanian. */
internal fun whenAndWhere(event: Event): String {
    val start = event.startsAt.toLocalDateTime(FacultyTimeZone)
    val end = event.endsAt?.toLocalDateTime(FacultyTimeZone)
    fun day(d: LocalDate) = "${WEEKDAYS[d.dayOfWeek.isoDayNumber - 1]}, ${d.day} ${MONTHS_RO[d.month.ordinal]}"
    fun time(t: LocalTime) = t.toString().take(5)
    val span = when {
        end == null -> "${day(start.date)}, ${time(start.time)}"
        end.date == start.date -> "${day(start.date)}, ${time(start.time)} - ${time(end.time)}"
        else -> "${day(start.date)}, ${time(start.time)} - ${day(end.date)}, ${time(end.time)}"
    }
    return listOfNotNull(span, event.place).joinToString(" · ")
}

internal fun Route.eventRoutes(services: Services) {
    page("/events", services) { admin ->
        val events = services.events.all()
        call.respondHtml {
            eventsPage(admin, events, draft = null, msg = call.parameters["msg"], err = call.parameters["err"])
        }
    }
    action("/events", services) { admin ->
        val draft = receiveDraft(services, existing = null)
        if (draft.preview || draft.error != null) {
            val events = services.events.all()
            return@action call.respondHtml { eventsPage(admin, events, draft, msg = null, err = null) }
        }
        val event = services.events.publish(draft.fields!!, draft.imageHash!!)
        services.audit.record(admin, AuditCategory.EVENTS, "a publicat evenimentul ${event.title}")
        if (draft.notify) {
            // every message would fail, so the admin hears why instead of phones staying silent
            val drift = services.push.clockDrift()
            if (drift != null) {
                return@action call.respondRedirect("/admin/events?err=${clockNotice(drift).encodeURLParameter()}")
            }
            services.push.announce(event, whenAndWhere(event))
        }
        call.respondRedirect("/admin/events?msg=${"Evenimentul a fost publicat.".encodeURLParameter()}")
    }
    page("/events/{id}", services) { admin ->
        val event = services.events.byId(call.parameters["id"].orEmpty())
            ?: return@page call.respond(HttpStatusCode.NotFound)
        call.respondHtml { editEventPage(admin, event, EventDraft.of(event)) }
    }
    action("/events/{id}", services) { admin ->
        val event = services.events.byId(call.parameters["id"].orEmpty())
            ?: return@action call.respond(HttpStatusCode.NotFound)
        val draft = receiveDraft(services, existing = event)
        if (draft.preview || draft.error != null) {
            return@action call.respondHtml { editEventPage(admin, event, draft) }
        }
        services.events.update(event.id, draft.fields!!, draft.imageHash!!)
        services.audit.record(admin, AuditCategory.EVENTS, "a modificat evenimentul ${draft.fields!!.title}")
        call.respondRedirect("/admin/events?msg=${"Evenimentul a fost salvat.".encodeURLParameter()}")
    }
    action("/events/{id}/delete", services) { admin ->
        val id = call.parameters["id"].orEmpty()
        services.events.byId(id)?.let {
            services.audit.record(admin, AuditCategory.EVENTS, "a șters evenimentul ${it.title}")
        }
        services.events.delete(id)
        call.respondRedirect("/admin/events?msg=${"Evenimentul a fost șters.".encodeURLParameter()}")
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
) {
    val fields: EventFields? get() = if (error == null) parse(form).getOrNull() else null

    fun value(name: String) = form[name].orEmpty()

    companion object {
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
                title.length > 200 -> fail("Titlul are peste 200 de caractere.")
                text("place").length > 200 -> fail("Locul are peste 200 de caractere.")
                text("body").length > 20_000 -> fail("Descrierea are peste 20000 de caractere.")
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
    )
}

private fun HTML.eventsPage(admin: Admin, events: List<Event>, draft: EventDraft?, msg: String?, err: String?) {
    val now = Clock.System.now()
    adminPage(
        "Evenimente",
        admin,
        Section.EVENTS,
        context = { +"Cardurile cu imagine din fila Evenimente a aplicației; le văd toți studenții." },
    ) {
        notice(msg)
        notice(err, error = true)
        h2 {
            id = "new"
            style = "margin-top: 0"
            +"Eveniment nou"
        }
        eventForm("/admin/events", draft, submit = "Publică", isNew = true)
        val (past, upcoming) = events.partition { it.over < now }
        h2 {
            +"Următoare"
            span("count") { +upcoming.size.toString() }
        }
        if (upcoming.isEmpty()) p("hint") { +"Niciun eveniment programat." }
        eventGrid(upcoming.sortedBy { it.startsAt }, past = false)
        if (past.isNotEmpty()) {
            h2 {
                +"Trecute"
                span("count") { +past.size.toString() }
            }
            p("hint") {
                +"Aplicația le arată încă ${EventRepository.KEPT_DAYS} de zile după ce se termină, la Trecute."
            }
            eventGrid(past, past = true)
        }
    }
}

private fun FlowContent.eventGrid(events: List<Event>, past: Boolean) {
    div("event-grid") {
        events.forEach { e ->
            div(if (past) "event-card past" else "event-card") {
                img(src = e.image, alt = "") { attributes["loading"] = "lazy" }
                div("text") {
                    strong { +e.title }
                    span("meta") { +whenAndWhere(e) }
                    div("form-actions") {
                        a(href = "/admin/events/${e.id}", classes = "btn secondary") { +"Editează" }
                        form(action = "/admin/events/${e.id}/delete", method = FormMethod.post, classes = "inline") {
                            onSubmit = "return confirm('Ștergi evenimentul? Dispare și din aplicații.')"
                            button(classes = "danger") { +"Șterge" }
                        }
                    }
                }
            }
        }
    }
}

private fun HTML.editEventPage(admin: Admin, event: Event, draft: EventDraft) {
    adminPage(
        "Editează evenimentul",
        admin,
        Section.EVENTS,
        context = {
            a(href = "/admin/events") { +"Evenimente" }
            +" · ${whenAndWhere(event)}"
        },
    ) {
        eventForm("/admin/events/${event.id}", draft, submit = "Salvează", isNew = false)
        p("hint") { +"Aplicațiile primesc schimbările la următoarea sincronizare, fără notificare." }
    }
}

private fun FlowContent.eventForm(action: String, draft: EventDraft?, submit: String, isNew: Boolean) {
    if (draft?.error != null && !draft.preview) notice(draft.error, error = true)
    div("cols") {
        form(action = action, method = FormMethod.post, encType = FormEncType.multipartFormData, classes = "card") {
            field("title", "Titlu") {
                textInput(name = "title") {
                    id = "title"
                    required = true
                    maxLength = "200"
                    placeholder = "Ziua Carierei FAIMA"
                    value = draft?.value("title").orEmpty()
                }
                p("hint") { +"Apare sub imagine, pe card." }
            }
            field("image", if (draft?.imageHash == null) "Imagine" else "Imagine nouă") {
                if (draft?.imageHash != null) {
                    hiddenInput(name = "image_hash") { value = draft.imageHash }
                    img(src = EventRepository.imagePath(draft.imageHash), alt = "", classes = "current-image")
                }
                fileInput(name = "image") {
                    id = "image"
                    accept = "image/jpeg,image/png"
                    if (draft?.imageHash == null) required = true
                }
                p("hint") {
                    +"JPEG sau PNG, cel mult 8 MB. Se micșorează la 1600 px. Cardul o taie la 16:9, "
                    +"pagina evenimentului o arată mai mare; ține textul unui afiș spre mijloc."
                }
            }
            div("cols") {
                style = "gap: 0 16px"
                field("start_date", "Data") {
                    dateInput(name = "start_date") {
                        id = "start_date"
                        required = true
                        value = draft?.value("start_date").orEmpty()
                    }
                }
                field("start_time", "Ora de început") {
                    timeInput(name = "start_time") {
                        id = "start_time"
                        required = true
                        value = draft?.value("start_time").orEmpty()
                    }
                }
                field("end_time", "Ora de sfârșit (opțional)") {
                    timeInput(name = "end_time") {
                        id = "end_time"
                        value = draft?.value("end_time").orEmpty()
                    }
                }
                field("end_date", "Se termină în altă zi (opțional)") {
                    dateInput(name = "end_date") {
                        id = "end_date"
                        value = draft?.value("end_date").orEmpty()
                    }
                }
            }
            field("place", "Locul (opțional)") {
                textInput(name = "place") {
                    id = "place"
                    maxLength = "200"
                    placeholder = "Aula BN"
                    value = draft?.value("place").orEmpty()
                }
            }
            field("body", "Descriere (opțional)") {
                textArea {
                    id = "body"
                    name = "body"
                    +draft?.value("body").orEmpty()
                }
                p("hint") { +"**îngroșat**, *cursiv*, [link](https://faima.upb.ro), liste cu - la început de rând." }
            }
            field("link", "Link (opțional)") {
                urlInput(name = "link") {
                    id = "link"
                    placeholder = "https://faima.upb.ro/..."
                    value = draft?.value("link").orEmpty()
                }
                p("hint") { +"Înscriere, program sau pagina evenimentului; aplicația îl arată ca buton." }
            }
            if (isNew) {
                label("check") {
                    checkBoxInput(name = "notify") { checked = draft?.notify ?: true }
                    +"Trimite notificare pe telefoane"
                }
            }
            div("form-actions") {
                button { +submit }
                button(classes = "secondary") {
                    name = "preview"
                    value = "1"
                    attributes["formnovalidate"] = "formnovalidate"
                    +"Previzualizează"
                }
            }
        }
        if (draft?.preview == true) preview(draft)
    }
}

/** The card and the page as the app draws them, from what the form holds now. */
private fun FlowContent.preview(draft: EventDraft) {
    div("preview") {
        div("label") { +"Așa apare în aplicație" }
        val fields = EventDraft.parse(draft.form).getOrNull()
        val sample = draft.imageHash?.let { hash ->
            fields?.let {
                Event(
                    id = "preview",
                    title = it.title,
                    startsAt = it.startsAt,
                    endsAt = it.endsAt,
                    place = it.place,
                    body = it.body,
                    link = it.link,
                    image = EventRepository.imagePath(hash),
                    imageRatio = draft.imageRatio ?: 1.6f,
                    publishedAt = Clock.System.now(),
                )
            }
        }
        if (sample == null) {
            p("hint") { +(draft.error ?: "Completează titlul, data și imaginea ca să vezi cardul.") }
            return@div
        }
        div("event-card") {
            img(src = sample.image, alt = "")
            div("text") {
                strong { +sample.title }
                span("meta") { +whenAndWhere(sample) }
            }
        }
        if (sample.body.isNotBlank()) div("announcement") { div("body") { markdown(sample.body) } }
        if (sample.link != null) p { a(href = sample.link) { +"Deschide linkul" } }
        if (draft.error != null) notice(draft.error, error = true)
    }
}
