// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.schedule

import kotlinx.datetime.LocalDate
import net.mehstudios.etimetable.SampleData
import net.mehstudios.etimetable.model.Parity
import net.mehstudios.etimetable.model.ParityOverride
import net.mehstudios.etimetable.model.WeekRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TeachingCalendarTest {
    private val calendar = TeachingCalendar(SampleData.semester)

    @Test
    fun numbersWeeksFromTheFirstWeek() {
        assertEquals(1, calendar.weekOf(LocalDate(2026, 9, 28))?.number)
        assertEquals(1, calendar.weekOf(LocalDate(2026, 10, 4))?.number)
        val week5 = calendar.weekOf(LocalDate(2026, 10, 28))
        assertEquals(5, week5?.number)
        assertEquals(Parity.ODD, week5?.parity)
        assertEquals(Parity.EVEN, calendar.weekOf(LocalDate(2026, 11, 4))?.parity)
    }

    @Test
    fun weeksInsideTheWinterBreakHaveNoNumber() {
        assertNull(calendar.weekOf(LocalDate(2026, 12, 23)))
        assertNull(calendar.weekOf(LocalDate(2026, 12, 30)))
        assertEquals(12, calendar.weekOf(LocalDate(2026, 12, 18))?.number)
        assertEquals(13, calendar.weekOf(LocalDate(2027, 1, 4))?.number)
        assertEquals(14, calendar.weeks.size)
    }

    @Test
    fun aOneDayHolidayKeepsItsWeekButHasNoClasses() {
        val holiday = LocalDate(2026, 12, 1)
        assertEquals(10, calendar.weekOf(holiday)?.number)
        assertFalse(calendar.isTeachingDay(holiday))
        assertTrue(calendar.isTeachingDay(LocalDate(2026, 12, 2)))
        assertEquals("Ziua Națională", calendar.breakOn(holiday)?.name?.ro)
    }

    @Test
    fun daysOutsideTheSemesterAreNotTeachingDays() {
        assertFalse(calendar.isTeachingDay(LocalDate(2026, 9, 27)))
        assertFalse(calendar.isTeachingDay(LocalDate(2027, 1, 16)))
    }

    @Test
    fun parityOverrideWinsOverTheNumber() {
        val shifted = TeachingCalendar(
            SampleData.semester.copy(parityOverrides = listOf(ParityOverride(LocalDate(2026, 10, 29), Parity.EVEN))),
        )
        assertEquals(5, shifted.weekOf(LocalDate(2026, 10, 26))?.number)
        assertEquals(Parity.EVEN, shifted.weekOf(LocalDate(2026, 10, 26))?.parity)
        assertTrue(5 in shifted.weekNumbers(WeekRule.Even))
        assertFalse(5 in shifted.weekNumbers(WeekRule.Odd))
    }

    @Test
    fun weekNumbersOfRules() {
        assertEquals(setOf(1, 3, 5, 7, 9, 11, 13), calendar.weekNumbers(WeekRule.Odd))
        assertEquals(setOf(2, 4), calendar.weekNumbers(WeekRule.Only(setOf(2, 4, 20))))
    }
}
