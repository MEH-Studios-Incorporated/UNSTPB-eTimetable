// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import net.mehstudios.etimetable.model.ActivityType
import net.mehstudios.etimetable.model.Audience
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Enrollment
import net.mehstudios.etimetable.model.ExtraSlot
import net.mehstudios.etimetable.model.Profile
import net.mehstudios.etimetable.model.WeekRule
import net.mehstudios.etimetable.model.WeeklySlot
import net.mehstudios.etimetable.server.sampleDataset
import net.mehstudios.etimetable.server.timetable.Csv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImportImpactTest {
    private val old = sampleDataset()

    /**
     * contab-s (group 1541E) removed, calit-l-1541A (half 1541E/A) moved to another room,
     * calit-s-new added for half 1542E/B.
     */
    private val new: Dataset = run {
        val lab = old.activitiesById.getValue("calit-l-1541A")
        val otherRoom = old.rooms.first { it.id != lab.roomId }
        val added = old.activitiesById.getValue("calit-l-1542B").copy(
            id = "calit-s-new",
            type = ActivityType.SEMINAR,
            slot = WeeklySlot(2, LocalTime(10, 0), LocalTime(12, 0), WeekRule.All),
            audience = Audience(halfGroupIds = setOf("1542E/B")),
        )
        old.copy(
            activities = old.activities.filter { it.id != "contab-s" }
                .map { if (it.id == lab.id) it.copy(roomId = otherRoom.id) else it } + added,
        )
    }
    private val diff = diffTimetables(old, new)

    @Test
    fun theGroupsAreThoseOfEveryChangeMostChangesFirst() {
        assertEquals(
            listOf(TouchedGroup("1541E", "1541E", 2), TouchedGroup("1542E", "1542E", 1)),
            touchedGroups(old, new, diff),
        )
        assertEquals(emptyList(), touchedGroups(old, old, diffTimetables(old, old)))
    }

    @Test
    fun theGroupWeekMarksEachChangeAndKeepsRemovedClasses() {
        val states = groupWeekAfterImport(old, new, diff, "1541E").associate { it.id to it.state }
        assertEquals(
            mapOf(
                "micro-c" to null,
                "calit-l-1541A" to ChangeKind.CHANGED,
                "calit-l-1541B" to null,
                "calit-c" to null,
                "mkt-p-1541" to null,
                "contab-s" to ChangeKind.REMOVED,
            ),
            states,
        )
        val added = groupWeekAfterImport(old, new, diff, "1542E").single { it.id == "calit-s-new" }
        assertEquals(ChangeKind.ADDED, added.state)
        // marked with its half, as on the schedule's week
        assertEquals("Seminar · 1542E/B", added.kind)
        // a class whose row could not be read is shown as it is now
        val unreadable = groupWeekAfterImport(old, new, diff, "1541E", unreadable = setOf("contab-s"))
        assertNull(unreadable.single { it.id == "contab-s" }.state)
    }

    @Test
    fun affectedStudentsAreCountedByWhy() {
        val profiles = listOf(
            // the whole group: has the removed seminar and the changed lab
            Profile(enrollment = Enrollment.National("1541E")),
            // the other half of 1542E: the new seminar is not theirs
            Profile(enrollment = Enrollment.National("1542E", "1542E/A")),
            // no half chosen: sees both halves, so the new seminar too
            Profile(enrollment = Enrollment.National("1542E")),
            Profile(enrollment = Enrollment.Erasmus(setOf("calit-l-1541A"))),
            Profile(enrollment = Enrollment.Erasmus(setOf("calit-l-1542B"))),
            Profile(
                enrollment = Enrollment.National("1542E", "1542E/A"),
                extraSlots = listOf(ExtraSlot("contab-s", LocalDate(2026, 10, 7))),
            ),
            Profile(enrollment = Enrollment.Erasmus(setOf("contab-s", "micro-c"))),
            Profile(droppedSlots = setOf("contab-s")),
            Profile(),
        )
        assertEquals(
            AffectedStudents(national = 2, erasmus = 2, slots = 2, total = 6, losing = 3),
            affectedStudents(old, new, diff, profiles),
        )
        assertEquals(AffectedStudents.NONE, affectedStudents(old, old, diffTimetables(old, old), profiles))
    }

    @Test
    fun aRowIsReplacedWithoutTouchingTheRestOfTheFile() {
        val text = "id,day,note\na,luni,\"two\nlines\"\n\nb,marti,x\r\nc,joi,y"
        val replaced = Csv.replaceRow(text, 2, listOf("b", "vineri", "with, comma"))
        assertEquals("id,day,note\na,luni,\"two\nlines\"\n\nb,vineri,\"with, comma\"\r\nc,joi,y", replaced)
        assertEquals(listOf("b", "vineri", "with, comma"), Csv.parse(replaced)[2])
        assertEquals(
            "id,day,note\na,luni,\"two\nlines\"\n\nb,marti,x\r\nc,mi",
            Csv.replaceRow(text, 3, listOf("c", "mi")),
        )

        // a spreadsheet's file: byte order mark and semicolons stay
        val excel = Csv.write(listOf(listOf("id", "day"), listOf("a", "luni"), listOf("b", "marti")))
        val fixed = Csv.replaceRow(excel, 1, listOf("a", "joi"))
        assertEquals(excel.replace("a;luni", "a;joi"), fixed)
        assertEquals(listOf(listOf("id", "day"), listOf("a", "joi"), listOf("b", "marti")), Csv.parse(fixed))
    }
}
