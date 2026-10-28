// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Subject(val id: String, val code: String, val name: LocalizedText, val programmeId: String, val year: Int)

@Serializable
data class Professor(
    val id: String,
    /** Academic title as written in Romanian, for example "Conf. dr.". */
    val title: String,
    val name: String,
    val email: String? = null,
    val officeRoomId: String? = null,
    val officeHours: List<OfficeHour> = emptyList(),
) {
    val displayName: String get() = "$title $name"
}

@Serializable
data class OfficeHour(val slot: WeeklySlot, val roomId: String? = null)

@Serializable
data class Building(
    val id: String,
    val code: String,
    val name: String,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class Room(
    val id: String,
    val buildingId: String,
    /** As written on the door, for example "BN 212 b". */
    val code: String,
    val capacity: Int? = null,
)

@Serializable
enum class ActivityType { COURSE, SEMINAR, LAB, PROJECT }

/** One recurring slot of the timetable. */
@Serializable
data class Activity(
    val id: String,
    val subjectId: String,
    val type: ActivityType,
    val slot: WeeklySlot,
    val professorId: String? = null,
    val roomId: String? = null,
    val audience: Audience = Audience(),
    val openToErasmus: Boolean = false,
)

/** Who attends an activity. A series includes all its groups and half-groups. */
@Serializable
data class Audience(
    val seriesIds: Set<String> = emptySet(),
    val groupIds: Set<String> = emptySet(),
    val halfGroupIds: Set<String> = emptySet(),
)

@Serializable
data class WeeklySlot(
    /** ISO day number: 1 is Monday, 7 is Sunday. */
    val isoDay: Int,
    val start: LocalTime,
    val end: LocalTime,
    val weeks: WeekRule = WeekRule.All,
) {
    init {
        require(isoDay in 1..7) { "isoDay must be 1..7, was $isoDay" }
        require(start < end) { "A slot must end after it starts ($start, $end)" }
    }

    val day: DayOfWeek get() = DayOfWeek(isoDay)

    companion object {
        fun of(day: DayOfWeek, start: LocalTime, end: LocalTime, weeks: WeekRule = WeekRule.All) =
            WeeklySlot(day.isoDayNumber, start, end, weeks)
    }
}

/** Which teaching weeks a slot runs in. */
@Serializable
sealed interface WeekRule {
    @Serializable
    @SerialName("all")
    data object All : WeekRule

    @Serializable
    @SerialName("odd")
    data object Odd : WeekRule

    @Serializable
    @SerialName("even")
    data object Even : WeekRule

    /** Only the listed teaching week numbers. */
    @Serializable
    @SerialName("only")
    data class Only(val weeks: Set<Int>) : WeekRule
}
