// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.ui.components.label
import net.mehstudios.etimetable.android.ui.components.longLabel
import net.mehstudios.etimetable.android.ui.components.subjectName
import net.mehstudios.etimetable.android.ui.onboarding.audienceText
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.schedule.Occurrence

/** Everything known about one class, from a tap on the timetable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitySheet(dataset: Dataset, occurrence: Occurrence, onDismiss: () -> Unit) {
    val activity = occurrence.activity
    val room = dataset.roomsById[activity.roomId]
    val building = room?.let { dataset.buildingsById[it.buildingId] }
    ModalBottomSheet(onDismissRequest = onDismiss) {
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
            Field(stringResource(R.string.professor), dataset.professorsById[activity.professorId]?.displayName)
            Field(stringResource(R.string.weeks), activity.slot.weeks.label())
            Field(stringResource(R.string.groups), audienceText(dataset, activity))
        }
    }
}

@Composable
private fun Field(name: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row {
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
