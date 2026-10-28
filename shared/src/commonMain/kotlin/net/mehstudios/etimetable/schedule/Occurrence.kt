// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.schedule

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import net.mehstudios.etimetable.model.Activity
import net.mehstudios.etimetable.model.WeeklySlot

/** One meeting of an activity on a given date. */
data class Occurrence(val activity: Activity, val date: LocalDate) {
    val start: LocalDateTime get() = date.atTime(activity.slot.start)
    val end: LocalDateTime get() = date.atTime(activity.slot.end)
}

/** Whether [slot] meets on [date]: right weekday, a teaching day, a week its rule selects. */
fun TeachingCalendar.meetsOn(slot: WeeklySlot, date: LocalDate): Boolean {
    if (date.dayOfWeek.isoDayNumber != slot.isoDay || !isTeachingDay(date)) return false
    val week = weekOf(date) ?: return false
    return matches(slot.weeks, week)
}

/** Every meeting of [activity] from [from] to [to], both included. */
fun TeachingCalendar.occurrences(activity: Activity, from: LocalDate, to: LocalDate): List<Occurrence> {
    val first = maxOf(from, semester.firstDay)
    val last = minOf(to, semester.lastDay)
    if (first > last) return emptyList()
    val offset = (activity.slot.isoDay - first.dayOfWeek.isoDayNumber + 7) % 7
    var date = first.plus(offset, DateTimeUnit.DAY)
    val result = mutableListOf<Occurrence>()
    while (date <= last) {
        if (meetsOn(activity.slot, date)) result += Occurrence(activity, date)
        date = date.plus(7, DateTimeUnit.DAY)
    }
    return result
}

internal fun LocalDate.daysThrough(last: LocalDate): Sequence<LocalDate> =
    (0..daysUntil(last)).asSequence().map { plus(it, DateTimeUnit.DAY) }
