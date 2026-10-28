// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import net.mehstudios.etimetable.model.ActivityType
import net.mehstudios.etimetable.model.WeekRule
import net.mehstudios.etimetable.server.timetable.Csv
import net.mehstudios.etimetable.server.timetable.TimetableSheet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimetableSheetTest {
    private val sample = sampleDataset()

    @Test
    fun exportThenImportKeepsTheTimetable() {
        val csv = TimetableSheet.export(sample)
        val result = TimetableSheet.import(csv, sample.semester)
        assertEquals(emptyList(), result.errors)
        val imported = assertNotNull(result.dataset)

        assertEquals(sample.activities.map { it.id }, imported.activities.map { it.id })
        for (original in sample.activities) {
            val copy = imported.activitiesById.getValue(original.id)
            assertEquals(original.slot, copy.slot)
            assertEquals(original.type, copy.type)
            assertEquals(original.openToErasmus, copy.openToErasmus)
            assertEquals(sample.roomsById[original.roomId]?.code, imported.roomsById[copy.roomId]?.code)
        }
        // a second round gives the same sheet
        assertEquals(csv, TimetableSheet.export(imported))
        assertEquals(1, imported.professors.single { it.name == "Ioana Marinescu" }.officeHours.size)
    }

    @Test
    fun readsWhatExcelWrites() {
        // Excel's "CSV UTF-8" starts with a byte order mark
        val csv = Char(0xFEFF) +
            "programme;year;series;audience;subject_code;subject_ro;subject_en;" +
            "type;day;start;end;weeks;professor;room;erasmus\r\n" +
            "BEM;2;A;1221A/A;MKT;Marketing;Marketing;" +
            "Laborator;Marți;8.00;10;impare;Conf. dr. ing. Ana Pop;JA 101;Da\r\n" +
            "BEM;2;A;;MKT;;;curs;1;12:00;14:00;\"1-3,5\";Conf. dr. ing. Ana Pop;JA 101;nu\r\n"
        val dataset = assertNotNull(TimetableSheet.import(csv, sample.semester).dataset)

        val lab = dataset.activities.first { it.type == ActivityType.LAB }
        assertEquals(2, lab.slot.isoDay)
        assertEquals("08:00", lab.slot.start.toString())
        assertEquals("10:00", lab.slot.end.toString())
        assertEquals(WeekRule.Odd, lab.slot.weeks)
        assertEquals(setOf("1221A/A"), lab.audience.halfGroupIds)
        assertTrue(lab.openToErasmus)

        val course = dataset.activities.first { it.type == ActivityType.COURSE }
        assertEquals(WeekRule.Only(setOf(1, 2, 3, 5)), course.slot.weeks)
        assertEquals(1, course.audience.seriesIds.size)

        val professor = dataset.professors.single()
        assertEquals("Conf. dr. ing.", professor.title)
        assertEquals("Ana Pop", professor.name)
        assertEquals("JA", dataset.buildings.single().code)
    }

    @Test
    fun errorsNameTheLineAndSaveNothing() {
        val csv =
            "programme,year,series,audience,subject_code,subject_ro,subject_en," +
                "type,day,start,end,weeks,professor,room,erasmus\n" +
                "BEM,2,A,,MKT,Marketing,,curs,luni,10:00,12:00,,,,\n" +
                "BEM,2,A,,MKT,Marketing,,curs,vinerea,10:00,12:00,,,,\n" +
                "BEM,2,A,,MKT,Marketing,,atelier,luni,10:00,12:00,,,,\n" +
                "BEM,2,A,,MKT,Marketing,,curs,luni,12:00,10:00,,,,\n"
        val result = TimetableSheet.import(csv, sample.semester)
        assertNull(result.dataset)
        assertEquals(3, result.errors.size, result.errors.toString())
        assertTrue(result.errors[0].startsWith("Rândul 3"), result.errors[0])
        assertTrue(result.errors[1].startsWith("Rândul 4"), result.errors[1])
    }

    @Test
    fun missingColumns() {
        val result = TimetableSheet.import("programme;year\nBEM;2\n", sample.semester)
        assertTrue(result.errors.single().startsWith("Lipsesc coloanele"))
    }

    @Test
    fun warnsAboutDoubleBookedRooms() {
        val lab = sample.activities.first { it.id == "calit-l-1541A" }
        val twin = lab.copy(id = "twin", professorId = null)
        val warnings = TimetableSheet.doubleBookings(sample.copy(activities = sample.activities + twin))
        assertEquals(1, warnings.size, warnings.toString())
        assertTrue("BN 212 b" in warnings.single())
    }

    @Test
    fun quotesFieldsThatNeedIt() {
        val text = Csv.write(listOf(listOf("a;b", "say \"hi\"", "plain")))
        assertEquals(listOf(listOf("a;b", "say \"hi\"", "plain")), Csv.parse(text))
    }

    @Test
    fun splitsAcademicTitles() {
        assertEquals("Prof. dr. ing." to "C. Voicu", TimetableSheet.splitTitle("Prof. dr. ing. C. Voicu"))
        assertEquals("Ș.l. dr." to "Dan Pop", TimetableSheet.splitTitle("Ș.l. dr. Dan Pop"))
        assertEquals("" to "Dan Pop", TimetableSheet.splitTitle("Dan Pop"))
    }
}
