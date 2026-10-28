// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What a student chose. Nothing here is checked against official records. */
@Serializable
data class Profile(
    val displayName: String = "",
    val language: String = "ro",
    val enrollment: Enrollment? = null,
    val extraSlots: List<ExtraSlot> = emptyList(),
)

@Serializable
sealed interface Enrollment {
    /** Timetable of [groupId]; [halfGroupId] picks one half's labs when the group is split. */
    @Serializable
    @SerialName("national")
    data class National(val groupId: String, val halfGroupId: String? = null) : Enrollment

    /** The activities picked from the learning agreement. */
    @Serializable
    @SerialName("erasmus")
    data class Erasmus(val activityIds: Set<String> = emptySet()) : Enrollment
}

/**
 * An activity a student adds on top of their timetable, such as another
 * group's lab. With [date] it is a single visit, without it it repeats.
 */
@Serializable
data class ExtraSlot(val activityId: String, val date: LocalDate? = null)
