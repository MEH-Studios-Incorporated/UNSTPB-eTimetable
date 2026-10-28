// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.accounts

import io.ktor.http.HttpStatusCode
import net.mehstudios.etimetable.api.ErrorCodes
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Enrollment
import net.mehstudios.etimetable.model.Profile
import net.mehstudios.etimetable.schedule.StudentTimetable
import net.mehstudios.etimetable.schedule.TeachingCalendar
import net.mehstudios.etimetable.schedule.findClash
import net.mehstudios.etimetable.server.ApiProblem

/**
 * The checks a profile must pass: ids that exist in the current semester and
 * no clashes between Erasmus picks or extra slots. Whether the student
 * really belongs to the group is not checked, on purpose (docs/brief.md).
 */
fun checkProfile(profile: Profile, dataset: Dataset?) {
    // before the first semester there is nothing to check against
    if (dataset == null) return
    val calendar = TeachingCalendar(dataset.semester)

    when (val enrollment = profile.enrollment) {
        null -> Unit
        is Enrollment.National -> {
            if (enrollment.groupId !in dataset.groupsById) unknown(ErrorCodes.UNKNOWN_GROUP, enrollment.groupId)
            enrollment.halfGroupId?.let { half ->
                if (dataset.halfGroupsById[half]?.groupId != enrollment.groupId) unknown(ErrorCodes.UNKNOWN_GROUP, half)
            }
        }
        is Enrollment.Erasmus -> {
            val picks = enrollment.activityIds.map {
                dataset.activitiesById[it]
                    ?: unknown(ErrorCodes.UNKNOWN_ACTIVITY, it)
            }
            picks.forEachIndexed { i, pick ->
                calendar.findClash(pick, picks.take(i))?.let { clash(it.id, pick.id) }
            }
        }
    }

    var accepted = profile.copy(extraSlots = emptyList())
    for (extra in profile.extraSlots) {
        val activity =
            dataset.activitiesById[extra.activityId] ?: unknown(ErrorCodes.UNKNOWN_ACTIVITY, extra.activityId)
        StudentTimetable(dataset, accepted, calendar).clashFor(activity, extra.date)?.let { clash(it.id, activity.id) }
        accepted = accepted.copy(extraSlots = accepted.extraSlots + extra)
    }
}

private fun unknown(code: String, id: String): Nothing =
    throw ApiProblem(HttpStatusCode.UnprocessableEntity, code, listOf(id))

private fun clash(a: String, b: String): Nothing =
    throw ApiProblem(HttpStatusCode.Conflict, ErrorCodes.CLASH, listOf(a, b))
