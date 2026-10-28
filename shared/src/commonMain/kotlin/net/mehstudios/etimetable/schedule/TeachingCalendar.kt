// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.schedule

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import net.mehstudios.etimetable.model.Break
import net.mehstudios.etimetable.model.Parity
import net.mehstudios.etimetable.model.Semester
import net.mehstudios.etimetable.model.WeekRule

/** A numbered week of classes, starting on [monday]. */
data class TeachingWeek(val number: Int, val monday: LocalDate, val parity: Parity)

/**
 * Week numbers and parity of a semester. Weeks are numbered from 1 starting
 * with the week of the first day; a week whose weekdays all fall in a break
 * gets no number, so numbering continues after holidays.
 */
class TeachingCalendar(val semester: Semester) {
    val weeks: List<TeachingWeek>

    private val weeksByMonday: Map<LocalDate, TeachingWeek>

    init {
        val overrides = semester.parityOverrides.associate { it.weekOf.mondayOfWeek() to it.parity }
        val result = mutableListOf<TeachingWeek>()
        var monday = semester.firstDay.mondayOfWeek()
        while (monday <= semester.lastDay) {
            val hasClasses = (0 until 5).any { isTeachingDay(monday.plus(it, DateTimeUnit.DAY)) }
            if (hasClasses) {
                val number = result.size + 1
                val parity = overrides[monday] ?: if (number % 2 == 1) Parity.ODD else Parity.EVEN
                result += TeachingWeek(number, monday, parity)
            }
            monday = monday.plus(7, DateTimeUnit.DAY)
        }
        weeks = result
        weeksByMonday = result.associateBy { it.monday }
    }

    fun breakOn(date: LocalDate): Break? = semester.breaks.firstOrNull { date in it.firstDay..it.lastDay }

    fun isTeachingDay(date: LocalDate): Boolean = date in semester.firstDay..semester.lastDay && breakOn(date) == null

    /** The numbered week containing [date], also on days of that week without classes. */
    fun weekOf(date: LocalDate): TeachingWeek? = weeksByMonday[date.mondayOfWeek()]

    fun matches(rule: WeekRule, week: TeachingWeek): Boolean = when (rule) {
        WeekRule.All -> true
        WeekRule.Odd -> week.parity == Parity.ODD
        WeekRule.Even -> week.parity == Parity.EVEN
        is WeekRule.Only -> week.number in rule.weeks
    }

    /** Numbers of the weeks [rule] selects in this semester. */
    fun weekNumbers(rule: WeekRule): Set<Int> = weeks.filter { matches(rule, it) }.mapTo(mutableSetOf()) { it.number }
}

fun LocalDate.mondayOfWeek(): LocalDate =
    minus(dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber, DateTimeUnit.DAY)
