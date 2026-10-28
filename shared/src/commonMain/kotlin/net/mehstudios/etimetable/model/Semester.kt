// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class Semester(
    val id: String,
    val name: LocalizedText,
    val firstDay: LocalDate,
    val lastDay: LocalDate,
    val breaks: List<Break> = emptyList(),
    val parityOverrides: List<ParityOverride> = emptyList(),
) {
    init {
        require(firstDay <= lastDay) { "Semester $id ends before it starts" }
    }
}

/** Holidays or an exam session: no classes on these days, both included. */
@Serializable
data class Break(val name: LocalizedText, val firstDay: LocalDate, val lastDay: LocalDate)

/**
 * Forces the parity of the teaching week that contains [weekOf], for weeks the
 * faculty shifts around public holidays.
 */
@Serializable
data class ParityOverride(val weekOf: LocalDate, val parity: Parity)

@Serializable
enum class Parity { ODD, EVEN }
