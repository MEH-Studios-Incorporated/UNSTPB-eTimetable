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
import kotlinx.datetime.LocalTime
import kotlinx.html.*
import net.mehstudios.etimetable.model.Activity
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.server.Services
import net.mehstudios.etimetable.server.timetable.TimetableSheet

internal fun Route.scheduleRoutes(services: Services) {
    page("/schedule", services) { _ ->
        val id = services.datasets.current()?.semesterId ?: return@page call.respondRedirect("/admin/semesters")
        call.respondRedirect("/admin/semesters/${id.encodeURLParameter()}/schedule")
    }
    page("/semesters/{id}/schedule", services) { admin ->
        val id = call.parameters["id"].orEmpty()
        // Read the revision before the dataset, so a concurrent edit cannot be silently overwritten.
        val version = services.datasets.version(id) ?: return@page call.respond(HttpStatusCode.NotFound)
        val dataset = services.datasets.dataset(id) ?: return@page call.respond(HttpStatusCode.NotFound)
        val q = TimetableSheet.fold(call.parameters["q"].orEmpty())
        val day = call.parameters["day"]?.toIntOrNull()
        val incomplete = call.parameters["incomplete"] == "on"
        val activities = dataset.activities.filter { a ->
            (day == null || a.slot.isoDay == day) &&
                (!incomplete || a.roomId == null || a.professorId == null) &&
                (q.isEmpty() || TimetableSheet.fold(searchText(dataset, a)).contains(q))
        }.sortedWith(compareBy({ it.slot.isoDay }, { it.slot.start }, { it.subjectId }))
        val paging = call.parameters.paging(activities.size, defaultSize = 25)
        val path = "/admin/semesters/${id.encodeURLParameter()}/schedule"
        call.respondHtml {
            adminPage(
                "Gestionare orar",
                admin,
                Section.SCHEDULE,
                context = { +dataset.semester.name.ro },
                actions = {
                    a(href = "/admin/semesters/${id.encodeURLParameter()}/activities/new", classes = "btn") {
                        +"Adaugă activitate"
                    }
                },
            ) {
                notice(call.parameters["msg"])
                notice(call.parameters["err"], error = true)
                tableCard(
                    filters = {
                        form(action = path, method = FormMethod.get, classes = "filters") {
                            searchInput(name = "q") {
                                value = call.parameters["q"].orEmpty()
                                placeholder = "Materie, profesor, sală sau grupă"
                                attributes["aria-label"] = "Caută activități"
                            }
                            select {
                                name = "day"
                                attributes["aria-label"] = "Ziua"
                                option {
                                    value = ""
                                    +"Toate zilele"
                                }
                                DAY_NAMES.forEachIndexed { index, label ->
                                    option {
                                        value = (index + 1).toString()
                                        selected = day == index + 1
                                        +label
                                    }
                                }
                            }
                            label("check") {
                                checkBoxInput(name = "incomplete") { checked = incomplete }
                                +"Fără sală sau profesor"
                            }
                            button(classes = "secondary") { +"Filtrează" }
                        }
                    },
                    footer = { pager(paging, path, call.request.queryParameters) },
                ) {
                    if (activities.isEmpty()) {
                        emptyState(
                            "Nicio activitate",
                            if (dataset.activities.isEmpty()) {
                                "Importă orarul sau adaugă o activitate."
                            } else {
                                "Schimbă căutarea sau filtrele."
                            },
                        )
                    } else {
                        table {
                            thead {
                                tr {
                                    th {
                                        checkBoxInput {
                                            attributes["data-select-all"] = "activity"
                                            attributes["aria-label"] = "Selectează toate rândurile de pe pagină"
                                            attributes["hidden"] = "hidden"
                                        }
                                    }
                                    listOf("Activitate", "Când", "Profesor / sală", "Grupe").forEach { th { +it } }
                                }
                            }
                            tbody {
                                paging.slice(activities).forEach { a ->
                                    tr {
                                        td {
                                            checkBoxInput(name = "activity") {
                                                value = a.id
                                                attributes["form"] = "schedule-bulk"
                                                attributes["aria-label"] =
                                                    "Selectează ${a.id}"
                                            }
                                        }
                                        td {
                                            a(
                                                href = "/admin/semesters/${id.encodeURLParameter()}/activities/" +
                                                    a.id.encodeURLParameter(),
                                            ) {
                                                +(
                                                    dataset.subjectsById[a.subjectId]?.name?.ro
                                                        ?: a.subjectId
                                                    )
                                            }
                                            div("hint") { +TYPE_NAMES.getValue(a.type) }
                                        }
                                        td {
                                            +"${DAY_NAMES[a.slot.isoDay - 1]} ${a.slot.start} - ${a.slot.end}"
                                            div("hint") { +TimetableSheet.weeksText(a.slot.weeks) }
                                        }
                                        td {
                                            +(dataset.professorsById[a.professorId]?.displayName ?: "Fără profesor")
                                            div("hint") {
                                                +(
                                                    dataset.roomsById[a.roomId]?.code
                                                        ?: "Fără sală"
                                                    )
                                            }
                                        }
                                        td { +audience(dataset, a) }
                                    }
                                }
                            }
                        }
                    }
                }
                form(action = path, method = FormMethod.post) {
                    this.id = "schedule-bulk"
                    hiddenInput(name = "version") { value = version.toString() }
                    p("hint") {
                        +"Bifează rândurile de modificat, apoi alege ce se schimbă și verifică previzualizarea."
                    }
                    div("card") {
                        h2 { +"Modifică selecția" }
                        div("field") {
                            label {
                                htmlFor = "bulk-room"
                                +"Sala"
                            }
                            select {
                                this.id = "bulk-room"
                                name = "room"
                                option {
                                    value = ""
                                    +"Păstrează"
                                }
                                option {
                                    value =
                                        "none"
                                    +"Fără sală"
                                }
                                dataset.rooms.forEach {
                                    option {
                                        value = it.id
                                        +it.code
                                    }
                                }
                            }
                        }
                        div("field") {
                            label {
                                htmlFor = "bulk-professor"
                                +"Profesor"
                            }
                            select {
                                this.id = "bulk-professor"
                                name = "professor"
                                option {
                                    value = ""
                                    +"Păstrează"
                                }
                                option {
                                    value =
                                        "none"
                                    +"Fără profesor"
                                }
                                dataset.professors.forEach {
                                    option {
                                        value = it.id
                                        +it.displayName
                                    }
                                }
                            }
                        }
                        div("field") {
                            label {
                                htmlFor = "bulk-shift"
                                +"Deplasare în minute (de exemplu 60 sau -30)"
                            }
                            numberInput(name = "shift") {
                                this.id = "bulk-shift"
                                value =
                                    "0"
                                min = "-720"
                                max = "720"
                            }
                        }
                        div("form-actions") {
                            button { +"Previzualizează modificările" }
                            span("hint") {
                                attributes["data-selected-count"] = ""
                                attributes["aria-live"] = "polite"
                            }
                        }
                    }
                }
            }
        }
    }
    action("/semesters/{id}/schedule", services) { admin ->
        val id = call.parameters["id"].orEmpty()
        val dataset = services.datasets.dataset(id) ?: return@action call.respond(HttpStatusCode.NotFound)
        val form = call.receiveParameters()
        try {
            val version = requireNotNull(form["version"]?.toLongOrNull()) { "Lipsește versiunea orarului." }
            check(services.datasets.version(id) == version) { "Orarul a fost modificat. Reîncarcă pagina." }
            val next = applyScheduleChange(dataset, form)
            if (form["confirm"] == "yes") {
                services.datasets.replace(next, makeCurrent = false, expectedVersion = version)
                return@action call.respondRedirect(
                    "/admin/semesters/${id.encodeURLParameter()}/schedule?msg=Modificările+au+fost+salvate.",
                )
            }
            val selected = form.getAll("activity").orEmpty().toSet()
            val existingWarnings = TimetableSheet.doubleBookings(dataset).toSet()
            val warnings = TimetableSheet.doubleBookings(next).filterNot { it in existingWarnings }
            call.respondHtml {
                adminPage("Verifică modificările", admin, Section.SCHEDULE) {
                    val verb = if (selected.size == 1) "va fi modificată" else "vor fi modificate"
                    p { +"${activityCount(selected.size)} $verb. Grupele și săptămânile rămân aceleași." }
                    next.activities.filter { it.id in selected }.forEach { a ->
                        p("card") {
                            +listOfNotNull(
                                dataset.subjectsById[a.subjectId]?.name?.ro,
                                "${DAY_NAMES[a.slot.isoDay - 1]} ${a.slot.start} - ${a.slot.end}",
                                dataset.roomsById[a.roomId]?.code ?: "fără sală",
                                dataset.professorsById[a.professorId]?.displayName ?: "fără profesor",
                            ).joinToString(" · ")
                        }
                    }
                    warnings.take(50).forEach { p("notice error") { +it } }
                    if (warnings.size > 50) p { +"${warnings.size} suprapuneri noi; sunt afișate primele 50." }
                    if (warnings.isNotEmpty()) p { +"Confirmă numai dacă aceste suprapuneri sunt intenționate." }
                    form(method = FormMethod.post) {
                        form.entries().forEach { (key, values) ->
                            values.forEach { v ->
                                hiddenInput(name = key) {
                                    value =
                                        v
                                }
                            }
                        }
                        hiddenInput(name = "confirm") { value = "yes" }
                        button { +"Confirmă și salvează" }
                        a(href = "/admin/semesters/${id.encodeURLParameter()}/schedule", classes = "btn secondary") {
                            +"Renunță"
                        }
                    }
                }
            }
        } catch (e: IllegalArgumentException) {
            call.respondRedirect(
                "/admin/semesters/${id.encodeURLParameter()}/schedule?err=${e.message.orEmpty().encodeURLParameter()}",
            )
        } catch (e: IllegalStateException) {
            call.respondRedirect(
                "/admin/semesters/${id.encodeURLParameter()}/schedule?err=${e.message.orEmpty().encodeURLParameter()}",
            )
        }
    }
}

internal fun applyScheduleChange(dataset: Dataset, form: Parameters): Dataset {
    val ids = form.getAll("activity").orEmpty().toSet()
    require(ids.isNotEmpty() && ids.size <= 200) { "Selectează între 1 și 200 de activități." }
    require(ids.all { it in dataset.activitiesById }) { "Selecția conține o activitate necunoscută." }
    val room = form["room"].orEmpty()
    val professor = form["professor"].orEmpty()
    require(room in setOf("", "none") || room in dataset.roomsById) { "Sala nu există." }
    require(professor in setOf("", "none") || professor in dataset.professorsById) { "Profesorul nu există." }
    val shift = requireNotNull(form["shift"]?.toIntOrNull()) { "Deplasarea trebuie să fie un număr." }
    require(shift in -720..720 && (shift != 0 || room.isNotEmpty() || professor.isNotEmpty())) {
        "Alege o modificare validă."
    }
    fun shifted(time: LocalTime): LocalTime {
        val seconds = time.toSecondOfDay() + shift * 60
        require(seconds in 0..86399) { "O activitate ar trece în altă zi." }
        return LocalTime.fromSecondOfDay(seconds)
    }
    return dataset.copy(
        activities = dataset.activities.map { a ->
            if (a.id !in ids) {
                a
            } else {
                a.copy(
                    roomId = if (room.isEmpty()) a.roomId else room.takeUnless { it == "none" },
                    professorId = if (professor.isEmpty()) a.professorId else professor.takeUnless { it == "none" },
                    slot = a.slot.copy(start = shifted(a.slot.start), end = shifted(a.slot.end)),
                )
            }
        },
    )
}

private fun activityCount(n: Int) = "$n ${plural(n, "activitate", "activități")}"

private fun audience(dataset: Dataset, a: Activity): String = (
    a.audience.seriesIds.map { id -> "Seria ${dataset.series.firstOrNull { it.id == id }?.name ?: id}" } +
        a.audience.groupIds.map { dataset.groupsById[it]?.code ?: it } + a.audience.halfGroupIds
    ).joinToString(" ")

private fun searchText(dataset: Dataset, a: Activity) = listOfNotNull(
    dataset.subjectsById[a.subjectId]?.name?.ro,
    dataset.subjectsById[a.subjectId]?.name?.en,
    dataset.professorsById[a.professorId]?.displayName,
    dataset.roomsById[a.roomId]?.code,
    audience(dataset, a),
    dataset.groups.filter { it.seriesId in a.audience.seriesIds }.joinToString(" ") { it.code },
).joinToString(" ")
