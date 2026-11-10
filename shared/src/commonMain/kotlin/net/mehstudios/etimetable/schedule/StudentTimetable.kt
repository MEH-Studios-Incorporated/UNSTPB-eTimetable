// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.schedule

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import net.mehstudios.etimetable.model.Activity
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Enrollment
import net.mehstudios.etimetable.model.Profile

data class NowAndNext(val current: Occurrence?, val next: Occurrence?)

/** A student's timetable, computed from the dataset and their choices; never stored. */
class StudentTimetable(
    private val dataset: Dataset,
    val profile: Profile,
    val calendar: TeachingCalendar = TeachingCalendar(dataset.semester),
) {
    /** What the student's group has, before the slots they dropped. */
    private val groupSlots: List<Activity> = baseActivities(dataset, profile.enrollment)
    private val base: List<Activity> = groupSlots.filter { it.id !in profile.droppedSlots }

    /** Activities that repeat every week their rule selects. */
    val recurring: List<Activity> = buildList {
        addAll(base)
        profile.extraSlots.filter { it.date == null }.forEach { extra ->
            dataset.activitiesById[extra.activityId]?.let(::add)
        }
    }.distinctBy { it.id }

    private val oneTime: List<Pair<LocalDate, Activity>> = profile.extraSlots.mapNotNull { extra ->
        val date = extra.date ?: return@mapNotNull null
        dataset.activitiesById[extra.activityId]?.let { date to it }
    }

    fun on(date: LocalDate): List<Occurrence> {
        val repeating = recurring.filter { calendar.meetsOn(it.slot, date) }.map { Occurrence(it, date) }
        val single = oneTime.filter { (d, a) -> d == date && calendar.meetsOn(a.slot, date) && a !in recurring }
            .map { (d, a) -> Occurrence(a, d) }
        return (repeating + single).sortedWith(compareBy({ it.start }, { it.end }))
    }

    fun between(from: LocalDate, to: LocalDate): List<Occurrence> = from.daysThrough(to).flatMap { on(it) }.toList()

    /** What is on at [at] and what comes after it, looking at most [horizonDays] ahead. */
    fun nowAndNext(at: LocalDateTime, horizonDays: Int = 21): NowAndNext {
        val today = on(at.date)
        val current = today.lastOrNull { it.start <= at && at < it.end }
        val next = today.firstOrNull { it.start > at }
            ?: at.date.plus(1, DateTimeUnit.DAY).daysThrough(at.date.plus(horizonDays, DateTimeUnit.DAY))
                .firstNotNullOfOrNull { on(it).firstOrNull() }
        return NowAndNext(current, next)
    }

    /** The first meeting of [activity] that starts after [at], or null when the semester has none left. */
    fun nextMeeting(activity: Activity, at: LocalDateTime): Occurrence? =
        calendar.occurrences(activity, at.date, calendar.semester.lastDay).firstOrNull { it.start > at }

    /** How [activity] stands for this student, as the subject page shows it. */
    fun standing(activity: Activity): SlotStanding {
        when (val enrollment = profile.enrollment) {
            is Enrollment.Erasmus -> if (activity.id in enrollment.activityIds) return SlotStanding.Picked
            is Enrollment.National -> if (groupSlots.any { it.id == activity.id }) {
                return if (activity.id in profile.droppedSlots) {
                    SlotStanding.Dropped(clashFor(activity))
                } else {
                    SlotStanding.Group
                }
            }
            null -> Unit
        }
        profile.extraSlots.firstOrNull { it.activityId == activity.id }?.let { return SlotStanding.Extra(it.date) }
        return SlotStanding.Open(clashFor(activity))
    }

    /**
     * Whether [activity] can leave the timetable for good: another slot of the same subject
     * and kind (another group's lab, say) stays every week, so the student never ends up
     * without one. A single visit can always go.
     */
    fun canRemove(activity: Activity): Boolean = recurring.any {
        it.id != activity.id && it.subjectId == activity.subjectId && it.type == activity.type
    }

    /**
     * What [candidate] would clash with if added: every week when [date] is
     * null, or only on [date] for a single visit. Null when it fits.
     */
    fun clashFor(candidate: Activity, date: LocalDate? = null): Activity? {
        if (date != null) {
            return on(date).firstOrNull {
                it.activity.id != candidate.id &&
                    timesOverlap(it.activity.slot.start, it.activity.slot.end, candidate.slot.start, candidate.slot.end)
            }?.activity
        }
        calendar.findClash(candidate, recurring)?.let { return it }
        return oneTime.firstOrNull { (d, a) ->
            a.id != candidate.id &&
                calendar.meetsOn(candidate.slot, d) &&
                calendar.meetsOn(a.slot, d) &&
                timesOverlap(a.slot.start, a.slot.end, candidate.slot.start, candidate.slot.end)
        }?.second
    }
}

sealed interface SlotStanding {
    /** Comes with the student's group or half-group. */
    data object Group : SlotStanding

    /** One of an Erasmus student's picks. */
    data object Picked : SlotStanding

    /** Added on top of the group: every week, or only on [date]. */
    data class Extra(val date: LocalDate?) : SlotStanding

    /** The student's group has it, but they dropped it for another; [clash] as for [Open]. */
    data class Dropped(val clash: Activity?) : SlotStanding

    /** Not in the timetable. [clash] is what it would overlap every week, or null when it fits. */
    data class Open(val clash: Activity?) : SlotStanding
}

/**
 * National students get the activities of their series, group and half-group.
 * Without a half-group chosen they see the labs of both halves and pick.
 */
fun baseActivities(dataset: Dataset, enrollment: Enrollment?): List<Activity> = when (enrollment) {
    null -> emptyList()
    is Enrollment.Erasmus -> enrollment.activityIds.mapNotNull { dataset.activitiesById[it] }
    is Enrollment.National -> {
        val group = dataset.groupsById[enrollment.groupId]
        if (group == null) {
            emptyList()
        } else {
            val halvesOfGroup = dataset.halfGroups.filter { it.groupId == group.id }.mapTo(mutableSetOf()) { it.id }
            val myHalves = enrollment.halfGroupId?.let { setOf(it) } ?: halvesOfGroup
            dataset.activities.filter { a ->
                group.seriesId in a.audience.seriesIds ||
                    group.id in a.audience.groupIds ||
                    a.audience.halfGroupIds.any { it in myHalves }
            }
        }
    }
}
