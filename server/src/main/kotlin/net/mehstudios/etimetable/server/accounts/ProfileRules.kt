// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.accounts

import io.ktor.http.HttpStatusCode
import net.mehstudios.etimetable.api.ErrorCodes
import net.mehstudios.etimetable.api.isValidPhone
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Enrollment
import net.mehstudios.etimetable.model.Profile
import net.mehstudios.etimetable.schedule.StudentTimetable
import net.mehstudios.etimetable.schedule.TeachingCalendar
import net.mehstudios.etimetable.schedule.findClash
import net.mehstudios.etimetable.server.ApiProblem

/**
 * [profile] without the classes an import removed since the student saved [saved]: picks,
 * dropped slots and extra slots whose class no longer exists and that the saved profile
 * already had. Without this a student could not save anything after such an import.
 * Ids that are new and unknown stay, so [checkProfile] still refuses them.
 */
fun withoutRemovedClasses(profile: Profile, saved: Profile?, dataset: Dataset?): Profile {
    if (dataset == null || saved == null) return profile
    val savedIds = buildSet {
        (saved.enrollment as? Enrollment.Erasmus)?.let { addAll(it.activityIds) }
        addAll(saved.droppedSlots)
        saved.extraSlots.forEach { add(it.activityId) }
    }
    fun gone(id: String) = id !in dataset.activitiesById && id in savedIds
    val enrollment = when (val e = profile.enrollment) {
        is Enrollment.Erasmus -> e.copy(activityIds = e.activityIds.filterNot(::gone).toSet())
        else -> e
    }
    return profile.copy(
        enrollment = enrollment,
        droppedSlots = profile.droppedSlots.filterNot(::gone).toSet(),
        extraSlots = profile.extraSlots.filterNot { gone(it.activityId) },
    )
}

/**
 * The checks a profile must pass: ids that exist in the current semester and
 * no clashes between Erasmus picks or extra slots. A dropped slot frees its time,
 * so an extra slot may take it. Whether the student
 * really belongs to the group is not checked, on purpose (docs/brief.md). A group
 * that an import removed is accepted while it is the one already [saved], so the
 * student can still change their name or phone before picking a new group.
 */
fun checkProfile(profile: Profile, dataset: Dataset?, saved: Profile? = null) {
    checkContact(profile)
    // before the first semester there is nothing to check against
    if (dataset == null) return
    val calendar = TeachingCalendar(dataset.semester)

    when (val enrollment = profile.enrollment) {
        null -> Unit
        is Enrollment.National -> {
            val kept = saved?.enrollment == enrollment
            if (enrollment.groupId !in dataset.groupsById &&
                !kept
            ) {
                unknown(ErrorCodes.UNKNOWN_GROUP, enrollment.groupId)
            }
            enrollment.halfGroupId?.let { half ->
                if (dataset.halfGroupsById[half]?.groupId != enrollment.groupId && !kept) {
                    unknown(ErrorCodes.UNKNOWN_GROUP, half)
                }
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

    for (dropped in profile.droppedSlots) {
        if (dropped !in dataset.activitiesById) unknown(ErrorCodes.UNKNOWN_ACTIVITY, dropped)
    }

    var accepted = profile.copy(extraSlots = emptyList())
    for (extra in profile.extraSlots) {
        val activity =
            dataset.activitiesById[extra.activityId] ?: unknown(ErrorCodes.UNKNOWN_ACTIVITY, extra.activityId)
        StudentTimetable(dataset, accepted, calendar).clashFor(activity, extra.date)?.let { clash(it.id, activity.id) }
        accepted = accepted.copy(extraSlots = accepted.extraSlots + extra)
    }
}

/** First and last name are required, and a phone number that looks like one. */
fun checkContact(profile: Profile) {
    if (profile.firstName.isBlank() || profile.lastName.isBlank() || profile.fullName.length > 200) {
        throw ApiProblem(HttpStatusCode.UnprocessableEntity, ErrorCodes.MISSING_NAME)
    }
    if (!isValidPhone(profile.phone)) throw ApiProblem(HttpStatusCode.UnprocessableEntity, ErrorCodes.INVALID_PHONE)
}

private fun unknown(code: String, id: String): Nothing =
    throw ApiProblem(HttpStatusCode.UnprocessableEntity, code, listOf(id))

private fun clash(a: String, b: String): Nothing =
    throw ApiProblem(HttpStatusCode.Conflict, ErrorCodes.CLASH, listOf(a, b))
