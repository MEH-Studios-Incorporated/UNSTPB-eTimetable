// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import net.mehstudios.etimetable.model.Activity
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Enrollment
import net.mehstudios.etimetable.model.Profile
import net.mehstudios.etimetable.schedule.baseActivities

/** A group whose classes an import adds, changes or removes, and how many. */
internal data class TouchedGroup(val id: String, val code: String, val changes: Int)

/** The ids of the groups that attend [activity]: through its series, the group itself or one of its halves. */
private fun groupsOf(dataset: Dataset, activity: Activity): Set<String> {
    val a = activity.audience
    return dataset.groups.filter { it.seriesId in a.seriesIds || it.id in a.groupIds }.mapTo(mutableSetOf()) { it.id } +
        a.halfGroupIds.mapNotNull { dataset.halfGroupsById[it]?.groupId }
}

/**
 * The groups [diff] touches, most changes first. A change counts once per group, before
 * and after the import, so a class moved from one group to another counts for both.
 */
internal fun touchedGroups(old: Dataset, new: Dataset, diff: TimetableDiff): List<TouchedGroup> {
    val counts = mutableMapOf<String, Int>()
    for (change in diff.changes) {
        val before = old.activitiesById[change.activityId]?.let { groupsOf(old, it) }.orEmpty()
        val after = new.activitiesById[change.activityId]?.let { groupsOf(new, it) }.orEmpty()
        (before + after).forEach { counts[it] = (counts[it] ?: 0) + 1 }
    }
    return counts.map { (id, n) ->
        TouchedGroup(id, (new.groupsById[id] ?: old.groupsById[id])?.code ?: id, n)
    }.sortedWith(compareByDescending<TouchedGroup> { it.changes }.thenBy { it.code })
}

/**
 * The week of [groupId] after the import, as blocks: its classes in [new], marked added
 * or changed as [diff] says, and the ones it loses, taken from [old] and marked removed.
 * Classes whose row in the file could not be read ([unreadable]) stay as they are now.
 */
internal fun groupWeekAfterImport(
    old: Dataset,
    new: Dataset,
    diff: TimetableDiff,
    groupId: String,
    unreadable: Set<String> = emptySet(),
): List<GridEntry> {
    val kinds = diff.changes.associate { it.activityId to it.kind }
    // the subjects of both, so a removed subject keeps a colour
    val colours = subjectColours(new.copy(subjects = new.subjects + old.subjects))
    fun entries(dataset: Dataset, activities: List<Activity>, state: (Activity) -> ChangeKind?): List<GridEntry> {
        val group = dataset.groupsById[groupId] ?: return emptyList()
        return activities.map { a ->
            classEntry(
                dataset,
                colours,
                a,
                href = null,
                kindSuffix = halvesOf(dataset, group, a),
                who = dataset.professorsById[a.professorId]?.displayName?.trim(),
            ).copy(state = state(a))
        }
    }
    val after = baseActivities(new, Enrollment.National(groupId))
    val afterIds = after.mapTo(mutableSetOf()) { it.id }
    val lost = baseActivities(old, Enrollment.National(groupId)).filter { it.id !in afterIds }
    return entries(new, after) { kinds[it.id]?.takeIf { k -> k != ChangeKind.REMOVED } } +
        entries(old, lost) { if (it.id in unreadable) null else ChangeKind.REMOVED }
}

/**
 * Student accounts an import touches, by why. [national]: their group or half-group has
 * a class added, changed or removed. [erasmus]: they picked a class that changes or goes.
 * [slots]: an extra or dropped slot of theirs points at a removed class. [total] counts
 * each student once. [losing]: students with a removed class among their Erasmus picks,
 * extra or dropped slots, which the app stops showing.
 */
internal data class AffectedStudents(
    val national: Int,
    val erasmus: Int,
    val slots: Int,
    val total: Int,
    val losing: Int,
) {
    companion object {
        val NONE = AffectedStudents(0, 0, 0, 0, 0)
    }
}

/** One pass over [profiles]; a group's classes are looked up once per group and half-group. */
internal fun affectedStudents(
    old: Dataset,
    new: Dataset,
    diff: TimetableDiff,
    profiles: Iterable<Profile>,
): AffectedStudents {
    if (diff.changes.isEmpty()) return AffectedStudents.NONE
    val touched = diff.changes.mapTo(mutableSetOf()) { it.activityId }
    val notKept = diff.changes.filter { it.kind != ChangeKind.ADDED }.mapTo(mutableSetOf()) { it.activityId }
    val removed = diff.changes.filter { it.kind == ChangeKind.REMOVED }.mapTo(mutableSetOf()) { it.activityId }
    val byGroup = mutableMapOf<Enrollment.National, Boolean>()
    var national = 0
    var erasmus = 0
    var slots = 0
    var total = 0
    var losing = 0
    for (profile in profiles) {
        val enrollment = profile.enrollment
        val inGroup = enrollment is Enrollment.National &&
            byGroup.getOrPut(enrollment) {
                (baseActivities(old, enrollment) + baseActivities(new, enrollment)).any { it.id in touched }
            }
        val picks = (enrollment as? Enrollment.Erasmus)?.activityIds.orEmpty()
        val picked = picks.any { it in notKept }
        val slot = profile.extraSlots.any { it.activityId in removed } || profile.droppedSlots.any { it in removed }
        if (inGroup) national++
        if (picked) erasmus++
        if (slot) slots++
        if (inGroup || picked || slot) total++
        if (slot || picks.any { it in removed }) losing++
    }
    return AffectedStudents(national, erasmus, slots, total, losing)
}
