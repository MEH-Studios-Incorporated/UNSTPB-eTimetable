// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.schedule

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import net.mehstudios.etimetable.SampleData
import net.mehstudios.etimetable.model.Enrollment
import net.mehstudios.etimetable.model.ExtraSlot
import net.mehstudios.etimetable.model.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudentTimetableTest {
    private val wednesday = LocalDate(2026, 10, 28)

    private fun timetable(enrollment: Enrollment, extras: List<ExtraSlot> = emptyList()) =
        StudentTimetable(SampleData.dataset, Profile(enrollment = enrollment, extraSlots = extras))

    private fun ids(list: List<Occurrence>) = list.map { it.activity.id }

    @Test
    fun nationalStudentGetsSeriesGroupAndHalfGroup() {
        val day = timetable(Enrollment.National("1541E", "1541E/A")).on(wednesday)
        assertEquals(listOf("micro-c", "contab-s", "calit-l-1541A", "mkt-p-1541"), ids(day))
    }

    @Test
    fun withoutAHalfGroupBothHalvesShow() {
        val day = timetable(Enrollment.National("1541E")).on(wednesday)
        assertTrue("calit-l-1541A" in ids(day) && "calit-l-1541B" in ids(day))
    }

    @Test
    fun oddAndEvenWeeksAlternate() {
        val other = timetable(Enrollment.National("1542E", "1542E/A"))
        assertTrue("mkt-p-1542" !in ids(other.on(wednesday)))
        assertTrue("mkt-p-1542" in ids(other.on(LocalDate(2026, 11, 4))))
    }

    @Test
    fun nothingOnHolidays() {
        val student = timetable(Enrollment.National("1541E", "1541E/A"))
        assertEquals(emptyList(), student.on(LocalDate(2026, 12, 23)))
        assertEquals(emptyList(), student.on(LocalDate(2026, 12, 1)))
    }

    @Test
    fun erasmusStudentGetsOnlyWhatTheyPicked() {
        val student = timetable(Enrollment.Erasmus(setOf("micro-c", "calit-l-1542B")))
        assertEquals(listOf("micro-c"), ids(student.on(wednesday)))
        assertEquals(listOf("calit-l-1542B"), ids(student.on(LocalDate(2026, 10, 30))))
    }

    @Test
    fun aOneTimeVisitShowsOnlyOnItsDate() {
        val visit = ExtraSlot("calit-l-1542A", LocalDate(2026, 10, 29))
        val student = timetable(Enrollment.National("1541E", "1541E/A"), listOf(visit))
        assertTrue("calit-l-1542A" in ids(student.on(LocalDate(2026, 10, 29))))
        assertTrue("calit-l-1542A" !in ids(student.on(LocalDate(2026, 11, 5))))
    }

    @Test
    fun nowAndNext() {
        val student = timetable(Enrollment.National("1541E", "1541E/A"))

        val atNine = student.nowAndNext(LocalDateTime(2026, 10, 28, 9, 0))
        assertEquals("micro-c", atNine.current?.activity?.id)
        assertEquals("contab-s", atNine.next?.activity?.id)

        // 11:35, as on the mockup: the seminar ended at 11:30, the lab starts at 12:00
        val atLunch = student.nowAndNext(LocalDateTime(2026, 10, 28, 11, 35))
        assertNull(atLunch.current)
        assertEquals("calit-l-1541A", atLunch.next?.activity?.id)
    }

    @Test
    fun nextLooksPastTheWeekendAndTheBreak() {
        val student = timetable(Enrollment.National("1541E", "1541E/A"))
        val friday = student.nowAndNext(LocalDateTime(2026, 10, 30, 18, 0))
        assertEquals(LocalDate(2026, 11, 4), friday.next?.date)

        val beforeBreak = student.nowAndNext(LocalDateTime(2026, 12, 18, 20, 0))
        assertEquals(LocalDate(2027, 1, 6), beforeBreak.next?.date)
    }

    @Test
    fun nothingNextAfterTheSemester() {
        val student = timetable(Enrollment.National("1541E", "1541E/A"))
        assertNull(student.nowAndNext(LocalDateTime(2027, 1, 15, 20, 0)).next)
    }
}
