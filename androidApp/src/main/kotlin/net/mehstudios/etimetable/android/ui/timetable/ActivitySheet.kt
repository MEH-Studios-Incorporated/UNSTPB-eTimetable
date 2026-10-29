// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.ui.components.EtIcons
import net.mehstudios.etimetable.android.ui.components.label
import net.mehstudios.etimetable.android.ui.components.longLabel
import net.mehstudios.etimetable.android.ui.components.roleLabel
import net.mehstudios.etimetable.android.ui.components.subjectName
import net.mehstudios.etimetable.android.ui.onboarding.audienceText
import net.mehstudios.etimetable.model.ActivityType
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Professor
import net.mehstudios.etimetable.schedule.Occurrence
import net.mehstudios.etimetable.schedule.courseProfessors

/** Everything known about one class, from a tap on the timetable. Names open the person's schedule. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitySheet(dataset: Dataset, occurrence: Occurrence, onDismiss: () -> Unit, openProfessor: (String) -> Unit) {
    val activity = occurrence.activity
    val room = dataset.roomsById[activity.roomId]
    val building = room?.let { dataset.buildingsById[it.buildingId] }
    val sheet = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    fun open(professor: Professor) {
        scope.launch { sheet.hide() }.invokeOnCompletion {
            onDismiss()
            openProfessor(professor.id)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                activity.type.label(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(dataset.subjectName(activity.subjectId), style = MaterialTheme.typography.headlineSmall)
            Text(
                "${occurrence.date.longLabel()}, ${activity.slot.start} - ${activity.slot.end}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Field(
                stringResource(R.string.room),
                listOfNotNull(
                    room?.code,
                    building?.name?.takeIf {
                        it != building.code
                    },
                ).joinToString(", "),
            )
            Person(activity.type.roleLabel(), dataset.professorsById[activity.professorId], ::open)
            if (activity.type != ActivityType.COURSE) {
                dataset.courseProfessors(activity).forEach {
                    Person(ActivityType.COURSE.roleLabel(), it, ::open)
                }
            }
            Field(stringResource(R.string.weeks), activity.slot.weeks.label())
            Field(stringResource(R.string.groups), audienceText(dataset, activity))
        }
    }
}

@Composable
private fun Field(name: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FieldName(name)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun FieldName(name: String) {
    Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A teacher as a row that opens their schedule, or a note when the timetable names nobody. */
@Composable
private fun Person(role: String, professor: Professor?, open: (Professor) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FieldName(role)
        if (professor == null) {
            Text(
                stringResource(R.string.not_listed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Surface(
            onClick = { open(professor) },
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.small,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Text(
                    professor.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    EtIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
