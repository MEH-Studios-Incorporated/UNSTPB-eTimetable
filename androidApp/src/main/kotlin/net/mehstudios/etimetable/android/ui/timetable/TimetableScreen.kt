// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.timetable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.ui.components.EtIcons
import net.mehstudios.etimetable.android.ui.components.appLanguage
import net.mehstudios.etimetable.android.ui.components.dayInitials
import net.mehstudios.etimetable.android.ui.components.dayName
import net.mehstudios.etimetable.android.ui.components.label
import net.mehstudios.etimetable.android.ui.components.longLabel
import net.mehstudios.etimetable.android.ui.components.subjectColor
import net.mehstudios.etimetable.android.ui.components.subjectName
import net.mehstudios.etimetable.android.ui.components.subjectShortName
import net.mehstudios.etimetable.android.ui.components.weekRangeLabel
import net.mehstudios.etimetable.android.ui.theme.ButtonShape
import net.mehstudios.etimetable.android.ui.theme.TimeStyle
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Parity
import net.mehstudios.etimetable.model.WeekRule
import net.mehstudios.etimetable.schedule.Occurrence
import net.mehstudios.etimetable.schedule.StudentTimetable
import net.mehstudios.etimetable.schedule.mondayOfWeek

private const val FIRST_HOUR = 8
private const val LAST_HOUR = 20

private enum class Mode { DAY, WEEK }

@Composable
fun TimetableScreen(
    dataset: Dataset,
    timetable: StudentTimetable,
    now: LocalDateTime,
    initials: String,
    onOpenProfile: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(Mode.DAY) }
    var selectedText by rememberSaveable { mutableStateOf(now.date.toString()) }
    val selected = LocalDate.parse(selectedText)
    var opened by remember { mutableStateOf<Occurrence?>(null) }
    val calendar = timetable.calendar
    val monday = selected.mondayOfWeek()
    val hasSaturday = timetable.between(monday.plus(5, DateTimeUnit.DAY), monday.plus(5, DateTimeUnit.DAY)).isNotEmpty()
    val days = (0 until if (hasSaturday) 6 else 5).map { monday.plus(it, DateTimeUnit.DAY) }

    fun select(date: LocalDate) {
        selectedText = date.toString()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (mode == Mode.DAY) selected.longLabel() else weekRangeLabel(days.first(), days.last()),
                    style = MaterialTheme.typography.headlineSmall,
                )
                val week = calendar.weekOf(selected)
                val subtitle = when {
                    week == null -> calendar.breakOn(selected)?.name?.get(appLanguage()).orEmpty()
                    week.parity == Parity.ODD -> stringResource(R.string.week_odd, week.number)
                    else -> stringResource(R.string.week_even, week.number)
                }
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                onClick = onOpenProfile,
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(initials, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(selected = mode == Mode.DAY, onClick = {
                    mode = Mode.DAY
                }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                    Text(stringResource(R.string.day))
                }
                SegmentedButton(selected = mode == Mode.WEEK, onClick = {
                    mode = Mode.WEEK
                }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                    Text(stringResource(R.string.week))
                }
            }
            Spacer(Modifier.weight(1f))
            if (mode == Mode.WEEK) {
                IconButton(onClick = { select(selected.minus(7, DateTimeUnit.DAY)) }) {
                    Icon(EtIcons.ChevronLeft, contentDescription = stringResource(R.string.previous_week))
                }
                IconButton(onClick = { select(selected.plus(7, DateTimeUnit.DAY)) }) {
                    Icon(EtIcons.ChevronRight, contentDescription = stringResource(R.string.next_week))
                }
            }
            if (selected != now.date) {
                OutlinedButton(shape = ButtonShape, onClick = {
                    select(now.date)
                }) { Text(stringResource(R.string.today)) }
            }
        }

        when (mode) {
            Mode.DAY -> {
                DayStrip(days, selected, timetable, ::select)
                if (selected == now.date) NextCard(dataset, timetable, now) { opened = it }
                DayTimeline(dataset, timetable.on(selected), now, Modifier.weight(1f)) { opened = it }
            }
            Mode.WEEK -> WeekGrid(dataset, days, timetable, now, Modifier.weight(1f)) { opened = it }
        }
    }

    opened?.let { ActivitySheet(dataset, it) { opened = null } }
}

@Composable
private fun DayStrip(
    days: List<LocalDate>,
    selected: LocalDate,
    timetable: StudentTimetable,
    select: (LocalDate) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { day ->
            val isSelected = day == selected
            val count = timetable.on(day).size
            Surface(
                onClick = { select(day) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
                contentColor = when {
                    isSelected -> MaterialTheme.colorScheme.surface
                    else -> MaterialTheme.colorScheme.onSurface
                },
                border = BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier.weight(1f).height(64.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(day.dayInitials(), style = MaterialTheme.typography.bodySmall)
                    Text(
                        day.day.toString(),
                        style = MaterialTheme.typography.titleMedium.merge(
                            TimeStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                        ),
                    )
                    Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(count.coerceAtMost(5)) {
                            Box(Modifier.size(4.dp).alpha(0.7f).background(LocalContentColor.current, CircleShape))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NextCard(dataset: Dataset, timetable: StudentTimetable, now: LocalDateTime, open: (Occurrence) -> Unit) {
    val next = timetable.nowAndNext(now).next ?: return
    val heading = if (next.date == now.date) {
        stringResource(R.string.next_in_minutes, minutesBetween(now.time, next.start.time))
    } else {
        stringResource(R.string.next_on, "${next.date.dayName()}, ${next.activity.slot.start}")
    }
    Surface(
        onClick = { open(next) },
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(
                    4.dp,
                ).height(48.dp).background(dataset.subjectColor(next.activity.subjectId), RoundedCornerShape(2.dp)),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    heading,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    dataset.subjectName(next.activity.subjectId),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        next.activity.type.label(),
                        dataset.roomsById[next.activity.roomId]?.code,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                next.activity.slot.start.toString(),
                style = TimeStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
            )
        }
    }
}

private fun minutesBetween(from: LocalTime, to: LocalTime): Int = (to.toSecondOfDay() - from.toSecondOfDay()) / 60

@Composable
private fun DayTimeline(
    dataset: Dataset,
    occurrences: List<Occurrence>,
    now: LocalDateTime,
    modifier: Modifier,
    open: (Occurrence) -> Unit,
) {
    val hourHeight = 56.dp
    if (occurrences.isEmpty()) {
        Text(
            stringResource(R.string.nothing_today),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(16.dp),
        )
        return
    }
    Row(modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp, end = 16.dp, bottom = 24.dp)) {
        HourLabels(hourHeight, Modifier.width(52.dp))
        Box(Modifier.weight(1f).height(hourHeight * (LAST_HOUR - FIRST_HOUR))) {
            HourLines(hourHeight)
            occurrences.forEach { occurrence ->
                OccurrenceBlock(
                    dataset,
                    occurrence,
                    now,
                    hourHeight,
                    compact = false,
                    modifier = Modifier.padding(start = 6.dp),
                ) {
                    open(occurrence)
                }
            }
            NowLine(occurrences.firstOrNull()?.date, now, hourHeight)
        }
    }
}

@Composable
private fun WeekGrid(
    dataset: Dataset,
    days: List<LocalDate>,
    timetable: StudentTimetable,
    now: LocalDateTime,
    modifier: Modifier,
    open: (Occurrence) -> Unit,
) {
    val hourHeight = 44.dp
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(start = 36.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            days.forEach { day ->
                val today = day == now.date
                Text(
                    "${day.dayInitials()} ${day.day}",
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        today -> MaterialTheme.colorScheme.surface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                        .background(
                            if (today) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            RoundedCornerShape(6.dp),
                        )
                        .padding(vertical = 4.dp),
                )
            }
        }
        Row(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp, end = 8.dp, bottom = 24.dp)) {
            HourLabels(hourHeight, Modifier.width(36.dp), short = true)
            Box(Modifier.weight(1f).height(hourHeight * (LAST_HOUR - FIRST_HOUR))) {
                HourLines(hourHeight)
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    days.forEach { day ->
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .background(
                                    if (day ==
                                        now.date
                                    ) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                    } else {
                                        Color.Transparent
                                    },
                                    RoundedCornerShape(6.dp),
                                ),
                        ) {
                            timetable.on(day).forEach { occurrence ->
                                OccurrenceBlock(dataset, occurrence, now, hourHeight, compact = true) {
                                    open(occurrence)
                                }
                            }
                            NowLine(day, now, hourHeight)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HourLabels(hourHeight: Dp, modifier: Modifier, short: Boolean = false) {
    Box(modifier.height(hourHeight * (LAST_HOUR - FIRST_HOUR))) {
        for (hour in FIRST_HOUR until LAST_HOUR) {
            Text(
                if (short) hour.toString().padStart(2, '0') else "${hour.toString().padStart(2, '0')}:00",
                style = TimeStyle.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(
                    Alignment.TopEnd,
                ).offset(y = hourHeight * (hour - FIRST_HOUR) - 7.dp).padding(end = 8.dp),
            )
        }
    }
}

@Composable
private fun HourLines(hourHeight: Dp) {
    for (hour in FIRST_HOUR until LAST_HOUR) {
        Box(
            Modifier.fillMaxWidth().height(
                1.dp,
            ).offset(y = hourHeight * (hour - FIRST_HOUR)).background(MaterialTheme.colorScheme.outline),
        )
    }
}

@Composable
private fun NowLine(date: LocalDate?, now: LocalDateTime, hourHeight: Dp) {
    if (date != now.date) return
    val hours = now.time.toSecondOfDay() / 3600f - FIRST_HOUR
    if (hours < 0 || hours > LAST_HOUR - FIRST_HOUR) return
    Row(Modifier.fillMaxWidth().offset(y = hourHeight * hours - 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(MaterialTheme.colorScheme.error, CircleShape))
        Box(Modifier.weight(1f).height(2.dp).background(MaterialTheme.colorScheme.error))
    }
}

@Composable
private fun OccurrenceBlock(
    dataset: Dataset,
    occurrence: Occurrence,
    now: LocalDateTime,
    hourHeight: Dp,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val slot = occurrence.activity.slot
    val top = hourHeight * (slot.start.toSecondOfDay() / 3600f - FIRST_HOUR)
    val height = hourHeight * ((slot.end.toSecondOfDay() - slot.start.toSecondOfDay()) / 3600f) - 3.dp
    val color = dataset.subjectColor(occurrence.activity.subjectId)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tint = lerp(MaterialTheme.colorScheme.background, color, if (dark) 0.2f else 0.12f)
    val past = occurrence.end <= now
    Row(
        modifier.fillMaxWidth().offset(y = top + 1.dp).height(height).alpha(if (past) 0.55f else 1f)
            .background(tint, RoundedCornerShape(if (compact) 4.dp else 8.dp)).clickable(onClick = onClick),
    ) {
        Box(
            Modifier.width(
                if (compact) 3.dp else 4.dp,
            ).fillMaxHeight().background(color, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)),
        )
        Column(Modifier.padding(horizontal = if (compact) 4.dp else 10.dp, vertical = if (compact) 4.dp else 6.dp)) {
            if (compact) {
                Text(
                    dataset.subjectShortName(occurrence.activity.subjectId),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    occurrence.activity.type.label(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                dataset.roomsById[occurrence.activity.roomId]?.code?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            } else {
                Text(
                    listOf(
                        occurrence.activity.type.label(),
                        slot.weeks.takeIf {
                            it != WeekRule.All
                        }?.label(),
                    ).filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    dataset.subjectName(occurrence.activity.subjectId),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        slot.start.toString(),
                        dataset.roomsById[occurrence.activity.roomId]?.code,
                        dataset.professorsById[occurrence.activity.professorId]?.displayName,
                    ).joinToString(" · "),
                    style = TimeStyle.copy(fontWeight = FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
