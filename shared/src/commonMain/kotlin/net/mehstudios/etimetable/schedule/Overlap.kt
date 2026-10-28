// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.schedule

import kotlinx.datetime.LocalTime
import net.mehstudios.etimetable.model.Activity
import net.mehstudios.etimetable.model.WeeklySlot

/** Time ranges overlap when each starts before the other ends; 10:00-12:00 and 12:00-14:00 do not. */
fun timesOverlap(aStart: LocalTime, aEnd: LocalTime, bStart: LocalTime, bEnd: LocalTime): Boolean =
    aStart < bEnd && bStart < aEnd

/** Two weekly slots clash when they share a weekday, a time and at least one teaching week. */
fun TeachingCalendar.overlaps(a: WeeklySlot, b: WeeklySlot): Boolean = a.isoDay == b.isoDay &&
    timesOverlap(a.start, a.end, b.start, b.end) &&
    weekNumbers(a.weeks).any { it in weekNumbers(b.weeks) }

/** The first of [existing] that clashes with [candidate], or null. */
fun TeachingCalendar.findClash(candidate: Activity, existing: Iterable<Activity>): Activity? =
    existing.firstOrNull { it.id != candidate.id && overlaps(it.slot, candidate.slot) }
