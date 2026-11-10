// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import io.ktor.http.Parameters
import io.ktor.http.encodeURLParameter
import kotlinx.datetime.LocalTime
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.nav
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.style
import net.mehstudios.etimetable.model.Activity
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Group
import net.mehstudios.etimetable.model.OfficeHour
import net.mehstudios.etimetable.model.Parity
import net.mehstudios.etimetable.model.Professor
import net.mehstudios.etimetable.model.WeekRule
import net.mehstudios.etimetable.model.WeeklySlot
import net.mehstudios.etimetable.schedule.TeachingCalendar

/** The eight subject colours of docs/design.md, in their fixed order. */
internal val SUBJECT_COLOURS = listOf(
    "#4C6A92",
    "#2F7F7A",
    "#6F7F2F",
    "#B07A1F",
    "#A84B3A",
    "#7A4C7F",
    "#4B4F9E",
    "#3F7A4F",
)

/** Each subject's colour: the subjects sorted by id take the colours in turn, so a subject keeps its colour. */
internal fun subjectColours(dataset: Dataset): Map<String, String> =
    dataset.subjects.map { it.id }.distinct().sorted().withIndex()
        .associate { (i, id) -> id to SUBJECT_COLOURS[i % SUBJECT_COLOURS.size] }

/**
 * One block of the week. [colour] null draws a neutral block (office hours). [room] null
 * with [roomless] shows "Fără sală" in red; [who] is an optional last line.
 */
internal data class GridEntry(
    val id: String,
    val slot: WeeklySlot,
    val title: String,
    val kind: String?,
    val room: String? = null,
    val roomless: Boolean = false,
    val who: String? = null,
    val colour: String? = null,
    val href: String? = null,
)

/**
 * Where a block goes: [top] and [length] in minutes from the first hour of the grid, and
 * [column] of [columns] when it overlaps other blocks of the same day.
 */
internal data class Placement(val entry: GridEntry, val top: Int, val length: Int, val column: Int, val columns: Int)

internal data class WeekLayout(val firstHour: Int, val lastHour: Int, val days: List<Int>, val blocks: List<Placement>)

/** 08:00 to 20:00, Monday to Friday, stretched to fit every entry. */
internal fun layoutWeek(entries: List<GridEntry>): WeekLayout {
    val firstHour = minOf(8, entries.minOfOrNull { it.slot.start.hour } ?: 8)
    val lastHour = maxOf(20, entries.maxOfOrNull { (minutes(it.slot.end) + 59) / 60 } ?: 20)
    val days = ((1..5) + entries.map { it.slot.isoDay }).distinct().sorted()
    val blocks = entries.groupBy { it.slot.isoDay }.values.flatMap { placeDay(it, firstHour * 60) }
    return WeekLayout(firstHour, lastHour, days, blocks)
}

/**
 * Blocks that overlap, directly or through a chain, form a cluster and share its width:
 * each takes the first sub-column free at its start.
 */
private fun placeDay(entries: List<GridEntry>, origin: Int): List<Placement> {
    val out = mutableListOf<Placement>()
    val cluster = mutableListOf<Pair<GridEntry, Int>>()
    val columnEnds = mutableListOf<LocalTime>()
    var clusterEnd = LocalTime(0, 0)
    fun flush() {
        cluster.forEach { (e, column) ->
            val start = minutes(e.slot.start)
            out += Placement(e, start - origin, minutes(e.slot.end) - start, column, columnEnds.size)
        }
        cluster.clear()
        columnEnds.clear()
    }
    entries.sortedWith(compareBy({ it.slot.start }, { it.slot.end }, { it.id })).forEach { e ->
        if (cluster.isNotEmpty() && e.slot.start >= clusterEnd) flush()
        var column = columnEnds.indexOfFirst { it <= e.slot.start }
        if (column < 0) {
            column = columnEnds.size
            columnEnds += e.slot.end
        } else {
            columnEnds[column] = e.slot.end
        }
        if (cluster.isEmpty() || e.slot.end > clusterEnd) clusterEnd = e.slot.end
        cluster += e to column
    }
    flush()
    return out
}

private fun minutes(time: LocalTime) = time.hour * 60 + time.minute

/** The Toate / Impare / Pare switch; [param] is its value in the query string. */
internal enum class WeekFilter(val param: String?, val label: String, val parity: Parity?) {
    ALL(null, "Toate", null),
    ODD("impare", "Impare", Parity.ODD),
    EVEN("pare", "Pare", Parity.EVEN),
    ;

    companion object {
        const val PARAM = "parity"

        fun of(query: Parameters) = entries.firstOrNull { it.param != null && it.param == query[PARAM] } ?: ALL
    }
}

/** Whether a class with [rule] meets in at least one week of [parity]; every class does when it is null. */
internal fun runsIn(rule: WeekRule, parity: Parity?, calendar: TeachingCalendar): Boolean = when {
    parity == null || rule == WeekRule.All -> true
    rule == WeekRule.Odd -> parity == Parity.ODD
    rule == WeekRule.Even -> parity == Parity.EVEN
    else -> calendar.weekNumbers(rule)
        .any { it in calendar.weekNumbers(if (parity == Parity.ODD) WeekRule.Odd else WeekRule.Even) }
}

/** The mark on a block that does not meet every week. */
internal fun weekMark(rule: WeekRule): String? = when (rule) {
    WeekRule.All -> null
    WeekRule.Odd -> "Impară"
    WeekRule.Even -> "Pară"
    is WeekRule.Only -> "Săpt. ${rule.weeks.sorted().joinToString(", ")}"
}

/** A class as a block: the subject's colour, its type, room and [who]. */
internal fun classEntry(
    dataset: Dataset,
    colours: Map<String, String>,
    activity: Activity,
    href: String?,
    kindSuffix: String? = null,
    showRoom: Boolean = true,
    who: String? = null,
): GridEntry = GridEntry(
    id = activity.id,
    slot = activity.slot,
    title = dataset.subjectsById[activity.subjectId]?.name?.ro ?: activity.subjectId,
    kind = listOfNotNull(TYPE_NAMES.getValue(activity.type), kindSuffix).joinToString(" · "),
    room = if (showRoom) dataset.roomsById[activity.roomId]?.code else null,
    roomless = showRoom && activity.roomId == null,
    who = who,
    colour = colours[activity.subjectId],
    href = href,
)

/** "1541E/A" when [activity] is for half of [group] only, null when the whole group attends. */
internal fun halvesOf(dataset: Dataset, group: Group, activity: Activity): String? {
    if (group.id in activity.audience.groupIds || group.seriesId in activity.audience.seriesIds) return null
    return activity.audience.halfGroupIds.mapNotNull { dataset.halfGroupsById[it] }
        .filter { it.groupId == group.id }
        .joinToString(" ") { "${group.code}/${it.suffix}" }
        .ifEmpty { null }
}

/** Office hours as a neutral block, titled with the professor's name or [title]. */
internal fun officeEntry(dataset: Dataset, professor: Professor, hour: OfficeHour, index: Int, title: String? = null) =
    GridEntry(
        id = "office-${professor.id}-$index",
        slot = hour.slot,
        title = title ?: professor.displayName.trim(),
        kind = if (title == null) "Consultații" else null,
        room = dataset.roomsById[hour.roomId]?.code,
    )

/**
 * The week grid: an hour axis, a column per day and each entry as a block placed by its
 * times. Above it [controls] and the parity switch, whose links keep [query]; [selected]
 * is outlined. It scrolls sideways inside its card on a phone.
 */
internal fun FlowContent.weekGrid(
    entries: List<GridEntry>,
    calendar: TeachingCalendar,
    path: String,
    query: Parameters,
    selected: String? = null,
    controls: (FlowContent.() -> Unit)? = null,
) {
    val filter = WeekFilter.of(query)
    val layout = layoutWeek(entries)
    val hours = layout.lastHour - layout.firstHour
    div("wg-card") {
        div("wg-bar") {
            if (controls != null) controls()
            span("wg-spacer")
            div("wg-switch") {
                span("wg-weeks") { +"Săptămâni" }
                nav("wg-parity") {
                    attributes["aria-label"] = "Săptămâni afișate"
                    WeekFilter.entries.forEach { f ->
                        a(href = linkTo(path, query, mapOf(WeekFilter.PARAM to f.param))) {
                            if (f == filter) attributes["aria-current"] = "true"
                            +f.label
                        }
                    }
                }
            }
        }
        div("wg-scroll") {
            div("wg") {
                style = "--days: ${layout.days.size}; --hours: $hours"
                div("wg-head") {
                    span()
                    div("wg-names") {
                        layout.days.forEach { day ->
                            div {
                                +DAY_NAMES[day - 1]
                                val n = layout.blocks.count { it.entry.slot.isoDay == day }
                                if (n > 0) span("n tnum") { +" · $n" }
                            }
                        }
                    }
                }
                div("wg-body") {
                    div("wg-axis") {
                        attributes["aria-hidden"] = "true"
                        (0..hours).forEach { h ->
                            span("tnum") {
                                style = "--at: $h"
                                +"${(layout.firstHour + h).toString().padStart(2, '0')}:00"
                            }
                        }
                    }
                    div("wg-days") {
                        layout.days.forEach { day ->
                            div("wg-day") {
                                attributes["aria-label"] = DAY_NAMES[day - 1]
                                layout.blocks.filter { it.entry.slot.isoDay == day }
                                    .sortedBy { it.top }
                                    .forEach { block(it, filter, calendar, selected) }
                            }
                        }
                    }
                }
            }
        }
        legend(entries)
    }
}

private fun FlowContent.block(p: Placement, filter: WeekFilter, calendar: TeachingCalendar, selected: String?) {
    val e = p.entry
    val classes = listOfNotNull(
        "wg-block",
        "office".takeIf { e.colour == null },
        "dim".takeUnless { runsIn(e.slot.weeks, filter.parity, calendar) },
        "on".takeIf { e.id == selected },
    ).joinToString(" ")
    val time = "${e.slot.start} - ${e.slot.end}"
    val mark = weekMark(e.slot.weeks)
    val label = listOfNotNull(
        e.kind,
        e.title,
        "${DAY_NAMES[e.slot.isoDay - 1]} $time",
        e.room?.let { "sala $it" } ?: "fără sală".takeIf { e.roomless },
        e.who,
        weeksLabel(e.slot.weeks),
    ).joinToString(", ")
    val position = "--top: ${p.top}; --len: ${p.length}; --col: ${p.column}; --cols: ${p.columns}" +
        (e.colour?.let { "; --sc: $it" } ?: "")
    val content: FlowContent.() -> Unit = {
        if (e.kind != null || mark != null) {
            span("wg-kind") {
                if (e.kind != null) +e.kind
                if (mark != null) span("wg-mark") { +mark }
            }
        }
        strong { +e.title }
        span("wg-meta tnum") {
            when {
                e.room != null -> +"${e.room} · "
                e.roomless -> {
                    span("missing") { +"Fără sală" }
                    +" · "
                }
            }
            +time
        }
        if (e.who != null) span("wg-meta") { +e.who }
    }
    if (e.href != null) {
        a(href = e.href, classes = classes) {
            style = position
            attributes["title"] = label
            attributes["aria-label"] = label
            if (e.id == selected) attributes["aria-current"] = "true"
            content()
        }
    } else {
        div(classes) {
            style = position
            attributes["title"] = label
            content()
        }
    }
}

private fun FlowContent.legend(entries: List<GridEntry>) {
    val rules = entries.map { it.slot.weeks }.toSet()
    val office = entries.any { it.colour == null }
    if (WeekRule.Odd !in rules && WeekRule.Even !in rules && !office) return
    div("wg-legend") {
        if (WeekRule.Odd in rules) {
            span {
                span("wg-mark") { +"Impară" }
                +" doar în săptămânile impare"
            }
        }
        if (WeekRule.Even in rules) {
            span {
                span("wg-mark") { +"Pară" }
                +" doar în săptămânile pare"
            }
        }
        if (office) {
            span {
                span("wg-swatch")
                +" consultații"
            }
        }
    }
}

/** The link to the full editor of [activity], returning to [back]. */
internal fun activityHref(dataset: Dataset, activity: Activity, back: String) =
    "/admin/semesters/${dataset.semester.id.encodeURLParameter()}/activities/" +
        "${activity.id.encodeURLParameter()}?back=${back.encodeURLParameter()}"

internal val WEEK_GRID_STYLES = """
:root { --wg-hour: 60px; --wg-tint: 12%; }
@media (prefers-color-scheme: dark) { :root:not([data-theme=light]) { --wg-tint: 20%; } }
:root[data-theme=dark] { --wg-tint: 20%; }
.wg-card { background: var(--surface); border: 1px solid var(--border); border-radius: 8px; overflow: hidden; margin-bottom: 16px; min-width: 0; }
.wg-bar { display: flex; flex-wrap: wrap; align-items: center; gap: 10px 16px; padding: 10px 14px; border-bottom: 1px solid var(--border);
  font-size: 13px; line-height: 18px; }
.wg-spacer { flex: 1; }
.wg-switch { display: inline-flex; align-items: center; gap: 10px; }
.wg-weeks { color: var(--muted); }
.wg-parity { display: inline-flex; border: 1px solid var(--border); border-radius: 6px; overflow: hidden; }
.wg-parity a { padding: 6px 12px; color: var(--text); text-decoration: none; font-weight: 600; border-left: 1px solid var(--border);
  transition: background-color 150ms; }
.wg-parity a:first-child { border-left: 0; }
.wg-parity a:hover { background: var(--alt); }
.wg-parity a[aria-current] { background: var(--text); color: var(--bg); }
.wg-scroll { overflow-x: auto; }
.wg { min-width: calc(56px + var(--days) * 128px); padding: 0 14px 12px 0; }
.wg-head, .wg-body { display: grid; grid-template-columns: 56px minmax(0, 1fr); }
.wg-names, .wg-days { display: grid; grid-template-columns: repeat(var(--days), minmax(0, 1fr)); }
.wg-names div { padding: 9px 10px; font-size: 13px; line-height: 18px; font-weight: 600; }
.wg-names .n { color: var(--muted); font-weight: 400; }
.wg-body { padding: 10px 0; }
.wg-axis { position: relative; height: calc(var(--hours) * var(--wg-hour)); }
.wg-axis span { position: absolute; right: 8px; top: calc(var(--at) * var(--wg-hour) - 8px); font-size: 12px; line-height: 16px; color: var(--muted); }
.wg-days { height: calc(var(--hours) * var(--wg-hour) + 2px); border: 1px solid var(--border); border-radius: 8px; overflow: hidden;
  background: var(--alt) repeating-linear-gradient(to bottom, transparent 0, transparent calc(var(--wg-hour) - 1px),
  var(--border) calc(var(--wg-hour) - 1px), var(--border) var(--wg-hour)); }
.wg-day { position: relative; border-left: 1px solid var(--border); }
.wg-day:first-child { border-left: 0; }
.wg-block { position: absolute; overflow: hidden; display: block; color: var(--text); text-decoration: none; border-radius: 6px;
  top: calc(var(--top) * var(--wg-hour) / 60 + 2px); height: calc(var(--len) * var(--wg-hour) / 60 - 3px);
  left: calc(100% * var(--col) / var(--cols) + 3px); width: calc(100% / var(--cols) - 6px);
  padding: 4px 7px 4px 8px; font-size: 12px; line-height: 16px;
  background: var(--surface); border: 1px solid var(--border); border-left: 4px solid var(--sc, var(--muted));
  background: color-mix(in srgb, var(--sc, var(--muted)) var(--wg-tint), var(--surface));
  border-color: color-mix(in srgb, var(--sc, var(--muted)) 35%, var(--surface)); border-left-color: var(--sc, var(--muted));
  transition: box-shadow 150ms, opacity 150ms; }
a.wg-block:hover { box-shadow: 0 2px 8px rgba(0, 0, 0, 0.14); z-index: 1; }
.wg-block.office { background: var(--surface); border-color: var(--border); border-left-color: var(--muted); border-style: dashed solid dashed solid; }
.wg-block.dim { opacity: 0.3; }
.wg-block.on { outline: 2px solid var(--primary); outline-offset: 1px; box-shadow: var(--shadow); z-index: 2; opacity: 1; }
.wg-block strong { display: block; font-size: 13px; line-height: 17px; font-weight: 600; }
.wg-kind { display: flex; flex-wrap: wrap; align-items: center; gap: 2px 6px; color: var(--muted); }
.wg-meta { display: block; margin-top: 1px; }
.wg-meta .missing { color: var(--now); font-weight: 600; }
.wg-mark { border: 1px solid currentColor; border-radius: 4px; padding: 0 4px; font-size: 11px; line-height: 14px; font-weight: 600; color: var(--text); }
.wg-legend { display: flex; flex-wrap: wrap; gap: 6px 18px; padding: 0 14px 12px 70px; font-size: 13px; line-height: 18px; color: var(--muted); }
.wg-swatch { display: inline-block; width: 14px; height: 10px; border: 1px dashed var(--border); border-left: 3px solid var(--muted);
  border-radius: 2px; background: var(--surface); vertical-align: -1px; }
.wg-pick { display: inline-flex; align-items: center; gap: 8px; margin: 0; }
.wg-pick label { margin: 0; color: var(--muted); font-size: 13px; font-weight: 500; }
.wg-pick select { width: auto; max-width: 220px; padding: 6px 8px; font-size: 13px; }
.wg-layout { display: flex; flex-wrap: wrap; gap: 24px; align-items: flex-start; }
.wg-layout > .wg-card { flex: 999 1 620px; }
.wg-panel { flex: 1 1 340px; max-width: 420px; min-width: 0; background: var(--surface); border: 1px solid var(--border); border-radius: 8px;
  box-shadow: var(--shadow); overflow: hidden; }
.wg-panel .head { display: flex; align-items: center; gap: 10px; padding: 14px 18px; border-bottom: 1px solid var(--border); }
.wg-panel .head .bar { flex: none; width: 4px; height: 22px; border-radius: 2px; background: var(--sc, var(--muted)); }
.wg-panel .head h2 { flex: 1; margin: 0; }
.wg-panel .body { padding: 16px 18px; }
.wg-panel .body > p { margin: 0 0 6px; }
.wg-panel .card { border: 0; padding: 0; margin: 0; }
.wg-panel .cols { grid-template-columns: 1fr; }
.wg-panel form + form { margin-top: 12px; }
.wg-check { display: flex; gap: 10px; align-items: flex-start; border: 1px solid var(--border); border-radius: 6px; padding: 10px 12px;
  background: var(--bg); font-size: 13px; line-height: 18px; margin: 12px 0 16px; }
.wg-check .dot { flex: none; margin-top: 5px; background: var(--ok); }
.wg-check.bad .dot { background: var(--now); }
.wg-check ul { margin: 4px 0 0; padding-left: 18px; }
@media (max-width: 860px) {
  .wg-panel { order: -1; max-width: none; }
}
"""
