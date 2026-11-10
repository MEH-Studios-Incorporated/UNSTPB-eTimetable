// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.professors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import net.mehstudios.etimetable.android.ui.components.BrandBar
import net.mehstudios.etimetable.android.ui.components.LinkText
import net.mehstudios.etimetable.android.ui.components.PageBar
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
import java.text.Normalizer

@Composable
fun ProfessorsScreen(
    dataset: Dataset,
    calendar: TeachingCalendar,
    now: LocalDateTime,
    actions: @Composable () -> Unit,
    open: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var activeOnly by rememberSaveable { mutableStateOf(false) }
    val professors = remember(dataset, query, activeOnly, now) {
        val q = fold(query)
        dataset.professors.filter { p ->
            val subjects = dataset.activities.filter {
                it.professorId == p.id
            }.mapNotNull { dataset.subjectsById[it.subjectId] }
            val text =
                p.displayName + " " + p.email.orEmpty() + " " + subjects.joinToString { it.name.ro + " " + it.name.en }
            fold(text).contains(q) &&
                (!activeOnly || calendar.professorStatus(dataset, p.id, now) is ProfessorStatus.Now)
        }.sortedBy { fold(it.name) }
    }
    Column(Modifier.fillMaxSize()) {
        BrandBar { actions() }
        Text(
            stringResource(R.string.tab_professors),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text(stringResource(R.string.prof_search)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.prof_active_only), modifier = Modifier.weight(1f))
            Switch(checked = activeOnly, onCheckedChange = { activeOnly = it })
        }
        if (professors.isEmpty()) Text(stringResource(R.string.prof_search_empty), modifier = Modifier.padding(16.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(professors, key = { it.id }) { p ->
                Column(
                    Modifier.fillMaxWidth().clickable {
                        open(p.id)
                    }.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(p.displayName.trim(), style = MaterialTheme.typography.titleMedium)
                    Status(dataset, calendar.professorStatus(dataset, p.id, now))
                }
                HorizontalDivider()
            }
        }
    }
}

private fun fold(text: String): String = Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}"), "")

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
        (0 until 7).map { monday.plus(it, DateTimeUnit.DAY) }
            .map { it to calendar.professorDay(dataset, professorId, it) }
            .filter { it.second.isNotEmpty() }
    }

    Column(Modifier.fillMaxSize()) {
        PageBar(stringResource(R.string.page_professor), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
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
                Text(
                    stringResource(R.string.prof_status_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val office = dataset.roomsById[professor.officeRoomId]
                val building = dataset.buildingsById[office?.buildingId]
                Text(
                    stringResource(R.string.prof_office, office?.code ?: stringResource(R.string.prof_not_provided)),
                    style = MaterialTheme.typography.bodyLarge,
                )
                building?.let {
                    Text(
                        listOfNotNull(it.name, it.address).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                professor.email?.let { email ->
                    LinkText(stringResource(R.string.write_email) + ": " + email, MaterialTheme.typography.bodyMedium) {
                        uri.openUri("mailto:$email")
                    }
                }
                if (professor.email.isNullOrBlank()) Muted(stringResource(R.string.prof_email_none))
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
    val slot = when (status) {
        is ProfessorStatus.Now -> status.slot
        is ProfessorStatus.LaterToday -> status.slot
        ProfessorStatus.NothingMoreToday -> null
    }
    slot?.let {
        val room = dataset.roomsById[it.activity?.roomId ?: it.officeHour?.roomId]
        val building = dataset.buildingsById[room?.buildingId]
        Text(
            listOfNotNull(
                it.activity?.let { a -> dataset.subjectName(a.subjectId) } ?: stringResource(R.string.office_hours),
                room?.code ?: stringResource(R.string.prof_not_provided),
                building?.address,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
