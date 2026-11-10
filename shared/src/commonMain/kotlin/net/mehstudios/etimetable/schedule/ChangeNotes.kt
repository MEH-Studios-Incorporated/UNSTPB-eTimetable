// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.schedule

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import net.mehstudios.etimetable.model.Activity
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Profile
import net.mehstudios.etimetable.model.WeeklySlot
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** How long a change note shows at most; it goes earlier once the class has met. */
val CHANGE_NOTE_LIFETIME = 7.days

@Serializable
enum class ChangeKind { ROOM, TIME, TEACHER, ADDED, REMOVED }

/**
 * One change to a class of the student's own timetable, found when a sync brought a new
 * dataset. Kept on the phone so the class can say what changed ("Sală schimbată, era BN 024").
 */
@Serializable
data class ChangeNote(
    val activityId: String,
    val kind: ChangeKind,
    val seenAt: Instant,
    /** [ChangeKind.ROOM]: the room codes; [ChangeKind.TEACHER]: the names. Null when there was none. */
    val old: String? = null,
    val new: String? = null,
    /** [ChangeKind.TIME]: the slot before and after (day, hours or weeks). */
    val oldSlot: WeeklySlot? = null,
    val newSlot: WeeklySlot? = null,
    /** [ChangeKind.REMOVED]: the class as it was; the new dataset no longer has it. */
    val removed: Activity? = null,
)

/**
 * What a new dataset changed in a student's classes. [renamed] maps an old activity id to a
 * new one when the timetable gave the same class another id (an import without ids makes
 * the id from the day and the hour, so a moved class gets a new one).
 */
data class TimetableChanges(val notes: List<ChangeNote>, val renamed: Map<String, String> = emptyMap())

/** Every class the student has: the group's (minus the dropped ones), the picks and the extras. */
fun ownActivities(dataset: Dataset, profile: Profile): List<Activity> {
    val timetable = StudentTimetable(dataset, profile)
    val extras = profile.extraSlots.mapNotNull { dataset.activitiesById[it.activityId] }
    return (timetable.recurring + extras).distinctBy { it.id }
}

/**
 * Compares the student's classes in [old] and [new]. Nothing is reported on the first
 * download ([old] null), without an enrollment, or when the semester itself changed.
 */
fun timetableChanges(old: Dataset?, new: Dataset, profile: Profile?, seenAt: Instant): TimetableChanges {
    if (old == null || profile?.enrollment == null || old.semester.id != new.semester.id) {
        return TimetableChanges(emptyList())
    }
    val before = ownActivities(old, profile).associateBy { it.id }
    val after = ownActivities(new, profile).associateBy { it.id }
    val removed = before.values.filter { it.id !in after }.toMutableList()
    val added = after.values.filter { it.id !in before }.toMutableList()

    // a class that left under one id and came back under another is the same class, when
    // nothing else of the same subject, type and audience could be it
    val renamed = mutableMapOf<String, String>()
    fun key(a: Activity) = Triple(a.subjectId, a.type, a.audience)
    removed.groupBy(::key).forEach { (k, gone) ->
        val came = added.filter { key(it) == k }
        if (gone.size == 1 && came.size == 1) renamed[gone.single().id] = came.single().id
    }
    removed.removeAll { it.id in renamed }
    added.removeAll { it.id in renamed.values }

    val pairs = before.values.mapNotNull { a -> after[a.id]?.let { a to it } } +
        renamed.map { (from, to) -> before.getValue(from) to after.getValue(to) }
    val notes = buildList {
        pairs.forEach { (was, now) -> addAll(differences(old, was, new, now, seenAt)) }
        removed.forEach { add(ChangeNote(it.id, ChangeKind.REMOVED, seenAt, removed = it)) }
        added.forEach { add(ChangeNote(it.id, ChangeKind.ADDED, seenAt)) }
    }
    return TimetableChanges(notes, renamed)
}

private fun differences(old: Dataset, was: Activity, new: Dataset, now: Activity, seenAt: Instant): List<ChangeNote> =
    buildList {
        val oldRoom = old.roomsById[was.roomId]?.code ?: was.roomId
        val newRoom = new.roomsById[now.roomId]?.code ?: now.roomId
        if (oldRoom != newRoom) add(ChangeNote(now.id, ChangeKind.ROOM, seenAt, old = oldRoom, new = newRoom))
        if (was.slot != now.slot) {
            add(ChangeNote(now.id, ChangeKind.TIME, seenAt, oldSlot = was.slot, newSlot = now.slot))
        }
        val oldTeacher = old.professorsById[was.professorId]?.displayName ?: was.professorId
        val newTeacher = new.professorsById[now.professorId]?.displayName ?: now.professorId
        if (oldTeacher != newTeacher) {
            add(ChangeNote(now.id, ChangeKind.TEACHER, seenAt, old = oldTeacher, new = newTeacher))
        }
    }

/**
 * The notes to keep after a sync: [existing] ones carried over to renamed classes, merged
 * with the [changes] just found. A value changed twice keeps the first old value; one
 * changed back loses its note. Notes older than [CHANGE_NOTE_LIFETIME] go.
 */
fun mergeChangeNotes(existing: List<ChangeNote>, changes: TimetableChanges, now: Instant): List<ChangeNote> {
    val notes = existing.map { note ->
        changes.renamed[note.activityId]?.let { note.copy(activityId = it) } ?: note
    }.associateBy { it.activityId to it.kind }.toMutableMap()
    changes.notes.forEach { fresh ->
        val id = fresh.activityId
        val earlier = notes[id to fresh.kind]
        when (fresh.kind) {
            ChangeKind.REMOVED -> {
                val wasNew = notes.remove(id to ChangeKind.ADDED) != null
                notes.keys.removeAll { it.first == id }
                // a class that came and went again is no change at all
                if (!wasNew) notes[id to ChangeKind.REMOVED] = fresh
            }
            ChangeKind.ADDED -> {
                notes.keys.removeAll { it.first == id }
                notes[id to ChangeKind.ADDED] = fresh
            }
            ChangeKind.ROOM, ChangeKind.TEACHER -> when {
                // a new class says so; its room or teacher are not a change for the student
                (id to ChangeKind.ADDED) in notes -> Unit
                earlier == null -> notes[id to fresh.kind] = fresh
                earlier.old == fresh.new -> notes.remove(id to fresh.kind)
                else -> notes[id to fresh.kind] = fresh.copy(old = earlier.old)
            }
            ChangeKind.TIME -> when {
                (id to ChangeKind.ADDED) in notes -> Unit
                earlier == null -> notes[id to fresh.kind] = fresh
                earlier.oldSlot == fresh.newSlot -> notes.remove(id to fresh.kind)
                else -> notes[id to fresh.kind] = fresh.copy(oldSlot = earlier.oldSlot)
            }
        }
    }
    return notes.values.filter { now < it.seenAt + CHANGE_NOTE_LIFETIME }.sortedBy { it.seenAt }
}

/**
 * Whether [note] still shows at [at] (faculty time): for [CHANGE_NOTE_LIFETIME] after it was
 * seen, and only until the class has met once since. A removed class counts as met once the
 * time it would have met has passed.
 */
fun ChangeNote.isShown(at: LocalDateTime, dataset: Dataset, calendar: TeachingCalendar): Boolean {
    val seen = seenAt.toLocalDateTime(FacultyTimeZone)
    if (at >= (seenAt + CHANGE_NOTE_LIFETIME).toLocalDateTime(FacultyTimeZone)) return false
    val activity = removed ?: dataset.activitiesById[activityId] ?: return false
    val firstMeeting = calendar.occurrences(activity, seen.date, calendar.semester.lastDay)
        .firstOrNull { it.end > seen } ?: return true
    return at < firstMeeting.end
}

/** The notes shown at [at], by activity id; removed classes are under their old id. */
fun List<ChangeNote>.shownAt(at: LocalDateTime, dataset: Dataset, calendar: TeachingCalendar) =
    filter { it.isShown(at, dataset, calendar) }.groupBy { it.activityId }
