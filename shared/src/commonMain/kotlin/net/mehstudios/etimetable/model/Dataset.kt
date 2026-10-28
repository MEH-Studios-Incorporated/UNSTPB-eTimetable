// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.model

import kotlinx.serialization.Serializable

/** Everything the app needs for one semester; the body of `GET /api/v1/dataset`. */
@Serializable
data class Dataset(
    val semester: Semester,
    val programmes: List<Programme> = emptyList(),
    val series: List<Series> = emptyList(),
    val groups: List<Group> = emptyList(),
    val halfGroups: List<HalfGroup> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val professors: List<Professor> = emptyList(),
    val buildings: List<Building> = emptyList(),
    val rooms: List<Room> = emptyList(),
    val activities: List<Activity> = emptyList(),
) {
    // Lookups built once; the app reads them on every screen.
    val subjectsById: Map<String, Subject> by lazy { subjects.associateBy { it.id } }
    val professorsById: Map<String, Professor> by lazy { professors.associateBy { it.id } }
    val roomsById: Map<String, Room> by lazy { rooms.associateBy { it.id } }
    val buildingsById: Map<String, Building> by lazy { buildings.associateBy { it.id } }
    val groupsById: Map<String, Group> by lazy { groups.associateBy { it.id } }
    val halfGroupsById: Map<String, HalfGroup> by lazy { halfGroups.associateBy { it.id } }
    val activitiesById: Map<String, Activity> by lazy { activities.associateBy { it.id } }
}
