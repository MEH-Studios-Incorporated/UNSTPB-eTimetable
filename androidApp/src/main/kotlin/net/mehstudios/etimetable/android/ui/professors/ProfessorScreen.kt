// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.professors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.ui.components.LinkText
import net.mehstudios.etimetable.android.ui.components.dayName
import net.mehstudios.etimetable.android.ui.components.label
import net.mehstudios.etimetable.android.ui.components.longLabel
import net.mehstudios.etimetable.android.ui.components.subjectColor
import net.mehstudios.etimetable.android.ui.components.subjectName
import net.mehstudios.etimetable.android.ui.onboarding.audienceText
import net.mehstudios.etimetable.android.ui.theme.TimeStyle
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.WeekRule
import net.mehstudios.etimetable.schedule.ProfessorSlot
import net.mehstudios.etimetable.schedule.ProfessorStatus
import net.mehstudios.etimetable.schedule.TeachingCalendar
import net.mehstudios.etimetable.schedule.mondayOfWeek
import net.mehstudios.etimetable.schedule.professorDay
import net.mehstudios.etimetable.schedule.professorStatus

/** Where a professor is now, their office hours and what they teach this week. */
@Composable
fun ProfessorScreen(
    dataset: Dataset,
    calendar: TeachingCalendar,
    professorId: String,
    now: LocalDateTime,
    onBack: () -> Unit,
) {
    val professor = dataset.professorsById[professorId] ?: return
    val uri = LocalUriHandler.current
    // on a weekend the week that matters is the next one
    val weekend = now.date.isWeekend()
    val monday = now.date.mondayOfWeek().let { if (weekend) it.plus(7, DateTimeUnit.DAY) else it }
    val days = remember(dataset, professorId, monday) {
        (0 until 6).map { monday.plus(it, DateTimeUnit.DAY) }
            .map { it to calendar.professorDay(dataset, professorId, it) }
            .filter { it.second.isNotEmpty() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TextButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) {
            Text(stringResource(R.string.back))
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (professor.title.isNotBlank()) {
                Text(
                    professor.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(professor.name, style = MaterialTheme.typography.headlineSmall)
            Status(dataset, calendar.professorStatus(dataset, professorId, now))
            professor.email?.let { email ->
                LinkText(stringResource(R.string.write_email) + ": " + email, MaterialTheme.typography.bodyMedium) {
                    uri.openUri("mailto:$email")
                }
            }
        }

        HorizontalDivider(Modifier.padding(16.dp))

        Section(stringResource(R.string.office_hours)) {
            if (professor.officeHours.isEmpty()) {
                Muted(stringResource(R.string.office_hours_none))
            }
            professor.officeHours.sortedWith(compareBy({ it.slot.isoDay }, { it.slot.start })).forEach { office ->
                val day = monday.plus(office.slot.isoDay - 1, DateTimeUnit.DAY).dayName()
                Text(
                    listOfNotNull(
                        "$day, ${office.slot.start} - ${office.slot.end}",
                        dataset.roomsById[office.roomId]?.code,
                        office.slot.weeks.takeIf { it != WeekRule.All }?.label(),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        HorizontalDivider(Modifier.padding(16.dp))

        Section(stringResource(if (weekend) R.string.prof_next_week else R.string.prof_this_week)) {
            if (days.isEmpty()) Muted(stringResource(R.string.prof_week_empty))
            days.forEach { (date, slots) ->
                Text(
                    date.longLabel(),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (date == now.date) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    modifier = Modifier.padding(top = 6.dp),
                )
                slots.forEach { SlotRow(dataset, it) }
            }
        }
        Box(Modifier.height(32.dp))
    }
}

@Composable
private fun Status(dataset: Dataset, status: ProfessorStatus) {
    val (text, active) = when (status) {
        is ProfessorStatus.Now -> stringResource(
            if (status.slot.activity != null) R.string.prof_now_teaching else R.string.prof_now_office,
            status.slot.end.time.toString(),
        ) + (status.slot.activity?.let { " · " + dataset.subjectName(it.subjectId) }.orEmpty()) to true
        is ProfessorStatus.LaterToday -> stringResource(
            R.string.prof_later_today,
            status.slot.start.time.toString(),
        ) to false
        ProfessorStatus.NothingMoreToday -> stringResource(R.string.prof_done_today) to false
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Box(
            Modifier.size(8.dp).background(
                if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                CircleShape,
            ),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SlotRow(dataset: Dataset, slot: ProfessorSlot) {
    val activity = slot.activity
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            "${slot.start.time} - ${slot.end.time}",
            style = TimeStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(104.dp).padding(top = 2.dp),
        )
        Box(
            Modifier.padding(top = 2.dp).width(3.dp).height(36.dp).background(
                if (activity != null) dataset.subjectColor(activity.subjectId) else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(2.dp),
            ),
        )
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                if (activity !=
                    null
                ) {
                    dataset.subjectName(activity.subjectId)
                } else {
                    stringResource(R.string.office_hours)
                },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val room = dataset.roomsById[activity?.roomId ?: slot.officeHour?.roomId]?.code
            Text(
                listOfNotNull(activity?.type?.label(), room, activity?.let { audienceText(dataset, it) })
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun LocalDate.isWeekend() = dayOfWeek.isoDayNumber >= 6
