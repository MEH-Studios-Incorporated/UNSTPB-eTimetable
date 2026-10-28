// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.timetable

import kotlinx.datetime.LocalTime
import net.mehstudios.etimetable.model.Activity
import net.mehstudios.etimetable.model.ActivityType
import net.mehstudios.etimetable.model.Audience
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
import net.mehstudios.etimetable.schedule.TeachingCalendar
import net.mehstudios.etimetable.schedule.overlaps
import java.text.Normalizer

/**
 * The timetable as one spreadsheet, one row per activity or office hour.
 * The format is described for admins in docs/admin.md.
 */
object TimetableSheet {
    val COLUMNS = listOf(
        "id", "programme", "programme_name", "year", "series", "audience", "subject_code", "subject_ro", "subject_en",
        "type", "day", "start", "end", "weeks", "professor", "professor_email", "room", "erasmus",
    )

    class ImportResult(val dataset: Dataset?, val errors: List<String>, val warnings: List<String>)

    private const val OFFICE_HOURS = "consultatii"

    private val TYPES = mapOf(
        "curs" to ActivityType.COURSE,
        "course" to ActivityType.COURSE,
        "seminar" to ActivityType.SEMINAR,
        "laborator" to ActivityType.LAB,
        "lab" to ActivityType.LAB,
        "proiect" to ActivityType.PROJECT,
        "project" to ActivityType.PROJECT,
    )
    private val TYPE_NAMES = mapOf(
        ActivityType.COURSE to "curs",
        ActivityType.SEMINAR to "seminar",
        ActivityType.LAB to "laborator",
        ActivityType.PROJECT to "proiect",
    )
    private val DAYS = listOf("luni", "marti", "miercuri", "joi", "vineri", "sambata", "duminica")
    private val DAYS_EN = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
    private val TITLE_WORDS =
        setOf("prof", "conf", "lect", "as", "sl", "s.l", "dr", "ing", "ec", "univ", "habil", "drd", "mat", "fiz")

    fun import(text: String, semester: Semester): ImportResult {
        val rows = Csv.parse(text)
        if (rows.isEmpty()) return ImportResult(null, listOf("Fișierul este gol."), emptyList())
        val header = rows.first().map { fold(it) }
        val missing = COLUMNS.filter {
            it != "id" && it != "programme_name" && it != "professor_email" && it !in header
        }
        if (missing.isNotEmpty()) {
            return ImportResult(null, listOf("Lipsesc coloanele: ${missing.joinToString(", ")}."), emptyList())
        }

        val errors = mutableListOf<String>()
        val programmes = linkedMapOf<String, Programme>()
        val series = linkedMapOf<String, Series>()
        val groups = linkedMapOf<String, Group>()
        val halfGroups = linkedMapOf<String, HalfGroup>()
        val subjects = linkedMapOf<String, Subject>()
        val professors = linkedMapOf<String, Professor>()
        val buildings = linkedMapOf<String, Building>()
        val rooms = linkedMapOf<String, Room>()
        val activities = linkedMapOf<String, Activity>()

        for ((index, cells) in rows.drop(1).withIndex()) {
            val line = index + 2
            fun cell(name: String) = header.indexOf(name).let { if (it < 0) "" else cells.getOrElse(it) { "" }.trim() }
            fun fail(message: String) {
                errors += "Rândul $line: $message"
            }

            val type = fold(cell("type"))
            val slot = try {
                WeeklySlot(
                    parseDay(cell("day")),
                    parseTime(cell("start")),
                    parseTime(cell("end")),
                    parseWeeks(cell("weeks")),
                )
            } catch (e: IllegalArgumentException) {
                fail(e.message ?: "zi, oră sau săptămâni greșite")
                continue
            }

            val professorId = cell("professor").takeIf { it.isNotEmpty() }?.let { full ->
                val (title, name) = splitTitle(full)
                val id = slug(name)
                val existing = professors[id]
                val email = cell("professor_email").ifEmpty { existing?.email }
                professors[id] = (existing ?: Professor(id, title, name)).copy(email = email?.ifEmpty { null })
                id
            }
            val roomId = cell("room").takeIf { it.isNotEmpty() }?.let { code ->
                val buildingCode = code.substringBefore(' ').uppercase()
                buildings.getOrPut(slug(buildingCode)) { Building(slug(buildingCode), buildingCode, buildingCode) }
                rooms.getOrPut(slug(code)) { Room(slug(code), slug(buildingCode), code) }.id
            }

            if (type == OFFICE_HOURS) {
                if (professorId == null) {
                    fail("consultațiile au nevoie de profesor")
                    continue
                }
                val professor = professors.getValue(professorId)
                professors[professorId] = professor.copy(officeHours = professor.officeHours + OfficeHour(slot, roomId))
                continue
            }

            val activityType = TYPES[type]
            if (activityType == null) {
                fail("tip necunoscut \"${cell("type")}\" (curs, seminar, laborator, proiect, consultații)")
                continue
            }
            val programmeCode = cell("programme").uppercase()
            val year = cell("year").toIntOrNull()
            val seriesName = cell("series")
            val subjectCode = cell("subject_code")
            if (programmeCode.isEmpty() || year == null || seriesName.isEmpty() || subjectCode.isEmpty()) {
                fail("programme, year, series și subject_code sunt obligatorii")
                continue
            }

            val programmeId = slug(programmeCode)
            val programmeName = cell("programme_name").ifEmpty { programmes[programmeId]?.name?.ro ?: programmeCode }
            programmes[programmeId] =
                Programme(
                    programmeId,
                    programmeCode,
                    LocalizedText(programmeName, programmeName),
                    StudyLevel.BACHELOR,
                    "ro",
                )
            val seriesId = "$programmeId-$year-${slug(seriesName)}"
            series.getOrPut(seriesId) { Series(seriesId, programmeId, year, seriesName) }

            val subjectRo = cell("subject_ro").ifEmpty { subjects[subjectCode]?.name?.ro ?: subjectCode }
            val subjectEn = cell("subject_en").ifEmpty { subjects[subjectCode]?.name?.en ?: subjectRo }
            subjects[subjectCode] =
                Subject(subjectCode, subjectCode, LocalizedText(subjectRo, subjectEn), programmeId, year)

            val codes = cell("audience").split(' ', '+').map { it.trim() }.filter { it.isNotEmpty() && it != "*" }
            val audience = if (codes.isEmpty()) {
                Audience(seriesIds = setOf(seriesId))
            } else {
                val whole = mutableSetOf<String>()
                val halves = mutableSetOf<String>()
                for (code in codes) {
                    val groupCode = code.substringBefore('/')
                    groups.getOrPut(groupCode) { Group(groupCode, seriesId, groupCode) }
                    if ('/' in code) {
                        halfGroups.getOrPut(code) { HalfGroup(code, groupCode, code.substringAfter('/')) }
                        halves += code
                    } else {
                        whole += groupCode
                    }
                }
                Audience(groupIds = whole, halfGroupIds = halves)
            }

            val audienceKey = if (codes.isEmpty()) seriesId else codes.sorted().joinToString("+")
            val id = cell("id").ifEmpty {
                slug(
                    "$subjectCode-${TYPE_NAMES.getValue(
                        activityType,
                    )}-${DAYS[slot.isoDay - 1]}-${slot.start}-$audienceKey",
                )
            }
            if (id in activities) {
                fail("activitatea $id apare de două ori")
                continue
            }
            activities[id] =
                Activity(
                    id,
                    subjectCode,
                    activityType,
                    slot,
                    professorId,
                    roomId,
                    audience,
                    openToErasmus = isYes(cell("erasmus")),
                )
        }

        if (errors.isNotEmpty()) return ImportResult(null, errors, emptyList())
        val dataset = Dataset(
            semester = semester,
            programmes = programmes.values.toList(),
            series = series.values.toList(),
            groups = groups.values.toList(),
            halfGroups = halfGroups.values.toList(),
            subjects = subjects.values.toList(),
            professors = professors.values.toList(),
            buildings = buildings.values.toList(),
            rooms = rooms.values.toList(),
            activities = activities.values.toList(),
        )
        return ImportResult(dataset, emptyList(), doubleBookings(dataset))
    }

    fun export(dataset: Dataset): String {
        val rows = mutableListOf(COLUMNS)
        val seriesById = dataset.series.associateBy { it.id }
        val programmesById = dataset.programmes.associateBy { it.id }
        val groupCodes = dataset.groups.associate { it.id to it.code }
        val halfCodes = dataset.halfGroups.associate { it.id to "${groupCodes[it.groupId] ?: it.groupId}/${it.suffix}" }
        for (a in dataset.activities) {
            val subject = dataset.subjectsById[a.subjectId]
            val seriesId = a.audience.seriesIds.firstOrNull()
                ?: (a.audience.groupIds + a.audience.halfGroupIds.mapNotNull { dataset.halfGroupsById[it]?.groupId })
                    .firstNotNullOfOrNull { dataset.groupsById[it]?.seriesId }
            val series = seriesById[seriesId]
            val programme = programmesById[series?.programmeId ?: subject?.programmeId]
            val audience = if (a.audience.seriesIds.isNotEmpty()) {
                ""
            } else {
                (
                    a.audience.groupIds.map {
                        groupCodes[it] ?: it
                    } + a.audience.halfGroupIds.map { halfCodes[it] ?: it }
                    ).sorted().joinToString(" ")
            }
            rows += listOf(
                a.id,
                programme?.code.orEmpty(),
                programme?.name?.ro.orEmpty(),
                (series?.year ?: subject?.year)?.toString().orEmpty(),
                series?.name.orEmpty(), audience, a.subjectId, subject?.name?.ro.orEmpty(), subject?.name?.en.orEmpty(),
                TYPE_NAMES.getValue(a.type), DAYS[a.slot.isoDay - 1], a.slot.start.toString(), a.slot.end.toString(),
                weeksText(
                    a.slot.weeks,
                ),
                professorText(dataset, a.professorId), dataset.professorsById[a.professorId]?.email.orEmpty(),
                dataset.roomsById[a.roomId]?.code.orEmpty(), if (a.openToErasmus) "da" else "nu",
            )
        }
        for (p in dataset.professors) {
            for (o in p.officeHours) {
                rows += listOf(
                    "", "", "", "", "", "", "", "", "",
                    "consultații", DAYS[o.slot.isoDay - 1], o.slot.start.toString(), o.slot.end.toString(),
                    weeksText(
                        o.slot.weeks,
                    ),
                    p.displayName, p.email.orEmpty(), dataset.roomsById[o.roomId]?.code.orEmpty(), "",
                )
            }
        }
        return Csv.write(rows)
    }

    /** A room or a professor in two places at once. Imported anyway; admins decide. */
    fun doubleBookings(dataset: Dataset): List<String> {
        val calendar = TeachingCalendar(dataset.semester)
        val warnings = mutableListOf<String>()
        val list = dataset.activities
        for (i in list.indices) {
            for (j in i + 1 until list.size) {
                val a = list[i]
                val b = list[j]
                val sameRoom = a.roomId != null && a.roomId == b.roomId
                val sameProfessor = a.professorId != null && a.professorId == b.professorId
                if ((sameRoom || sameProfessor) && calendar.overlaps(a.slot, b.slot)) {
                    val what = if (sameRoom) {
                        "sala ${dataset.roomsById[a.roomId]?.code}"
                    } else {
                        "profesorul ${professorText(
                            dataset,
                            a.professorId,
                        )}"
                    }
                    warnings += "$what are două activități în același timp: ${a.id} și ${b.id}"
                }
            }
        }
        return warnings
    }

    private fun professorText(dataset: Dataset, id: String?) = dataset.professorsById[id]?.displayName.orEmpty()

    private fun weeksText(rule: WeekRule) = when (rule) {
        WeekRule.All -> "toate"
        WeekRule.Odd -> "impare"
        WeekRule.Even -> "pare"
        is WeekRule.Only -> rule.weeks.sorted().joinToString(",")
    }

    private fun parseDay(text: String): Int {
        val value = fold(text)
        value.toIntOrNull()?.let { if (it in 1..7) return it }
        val index = DAYS.indexOf(value).takeIf { it >= 0 } ?: DAYS_EN.indexOf(value)
        require(index >= 0) { "zi necunoscută \"$text\"" }
        return index + 1
    }

    private fun parseTime(text: String): LocalTime {
        val match = Regex("""^(\d{1,2})[:.](\d{2})$""").matchEntire(text.trim())
            ?: Regex("""^(\d{1,2})$""").matchEntire(text.trim())
        requireNotNull(match) { "oră greșită \"$text\" (de exemplu 08:00)" }
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        require(hour in 0..23 && minute in 0..59) { "oră greșită \"$text\"" }
        return LocalTime(hour, minute)
    }

    private fun parseWeeks(text: String): WeekRule {
        val value = fold(text)
        return when (value) {
            "", "toate", "all" -> WeekRule.All
            "impare", "impar", "odd" -> WeekRule.Odd
            "pare", "par", "even" -> WeekRule.Even
            else -> {
                val weeks = value.split(',', ' ').filter { it.isNotEmpty() }.flatMap { part ->
                    val range = part.split('-').map {
                        it.toIntOrNull()
                            ?: throw IllegalArgumentException("săptămâni greșite \"$text\"")
                    }
                    if (range.size == 2) (range[0]..range[1]).toList() else range
                }
                require(weeks.isNotEmpty() && weeks.all { it in 1..20 }) { "săptămâni greșite \"$text\"" }
                WeekRule.Only(weeks.toSet())
            }
        }
    }

    /** "Conf. dr. ing. Ioana Marinescu" -> ("Conf. dr. ing.", "Ioana Marinescu") */
    fun splitTitle(full: String): Pair<String, String> {
        val words = full.trim().split(Regex("\\s+"))
        val titleWords = words.takeWhile { fold(it).trimEnd('.') in TITLE_WORDS }
        return titleWords.joinToString(" ") to words.drop(titleWords.size).joinToString(" ")
    }

    private fun isYes(text: String) = fold(text) in setOf("da", "yes", "1", "x", "true")

    /** Lower case without diacritics, so "Marți" and "marti" match. */
    private fun fold(text: String): String =
        Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

    private fun slug(text: String): String = fold(text).replace(Regex("[^a-z0-9/]+"), "-").trim('-')
}
