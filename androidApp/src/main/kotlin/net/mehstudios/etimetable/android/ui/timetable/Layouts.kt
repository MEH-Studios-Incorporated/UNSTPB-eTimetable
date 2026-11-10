// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.timetable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.ui.components.appLanguage
import net.mehstudios.etimetable.android.ui.components.dayInitials
import net.mehstudios.etimetable.android.ui.components.label
import net.mehstudios.etimetable.android.ui.components.longLabel
import net.mehstudios.etimetable.android.ui.components.subjectColor
import net.mehstudios.etimetable.android.ui.components.subjectName
import net.mehstudios.etimetable.android.ui.components.subjectShortName
import net.mehstudios.etimetable.android.ui.theme.TimeStyle
import net.mehstudios.etimetable.model.ActivityType
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.WeekRule
import net.mehstudios.etimetable.schedule.Occurrence
import net.mehstudios.etimetable.schedule.StudentTimetable

/** The colours of one class, opaque, faded into the page once it is over (as on the timeline). */
private data class ClassColors(val tint: Color, val bar: Color, val text: Color, val muted: Color)

@Composable
private fun classColors(dataset: Dataset, occurrence: Occurrence, now: LocalDateTime): ClassColors {
    val page = MaterialTheme.colorScheme.background
    val dark = page.red + page.green + page.blue < 1.5f
    val fade = if (occurrence.end <= now) 0.45f else 0f
    val subject = dataset.subjectColor(occurrence.activity.subjectId)
    return ClassColors(
        tint = lerp(page, subject, (if (dark) 0.22f else 0.13f) * (1 - fade)),
        bar = lerp(subject, page, fade),
        text = lerp(MaterialTheme.colorScheme.onSurface, page, fade),
        muted = lerp(MaterialTheme.colorScheme.onSurfaceVariant, page, fade),
    )
}

/** "1 h 30 min", "45 min" */
private fun duration(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

private fun minutes(from: LocalTime, to: LocalTime) = (to.toSecondOfDay() - from.toSecondOfDay()) / 60

@Composable
private fun Empty(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // scrollable, so pull-to-refresh works on an empty page too
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    )
}

/** List layout, one day: a card per class, the breaks between them written out. */
@Composable
fun DayList(
    dataset: Dataset,
    occurrences: List<Occurrence>,
    now: LocalDateTime,
    modifier: Modifier,
    open: (Occurrence) -> Unit,
) {
    if (occurrences.isEmpty()) return Empty(stringResource(R.string.nothing_today), modifier)
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        occurrences.forEachIndexed { i, occurrence ->
            val previous = occurrences.getOrNull(i - 1)
            val gap = previous?.let { minutes(it.activity.slot.end, occurrence.activity.slot.start) } ?: 0
            if (gap >= 15) BreakLine(gap)
            ClassCard(dataset, occurrence, now) { open(occurrence) }
        }
    }
}

@Composable
private fun BreakLine(minutes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            stringResource(R.string.break_for, duration(minutes)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        HorizontalDivider(Modifier.weight(1f))
    }
}

@Composable
private fun ClassCard(dataset: Dataset, occurrence: Occurrence, now: LocalDateTime, onClick: () -> Unit) {
    val colors = classColors(dataset, occurrence, now)
    val activity = occurrence.activity
    val current = occurrence.start <= now && now < occurrence.end
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(colors.tint, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier.width(
                4.dp,
            ).fillMaxHeight().background(colors.bar, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)),
        )
        Column(Modifier.width(64.dp).padding(start = 12.dp, top = 12.dp)) {
            Text(activity.slot.start.toString(), style = TimeStyle, color = colors.text)
            Text(
                activity.slot.end.toString(),
                style = TimeStyle.copy(fontWeight = FontWeight.Normal),
                color = colors.muted,
            )
        }
        Column(
            Modifier.weight(1f).padding(top = 10.dp, bottom = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(activity.type.label(), activity.slot.weeks.takeIf { it != WeekRule.All }?.label())
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.weight(1f),
                )
                if (current) NowTag()
            }
            Text(
                dataset.subjectName(activity.subjectId),
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    dataset.roomsById[activity.roomId]?.code,
                    dataset.professorsById[activity.professorId]?.displayName,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ChangeTagFor(activity.id, Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun NowTag() {
    Text(
        stringResource(R.string.now),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.background(MaterialTheme.colorScheme.error, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** Table layout, one day: dense rows of time, a letter for the type, the name and the room. */
@Composable
fun DayRows(
    dataset: Dataset,
    occurrences: List<Occurrence>,
    now: LocalDateTime,
    modifier: Modifier,
    open: (Occurrence) -> Unit,
) {
    if (occurrences.isEmpty()) return Empty(stringResource(R.string.nothing_today), modifier)
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                occurrences.forEachIndexed { i, occurrence ->
                    if (i > 0) HorizontalDivider()
                    CompactRow(dataset, occurrence, now) { open(occurrence) }
                }
            }
        }
    }
}

@Composable
private fun CompactRow(dataset: Dataset, occurrence: Occurrence, now: LocalDateTime, onClick: () -> Unit) {
    val colors = classColors(dataset, occurrence, now)
    val activity = occurrence.activity
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "${activity.slot.start}-${activity.slot.end}",
            style = TimeStyle.copy(fontWeight = FontWeight.Medium),
            color = colors.muted,
            modifier = Modifier.width(92.dp),
        )
        TypeLetter(activity.type, colors.bar)
        Text(
            dataset.subjectName(activity.subjectId),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ChangeDot(activity.id)
        dataset.roomsById[activity.roomId]?.code?.let {
            Text(it, style = TimeStyle.copy(fontWeight = FontWeight.Normal), color = colors.muted)
        }
    }
}

/** C, S, L, P in the subject's colour: the type at a glance in dense views. */
@Composable
private fun TypeLetter(type: ActivityType, color: Color) {
    Box(Modifier.size(20.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            type.label().take(1).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
        )
    }
}

/** List layout, one week: each day as a heading with its classes under it. */
@Composable
fun WeekAgenda(
    dataset: Dataset,
    days: List<LocalDate>,
    timetable: StudentTimetable,
    now: LocalDateTime,
    open: (Occurrence) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(
            rememberScrollState(),
        ).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        days.forEach { day ->
            val today = day == now.date
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
            ) {
                Text(
                    day.longLabel(),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                breakName(timetable, day)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            RemovedClasses(dataset, timetable.calendar, day)
            val occurrences = timetable.on(day)
            if (occurrences.isEmpty()) {
                Text(
                    stringResource(R.string.day_free),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            occurrences.forEach { occurrence ->
                val colors = classColors(dataset, occurrence, now)
                Row(
                    Modifier.fillMaxWidth().background(colors.tint, RoundedCornerShape(6.dp)).clickable {
                        open(occurrence)
                    }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        occurrence.activity.slot.start.toString(),
                        style = TimeStyle,
                        color = colors.text,
                        modifier = Modifier.width(44.dp),
                    )
                    Box(Modifier.size(8.dp).background(colors.bar, CircleShape))
                    Column(Modifier.weight(1f)) {
                        Text(
                            dataset.subjectName(occurrence.activity.subjectId),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(
                                occurrence.activity.type.label(),
                                "${occurrence.activity.slot.start} - ${occurrence.activity.slot.end}",
                                dataset.roomsById[occurrence.activity.roomId]?.code,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                        ChangeTagFor(occurrence.activity.id, Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

/** The break a day falls in, by name ("Vacanța de iarnă"). */
private fun breakName(timetable: StudentTimetable, day: LocalDate): String? =
    timetable.calendar.breakOn(day)?.name?.get(appLanguage())

/** Table layout, one week: a row for each starting time, a column for each day, like the faculty's sheet. */
@Composable
fun WeekTable(
    dataset: Dataset,
    days: List<LocalDate>,
    timetable: StudentTimetable,
    now: LocalDateTime,
    open: (Occurrence) -> Unit,
) {
    val byDay = days.associateWith { timetable.on(it) }
    val starts = byDay.values.flatten().map { it.activity.slot.start }.distinct().sorted()
    if (starts.isEmpty()) return Empty(stringResource(R.string.nothing_week))
    val outline = MaterialTheme.colorScheme.outline
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(
            rememberScrollState(),
        ).padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(44.dp))
            days.forEach { day ->
                val today = day == now.date
                Text(
                    "${day.dayInitials()} ${day.day}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (today) colors.surface else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(horizontal = 1.dp)
                        .background(
                            if (today) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            RoundedCornerShape(6.dp),
                        )
                        .padding(vertical = 4.dp),
                )
            }
        }
        Column(Modifier.padding(top = 6.dp).border(1.dp, outline, RoundedCornerShape(8.dp))) {
            starts.forEachIndexed { i, start ->
                if (i > 0) HorizontalDivider()
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 64.dp)) {
                    Text(
                        start.toString(),
                        style = TimeStyle.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(44.dp).padding(start = 4.dp, top = 6.dp),
                    )
                    days.forEach { day ->
                        Column(
                            Modifier.weight(1f).fillMaxHeight().padding(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            byDay.getValue(day).filter { it.activity.slot.start == start }.forEach { occurrence ->
                                TableCell(dataset, occurrence, now, Modifier.weight(1f)) { open(occurrence) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    dataset: Dataset,
    occurrence: Occurrence,
    now: LocalDateTime,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = classColors(dataset, occurrence, now)
    val activity = occurrence.activity
    Column(
        modifier.fillMaxWidth().background(colors.tint, RoundedCornerShape(4.dp)).clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                dataset.subjectShortName(activity.subjectId),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = colors.text,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            ChangeDot(activity.id, Modifier.padding(start = 2.dp))
        }
        Text(
            listOfNotNull(activity.type.label().take(1), dataset.roomsById[activity.roomId]?.code).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = colors.muted,
            maxLines = 2,
        )
    }
}
