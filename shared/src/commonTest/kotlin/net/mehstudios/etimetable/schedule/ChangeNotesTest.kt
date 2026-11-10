// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.schedule

import kotlinx.datetime.DayOfWeek.THURSDAY
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import net.mehstudios.etimetable.SampleData
import net.mehstudios.etimetable.model.Activity
import net.mehstudios.etimetable.model.ActivityType
import net.mehstudios.etimetable.model.Audience
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Enrollment
import net.mehstudios.etimetable.model.Profile
import net.mehstudios.etimetable.model.WeeklySlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class ChangeNotesTest {
    private val profile = Profile(enrollment = Enrollment.National("1541E", "1541E/A"))
    private val old = SampleData.dataset

    // Monday 26 October 2026, 09:00 in Bucharest
    private val seen = LocalDateTime(2026, 10, 26, 9, 0).toInstant(FacultyTimeZone)

    private fun edit(id: String, change: (Activity) -> Activity): Dataset =
        old.copy(activities = old.activities.map { if (it.id == id) change(it) else it })

    private fun changes(new: Dataset) = timetableChanges(old, new, profile, seen)

    @Test
    fun theFirstSyncMakesNoNotes() {
        assertEquals(emptyList(), timetableChanges(null, old, profile, seen).notes)
    }

    @Test
    fun anUnchangedTimetableMakesNoNotes() {
        assertEquals(emptyList(), changes(old.copy()).notes)
    }

    @Test
    fun aMovedRoomKeepsBothCodes() {
        val notes = changes(edit("contab-s") { it.copy(roomId = "bn212a") }).notes
        assertEquals(listOf(ChangeNote("contab-s", ChangeKind.ROOM, seen, old = "EC004", new = "BN212a")), notes)
    }

    @Test
    fun aNewDayOrTimeKeepsBothSlots() {
        val moved = WeeklySlot.of(THURSDAY, LocalTime(10, 0), LocalTime(12, 0))
        val notes = changes(edit("micro-c") { it.copy(slot = moved) }).notes
        assertEquals(
            listOf(
                ChangeNote(
                    "micro-c",
                    ChangeKind.TIME,
                    seen,
                    oldSlot = old.activitiesById.getValue("micro-c").slot,
                    newSlot = moved,
                ),
            ),
            notes,
        )
    }

    @Test
    fun anotherTeacherIsNamed() {
        val notes = changes(edit("calit-l-1541A") { it.copy(professorId = "marinescu") }).notes
        assertEquals(
            listOf(
                ChangeNote(
                    "calit-l-1541A",
                    ChangeKind.TEACHER,
                    seen,
                    old = "As. dr. Radu Ilie",
                    new = "Conf. dr. Ioana Marinescu",
                ),
            ),
            notes,
        )
    }

    @Test
    fun aRemovedClassKeepsHowItWas() {
        val notes = changes(old.copy(activities = old.activities.filter { it.id != "mkt-p-1541" })).notes
        val removed = SampleData.activity("mkt-p-1541")
        assertEquals(listOf(ChangeNote("mkt-p-1541", ChangeKind.REMOVED, seen, removed = removed)), notes)
    }

    @Test
    fun anAddedClassIsNoted() {
        val extra = Activity(
            "mkt-c",
            "mkt",
            ActivityType.COURSE,
            WeeklySlot.of(THURSDAY, LocalTime(16, 0), LocalTime(18, 0)),
            audience = Audience(seriesIds = setOf("e4")),
        )
        val notes = changes(old.copy(activities = old.activities + extra)).notes
        assertEquals(listOf(ChangeNote("mkt-c", ChangeKind.ADDED, seen)), notes)
    }

    @Test
    fun changesToOtherGroupsAreNotTheStudents() {
        assertEquals(emptyList(), changes(edit("calit-l-1542A") { it.copy(roomId = "ec210") }).notes)
    }

    @Test
    fun aClassThatGotANewIdIsStillTheSameClass() {
        val moved = WeeklySlot.of(THURSDAY, LocalTime(16, 0), LocalTime(17, 30))
        val result = changes(edit("contab-s") { it.copy(id = "contab-s-jo", slot = moved) })
        assertEquals(mapOf("contab-s" to "contab-s-jo"), result.renamed)
        assertEquals(listOf(ChangeKind.TIME), result.notes.map { it.kind })
        assertEquals("contab-s-jo", result.notes.single().activityId)
    }

    @Test
    fun aRoomChangedTwiceKeepsTheFirstOldRoomAndChangedBackHasNoNote() {
        val first = mergeChangeNotes(emptyList(), changes(edit("contab-s") { it.copy(roomId = "bn212a") }), seen)
        val later = seen + 1.hours
        val second =
            TimetableChanges(listOf(ChangeNote("contab-s", ChangeKind.ROOM, later, old = "BN212a", new = "EC210")))
        assertEquals(
            listOf(ChangeNote("contab-s", ChangeKind.ROOM, later, old = "EC004", new = "EC210")),
            mergeChangeNotes(first, second, later),
        )
        val back =
            TimetableChanges(listOf(ChangeNote("contab-s", ChangeKind.ROOM, later, old = "BN212a", new = "EC004")))
        assertEquals(emptyList(), mergeChangeNotes(first, back, later))
    }

    @Test
    fun aClassAddedAndRemovedAgainLeavesNothing() {
        val added = listOf(ChangeNote("mkt-c", ChangeKind.ADDED, seen))
        val removed = TimetableChanges(listOf(ChangeNote("mkt-c", ChangeKind.REMOVED, seen + 1.hours)))
        assertEquals(emptyList(), mergeChangeNotes(added, removed, seen + 1.hours))
    }

    @Test
    fun notesOlderThanAWeekGo() {
        val note = ChangeNote("contab-s", ChangeKind.ROOM, seen, old = "EC004", new = "BN212a")
        assertEquals(listOf(note), mergeChangeNotes(listOf(note), TimetableChanges(emptyList()), seen + 6.days))
        assertEquals(emptyList(), mergeChangeNotes(listOf(note), TimetableChanges(emptyList()), seen + 7.days))
    }

    @Test
    fun aNoteShowsUntilTheClassHasMetOnce() {
        // contab-s meets on Wednesdays, 10:00 to 11:30
        val note = ChangeNote("contab-s", ChangeKind.ROOM, seen, old = "EC004", new = "BN212a")
        val calendar = TeachingCalendar(old.semester)
        assertTrue(note.isShown(LocalDateTime(2026, 10, 28, 11, 0), old, calendar))
        assertFalse(note.isShown(LocalDateTime(2026, 10, 28, 11, 30), old, calendar))
    }

    @Test
    fun aRemovedClassShowsUntilItWouldHaveMet() {
        // mkt-p-1541 met on Wednesdays of odd weeks, 16:00 to 18:00; 28 October is in week 5
        val removed = old.copy(activities = old.activities.filter { it.id != "mkt-p-1541" })
        val note = changes(removed).notes.single()
        val calendar = TeachingCalendar(removed.semester)
        assertTrue(note.isShown(LocalDateTime(2026, 10, 28, 17, 0), removed, calendar))
        assertFalse(note.isShown(LocalDateTime(2026, 10, 28, 18, 0), removed, calendar))
    }

    @Test
    fun aNoteGoesAfterAWeekEvenWithoutAMeeting() {
        val note = ChangeNote("contab-s", ChangeKind.ROOM, seen, old = "EC004", new = "BN212a")
        // a dataset where the class no longer meets this semester: no meeting to wait for
        val calendar = TeachingCalendar(old.semester.copy(lastDay = kotlinx.datetime.LocalDate(2026, 10, 27)))
        assertTrue(note.isShown(LocalDateTime(2026, 11, 1, 9, 0), old, calendar))
        assertFalse(note.isShown(LocalDateTime(2026, 11, 2, 9, 0), old, calendar))
    }
}
