// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.ui.theme.SubjectColors
import net.mehstudios.etimetable.model.ActivityType
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.WeekRule
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** "ro" when the phone is in Romanian, otherwise "en"; picks the language of names in the dataset. */
fun appLanguage(): String = if (Locale.getDefault().language == "ro") "ro" else "en"

@Composable
fun ActivityType.label(): String = stringResource(
    when (this) {
        ActivityType.COURSE -> R.string.type_course
        ActivityType.SEMINAR -> R.string.type_seminar
        ActivityType.LAB -> R.string.type_lab
        ActivityType.PROJECT -> R.string.type_project
    },
)

@Composable
fun WeekRule.label(): String = when (this) {
    WeekRule.All -> stringResource(R.string.weeks_all)
    WeekRule.Odd -> stringResource(R.string.weeks_odd)
    WeekRule.Even -> stringResource(R.string.weeks_even)
    is WeekRule.Only -> stringResource(R.string.weeks_only, weeks.sorted().joinToString(", "))
}

/** The subject's colour from docs/design.md, fixed by its place in the dataset. */
fun Dataset.subjectColor(subjectId: String): Color {
    val index = subjects.indexOfFirst { it.id == subjectId }.coerceAtLeast(0)
    return SubjectColors[index % SubjectColors.size]
}

fun Dataset.subjectName(subjectId: String): String = subjectsById[subjectId]?.name?.get(appLanguage()) ?: subjectId

/** "Micro" for "Microeconomie": room for a narrow week column. */
fun Dataset.subjectShortName(subjectId: String): String {
    val word = subjectName(subjectId).split(' ').first()
    return if (word.length <= 7) word else word.take(6) + "."
}

private fun LocalDate.toJava(): java.time.LocalDate = java.time.LocalDate.of(year, month.ordinal + 1, day)

private fun String.capitalized() = replaceFirstChar { it.titlecase(Locale.getDefault()) }

/** "Miercuri, 28 octombrie" */
fun LocalDate.longLabel(): String =
    toJava().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())).capitalized()

/** "Mi" */
fun LocalDate.dayInitials(): String = java.time.DayOfWeek.of(
    dayOfWeek.isoDayNumber,
).getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2).capitalized()

/** "Joi, 12:00" style day name */
fun LocalDate.dayName(): String =
    java.time.DayOfWeek.of(dayOfWeek.isoDayNumber).getDisplayName(TextStyle.FULL, Locale.getDefault()).capitalized()

/** "26 - 30 octombrie" or "28 septembrie - 2 octombrie" */
fun weekRangeLabel(first: LocalDate, last: LocalDate): String {
    val month = DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())
    return if (first.month == last.month) {
        "${first.day} - ${last.day} ${last.toJava().format(month)}"
    } else {
        "${first.day} ${first.toJava().format(month)} - ${last.day} ${last.toJava().format(month)}"
    }
}
