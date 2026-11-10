// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import io.ktor.http.Parameters
import io.ktor.http.encodeURLParameter
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.RoutingContext
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.SELECT
import kotlinx.html.a
import kotlinx.html.aside
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.hiddenInput
import kotlinx.html.id
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.noScript
import kotlinx.html.optGroup
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.style
import kotlinx.html.ul
import net.mehstudios.etimetable.model.Activity
import net.mehstudios.etimetable.model.Audience
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Enrollment
import net.mehstudios.etimetable.schedule.TeachingCalendar
import net.mehstudios.etimetable.schedule.baseActivities
import net.mehstudios.etimetable.schedule.overlaps
import net.mehstudios.etimetable.server.timetable.TimetableSheet

/** Whose week the view shows: a group or half-group, a room or a professor, from the query. */
internal sealed interface WeekSelection {
    val title: String

    data class OfGroup(val groupId: String, val halfGroupId: String?, override val title: String) : WeekSelection
    data class OfRoom(val roomId: String, override val title: String) : WeekSelection
    data class OfProfessor(val professorId: String, override val title: String) : WeekSelection

    companion object {
        /** `group` (a group or half-group id), `room` or `professor`; the first group when none is given. */
        fun of(dataset: Dataset, query: Parameters): WeekSelection? {
            val key = query["group"].orEmpty()
            dataset.halfGroupsById[key]?.let { half ->
                val group = dataset.groupsById[half.groupId] ?: return@let null
                return OfGroup(group.id, half.id, "Semigrupa ${group.code}/${half.suffix}")
            }
            dataset.groupsById[key]?.let { return OfGroup(it.id, null, "Grupa ${it.code}") }
            dataset.roomsById[query["room"].orEmpty()]?.let { return OfRoom(it.id, "Sala ${it.code}") }
            dataset.professorsById[query["professor"].orEmpty()]?.let {
                return OfProfessor(it.id, it.displayName.trim())
            }
            return dataset.groups.minByOrNull { it.code }?.let { OfGroup(it.id, null, "Grupa ${it.code}") }
        }
    }
}

/** The blocks of [selection]'s week; each class links to [href]. */
internal fun weekEntries(dataset: Dataset, selection: WeekSelection, href: (Activity) -> String): List<GridEntry> {
    val colours = subjectColours(dataset)
    fun professor(a: Activity) = dataset.professorsById[a.professorId]?.displayName?.trim()
    return when (selection) {
        is WeekSelection.OfGroup -> {
            val group = dataset.groupsById.getValue(selection.groupId)
            baseActivities(dataset, Enrollment.National(group.id, selection.halfGroupId)).map { a ->
                classEntry(dataset, colours, a, href(a), halvesOf(dataset, group, a), who = professor(a))
            }
        }
        is WeekSelection.OfRoom ->
            dataset.activities.filter { it.roomId == selection.roomId }.map { a ->
                val who = listOfNotNull(audience(dataset, a), professor(a)).joinToString(" · ")
                classEntry(dataset, colours, a, href(a), showRoom = false, who = who)
            } + dataset.professors.flatMap { p ->
                p.officeHours.withIndex().filter { it.value.roomId == selection.roomId }
                    .map { (i, hour) -> officeEntry(dataset, p, hour, i) }
            }
        is WeekSelection.OfProfessor -> {
            val professor = dataset.professorsById.getValue(selection.professorId)
            dataset.activities.filter { it.professorId == professor.id }.map { a ->
                classEntry(dataset, colours, a, href(a), who = audience(dataset, a))
            } + professor.officeHours.mapIndexed { i, hour -> officeEntry(dataset, professor, hour, i, "Consultații") }
        }
    }
}

/**
 * The other activities that meet at the same time as [activity] in at least one week and
 * share its room, its professor or some of its students, each with what they share.
 */
internal fun clashesOf(dataset: Dataset, calendar: TeachingCalendar, activity: Activity): List<Pair<Activity, String>> {
    val students = students(dataset, activity.audience)
    return dataset.activities.filter { it.id != activity.id && calendar.overlaps(it.slot, activity.slot) }
        .mapNotNull { other ->
            val shared = listOfNotNull(
                "aceeași sală".takeIf { activity.roomId != null && other.roomId == activity.roomId },
                "același profesor".takeIf { activity.professorId != null && other.professorId == activity.professorId },
                "aceleași grupe".takeIf { students(dataset, other.audience).any { it in students } },
            )
            if (shared.isEmpty()) null else other to shared.joinToString(", ")
        }
}

/** An audience as the smallest units it covers: half-groups, or whole groups that have no halves. */
private fun students(dataset: Dataset, audience: Audience): Set<String> {
    val groups = dataset.groups.filter { it.seriesId in audience.seriesIds || it.id in audience.groupIds }
    val halves = dataset.halfGroups.groupBy { it.groupId }
    return groups.flatMapTo(mutableSetOf()) { g -> halves[g.id]?.map { it.id } ?: listOf(g.id) } + audience.halfGroupIds
}

/** "Tabel" and "Săptămână", the two views of Gestionare orar. */
internal fun FlowContent.scheduleTabs(path: String, week: Boolean) {
    div("tabs") {
        style = "margin-bottom: 16px"
        a(href = path, classes = if (week) null else "active") {
            if (!week) attributes["aria-current"] = "page"
            +"Tabel"
        }
        a(href = "$path?view=week", classes = if (week) "active" else null) {
            if (week) attributes["aria-current"] = "page"
            +"Săptămână"
        }
    }
}

/** Gestionare orar as a week: pick whose, click a class to edit it in the panel beside the grid. */
internal suspend fun RoutingContext.scheduleWeek(admin: Admin, dataset: Dataset) {
    val query = call.request.queryParameters
    val base = "/admin/semesters/${dataset.semester.id.encodeURLParameter()}"
    val path = "$base/schedule"
    val selection = WeekSelection.of(dataset, query)
    val selected = query["activity"]?.let { dataset.activitiesById[it] }
    val calendar = TeachingCalendar(dataset.semester)
    val current = when (selection) {
        is WeekSelection.OfGroup -> mapOf("group" to (selection.halfGroupId ?: selection.groupId))
        is WeekSelection.OfRoom -> mapOf("room" to selection.roomId)
        is WeekSelection.OfProfessor -> mapOf("professor" to selection.professorId)
        null -> emptyMap()
    }
    // links and the panel's return address carry the selection even when it was the default
    val viewQuery = Parameters.build {
        append("view", "week")
        current.forEach { (k, v) -> append(k, v) }
        query[WeekFilter.PARAM]?.let { append(WeekFilter.PARAM, it) }
    }
    val entries = selection?.let { s ->
        weekEntries(dataset, s) { linkTo(path, viewQuery, mapOf("activity" to it.id)) }
    }
        .orEmpty()
    val classes = entries.filter { it.colour != null }
    val roomless = classes.count { it.roomless }
    call.respondHtml {
        adminPage(
            "Gestionare orar",
            admin,
            Section.SCHEDULE,
            context = {
                +listOfNotNull(
                    selection?.title,
                    dataset.semester.name.ro,
                    "${classes.size} ${plural(classes.size, "activitate", "activități")}" +
                        (if (roomless > 0) ", $roomless fără sală" else ""),
                ).joinToString(" · ")
            },
            actions = {
                val prefill = when (selection) {
                    is WeekSelection.OfGroup ->
                        "group" to (selection.halfGroupId ?: dataset.groupsById.getValue(selection.groupId).code)
                    is WeekSelection.OfRoom -> "room" to dataset.roomsById.getValue(selection.roomId).code
                    is WeekSelection.OfProfessor -> "professor" to selection.professorId
                    null -> null
                }
                val back = linkTo(path, viewQuery, emptyMap())
                a(
                    href = "$base/activities/new?" + listOfNotNull(prefill, "back" to back)
                        .joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" },
                    classes = "btn",
                ) { +"Adaugă activitate" }
            },
        ) {
            notice(query["msg"])
            notice(query["err"], error = true)
            scheduleTabs(path, week = true)
            if (selection == null) {
                emptyState("Nicio grupă", "Importă orarul semestrului ca să apară grupele, sălile și profesorii.")
                return@adminPage
            }
            div("wg-layout") {
                weekGrid(entries, calendar, path, viewQuery, selected?.id) {
                    pickers(dataset, path, viewQuery, current)
                }
                if (selected != null) {
                    panel(
                        dataset,
                        calendar,
                        selected,
                        back = linkTo(path, viewQuery, mapOf("activity" to selected.id)),
                        close = linkTo(path, viewQuery, emptyMap()),
                    )
                }
            }
        }
    }
}

/** Three small forms, one per kind, so picking one replaces the others. */
private fun FlowContent.pickers(dataset: Dataset, path: String, query: Parameters, current: Map<String, String>) {
    picker(path, query, "group", "Grupă", current["group"]) {
        dataset.series.forEach { series ->
            val programme = dataset.programmes.firstOrNull { it.id == series.programmeId }?.code
            optGroup(listOfNotNull(programme, "anul ${series.year}", "seria ${series.name}").joinToString(", ")) {
                dataset.groups.filter { it.seriesId == series.id }.sortedBy { it.code }.forEach { g ->
                    option {
                        value = g.id
                        selected = g.id == current["group"]
                        +g.code
                    }
                    dataset.halfGroups.filter { it.groupId == g.id }.sortedBy { it.suffix }.forEach { h ->
                        option {
                            value = h.id
                            selected = h.id == current["group"]
                            +"${g.code}/${h.suffix}"
                        }
                    }
                }
            }
        }
    }
    picker(path, query, "room", "Sală", current["room"]) {
        dataset.rooms.sortedBy { it.code }.forEach { r ->
            option {
                value = r.id
                selected = r.id == current["room"]
                +r.code
            }
        }
    }
    picker(path, query, "professor", "Profesor", current["professor"]) {
        dataset.professors.sortedBy { TimetableSheet.fold(it.name) }.forEach { p ->
            option {
                value = p.id
                selected = p.id == current["professor"]
                +p.displayName.trim()
            }
        }
    }
}

private fun FlowContent.picker(
    path: String,
    query: Parameters,
    field: String,
    text: String,
    current: String?,
    options: SELECT.() -> Unit,
) {
    form(action = path, method = FormMethod.get, classes = "wg-pick") {
        hiddenInput(name = "view") { value = "week" }
        query[WeekFilter.PARAM]?.let { hiddenInput(name = WeekFilter.PARAM) { value = it } }
        label {
            htmlFor = "wg-$field"
            +text
        }
        select {
            id = "wg-$field"
            name = field
            attributes["data-autosubmit"] = ""
            option {
                value = ""
                selected = current == null
                +"Alege"
            }
            options()
        }
        noScript { button(classes = "secondary small") { +"Arată" } }
    }
}

/** The selected class: what it is, what it clashes with, and the activity form. */
private fun FlowContent.panel(
    dataset: Dataset,
    calendar: TeachingCalendar,
    activity: Activity,
    back: String,
    close: String,
) {
    aside("wg-panel") {
        attributes["aria-label"] = "Editează activitatea"
        subjectColours(dataset)[activity.subjectId]?.let { style = "--sc: $it" }
        div("head") {
            span("bar")
            h2 { +"Editează activitatea" }
            a(href = close, classes = "btn secondary small") {
                attributes["aria-label"] = "Închide panoul"
                +"Închide"
            }
        }
        div("body") {
            p { strong { +(dataset.subjectsById[activity.subjectId]?.name?.ro ?: activity.subjectId) } }
            p("hint tnum") {
                +listOfNotNull(
                    TYPE_NAMES.getValue(activity.type),
                    "${DAY_NAMES[activity.slot.isoDay - 1]} ${activity.slot.start} - ${activity.slot.end}",
                    weeksLabel(activity.slot.weeks),
                ).joinToString(" · ")
            }
            p("hint") {
                +listOfNotNull(
                    dataset.roomsById[activity.roomId]?.code?.let { "Sala $it" } ?: "Fără sală",
                    dataset.professorsById[activity.professorId]?.displayName?.trim() ?: "Fără profesor",
                    audience(dataset, activity).ifEmpty { null },
                ).joinToString(" · ")
            }
            val clashes = clashesOf(dataset, calendar, activity)
            val missing = listOfNotNull(
                "Nu are sală.".takeIf { activity.roomId == null },
                "Nu are profesor.".takeIf { activity.professorId == null },
            )
            div(if (clashes.isEmpty() && missing.isEmpty()) "wg-check" else "wg-check bad") {
                span("dot")
                div {
                    if (clashes.isEmpty()) {
                        +"Nicio suprapunere cu altă activitate a sălii, a profesorului sau a grupelor."
                    } else {
                        +"Se suprapune cu:"
                        ul {
                            clashes.forEach { (other, shared) ->
                                li { +"${activityLabel(dataset, other)} ($shared)" }
                            }
                        }
                    }
                    missing.forEach { p("hint") { +it } }
                }
            }
            activityForm(dataset, activity, Parameters.Empty, back, cancel = close)
        }
    }
}
