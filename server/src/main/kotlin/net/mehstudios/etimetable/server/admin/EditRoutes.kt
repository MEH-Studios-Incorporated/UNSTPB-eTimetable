// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.encodeURLParameter
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.*
import net.mehstudios.etimetable.model.Activity
import net.mehstudios.etimetable.model.ActivityType
import net.mehstudios.etimetable.model.Audience
import net.mehstudios.etimetable.model.Building
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Enrollment
import net.mehstudios.etimetable.model.LocalizedText
import net.mehstudios.etimetable.model.OfficeHour
import net.mehstudios.etimetable.model.Professor
import net.mehstudios.etimetable.model.Room
import net.mehstudios.etimetable.model.Subject
import net.mehstudios.etimetable.model.WeeklySlot
import net.mehstudios.etimetable.schedule.FacultyTimeZone
import net.mehstudios.etimetable.server.ApiProblem
import net.mehstudios.etimetable.server.Services
import net.mehstudios.etimetable.server.timetable.TimetableSheet
import java.util.UUID

/**
 * Editing a semester in place: subjects, professors and their office hours, single
 * activities, rooms and buildings, and the students' accounts. Every save stores the
 * whole semester again, which raises its version, so the apps take the change at their
 * next sync. A later CSV import replaces all of it.
 */
fun Route.editRoutes(services: Services) {
    // the sidebar's entries open the current semester
    listOf("subjects", "professors", "rooms").forEach { part ->
        page("/$part", services) { _ ->
            val id = services.datasets.current()?.semesterId
                ?: return@page call.respondRedirect(
                    "/admin/semesters?err=${"Niciun semestru nu este curent încă.".encodeURLParameter()}",
                )
            call.respondRedirect(linkTo("/admin/semesters/$id/$part", call.request.queryParameters, emptyMap()))
        }
    }
    subjectRoutes(services)
    professorRoutes(services)
    activityRoutes(services)
    roomRoutes(services)
    studentRoutes(services)
}

private fun Dataset.base() = "/admin/semesters/${semester.id}"

private suspend fun RoutingContext.semesterOrNull(services: Services): Dataset? =
    services.datasets.dataset(call.parameters["id"].orEmpty())

private suspend fun Services.save(dataset: Dataset) = datasets.replace(dataset, makeCurrent = false)

private fun RoutingContext.message() = call.parameters["msg"] to call.parameters["err"]

private fun msg(text: String) = "msg=${text.encodeURLParameter()}"

private fun err(text: String) = "err=${text.encodeURLParameter()}"

// ---- subjects ---------------------------------------------------------------------------

private fun Route.subjectRoutes(services: Services) {
    page("/semesters/{id}/subjects", services) { admin ->
        val dataset = semesterOrNull(services) ?: return@page call.respond(HttpStatusCode.NotFound)
        val query = call.parameters["q"].orEmpty().trim()
        val programme = call.parameters["programme"].orEmpty()
        val year = call.parameters["year"]?.toIntOrNull()
        val subjects = dataset.subjects.filter { s ->
            (programme.isEmpty() || s.programmeId == programme) &&
                (year == null || s.year == year) &&
                (
                    query.isEmpty() ||
                        TimetableSheet.fold(
                            s.name.ro + " " + s.name.en + " " + s.code,
                        ).contains(TimetableSheet.fold(query))
                    )
        }
        val paging = call.parameters.paging(subjects.size)
        call.respondHtml {
            adminPage("Materii", admin, Section.SUBJECTS, context = { semesterLink(dataset) }) {
                tableCard(
                    filters = { subjectFilters(dataset, query, programme, year) },
                    footer = { pager(paging, "${dataset.base()}/subjects", call.request.queryParameters) },
                ) {
                    if (subjects.isEmpty()) {
                        emptyState(
                            "Nicio materie",
                            if (dataset.subjects.isEmpty()) {
                                "Materiile apar după importul orarului."
                            } else {
                                "Schimbă filtrele."
                            },
                        )
                    } else {
                        subjectTable(dataset, paging.slice(subjects))
                    }
                }
            }
        }
    }
    page("/semesters/{id}/subjects/{subject}", services) { admin ->
        val dataset = semesterOrNull(services) ?: return@page call.respond(HttpStatusCode.NotFound)
        val subject = dataset.subjectsById[call.parameters["subject"].orEmpty()]
            ?: return@page call.respond(HttpStatusCode.NotFound)
        val (msg, err) = message()
        val activities = dataset.activities.filter { it.subjectId == subject.id }
        call.respondHtml {
            adminPage(
                subject.name.ro,
                admin,
                Section.SUBJECTS,
                context = {
                    a(href = "${dataset.base()}/subjects") { +"Materii" }
                    +" · ${dataset.programmes.firstOrNull { it.id == subject.programmeId }?.code}, anul ${subject.year}"
                },
                actions = {
                    a(
                        href = "${dataset.base()}/activities/new?subject=${subject.id.encodeURLParameter()}",
                        classes = "btn",
                    ) { +"Adaugă activitate" }
                },
            ) {
                notice(msg)
                notice(err, error = true)
                div("cols") {
                    form(
                        action = "${dataset.base()}/subjects/${subject.id.encodeURLParameter()}",
                        method = FormMethod.post,
                        classes = "card",
                    ) {
                        h2 {
                            style = "margin-top: 0"
                            +"Numele materiei"
                        }
                        field("name_ro", "În română") {
                            textInput(name = "name_ro") {
                                id = "name_ro"
                                required = true
                                value = subject.name.ro
                            }
                        }
                        field("name_en", "În engleză") {
                            textInput(name = "name_en") {
                                id = "name_en"
                                value = subject.name.en
                            }
                        }
                        field("code", "Cod") {
                            textInput(name = "code") {
                                id = "code"
                                required = true
                                value = subject.code
                            }
                        }
                        div("form-actions") { button { +"Salvează" } }
                    }
                    form(
                        action = "${dataset.base()}/subjects/${subject.id.encodeURLParameter()}/assign",
                        method = FormMethod.post,
                        classes = "card",
                    ) {
                        h2 {
                            style = "margin-top: 0"
                            +"Profesor pentru mai multe activități"
                        }
                        p("hint") {
                            +"De exemplu asistentul tuturor laboratoarelor, când orarul facultății nu îl trece."
                        }
                        field("type", "Activitățile de tip") {
                            select {
                                id = "type"
                                name = "type"
                                TYPE_NAMES.forEach { (type, label) ->
                                    option {
                                        value = type.name
                                        selected = type == ActivityType.LAB
                                        +label
                                    }
                                }
                            }
                        }
                        field("professor", "Profesor") { professorSelect(dataset, null, "professor") }
                        label("check") {
                            checkBoxInput(name = "only_empty") { checked = true }
                            +"Doar unde nu e trecut nimeni"
                        }
                        div("form-actions") { button(classes = "secondary") { +"Trece profesorul" } }
                    }
                }
                h2 {
                    +"Activități"
                    span("count") { +activities.size.toString() }
                }
                activityTable(dataset, activities, showSubject = false)
            }
        }
    }

    action("/semesters/{id}/subjects/{subject}", services) { admin ->
        val dataset = semesterOrNull(services) ?: return@action call.respond(HttpStatusCode.NotFound)
        val subject = dataset.subjectsById[call.parameters["subject"].orEmpty()]
            ?: return@action call.respond(HttpStatusCode.NotFound)
        val form = call.receiveParameters()
        val ro = form["name_ro"].orEmpty().trim()
        val en = form["name_en"].orEmpty().trim().ifEmpty { ro }
        val code = form["code"].orEmpty().trim()
        val back = "${dataset.base()}/subjects/${subject.id.encodeURLParameter()}"
        if (ro.isEmpty() ||
            code.isEmpty()
        ) {
            return@action call.respondRedirect("$back?${err("Numele și codul sunt obligatorii.")}")
        }
        services.save(
            dataset.copy(
                subjects = dataset.subjects.map {
                    if (it.id ==
                        subject.id
                    ) {
                        it.copy(name = LocalizedText(ro, en), code = code)
                    } else {
                        it
                    }
                },
            ),
        )
        services.audit.record(admin, AuditCategory.TIMETABLE, "a modificat materia $code, $ro")
        call.respondRedirect("$back?${msg("Materia a fost salvată.")}")
    }

    action("/semesters/{id}/subjects/{subject}/assign", services) { admin ->
        val dataset = semesterOrNull(services) ?: return@action call.respond(HttpStatusCode.NotFound)
        val subject = dataset.subjectsById[call.parameters["subject"].orEmpty()]
            ?: return@action call.respond(HttpStatusCode.NotFound)
        val form = call.receiveParameters()
        val type = ActivityType.entries.firstOrNull { it.name == form["type"] }
        val professorId = form["professor"].orEmpty().ifEmpty { null }?.takeIf { it in dataset.professorsById }
        val onlyEmpty = form["only_empty"] == "on"
        val back = "${dataset.base()}/subjects/${subject.id.encodeURLParameter()}"
        if (type == null) return@action call.respond(HttpStatusCode.BadRequest)
        var changed = 0
        val activities = dataset.activities.map {
            if (it.subjectId == subject.id && it.type == type && (!onlyEmpty || it.professorId == null)) {
                changed++
                it.copy(professorId = professorId)
            } else {
                it
            }
        }
        services.save(dataset.copy(activities = activities))
        services.audit.record(
            admin,
            AuditCategory.TIMETABLE,
            "a schimbat profesorul la $changed activități de ${TYPE_NAMES[type]?.lowercase()}, ${subject.name.ro}",
        )
        call.respondRedirect(
            "$back?${msg("Am schimbat profesorul la $changed ${if (changed == 1) "activitate" else "activități"}.")}",
        )
    }
}

// ---- professors -------------------------------------------------------------------------

private fun Route.professorRoutes(services: Services) {
    page("/semesters/{id}/professors", services) { admin ->
        val dataset = semesterOrNull(services) ?: return@page call.respond(HttpStatusCode.NotFound)
        val query = call.parameters["q"].orEmpty().trim()
        val (msg, err) = message()
        val professors = dataset.professors
            .filter {
                query.isEmpty() ||
                    TimetableSheet.fold(it.displayName + " " + it.email.orEmpty()).contains(TimetableSheet.fold(query))
            }
            .sortedBy { TimetableSheet.fold(it.name) }
        val paging = call.parameters.paging(professors.size)
        call.respondHtml {
            adminPage("Profesori", admin, Section.PROFESSORS, context = { semesterLink(dataset) }) {
                notice(msg)
                notice(err, error = true)
                tableCard(
                    filters = {
                        form(action = "${dataset.base()}/professors", method = FormMethod.get, classes = "filters") {
                            searchInput(name = "q") {
                                value = query
                                placeholder = "Caută după nume sau email"
                            }
                            button(classes = "secondary") { +"Caută" }
                        }
                    },
                    footer = { pager(paging, "${dataset.base()}/professors", call.request.queryParameters) },
                ) {
                    if (professors.isEmpty()) {
                        emptyState(
                            "Niciun profesor",
                            if (query.isEmpty()) {
                                "Adaugă unul mai jos sau importă orarul."
                            } else {
                                "Nimeni nu se potrivește căutării."
                            },
                        )
                    } else {
                        professorTable(dataset, paging.slice(professors))
                    }
                }
                h2 { +"Profesor nou" }
                form(action = "${dataset.base()}/professors", method = FormMethod.post, classes = "card") {
                    div("cols") {
                        style = "gap: 0 16px"
                        field("title", "Titlu") {
                            textInput(name = "title") {
                                id = "title"
                                placeholder = "As. dr. ing."
                            }
                        }
                        field("name", "Nume") {
                            textInput(name = "name") {
                                id = "name"
                                required = true
                                placeholder = "Ioana Marinescu"
                            }
                        }
                        field("email", "Email") {
                            emailInput(name = "email") { id = "email" }
                        }
                    }
                    div("form-actions") { button { +"Adaugă profesorul" } }
                }
            }
        }
    }

    action("/semesters/{id}/professors", services) { admin ->
        val dataset = semesterOrNull(services) ?: return@action call.respond(HttpStatusCode.NotFound)
        val form = call.receiveParameters()
        val name = form["name"].orEmpty().trim()
        val list = "${dataset.base()}/professors"
        if (name.isEmpty()) return@action call.respondRedirect("$list?${err("Numele este obligatoriu.")}")
        val base = TimetableSheet.slug(name).ifEmpty { "profesor" }
        val id = generateSequence(1) { it + 1 }.map {
            if (it ==
                1
            ) {
                base
            } else {
                "$base-$it"
            }
        }.first { it !in dataset.professorsById }
        val professor =
            Professor(id, form["title"].orEmpty().trim(), name, form["email"].orEmpty().trim().ifEmpty { null })
        services.save(dataset.copy(professors = dataset.professors + professor))
        services.audit.record(admin, AuditCategory.TIMETABLE, "a adăugat profesorul $name")
        call.respondRedirect("$list/${id.encodeURLParameter()}?${msg("Profesorul a fost adăugat.")}")
    }

    page("/semesters/{id}/professors/{professor}", services) { admin ->
        val dataset = semesterOrNull(services) ?: return@page call.respond(HttpStatusCode.NotFound)
        val professor = dataset.professorsById[call.parameters["professor"].orEmpty()]
            ?: return@page call.respond(HttpStatusCode.NotFound)
        call.respondHtml { professorPage(admin, dataset, professor, call.parameters, null) }
    }

    action("/semesters/{id}/professors/{professor}", services) { admin ->
        val dataset = semesterOrNull(services) ?: return@action call.respond(HttpStatusCode.NotFound)
        val professor = dataset.professorsById[call.parameters["professor"].orEmpty()]
            ?: return@action call.respond(HttpStatusCode.NotFound)
        val form = call.receiveParameters()
        val name = form["name"].orEmpty().trim()
        try {
            if (name.isEmpty()) throw AdminFormError("Numele este obligatoriu.")
            var next = dataset
            val officeRoom = form["office"].orEmpty().let { text ->
                next.withRoom(text).also { next = it.first }.second
            }
            val hours = form["office_hours"].orEmpty().lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
                try {
                    val (hour, withRoom) = parseOfficeHour(next, line)
                    next = withRoom
                    hour
                } catch (e: IllegalArgumentException) {
                    throw AdminFormError("Consultații, rândul ${i + 1}: ${e.message}")
                }
            }
            val changed = professor.copy(
                title = form["title"].orEmpty().trim(),
                name = name,
                email = form["email"].orEmpty().trim().ifEmpty { null },
                officeRoomId = officeRoom,
                officeHours = hours,
            )
            services.save(next.copy(professors = next.professors.map { if (it.id == professor.id) changed else it }))
            services.audit.record(admin, AuditCategory.TIMETABLE, "a modificat profesorul ${changed.name}")
            call.respondRedirect(
                "${dataset.base()}/professors/${professor.id.encodeURLParameter()}?${msg("Profesorul a fost salvat.")}",
            )
        } catch (e: AdminFormError) {
            call.respondHtml(HttpStatusCode.UnprocessableEntity) {
                professorPage(admin, dataset, professor, form, e.message)
            }
        }
    }

    action("/semesters/{id}/professors/{professor}/delete", services) { admin ->
        val dataset = semesterOrNull(services) ?: return@action call.respond(HttpStatusCode.NotFound)
        val id = call.parameters["professor"].orEmpty()
        val list = "${dataset.base()}/professors"
        if (dataset.activities.any { it.professorId == id }) {
            return@action call.respondRedirect(
                "$list/${id.encodeURLParameter()}?${err("Profesorul are încă activități.")}",
            )
        }
        services.save(dataset.copy(professors = dataset.professors.filterNot { it.id == id }))
        services.audit.record(
            admin,
            AuditCategory.TIMETABLE,
            "a șters profesorul ${dataset.professorsById[id]?.name ?: id}",
        )
        call.respondRedirect("$list?${msg("Profesorul a fost șters.")}")
    }
}

private fun HTML.professorPage(admin: Admin, dataset: Dataset, professor: Professor, form: Parameters, error: String?) {
    val activities = dataset.activities.filter { it.professorId == professor.id }
    val path = "${dataset.base()}/professors/${professor.id.encodeURLParameter()}"
    adminPage(
        professor.displayName,
        admin,
        Section.PROFESSORS,
        context = { a(href = "${dataset.base()}/professors") { +"Profesori" } },
    ) {
        notice(form["msg"])
        notice(form["err"] ?: error, error = true)
        form(action = path, method = FormMethod.post, classes = "card") {
            div("cols") {
                style = "gap: 0 16px"
                field("title", "Titlu") {
                    textInput(name = "title") {
                        id = "title"
                        value = form["title"] ?: professor.title
                    }
                }
                field("name", "Nume") {
                    textInput(name = "name") {
                        id = "name"
                        required = true
                        value = form["name"] ?: professor.name
                    }
                }
                field("email", "Email") {
                    emailInput(name = "email") {
                        id = "email"
                        value = form["email"] ?: professor.email.orEmpty()
                    }
                }
                field("office", "Birou") {
                    roomInput(
                        dataset,
                        "office",
                        form["office"] ?: dataset.roomsById[professor.officeRoomId]?.code.orEmpty(),
                    )
                }
            }
            field("office_hours", "Consultații") {
                textArea {
                    id = "office_hours"
                    name = "office_hours"
                    style = "min-height: 90px"
                    +(form["office_hours"] ?: professor.officeHours.joinToString("\n") { officeHourText(dataset, it) })
                }
                p("hint") {
                    +"Câte una pe rând: ziua, orele, sala și, dacă e cazul, pare sau impare. "
                    +"De exemplu: Miercuri 14:00-16:00 EC210 impare"
                }
            }
            div("form-actions") { button { +"Salvează" } }
        }
        h2 {
            +"Activități"
            span("count") { +activities.size.toString() }
        }
        activityTable(dataset, activities, showSubject = true, showProfessor = false)
        if (activities.isEmpty()) {
            form(action = "$path/delete", method = FormMethod.post) {
                style = "margin-top: 16px"
                onSubmit = "return confirm('Ștergi profesorul?')"
                button(classes = "danger") { +"Șterge profesorul" }
            }
        }
    }
}

/** "Miercuri 14:00-16:00 EC210 impare" */
private fun officeHourText(dataset: Dataset, hour: OfficeHour): String = listOfNotNull(
    DAY_NAMES[hour.slot.isoDay - 1],
    "${hour.slot.start}-${hour.slot.end}",
    dataset.roomsById[hour.roomId]?.code,
    TimetableSheet.weeksText(hour.slot.weeks).takeIf { it != "toate" },
).joinToString(" ")

private fun parseOfficeHour(dataset: Dataset, line: String): Pair<OfficeHour, Dataset> {
    val words = line.trim().split(Regex("\\s+"))
    require(words.size >= 2) { "scrie ziua și orele, de exemplu Miercuri 14:00-16:00" }
    val day = TimetableSheet.parseDay(words[0])
    val times = words[1].split('-')
    require(times.size == 2) { "orele se scriu 14:00-16:00" }
    val start = TimetableSheet.parseTime(times[0])
    val end = TimetableSheet.parseTime(times[1])
    require(start < end) { "ora de sfârșit este înaintea celei de început" }
    var rest = words.drop(2)
    val weeks = rest.lastOrNull()?.let { last ->
        runCatching { TimetableSheet.parseWeeks(last) }.getOrNull()?.takeIf { TimetableSheet.fold(last) in WEEK_WORDS }
    }
    if (weeks != null) rest = rest.dropLast(1)
    val (withRoom, roomId) = dataset.withRoom(rest.joinToString(" "))
    return OfficeHour(WeeklySlot(day, start, end, weeks ?: net.mehstudios.etimetable.model.WeekRule.All), roomId) to
        withRoom
}

private val WEEK_WORDS = setOf("pare", "impare", "par", "impar", "toate", "odd", "even")

// ---- activities -------------------------------------------------------------------------

private fun Route.activityRoutes(services: Services) {
    page("/semesters/{id}/activities/new", services) { admin ->
        val dataset = semesterOrNull(services) ?: return@page call.respond(HttpStatusCode.NotFound)
        call.respondHtml { activityPage(admin, dataset, null, call.parameters, null) }
    }
    action("/semesters/{id}/activities", services) { admin ->
        val dataset = semesterOrNull(services) ?: return@action call.respond(HttpStatusCode.NotFound)
        val form = call.receiveParameters()
        try {
            val (activity, next) = activityFrom(dataset, "a-" + UUID.randomUUID().toString().take(8), form)
            services.save(next.copy(activities = next.activities + activity))
            services.audit.record(
                admin,
                AuditCategory.TIMETABLE,
                "a adăugat activitatea ${activityLabel(next, activity)}",
            )
            call.respondRedirect("${backFor(dataset, form, activity)}?${msg("Activitatea a fost adăugată.")}")
        } catch (e: AdminFormError) {
            call.respondHtml(HttpStatusCode.UnprocessableEntity) { activityPage(admin, dataset, null, form, e.message) }
        }
    }
    page("/semesters/{id}/activities/{activity}", services) { admin ->
        val dataset = semesterOrNull(services) ?: return@page call.respond(HttpStatusCode.NotFound)
        val activity = dataset.activitiesById[call.parameters["activity"].orEmpty()]
            ?: return@page call.respond(HttpStatusCode.NotFound)
        call.respondHtml { activityPage(admin, dataset, activity, call.parameters, null) }
    }
    action("/semesters/{id}/activities/{activity}", services) { admin ->
        val dataset = semesterOrNull(services) ?: return@action call.respond(HttpStatusCode.NotFound)
        val old = dataset.activitiesById[call.parameters["activity"].orEmpty()]
            ?: return@action call.respond(HttpStatusCode.NotFound)
        val form = call.receiveParameters()
        try {
            val (activity, next) = activityFrom(dataset, old.id, form)
            services.save(next.copy(activities = next.activities.map { if (it.id == old.id) activity else it }))
            services.audit.record(
                admin,
                AuditCategory.TIMETABLE,
                "a modificat activitatea ${activityLabel(next, activity)}",
            )
            call.respondRedirect("${backFor(dataset, form, activity)}?${msg("Activitatea a fost salvată.")}")
        } catch (e: AdminFormError) {
            call.respondHtml(HttpStatusCode.UnprocessableEntity) { activityPage(admin, dataset, old, form, e.message) }
        }
    }
    action("/semesters/{id}/activities/{activity}/delete", services) { admin ->
        val dataset = semesterOrNull(services) ?: return@action call.respond(HttpStatusCode.NotFound)
        val old = dataset.activitiesById[call.parameters["activity"].orEmpty()]
            ?: return@action call.respond(HttpStatusCode.NotFound)
        val form = call.receiveParameters()
        services.save(dataset.copy(activities = dataset.activities.filterNot { it.id == old.id }))
        services.audit.record(admin, AuditCategory.TIMETABLE, "a șters activitatea ${activityLabel(dataset, old)}")
        call.respondRedirect("${backFor(dataset, form, old)}?${msg("Activitatea a fost ștearsă.")}")
    }
}

/** Where to go after a save: the page the form was opened from, or the subject's page. */
private fun backFor(dataset: Dataset, form: Parameters, activity: Activity): String =
    form["back"]?.takeIf { it.startsWith("/admin/") && !it.contains("//") }
        ?: "${dataset.base()}/subjects/${activity.subjectId.encodeURLParameter()}"

/** Reads the activity form with the importer's rules; a new room code adds the room. */
private fun activityFrom(dataset: Dataset, id: String, form: Parameters): Pair<Activity, Dataset> {
    val subject = dataset.subjectsById[form["subject"].orEmpty()] ?: throw AdminFormError("Alege materia.")
    val type = ActivityType.entries.firstOrNull { it.name == form["type"] } ?: throw AdminFormError("Alege tipul.")
    val slot = try {
        val day = TimetableSheet.parseDay(form["day"].orEmpty())
        val start = TimetableSheet.parseTime(form["start"].orEmpty())
        val end = TimetableSheet.parseTime(form["end"].orEmpty())
        if (start >= end) throw AdminFormError("Ora de sfârșit trebuie să fie după cea de început.")
        WeeklySlot(day, start, end, TimetableSheet.parseWeeks(form["weeks"].orEmpty()))
    } catch (e: IllegalArgumentException) {
        throw AdminFormError("Ziua, orele sau săptămânile: ${e.message}")
    }
    val professorId = form["professor"].orEmpty().ifEmpty { null }
    if (professorId != null && professorId !in dataset.professorsById) throw AdminFormError("Profesorul nu există.")
    val (next, roomId) = dataset.withRoom(form["room"].orEmpty())
    val audience = dataset.parseAudience(form["audience"].orEmpty())
    return Activity(id, subject.id, type, slot, professorId, roomId, audience, form["erasmus"] == "on") to next
}

private fun Dataset.parseAudience(text: String): Audience {
    val tokens = text.split(Regex("[\\s,;]+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) throw AdminFormError("Scrie cel puțin o grupă, de exemplu 1521A sau 1521A/A.")
    fun find(token: String, ids: Collection<String>) = ids.firstOrNull { it.equals(token, ignoreCase = true) }
    val series = mutableSetOf<String>()
    val groups = mutableSetOf<String>()
    val halves = mutableSetOf<String>()
    tokens.forEach { token ->
        find(token, halfGroupsById.keys)?.let {
            halves += it
            return@forEach
        }
        (find(token, groupsById.keys) ?: this.groups.firstOrNull { it.code.equals(token, ignoreCase = true) }?.id)
            ?.let {
                groups += it
                return@forEach
            }
        find(token, this.series.map { it.id })?.let {
            series += it
            return@forEach
        }
        throw AdminFormError("Nu există grupa sau seria $token.")
    }
    return Audience(series, groups, halves)
}

private fun audienceText(dataset: Dataset, audience: Audience): String = (
    audience.seriesIds + audience.groupIds.map {
        dataset.groupsById[it]?.code ?: it
    } + audience.halfGroupIds
    ).joinToString(" ")

/** The room for a text such as "BN 212 b", added with its building when it is new; null for an empty text. */
private fun Dataset.withRoom(text: String): Pair<Dataset, String?> {
    if (text.isBlank()) return this to null
    val (code, buildingCode) = TimetableSheet.roomCode(text)
    val roomId = TimetableSheet.slug(code)
    val buildingId = TimetableSheet.slug(buildingCode)
    var next = this
    if (buildingId !in
        buildingsById
    ) {
        next = next.copy(buildings = next.buildings + Building(buildingId, buildingCode, buildingCode))
    }
    if (roomId !in roomsById) next = next.copy(rooms = next.rooms + Room(roomId, buildingId, code))
    return next to roomId
}

private fun HTML.activityPage(admin: Admin, dataset: Dataset, activity: Activity?, form: Parameters, error: String?) {
    val subjectId = form["subject"] ?: activity?.subjectId
    val subject = dataset.subjectsById[subjectId.orEmpty()]
    val back = form["back"] ?: subject?.let { "${dataset.base()}/subjects/${it.id.encodeURLParameter()}" }
    val action = if (activity ==
        null
    ) {
        "${dataset.base()}/activities"
    } else {
        "${dataset.base()}/activities/${activity.id.encodeURLParameter()}"
    }
    adminPage(
        if (activity == null) "Activitate nouă" else "Editează activitatea",
        admin,
        Section.SUBJECTS,
        context = {
            if (subject !=
                null
            ) {
                a(href = "${dataset.base()}/subjects/${subject.id.encodeURLParameter()}") { +subject.name.ro }
            } else {
                semesterLink(dataset)
            }
        },
    ) {
        notice(error, error = true)
        form(action = action, method = FormMethod.post, classes = "card") {
            if (back != null) hiddenInput(name = "back") { value = back }
            div("cols") {
                style = "gap: 0 16px"
                field("subject", "Materia") {
                    select {
                        id = "subject"
                        name = "subject"
                        required = true
                        option {
                            value = ""
                            +"Alege"
                        }
                        dataset.subjects.sortedWith(
                            compareBy({
                                it.programmeId
                            }, { it.year }, { TimetableSheet.fold(it.name.ro) }),
                        ).forEach { s ->
                            option {
                                value = s.id
                                selected = s.id == subjectId
                                +"${s.name.ro} (${dataset.programmes.firstOrNull {
                                    it.id == s.programmeId
                                }?.code} ${s.year})"
                            }
                        }
                    }
                }
                field("type", "Tipul") {
                    select {
                        id = "type"
                        name = "type"
                        TYPE_NAMES.forEach { (type, label) ->
                            option {
                                value = type.name
                                selected = type.name == (form["type"] ?: activity?.type?.name ?: ActivityType.LAB.name)
                                +label
                            }
                        }
                    }
                }
                field("day", "Ziua") {
                    select {
                        id = "day"
                        name = "day"
                        (1..6).forEach { day ->
                            option {
                                value = day.toString()
                                selected = day.toString() == (form["day"] ?: activity?.slot?.isoDay?.toString() ?: "1")
                                +DAY_NAMES[day - 1]
                            }
                        }
                    }
                }
                div("pair") {
                    field("start", "De la") {
                        timeInput(name = "start") {
                            id = "start"
                            required = true
                            value = form["start"] ?: activity?.slot?.start?.toString() ?: "08:00"
                        }
                    }
                    field("end", "Până la") {
                        timeInput(name = "end") {
                            id = "end"
                            required = true
                            value = form["end"] ?: activity?.slot?.end?.toString() ?: "10:00"
                        }
                    }
                }
                field("weeks", "Săptămâni") {
                    textInput(name = "weeks") {
                        id = "weeks"
                        value = form["weeks"] ?: activity?.slot?.weeks?.let(TimetableSheet::weeksText) ?: "toate"
                        attributes["list"] = "weeks-list"
                    }
                    dataList {
                        id = "weeks-list"
                        listOf("toate", "pare", "impare").forEach { option { value = it } }
                    }
                    p("hint") { +"toate, pare, impare sau numerele lor, de exemplu 1-7, 9" }
                }
                field("professor", "Profesor sau asistent") {
                    professorSelect(dataset, form["professor"] ?: activity?.professorId, "professor")
                }
                field("room", "Sala") {
                    roomInput(dataset, "room", form["room"] ?: dataset.roomsById[activity?.roomId]?.code.orEmpty())
                }
                field("audience", "Grupe") {
                    textInput(name = "audience") {
                        id = "audience"
                        required = true
                        value = form["audience"] ?: activity?.audience?.let { audienceText(dataset, it) }
                            ?: form["group"].orEmpty()
                        placeholder = "1521A 1522A sau 1521A/A"
                    }
                    p("hint") { +"Grupele sau semigrupele, despărțite prin spațiu." }
                }
            }
            label("check") {
                checkBoxInput(name = "erasmus") {
                    checked =
                        (form["erasmus"] == "on") ||
                        (form["erasmus"] == null && activity?.openToErasmus == true)
                }
                +"Deschisă studenților Erasmus"
            }
            div("form-actions") {
                button { +(if (activity == null) "Adaugă activitatea" else "Salvează") }
                if (back != null) a(href = back, classes = "btn secondary") { +"Renunță" }
            }
        }
        if (activity != null) {
            form(action = "$action/delete", method = FormMethod.post) {
                onSubmit = "return confirm('Ștergi activitatea? Dispare din orarul tuturor grupelor ei.')"
                if (back != null) hiddenInput(name = "back") { value = back }
                button(classes = "danger") { +"Șterge activitatea" }
            }
        }
    }
}

private fun FlowContent.professorSelect(dataset: Dataset, selectedId: String?, fieldName: String) {
    select {
        id = fieldName
        name = fieldName
        option {
            value = ""
            +"Nimeni"
        }
        dataset.professors.sortedBy { TimetableSheet.fold(it.name) }.forEach { p ->
            option {
                value = p.id
                selected = p.id == selectedId
                +p.displayName.trim()
            }
        }
    }
}

private fun FlowContent.roomInput(dataset: Dataset, fieldName: String, current: String) {
    textInput(name = fieldName) {
        id = fieldName
        value = current
        placeholder = "BN212b"
        attributes["list"] = "rooms-list"
    }
    dataList {
        id = "rooms-list"
        dataset.rooms.sortedBy { it.code }.forEach { option { value = it.code } }
    }
    p("hint") { +"O sală nouă se adaugă singură." }
}

/** One row per activity, with a link to edit it. */
private fun FlowContent.activityTable(
    dataset: Dataset,
    activities: List<Activity>,
    showSubject: Boolean,
    showProfessor: Boolean = true,
) {
    if (activities.isEmpty()) {
        p("hint") { +"Nicio activitate." }
        return
    }
    div("scroll") {
        table {
            thead {
                tr {
                    th { +"Ziua și ora" }
                    th { +"Tip" }
                    if (showSubject) th { +"Materie" }
                    th { +"Grupe" }
                    if (showProfessor) th { +"Profesor" }
                    th { +"Sala" }
                    th { }
                }
            }
            tbody {
                activities.sortedWith(compareBy({ it.slot.isoDay }, { it.slot.start }, { it.type })).forEach { a ->
                    tr {
                        td("tnum") {
                            +"${DAY_NAMES[a.slot.isoDay - 1]}, ${a.slot.start} - ${a.slot.end}"
                            weeksLabel(a.slot.weeks)?.let { div("hint") { +it } }
                        }
                        td { +TYPE_NAMES.getValue(a.type) }
                        if (showSubject) {
                            td {
                                a(href = "${dataset.base()}/subjects/${a.subjectId.encodeURLParameter()}") {
                                    +(dataset.subjectsById[a.subjectId]?.name?.ro ?: a.subjectId)
                                }
                            }
                        }
                        td { +audienceText(dataset, a.audience) }
                        if (showProfessor) {
                            td {
                                val p = dataset.professorsById[a.professorId]
                                if (p != null) {
                                    a(href = "${dataset.base()}/professors/${p.id.encodeURLParameter()}") {
                                        +p.displayName.trim()
                                    }
                                } else {
                                    span("tag") { +"lipsește" }
                                }
                            }
                        }
                        td { +(dataset.roomsById[a.roomId]?.code ?: "") }
                        td("actions") {
                            a(
                                href = "${dataset.base()}/activities/${a.id.encodeURLParameter()}",
                                classes = "btn secondary",
                            ) {
                                +"Editează"
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun FlowContent.semesterLink(dataset: Dataset) {
    a(href = dataset.base()) { +dataset.semester.name.ro }
}

// ---- rooms ------------------------------------------------------------------------------

private fun Route.roomRoutes(services: Services) {
    page("/semesters/{id}/rooms", services) { admin ->
        val dataset = semesterOrNull(services) ?: return@page call.respond(HttpStatusCode.NotFound)
        val (msg, err) = message()
        val use = dataset.activities.groupingBy { it.roomId }.eachCount()
        call.respondHtml {
            adminPage(
                "Săli",
                admin,
                Section.ROOMS,
                context = {
                    semesterLink(dataset)
                    +" · ${dataset.rooms.size} săli în ${dataset.buildings.size} corpuri"
                },
            ) {
                notice(msg)
                notice(err, error = true)
                div("cols buildings") {
                    dataset.buildings.sortedBy { it.code }.forEach { building ->
                        val rooms = dataset.rooms.filter { it.buildingId == building.id }.sortedBy { it.code }
                        div("card") {
                            form(
                                action = "${dataset.base()}/buildings/${building.id.encodeURLParameter()}",
                                method = FormMethod.post,
                                classes = "building",
                            ) {
                                strong { +building.code }
                                textInput(name = "name") {
                                    value = building.name
                                    attributes["aria-label"] = "Numele corpului ${building.code}"
                                }
                                textInput(name = "address") {
                                    value = building.address.orEmpty()
                                    placeholder = "Adresa"
                                    attributes["aria-label"] = "Adresa corpului ${building.code}"
                                }
                                button(classes = "secondary") { +"Salvează" }
                            }
                            div("chips") {
                                rooms.forEach { room ->
                                    a(href = "${dataset.base()}/rooms/${room.id.encodeURLParameter()}") {
                                        +room.code
                                        span("hint") { +" ${use[room.id] ?: 0}" }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    action("/semesters/{id}/buildings/{building}", services) { admin ->
        val dataset = semesterOrNull(services) ?: return@action call.respond(HttpStatusCode.NotFound)
        val id = call.parameters["building"].orEmpty()
        val form = call.receiveParameters()
        val name = form["name"].orEmpty().trim()
        val list = "${dataset.base()}/rooms"
        if (id !in dataset.buildingsById ||
            name.isEmpty()
        ) {
            return@action call.respondRedirect("$list?${err("Numele corpului este obligatoriu.")}")
        }
        services.save(
            dataset.copy(
                buildings = dataset.buildings.map {
                    if (it.id ==
                        id
                    ) {
                        it.copy(name = name, address = form["address"].orEmpty().trim().ifEmpty { null })
                    } else {
                        it
                    }
                },
            ),
        )
        services.audit.record(admin, AuditCategory.TIMETABLE, "a modificat corpul $id")
        call.respondRedirect("$list?${msg("Corpul a fost salvat.")}")
    }

    page("/semesters/{id}/rooms/{room}", services) { admin ->
        val dataset = semesterOrNull(services) ?: return@page call.respond(HttpStatusCode.NotFound)
        val room =
            dataset.roomsById[call.parameters["room"].orEmpty()] ?: return@page call.respond(HttpStatusCode.NotFound)
        val activities = dataset.activities.filter { it.roomId == room.id }
        val office = dataset.professors.flatMap { p -> p.officeHours.filter { it.roomId == room.id }.map { p to it } }
        call.respondHtml {
            adminPage(
                "Sala ${room.code}",
                admin,
                Section.ROOMS,
                context = {
                    a(href = "${dataset.base()}/rooms") { +"Săli" }
                    +" · ${dataset.buildingsById[room.buildingId]?.name ?: room.buildingId}"
                },
            ) {
                p("hint") { +"Golurile din săptămână sunt orele în care sala e liberă după orar." }
                div("scroll") {
                    div("week") {
                        (1..5).forEach { day ->
                            div("day") {
                                h3 { +DAY_NAMES[day - 1] }
                                activities.filter { it.slot.isoDay == day }.sortedBy { it.slot.start }.forEach { a ->
                                    a(
                                        href = "${dataset.base()}/activities/${a.id.encodeURLParameter()}",
                                        classes = "slot ${slotKind(a.type)}".trim(),
                                    ) {
                                        span("time") {
                                            +"${a.slot.start} - ${a.slot.end} · ${TYPE_NAMES.getValue(a.type)}"
                                        }
                                        strong { +(dataset.subjectsById[a.subjectId]?.name?.ro ?: a.subjectId) }
                                        +listOfNotNull(
                                            audienceText(dataset, a.audience),
                                            weeksLabel(a.slot.weeks),
                                        ).joinToString(" · ")
                                    }
                                }
                                office.filter { it.second.slot.isoDay == day }.forEach { (p, hour) ->
                                    div("slot office") {
                                        span("time") { +"${hour.slot.start} - ${hour.slot.end} · Consultații" }
                                        strong { +p.displayName.trim() }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun slotKind(type: ActivityType) = when (type) {
    ActivityType.COURSE -> ""
    ActivityType.SEMINAR -> "seminar"
    ActivityType.LAB -> "lab"
    ActivityType.PROJECT -> "project"
}

// ---- students ---------------------------------------------------------------------------

private fun Route.studentRoutes(services: Services) {
    page("/students", services, AdminRole.ADMIN) { admin ->
        val query = call.parameters["q"].orEmpty()
        val (msg, err) = message()
        val paging = call.parameters.paging(services.accounts.count(query))
        val students = services.accounts.list(query, paging.offset, paging.size)
        val counts = services.accounts.counts()
        call.respondHtml {
            adminPage(
                "Studenți",
                admin,
                Section.STUDENTS,
                context = { +"${counts.total} conturi: ${counts.national} în grupe, ${counts.erasmus} Erasmus" },
            ) {
                notice(msg)
                notice(err, error = true)
                p("hint") {
                    +"Datele sunt cele scrise de studenți; nimic nu este verificat. "
                    +"O parolă nouă îi deconectează de pe toate telefoanele."
                }
                tableCard(
                    filters = {
                        form(action = "/admin/students", method = FormMethod.get, classes = "filters") {
                            searchInput(name = "q") {
                                value = query
                                placeholder = "Caută după email sau nume"
                            }
                            button(classes = "secondary") { +"Caută" }
                        }
                    },
                    footer = { pager(paging, "/admin/students", call.request.queryParameters) },
                ) {
                    if (students.isEmpty()) {
                        emptyState(
                            if (query.isBlank()) "Niciun cont de student" else "Niciun student găsit",
                            if (query.isBlank()) {
                                "Conturile apar când studenții se înscriu în aplicație."
                            } else {
                                "Caută după altă adresă sau alt nume."
                            },
                        )
                    } else {
                        table {
                            thead {
                                tr {
                                    th { +"Student" }
                                    th { +"Telefon" }
                                    th { +"Înscriere" }
                                    th { +"Cont creat" }
                                    th { }
                                }
                            }
                            tbody {
                                students.forEach { s ->
                                    tr {
                                        td {
                                            +s.profile.fullName.ifBlank { "(fără nume)" }
                                            div("hint") { +s.email }
                                        }
                                        td("tnum") { +s.profile.phone }
                                        td {
                                            +when (val e = s.profile.enrollment) {
                                                is Enrollment.National -> listOfNotNull(e.groupId, e.halfGroupId).last()
                                                is Enrollment.Erasmus -> "Erasmus, ${e.activityIds.size} ore"
                                                null -> "nu a ales"
                                            }
                                        }
                                        td("tnum") {
                                            val local = s.createdAt.toLocalDateTime(FacultyTimeZone)
                                            +longDate(local.date)
                                        }
                                        td("actions") {
                                            form(
                                                action = "/admin/students/${s.id}/password",
                                                method = FormMethod.post,
                                                classes = "inline-form",
                                            ) {
                                                passwordInput(name = "password") {
                                                    placeholder = "Parolă nouă"
                                                    minLength = "8"
                                                    required = true
                                                    attributes["autocomplete"] = "new-password"
                                                    attributes["aria-label"] = "Parolă nouă pentru ${s.email}"
                                                }
                                                button(classes = "secondary") { +"Schimbă" }
                                            }
                                            form(
                                                action = "/admin/students/${s.id}/delete",
                                                method = FormMethod.post,
                                                classes = "inline",
                                            ) {
                                                onSubmit =
                                                    "return confirm('Ștergi contul ${s.email}? Nu se poate anula.')"
                                                button(classes = "danger") { +"Șterge" }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    action("/students/{student}/password", services, AdminRole.ADMIN) { admin ->
        val id = runCatching { UUID.fromString(call.parameters["student"]) }.getOrNull()
            ?: return@action call.respond(HttpStatusCode.NotFound)
        val result = try {
            services.accounts.setPassword(id, call.receiveParameters()["password"].orEmpty())
            services.audit.record(
                admin,
                AuditCategory.STUDENTS,
                "a dat o parolă nouă contului ${services.accounts.email(id) ?: id}",
            )
            msg("Parola a fost schimbată; studentul se conectează din nou cu ea.")
        } catch (e: ApiProblem) {
            err("Parola trebuie să aibă cel puțin 8 caractere.")
        }
        call.respondRedirect("/admin/students?$result")
    }

    action("/students/{student}/delete", services, AdminRole.ADMIN) { admin ->
        val id = runCatching { UUID.fromString(call.parameters["student"]) }.getOrNull()
            ?: return@action call.respond(HttpStatusCode.NotFound)
        services.audit.record(
            admin,
            AuditCategory.STUDENTS,
            "a șters contul de student ${services.accounts.email(id) ?: id}",
        )
        services.accounts.delete(id)
        call.respondRedirect("/admin/students?${msg("Contul a fost șters.")}")
    }
}

private fun FlowContent.subjectFilters(dataset: Dataset, query: String, programme: String, year: Int?) {
    form(action = "${dataset.base()}/subjects", method = FormMethod.get, classes = "filters") {
        searchInput(name = "q") {
            value = query
            placeholder = "Caută după nume sau cod"
        }
        select {
            name = "programme"
            option {
                value = ""
                +"Toate specializările"
            }
            dataset.programmes.forEach { p ->
                option {
                    value = p.id
                    selected = p.id == programme
                    +p.code
                }
            }
        }
        select {
            name = "year"
            option {
                value = ""
                +"Toți anii"
            }
            dataset.subjects.map { it.year }.distinct().sorted().forEach { y ->
                option {
                    value = y.toString()
                    selected = y == year
                    +"Anul $y"
                }
            }
        }
        button(classes = "secondary") { +"Filtrează" }
    }
}

private fun FlowContent.subjectTable(dataset: Dataset, rows: List<Subject>) {
    table {
        thead {
            tr {
                th { +"Materie" }
                th { +"Specializare" }
                th(classes = "num") { +"An" }
                th(classes = "num") { +"Activități" }
                th(classes = "num") { +"Fără profesor" }
            }
        }
        tbody {
            rows.forEach { s ->
                val activities = dataset.activities.filter { it.subjectId == s.id }
                tr {
                    td {
                        a(href = "${dataset.base()}/subjects/${s.id.encodeURLParameter()}") {
                            +s.name.ro
                        }
                        div("hint") { +s.code }
                    }
                    td {
                        +(
                            dataset.programmes.firstOrNull { it.id == s.programmeId }?.code
                                ?: s.programmeId
                            )
                    }
                    td("num") { +s.year.toString() }
                    td("num") { +activities.size.toString() }
                    td("num") {
                        val missing = activities.count { it.professorId == null }
                        if (missing > 0) span("tag") { +missing.toString() }
                    }
                }
            }
        }
    }
}

private fun FlowContent.professorTable(dataset: Dataset, rows: List<Professor>) {
    table {
        thead {
            tr {
                th { +"Profesor" }
                th { +"Email" }
                th(classes = "num") { +"Activități" }
                th(classes = "num") { +"Consultații" }
            }
        }
        tbody {
            rows.forEach { p ->
                tr {
                    td {
                        a(href = "${dataset.base()}/professors/${p.id.encodeURLParameter()}") {
                            +p.name
                        }
                        div("hint") { +p.title }
                    }
                    td { if (p.email != null) +p.email.orEmpty() else span("tag") { +"lipsește" } }
                    td("num") { +dataset.activities.count { it.professorId == p.id }.toString() }
                    td("num") { +p.officeHours.size.toString() }
                }
            }
        }
    }
}

/** "Microeconomie, seminar, Luni 10:00", for the log. */
internal fun activityLabel(dataset: Dataset, activity: Activity): String {
    val subject = dataset.subjectsById[activity.subjectId]?.name?.ro ?: activity.subjectId
    val type = TYPE_NAMES[activity.type]?.lowercase()
    val day = DAY_NAMES.getOrNull(activity.slot.isoDay - 1)
    return listOfNotNull(subject, type, day?.let { "$it ${activity.slot.start}" }).joinToString(", ")
}
