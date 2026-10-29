// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.schedule

import net.mehstudios.etimetable.model.Activity
import net.mehstudios.etimetable.model.ActivityType
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Professor

/** The series an activity reaches, directly or through its groups and half-groups. */
fun Dataset.seriesOf(activity: Activity): Set<String> {
    val audience = activity.audience
    val groups = audience.groupIds + audience.halfGroupIds.mapNotNull { halfGroupsById[it]?.groupId }
    return audience.seriesIds + groups.mapNotNull { groupsById[it]?.seriesId }
}

/**
 * Who gives the course of a seminar, lab or project to the same series. The activity's own
 * professor is the assistant who runs it; this is the person students also ask about.
 */
fun Dataset.courseProfessors(activity: Activity): List<Professor> {
    if (activity.type == ActivityType.COURSE) return emptyList()
    val series = seriesOf(activity)
    return activities
        .filter { it.subjectId == activity.subjectId && it.type == ActivityType.COURSE }
        .filter { course -> seriesOf(course).any { it in series } }
        .mapNotNull { professorsById[it.professorId] }
        .distinct()
}
