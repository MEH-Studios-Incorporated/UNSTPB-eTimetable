// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.model

import kotlinx.serialization.Serializable

@Serializable
data class Programme(
    val id: String,
    /** As the faculty writes it, for example "IEDEEE". */
    val code: String,
    val name: LocalizedText,
    val level: StudyLevel,
    /** ISO 639-1 code of the teaching language. */
    val language: String,
)

@Serializable
enum class StudyLevel { BACHELOR, MASTER }

/** Groups of one programme and year that attend courses together. */
@Serializable
data class Series(val id: String, val programmeId: String, val year: Int, val name: String)

@Serializable
data class Group(val id: String, val seriesId: String, val code: String)

/** A group split for labs, such as 1541E/A. */
@Serializable
data class HalfGroup(val id: String, val groupId: String, val suffix: String)
