// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable

import kotlinx.datetime.DayOfWeek.FRIDAY
import kotlinx.datetime.DayOfWeek.THURSDAY
import kotlinx.datetime.DayOfWeek.WEDNESDAY
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import net.mehstudios.etimetable.model.Activity
import net.mehstudios.etimetable.model.ActivityType
import net.mehstudios.etimetable.model.Audience
import net.mehstudios.etimetable.model.Break
import net.mehstudios.etimetable.model.Building
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Group
import net.mehstudios.etimetable.model.HalfGroup
import net.mehstudios.etimetable.model.LocalizedText
import net.mehstudios.etimetable.model.OfficeHour
import net.mehstudios.etimetable.model.Professor
import net.mehstudios.etimetable.model.Programme
import net.mehstudios.etimetable.model.Room
import net.mehstudios.etimetable.model.Semester
import net.mehstudios.etimetable.model.Series
import net.mehstudios.etimetable.model.StudyLevel
import net.mehstudios.etimetable.model.Subject
import net.mehstudios.etimetable.model.WeekRule
import net.mehstudios.etimetable.model.WeeklySlot

/**
 * A made-up semester for tests. Only the code formats are FAIMA's; names,
 * rooms and times are invented. Week 1 starts on 28 September 2026, the
 * winter break has no week numbers, 1 December is a holiday, and Wednesday
 * 28 October is in week 5 (odd), as on the mockups.
 */
object SampleData {
    fun text(ro: String, en: String = ro) = LocalizedText(ro, en)

    private fun t(value: String) = LocalTime.parse(value)

    val semester = Semester(
        id = "2026-1",
        name = text("Semestrul 1, 2026-2027", "Semester 1, 2026-2027"),
        firstDay = LocalDate(2026, 9, 28),
        lastDay = LocalDate(2027, 1, 15),
        breaks = listOf(
            Break(text("Ziua Națională", "National Day"), LocalDate(2026, 12, 1), LocalDate(2026, 12, 1)),
            Break(text("Vacanța de iarnă", "Winter break"), LocalDate(2026, 12, 21), LocalDate(2027, 1, 3)),
        ),
    )

    private fun activity(
        id: String,
        subject: String,
        type: ActivityType,
        day: kotlinx.datetime.DayOfWeek,
        start: String,
        end: String,
        audience: Audience,
        professor: String? = null,
        room: String? = null,
        weeks: WeekRule = WeekRule.All,
        erasmus: Boolean = false,
    ) = Activity(id, subject, type, WeeklySlot.of(day, t(start), t(end), weeks), professor, room, audience, erasmus)

    val dataset = Dataset(
        semester = semester,
        programmes = listOf(
            Programme("iedeee", "IEDEEE", text("Inginerie economică în domeniul electric"), StudyLevel.BACHELOR, "ro"),
        ),
        series = listOf(Series("e4", "iedeee", 4, "E")),
        groups = listOf(Group("1541E", "e4", "1541E"), Group("1542E", "e4", "1542E")),
        halfGroups = listOf(
            HalfGroup("1541E/A", "1541E", "A"),
            HalfGroup("1541E/B", "1541E", "B"),
            HalfGroup("1542E/A", "1542E", "A"),
            HalfGroup("1542E/B", "1542E", "B"),
        ),
        subjects = listOf(
            Subject("micro", "MICRO", text("Microeconomie", "Microeconomics"), "iedeee", 4),
            Subject("contab", "CONTAB", text("Contabilitate financiară", "Financial accounting"), "iedeee", 4),
            Subject("calit", "CALIT", text("Managementul calității", "Quality management"), "iedeee", 4),
            Subject("mkt", "MKT", text("Marketing"), "iedeee", 4),
        ),
        professors = listOf(
            Professor(
                "marinescu",
                "Conf. dr.",
                "Ioana Marinescu",
                officeHours = listOf(OfficeHour(WeeklySlot.of(WEDNESDAY, t("14:00"), t("16:00")), "ec210")),
            ),
            Professor("ilie", "As. dr.", "Radu Ilie"),
        ),
        buildings = listOf(
            Building("an", "AN", "Corpul AN"),
            Building("bn", "BN", "Corpul BN"),
            Building("ec", "EC", "Corpul EC"),
        ),
        rooms = listOf(
            Room("an015", "an", "AN 015"),
            Room("bn212a", "bn", "BN 212 a"),
            Room("bn212b", "bn", "BN 212 b"),
            Room("ec004", "ec", "EC 004"),
            Room("ec210", "ec", "EC 210"),
        ),
        activities = listOf(
            activity(
                "micro-c", "micro", ActivityType.COURSE, WEDNESDAY, "08:00", "10:00",
                Audience(
                    seriesIds = setOf("e4"),
                ),
                "marinescu", "an015", erasmus = true,
            ),
            activity(
                "contab-s",
                "contab",
                ActivityType.SEMINAR,
                WEDNESDAY,
                "10:00",
                "11:30",
                Audience(groupIds = setOf("1541E")),
                room = "ec004",
            ),
            activity(
                "calit-l-1541A", "calit", ActivityType.LAB, WEDNESDAY, "12:00", "14:00",
                Audience(
                    halfGroupIds = setOf("1541E/A"),
                ),
                "ilie", "bn212b",
            ),
            activity(
                "calit-l-1541B", "calit", ActivityType.LAB, WEDNESDAY, "14:00", "16:00",
                Audience(
                    halfGroupIds = setOf("1541E/B"),
                ),
                "ilie", "bn212b",
            ),
            activity(
                "calit-l-1542A", "calit", ActivityType.LAB, THURSDAY, "08:00", "10:00",
                Audience(
                    halfGroupIds = setOf("1542E/A"),
                ),
                "ilie", "bn212a",
            ),
            activity(
                "calit-l-1542B", "calit", ActivityType.LAB, FRIDAY, "10:00", "12:00",
                Audience(
                    halfGroupIds = setOf("1542E/B"),
                ),
                "ilie", "bn212a", erasmus = true,
            ),
            activity(
                "calit-c",
                "calit",
                ActivityType.COURSE,
                THURSDAY,
                "12:00",
                "14:00",
                Audience(seriesIds = setOf("e4")),
                room = "an015",
            ),
            activity(
                "mkt-p-1541",
                "mkt",
                ActivityType.PROJECT,
                WEDNESDAY,
                "16:00",
                "18:00",
                Audience(groupIds = setOf("1541E")),
                weeks = WeekRule.Odd,
            ),
            activity(
                "mkt-p-1542",
                "mkt",
                ActivityType.PROJECT,
                WEDNESDAY,
                "16:00",
                "18:00",
                Audience(groupIds = setOf("1542E")),
                weeks = WeekRule.Even,
            ),
        ),
    )

    fun activity(id: String): Activity = dataset.activitiesById.getValue(id)
}
