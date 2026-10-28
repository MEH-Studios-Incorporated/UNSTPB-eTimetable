// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.schedule

import kotlinx.datetime.LocalDateTime
import net.mehstudios.etimetable.SampleData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProfessorScheduleTest {
    private val calendar = TeachingCalendar(SampleData.semester)

    private fun status(hour: Int, minute: Int = 0) =
        calendar.professorStatus(SampleData.dataset, "marinescu", LocalDateTime(2026, 10, 28, hour, minute))

    @Test
    fun teachingNow() {
        val now = assertIs<ProfessorStatus.Now>(status(9))
        assertEquals("micro-c", now.slot.activity?.id)
    }

    @Test
    fun officeHoursLaterToday() {
        val later = assertIs<ProfessorStatus.LaterToday>(status(11, 35))
        assertEquals("ec210", later.slot.officeHour?.roomId)
    }

    @Test
    fun inOfficeHours() {
        assertIs<ProfessorStatus.Now>(status(15))
    }

    @Test
    fun doneForTheDay() {
        assertEquals(ProfessorStatus.NothingMoreToday, status(17))
    }
}
