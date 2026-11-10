// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import io.ktor.http.encodeURLParameter
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.RoutingContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.*
import net.mehstudios.etimetable.model.Activity
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Event
import net.mehstudios.etimetable.model.Parity
import net.mehstudios.etimetable.schedule.FacultyTimeZone
import net.mehstudios.etimetable.schedule.TeachingCalendar
import net.mehstudios.etimetable.schedule.facultyNow
import net.mehstudios.etimetable.schedule.meetsOn
import net.mehstudios.etimetable.server.Services
import net.mehstudios.etimetable.server.reports.TimetableReport
import net.mehstudios.etimetable.server.timetable.TimetableSheet
import kotlin.time.Clock

private val MONTH_NAMES = listOf(
    "ianuarie", "februarie", "martie", "aprilie", "mai", "iunie",
    "iulie", "august", "septembrie", "octombrie", "noiembrie", "decembrie",
)
private val SHORT_MONTHS = listOf("ian", "feb", "mar", "apr", "mai", "iun", "iul", "aug", "sep", "oct", "nov", "dec")

/** The start page: the week, what is happening now, what needs doing, and what changed. */
internal suspend fun RoutingContext.overview(services: Services, admin: Admin) {
    val dataset = services.datasets.current()?.dataset
    val now = Clock.System.facultyNow()
    val reports = services.reports.page(closed = false, offset = 0, limit = 4)
    val openReports = services.reports.count(closed = false)
    val events = services.events.forApps().filter { it.over > Clock.System.now() }.take(3)
    val students = services.accounts.counts()
    val isAdmin = admin.role == AdminRole.ADMIN
    val audit = if (isAdmin) services.audit.page(AuditFilter(), 0, 6) else emptyList()
    val lastTimetableChange = services.audit.page(AuditFilter(category = AuditCategory.TIMETABLE), 0, 1).firstOrNull()
    val security = if (isAdmin) {
        SecurityFacts(
            signIns = services.admins.latestSignIns(3),
            accounts = services.admins.list(),
            sessions = services.admins.sessions(admin.id).size,
            failed = services.admins.failedLoginsToday(),
        )
    } else {
        null
    }
    call.respondHtml {
        adminPage(
            "Prezentare",
            admin,
            Section.OVERVIEW,
            context = { +"${DAY_NAMES[now.date.dayOfWeek.ordinal]}, ${longDate(now.date)}" },
            actions = {
                if (dataset != null) {
                    a(
                        href = "/admin/semesters/${dataset.semester.id.encodeURLParameter()}#import",
                        classes = "btn secondary",
                    ) {
                        +"Importă orarul"
                    }
                }
                a(href = "/admin/events#new", classes = "btn secondary") { +"Eveniment nou" }
                a(href = "/admin/announcements#new", classes = "btn") { +"Anunț nou" }
            },
        ) {
            if (dataset == null) {
                div("card") {
                    h2 { +"Niciun semestru în aplicație" }
                    p {
                        +"Aplicația nu are încă ce arăta. Creează semestrul, importă orarul, apoi fă-l curent. "
                        a(href = "/admin/semesters") { +"Mergi la semestre" }
                    }
                }
                return@adminPage
            }
            weekStrip(dataset, now.date, lastTimetableChange, students.total)
            div("overview") {
                div("ov-main") {
                    nowCard(dataset, now)
                    reportsCard(reports, openReports)
                    if (isAdmin) auditCard(audit)
                }
                div("ov-side") {
                    div("ov-card") {
                        div("ov-head") { h2 { +"De făcut" } }
                        todo(dataset)
                    }
                    eventsCard(events)
                    if (security != null) securityCard(security)
                }
            }
        }
    }
}

private fun FlowContent.weekStrip(dataset: Dataset, today: LocalDate, lastChange: AuditEntry?, students: Int) {
    val calendar = TeachingCalendar(dataset.semester)
    val week = calendar.weekOf(today)
    val pause = calendar.breakOn(today)
    val nextBreak = dataset.semester.breaks.filter { it.firstDay > today }.minByOrNull { it.firstDay }
    section("weekstrip") {
        attributes["aria-label"] = "Săptămâna curentă"
        div("num tnum") { +(week?.number?.toString() ?: "-") }
        div("part") {
            strong {
                +when {
                    week != null -> "Săptămâna ${week.number}, ${if (week.parity == Parity.ODD) "impară" else "pară"}"
                    pause != null -> pause.name.ro
                    today < dataset.semester.firstDay -> "Semestrul începe pe ${longDate(dataset.semester.firstDay)}"
                    else -> "Semestrul s-a încheiat"
                }
            }
            span {
                +listOfNotNull(
                    week?.let {
                        val friday = LocalDate.fromEpochDays(it.monday.toEpochDays() + 4)
                        "${it.monday.day} ${SHORT_MONTHS[it.monday.month.ordinal]} - " +
                            "${friday.day} ${SHORT_MONTHS[friday.month.ordinal]}"
                    },
                    week?.let { "săptămâna ${it.number} din ${calendar.weeks.size}" },
                    nextBreak?.let {
                        "${it.name.ro.lowercase()} începe pe ${it.firstDay.day} ${MONTH_NAMES[it.firstDay.month.ordinal]}"
                    },
                ).joinToString(" · ")
            }
        }
        div("part") {
            strong {
                span("dot ok")
                +dataset.semester.name.ro
            }
            span {
                +"${dataset.activities.size} clase · ${dataset.groups.size} grupe · $students conturi de studenți"
                if (lastChange != null) +" · ultima modificare ${shortTime(lastChange.at)}, ${lastChange.adminEmail}"
            }
        }
    }
}

private fun FlowContent.nowCard(dataset: Dataset, now: LocalDateTime) {
    val calendar = TeachingCalendar(dataset.semester)
    val today = dataset.activities.filter { calendar.meetsOn(it.slot, now.date) }
    val running = today.filter { it.slot.start <= now.time && now.time < it.slot.end }
    val next = today.filter { it.slot.start > now.time }
    val nextStart = next.minOfOrNull { it.slot.start }
    val shown = (running.ifEmpty { next.filter { it.slot.start == nextStart } }).sortedWith(
        compareBy({ it.slot.start }, { dataset.subjectsById[it.subjectId]?.name?.ro.orEmpty() }),
    )
    val colours = subjectColours(dataset)
    section("ov-card") {
        div("ov-head") {
            h2 {
                if (running.isNotEmpty()) span("dot open")
                +(if (running.isNotEmpty()) "Acum în facultate" else "Următoarele clase")
            }
            span("hint") {
                val time = now.time.toString().take(5)
                +when {
                    running.isNotEmpty() && nextStart != null ->
                        "$time · ${running.size} ${plural(running.size, "clasă", "clase")} în desfășurare, " +
                            "${next.count { it.slot.start == nextStart }} încep la $nextStart"
                    running.isNotEmpty() -> "$time · ${running.size} ${plural(
                        running.size,
                        "clasă",
                        "clase",
                    )} în desfășurare"
                    nextStart != null -> "$time · nicio clasă acum; următoarele încep la $nextStart"
                    else -> "$time · nicio clasă azi"
                }
            }
            a(href = "/admin/schedule?day=${now.date.dayOfWeek.ordinal + 1}", classes = "more") { +"Tot orarul de azi" }
        }
        if (shown.isEmpty()) {
            emptyState(
                if (now.date.dayOfWeek in
                    listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                ) {
                    "Weekend"
                } else {
                    "Nicio clasă azi"
                },
                "Orarul de azi nu are alte clase.",
            )
            return@section
        }
        div("scroll") {
            table {
                thead {
                    tr {
                        th { +"Ora" }
                        th { +"Materie" }
                        th { +"Tip" }
                        th { +"Grupe" }
                        th { +"Sală" }
                        th { +"Cadru didactic" }
                    }
                }
                tbody {
                    shown.take(8).forEach { a ->
                        tr {
                            td("tnum") {
                                style = "white-space: nowrap"
                                +"${a.slot.start} - ${a.slot.end}"
                            }
                            td {
                                span("bar") { style = "background: ${colours[a.subjectId] ?: SUBJECT_COLOURS[0]}" }
                                +(dataset.subjectsById[a.subjectId]?.name?.ro ?: a.subjectId)
                            }
                            td { +TYPE_NAMES.getValue(a.type) }
                            td { +audienceOf(dataset, a) }
                            td {
                                val room = dataset.roomsById[a.roomId]?.code
                                if (room == null) span("missing") { +"Fără sală" } else +room
                            }
                            td { +(dataset.professorsById[a.professorId]?.displayName ?: "-") }
                        }
                    }
                }
            }
        }
        if (shown.size > 8) p("ov-foot") { +"Încă ${shown.size - 8} ${plural(shown.size - 8, "clasă", "clase")}." }
    }
}

private fun FlowContent.reportsCard(reports: List<TimetableReport>, open: Int) {
    section("ov-card") {
        div("ov-head") {
            h2 {
                +"Probleme de orar deschise"
                span("count") { +open.toString() }
            }
            a(href = "/admin/reports", classes = "more") { +"Toate problemele" }
        }
        if (reports.isEmpty()) {
            emptyState("Nicio problemă deschisă", "Studenții raportează din aplicație, de pe pagina unei clase.")
            return@section
        }
        ul("ov-list") {
            reports.forEach { r ->
                li {
                    a(href = "/admin/reports?selected=${r.id}") {
                        span("what") {
                            strong {
                                +"${r.snapshot.subjectName}, ${TYPE_NAMES[r.snapshot.activity.type]?.lowercase()}"
                            }
                            span("hint") { +" · ${r.snapshot.audience.ifBlank { "fără grupe" }}" }
                        }
                        span("text") { +r.description.take(140) }
                    }
                    span("when tnum") { +shortTime(r.createdAt) }
                }
            }
        }
        if (open > reports.size) {
            p("ov-foot") {
                +"Încă ${open - reports.size} ${plural(open - reports.size, "problemă deschisă", "probleme deschise")}."
            }
        }
    }
}

private fun FlowContent.auditCard(entries: List<AuditEntry>) {
    section("ov-card") {
        div("ov-head") {
            h2 { +"Activitate recentă" }
            span("hint") { +"din jurnal" }
            a(href = "/admin/audit", classes = "more") { +"Tot jurnalul" }
        }
        if (entries.isEmpty()) {
            emptyState("Nicio modificare încă", "Aici apar ultimele schimbări făcute în panou.")
            return@section
        }
        ul("ov-list audit") {
            entries.forEach { e ->
                li {
                    span("when tnum") { +shortTime(e.at) }
                    span("who") { +e.adminEmail }
                    span("text") { +e.summary }
                }
            }
        }
    }
}

private fun FlowContent.eventsCard(events: List<Event>) {
    section("ov-card") {
        div("ov-head") {
            h2 { +"Evenimente următoare" }
            a(href = "/admin/events", classes = "more") { +"Toate" }
        }
        if (events.isEmpty()) {
            emptyState("Niciun eveniment programat") {
                a(href = "/admin/events#new", classes = "btn secondary small") { +"Adaugă unul" }
            }
            return@section
        }
        ul("ov-list events") {
            events.forEach { e ->
                val local = e.startsAt.toLocalDateTime(FacultyTimeZone)
                li {
                    a(href = "/admin/events/${e.id.encodeURLParameter()}") {
                        span("date") {
                            span("d tnum") { +local.date.day.toString() }
                            span("m") { +SHORT_MONTHS[local.date.month.ordinal] }
                        }
                        span("what") {
                            strong { +e.title }
                            span("text") { +whenAndWhere(e) }
                        }
                    }
                }
            }
        }
    }
}

private class SecurityFacts(
    val signIns: List<Pair<String, LoginAttempt>>,
    val accounts: List<AdminAccount>,
    val sessions: Int,
    val failed: Int,
)

private fun FlowContent.securityCard(facts: SecurityFacts) {
    section("ov-card") {
        div("ov-head") {
            h2 {
                unsafe { +svgIcon("M12 3l7 3v6c0 4-3 7-7 9-4-2-7-5-7-9V6z", 18) }
                +"Securitate"
            }
        }
        if (facts.signIns.isNotEmpty()) {
            div("ov-sub") { +"Ultimele autentificări" }
            ul("ov-list signins") {
                facts.signIns.forEach { (email, attempt) ->
                    li {
                        span("what") {
                            +email
                            span("hint") { +" · ${describeAgent(attempt.userAgent)}" }
                        }
                        span("when tnum") { +shortTime(attempt.at) }
                    }
                }
            }
        }
        val active = facts.accounts.filter { !it.disabled }
        ul("ov-list facts") {
            li {
                span("what") { +"Sesiunile tale active: ${facts.sessions}" }
                a(href = "/admin/account", classes = "more") { +"Vezi și închide" }
            }
            li {
                span("what") { +"Autentificare în doi pași: ${active.count { it.twoStep }} din ${active.size} conturi" }
                a(href = "/admin/admins", classes = "more") { +"Conturi" }
            }
        }
        p("ov-foot") {
            +"${facts.failed} ${plural(facts.failed, "încercare greșită", "încercări greșite")} în ultimele 24 de ore. "
            +"După 5 la rând, contul se blochează 15 minute."
        }
    }
}

/** What the current semester still lacks, from the data itself. */
private fun FlowContent.todo(dataset: Dataset) {
    val noTeacher = dataset.activities.count { it.professorId == null }
    val noRoom = dataset.activities.count { it.roomId == null }
    val clashes = TimetableSheet.doubleBookings(dataset).size
    val noEmail = dataset.professors.count { it.email == null }
    val semester = "/admin/semesters/${dataset.semester.id.encodeURLParameter()}"
    ul("ov-list todo") {
        if (dataset.activities.isEmpty()) {
            todoItem("bad", "Semestrul nu are încă orar", "Importă fișierul cu orarul.", "$semester#import")
        }
        if (noTeacher > 0) {
            todoItem(
                "warn",
                "$noTeacher ${plural(noTeacher, "clasă fără", "clase fără")} profesor",
                "Studenții nu văd cine ține clasa.",
                "/admin/schedule?incomplete=on",
            )
        }
        if (noRoom > 0) {
            todoItem(
                "warn",
                "$noRoom ${plural(noRoom, "clasă fără", "clase fără")} sală",
                "Aplicația arată \"Fără sală\".",
                "/admin/schedule?incomplete=on",
            )
        }
        if (clashes > 0) {
            todoItem(
                "warn",
                "$clashes ${plural(clashes, "suprapunere", "suprapuneri")} de săli sau profesori",
                "Aceeași sală sau același profesor, la aceeași oră.",
                "$semester#problems",
            )
        }
        if (noEmail > 0) {
            todoItem(
                "warn",
                "$noEmail ${plural(noEmail, "profesor fără", "profesori fără")} email",
                "Studenții nu au cum să le scrie din aplicație.",
                "/admin/professors?no_email=on",
            )
        }
        if (dataset.activities.isNotEmpty() && noTeacher == 0 && noRoom == 0 && clashes == 0) {
            todoItem("ok", "Orarul nu are probleme cunoscute", "Toate clasele au profesor și sală.", null)
        }
    }
}

private fun UL.todoItem(kind: String, title: String, note: String, href: String?) {
    li {
        val body: FlowContent.() -> Unit = {
            span("mark $kind")
            span("what") {
                strong { +title }
                span("text") { +note }
            }
        }
        if (href != null) a(href = href) { body() } else div { body() }
    }
}

private fun audienceOf(dataset: Dataset, a: Activity): String = (
    a.audience.seriesIds + a.audience.groupIds.map { dataset.groupsById[it]?.code ?: it } + a.audience.halfGroupIds
    ).joinToString(", ").ifBlank { "-" }

internal val OVERVIEW_STYLES = """
.weekstrip { display: flex; flex-wrap: wrap; align-items: stretch; background: var(--surface); border: 1px solid var(--border);
  border-radius: 8px; overflow: hidden; margin-bottom: 24px; }
.weekstrip .num { display: flex; align-items: center; justify-content: center; min-width: 76px; background: var(--alt);
  border-right: 1px solid var(--border); font-size: 32px; line-height: 40px; font-weight: 700; }
.weekstrip .part { flex: 1 1 320px; padding: 14px 18px; display: flex; flex-direction: column; gap: 2px; }
.weekstrip .part + .part { border-left: 1px solid var(--border); }
.weekstrip strong { font-size: 17px; line-height: 22px; font-weight: 600; }
.weekstrip span { color: var(--muted); }
.overview { display: flex; flex-wrap: wrap; gap: 24px; align-items: flex-start; }
.ov-main { flex: 2 1 600px; min-width: 0; display: flex; flex-direction: column; gap: 24px; }
.ov-side { flex: 1 1 340px; min-width: 0; display: flex; flex-direction: column; gap: 24px; }
.ov-card { background: var(--surface); border: 1px solid var(--border); border-radius: 8px; overflow: hidden; }
.ov-card table { border: 0; border-radius: 0; }
.ov-card thead tr:first-child th { border-radius: 0; }
.ov-head { display: flex; flex-wrap: wrap; align-items: baseline; gap: 4px 12px; padding: 14px 18px; border-bottom: 1px solid var(--border); }
.ov-head h2 { margin: 0; font-size: 17px; line-height: 22px; display: flex; align-items: center; gap: 8px; }
.ov-head h2 .dot { margin: 0; }
.ov-head .hint { margin: 0; }
.ov-card a.more, .ov-head .more { margin-left: auto; font-size: 13px; line-height: 18px; font-weight: 500; text-decoration: none; }
.ov-sub { padding: 10px 18px 4px; font-size: 12px; line-height: 16px; font-weight: 600; color: var(--muted);
  letter-spacing: 0.04em; text-transform: uppercase; }
.ov-foot { margin: 0; padding: 10px 18px; font-size: 13px; line-height: 18px; color: var(--muted); }
.ov-card .bar { display: inline-block; width: 4px; height: 18px; border-radius: 2px; margin-right: 10px; vertical-align: -4px; }
.ov-card .missing { color: var(--now); font-weight: 600; }
ul.ov-list { list-style: none; margin: 0; padding: 0; }
ul.ov-list > li { display: flex; gap: 12px; align-items: flex-start; padding: 11px 18px; border-bottom: 1px solid var(--border);
  font-size: 14px; line-height: 20px; transition: background-color 150ms; }
ul.ov-list > li:last-child { border-bottom: 0; }
ul.ov-list > li:hover { background: var(--row-hover); }
ul.ov-list > li > a:not(.more), ul.ov-list > li > div { display: flex; gap: 12px; flex: 1; min-width: 0; color: var(--text); text-decoration: none; }
ul.ov-list .what { flex: 1; min-width: 0; display: flex; flex-direction: column; }
ul.ov-list .what .hint { display: inline; margin: 0; }
ul.ov-list .text { color: var(--muted); font-size: 13px; line-height: 18px; overflow: hidden; text-overflow: ellipsis; }
ul.ov-list .when { flex: none; color: var(--muted); font-size: 13px; white-space: nowrap; }
ul.ov-list.audit > li { display: grid; grid-template-columns: 110px 190px minmax(0, 1fr); }
ul.ov-list.audit .who { font-weight: 500; overflow: hidden; text-overflow: ellipsis; }
ul.ov-list.audit .text { color: var(--text); font-size: 14px; }
ul.ov-list .mark { flex: none; width: 8px; height: 8px; margin-top: 6px; border-radius: 50%; background: var(--change); }
ul.ov-list .mark.bad { background: var(--now); } ul.ov-list .mark.ok { background: var(--ok); }
ul.ov-list .date { flex: none; width: 44px; display: flex; flex-direction: column; align-items: center; border: 1px solid var(--border);
  border-radius: 6px; padding: 3px 0; background: var(--bg); }
ul.ov-list .date .d { font-size: 17px; line-height: 22px; font-weight: 600; }
ul.ov-list .date .m { font-size: 11px; line-height: 14px; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; }
@media (max-width: 700px) { ul.ov-list.audit > li { grid-template-columns: 1fr; gap: 2px; } }
"""
