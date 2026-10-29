// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.schedule

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Timetable times are Bucharest times. An Erasmus student's phone may still
 * be on another time zone, so "now" is always taken in this one.
 */
val FacultyTimeZone: TimeZone = TimeZone.of("Europe/Bucharest")

fun Clock.facultyNow(): LocalDateTime = now().toLocalDateTime(FacultyTimeZone)
