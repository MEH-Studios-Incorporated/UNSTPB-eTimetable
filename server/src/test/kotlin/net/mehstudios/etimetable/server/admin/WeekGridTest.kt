// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import io.ktor.http.parametersOf
import kotlinx.datetime.LocalTime
import net.mehstudios.etimetable.model.Parity
import net.mehstudios.etimetable.model.WeekRule
import net.mehstudios.etimetable.model.WeeklySlot
import net.mehstudios.etimetable.schedule.TeachingCalendar
import net.mehstudios.etimetable.server.sampleDataset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeekGridTest {
    private fun entry(id: String, day: Int, start: String, end: String, weeks: WeekRule = WeekRule.All) =
        GridEntry(id, WeeklySlot(day, LocalTime.parse(start), LocalTime.parse(end), weeks), id, "Curs")

    private fun WeekLayout.of(id: String) = blocks.single { it.entry.id == id }

    @Test
    fun blocksArePlacedByTheirTimesFromEightOClock() {
        val layout = layoutWeek(listOf(entry("a", 1, "08:00", "10:00"), entry("b", 3, "10:50", "12:20")))
        assertEquals(8, layout.firstHour)
        assertEquals(20, layout.lastHour)
        assertEquals(listOf(1, 2, 3, 4, 5), layout.days)
        assertEquals(Placement(layout.of("a").entry, top = 0, length = 120, column = 0, columns = 1), layout.of("a"))
        assertEquals(170, layout.of("b").top)
        assertEquals(90, layout.of("b").length)
    }

    @Test
    fun theGridStretchesForEarlyLateAndWeekendClasses() {
        val layout = layoutWeek(
            listOf(
                entry("early", 1, "07:30", "09:00"),
                entry("late", 2, "19:00", "21:10"),
                entry("sat", 6, "10:00", "12:00"),
            ),
        )
        assertEquals(7, layout.firstHour)
        assertEquals(22, layout.lastHour)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), layout.days)
        assertEquals(30, layout.of("early").top)
        assertEquals(12 * 60, layout.of("late").top)
        assertEquals(listOf(1, 2, 3, 4, 5), layoutWeek(emptyList()).days)
    }

    @Test
    fun overlappingClassesShareTheDayInSubColumns() {
        val layout = layoutWeek(
            listOf(
                entry("a", 1, "08:00", "10:00"),
                entry("b", 1, "09:00", "11:00"),
                // starts when a ends, so it reuses a's column, but still overlaps b
                entry("c", 1, "10:00", "12:00"),
                entry("d", 1, "14:00", "16:00"),
                // the same times on another day do not count
                entry("e", 2, "09:00", "11:00"),
            ),
        )
        assertEquals(0 to 2, layout.of("a").let { it.column to it.columns })
        assertEquals(1 to 2, layout.of("b").let { it.column to it.columns })
        assertEquals(0 to 2, layout.of("c").let { it.column to it.columns })
        assertEquals(0 to 1, layout.of("d").let { it.column to it.columns })
        assertEquals(0 to 1, layout.of("e").let { it.column to it.columns })

        val three = layoutWeek(
            listOf(entry("x", 4, "10:00", "12:00"), entry("y", 4, "10:00", "12:00"), entry("z", 4, "11:00", "13:00")),
        )
        assertEquals(listOf(0, 1, 2), listOf("x", "y", "z").map { three.of(it).column })
        assertTrue(three.blocks.all { it.columns == 3 })
    }

    @Test
    fun theParityFilterKeepsWhatMeetsInThoseWeeks() {
        val calendar = TeachingCalendar(sampleDataset().semester)
        assertTrue(runsIn(WeekRule.Odd, null, calendar))
        assertTrue(runsIn(WeekRule.All, Parity.EVEN, calendar))
        assertTrue(runsIn(WeekRule.Odd, Parity.ODD, calendar))
        assertFalse(runsIn(WeekRule.Odd, Parity.EVEN, calendar))
        assertFalse(runsIn(WeekRule.Even, Parity.ODD, calendar))
        // numbered weeks follow the calendar: 1 and 3 are odd
        assertTrue(runsIn(WeekRule.Only(setOf(1, 3)), Parity.ODD, calendar))
        assertFalse(runsIn(WeekRule.Only(setOf(1, 3)), Parity.EVEN, calendar))
        assertTrue(runsIn(WeekRule.Only(setOf(1, 2)), Parity.EVEN, calendar))

        assertEquals(WeekFilter.ODD, WeekFilter.of(parametersOf(WeekFilter.PARAM, "impare")))
        assertEquals(WeekFilter.ALL, WeekFilter.of(parametersOf(WeekFilter.PARAM, "altceva")))
        assertEquals("Impară", weekMark(WeekRule.Odd))
        assertEquals("Săpt. 2, 9", weekMark(WeekRule.Only(setOf(9, 2))))
        assertNull(weekMark(WeekRule.All))
    }

    @Test
    fun subjectColoursFollowTheIdsNotTheListOrder() {
        val dataset = sampleDataset()
        val colours = subjectColours(dataset)
        // calit, contab, micro, mkt
        assertEquals(SUBJECT_COLOURS[0], colours["calit"])
        assertEquals(SUBJECT_COLOURS[3], colours["mkt"])
        assertEquals(colours, subjectColours(dataset.copy(subjects = dataset.subjects.reversed())))
    }

    @Test
    fun aMessageJoinsTheQueryOfTheReturnAddress() {
        assertEquals("/admin/x?msg=ok", withParam("/admin/x", "msg=ok"))
        assertEquals("/admin/x?view=week&activity=a&msg=ok", withParam("/admin/x?view=week&activity=a", "msg=ok"))
        assertEquals("/admin/x?a=1&msg=ok#panel", withParam("/admin/x?a=1#panel", "msg=ok"))
    }

    @Test
    fun classesClashOnlyWhenTheyShareSomething() {
        val dataset = sampleDataset()
        val calendar = TeachingCalendar(dataset.semester)
        val labA = dataset.activitiesById.getValue("calit-l-1541A")
        assertTrue(clashesOf(dataset, calendar, labA).isEmpty())
        // the same lab moved onto the course: the series course has the same students
        val moved = labA.copy(slot = labA.slot.copy(start = LocalTime(8, 0), end = LocalTime(10, 0)))
        val clashes = clashesOf(
            dataset.copy(
                activities = dataset.activities.map {
                    if (it.id ==
                        moved.id
                    ) {
                        moved
                    } else {
                        it
                    }
                },
            ),
            calendar,
            moved,
        )
        assertEquals(listOf("micro-c" to "aceleași grupe"), clashes.map { it.first.id to it.second })
        // odd and even projects of different groups at the same hour do not clash
        assertTrue(clashesOf(dataset, calendar, dataset.activitiesById.getValue("mkt-p-1541")).isEmpty())
    }
}
