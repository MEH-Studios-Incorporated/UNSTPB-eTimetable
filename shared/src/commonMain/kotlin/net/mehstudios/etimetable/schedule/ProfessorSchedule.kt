// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.schedule

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import net.mehstudios.etimetable.model.Activity
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.OfficeHour

/** Something a professor does at a time: an activity or office hours. */
data class ProfessorSlot(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val activity: Activity? = null,
    val officeHour: OfficeHour? = null,
)

sealed interface ProfessorStatus {
    data class Now(val slot: ProfessorSlot) : ProfessorStatus

    data class LaterToday(val slot: ProfessorSlot) : ProfessorStatus

    data object NothingMoreToday : ProfessorStatus
}

fun TeachingCalendar.professorDay(dataset: Dataset, professorId: String, date: LocalDate): List<ProfessorSlot> {
    val teaching = dataset.activities
        .filter { it.professorId == professorId && meetsOn(it.slot, date) }
        .map { ProfessorSlot(date.atTime(it.slot.start), date.atTime(it.slot.end), activity = it) }
    val office = dataset.professorsById[professorId]?.officeHours.orEmpty()
        .filter { meetsOn(it.slot, date) }
        .map { ProfessorSlot(date.atTime(it.slot.start), date.atTime(it.slot.end), officeHour = it) }
    return (teaching + office).sortedBy { it.start }
}

fun TeachingCalendar.professorStatus(dataset: Dataset, professorId: String, at: LocalDateTime): ProfessorStatus {
    val day = professorDay(dataset, professorId, at.date)
    day.firstOrNull { it.start <= at && at < it.end }?.let { return ProfessorStatus.Now(it) }
    day.firstOrNull { it.start > at }?.let { return ProfessorStatus.LaterToday(it) }
    return ProfessorStatus.NothingMoreToday
}
