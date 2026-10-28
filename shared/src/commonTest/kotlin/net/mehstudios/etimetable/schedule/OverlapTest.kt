// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.schedule

import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.TUESDAY
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import net.mehstudios.etimetable.SampleData
import net.mehstudios.etimetable.model.Enrollment
import net.mehstudios.etimetable.model.ExtraSlot
import net.mehstudios.etimetable.model.Profile
import net.mehstudios.etimetable.model.WeekRule
import net.mehstudios.etimetable.model.WeeklySlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OverlapTest {
    private val calendar = TeachingCalendar(SampleData.semester)

    private fun slot(
        start: String,
        end: String,
        weeks: WeekRule = WeekRule.All,
        day: kotlinx.datetime.DayOfWeek = MONDAY,
    ) = WeeklySlot.of(day, LocalTime.parse(start), LocalTime.parse(end), weeks)

    @Test
    fun backToBackSlotsDoNotOverlap() {
        assertFalse(calendar.overlaps(slot("10:00", "12:00"), slot("12:00", "14:00")))
    }

    @Test
    fun oneMinuteIsAClash() {
        assertTrue(calendar.overlaps(slot("10:00", "12:01"), slot("12:00", "14:00")))
    }

    @Test
    fun differentDaysDoNotOverlap() {
        assertFalse(calendar.overlaps(slot("10:00", "12:00"), slot("10:00", "12:00", day = TUESDAY)))
    }

    @Test
    fun oddAndEvenWeeksShareTheSameTime() {
        assertFalse(calendar.overlaps(slot("10:00", "12:00", WeekRule.Odd), slot("10:00", "12:00", WeekRule.Even)))
        assertFalse(
            calendar.overlaps(slot("10:00", "12:00", WeekRule.Odd), slot("10:00", "12:00", WeekRule.Only(setOf(2, 4)))),
        )
        assertTrue(
            calendar.overlaps(slot("10:00", "12:00", WeekRule.Odd), slot("10:00", "12:00", WeekRule.Only(setOf(3)))),
        )
        assertTrue(calendar.overlaps(slot("10:00", "12:00"), slot("11:00", "13:00", WeekRule.Even)))
    }

    @Test
    fun erasmusPickThatClashesNamesTheClash() {
        val student =
            StudentTimetable(SampleData.dataset, Profile(enrollment = Enrollment.Erasmus(setOf("calit-l-1541A"))))
        // the other half's lab starts when this one ends
        assertNull(student.clashFor(SampleData.activity("calit-l-1541B")))
        val clashing = SampleData.activity(
            "calit-l-1541A",
        ).copy(id = "other-lab", slot = slot("13:00", "15:00", day = kotlinx.datetime.DayOfWeek.WEDNESDAY))
        assertEquals("calit-l-1541A", student.clashFor(clashing)?.id)
    }

    @Test
    fun oneTimeVisitIsCheckedOnlyOnItsDate() {
        val student = StudentTimetable(
            SampleData.dataset,
            Profile(enrollment = Enrollment.National("1541E", "1541E/A")),
        )
        val otherLab = SampleData.activity("calit-l-1541B")
        // Wednesday 16:00 has the project only in odd weeks: 28 October clashes, 4 November does not
        val late = otherLab.copy(
            id = "late-lab",
            slot = slot("16:00", "18:00", day = kotlinx.datetime.DayOfWeek.WEDNESDAY),
        )
        assertEquals("mkt-p-1541", student.clashFor(late, LocalDate(2026, 10, 28))?.id)
        assertNull(student.clashFor(late, LocalDate(2026, 11, 4)))
        assertEquals("mkt-p-1541", student.clashFor(late)?.id)
    }

    @Test
    fun recurringPickAgainstAOneTimeVisit() {
        val visit = ExtraSlot("calit-l-1542A", LocalDate(2026, 10, 29))
        val student =
            StudentTimetable(SampleData.dataset, Profile(enrollment = Enrollment.Erasmus(), extraSlots = listOf(visit)))
        val sameTime = SampleData.activity("calit-l-1542A").copy(id = "thursday-8")
        assertEquals("calit-l-1542A", student.clashFor(sameTime)?.id)
    }
}
