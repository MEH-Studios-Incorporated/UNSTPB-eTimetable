// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.data.ActionResult
import net.mehstudios.etimetable.android.ui.components.EtIcons
import net.mehstudios.etimetable.android.ui.components.label
import net.mehstudios.etimetable.android.ui.components.longLabel
import net.mehstudios.etimetable.android.ui.components.message
import net.mehstudios.etimetable.android.ui.components.roleLabel
import net.mehstudios.etimetable.android.ui.components.subjectName
import net.mehstudios.etimetable.android.ui.onboarding.audienceText
import net.mehstudios.etimetable.android.ui.theme.ButtonShape
import net.mehstudios.etimetable.api.ErrorCodes
import net.mehstudios.etimetable.api.MAX_REPORT_DESCRIPTION_LENGTH
import net.mehstudios.etimetable.api.MIN_REPORT_DESCRIPTION_LENGTH
import net.mehstudios.etimetable.api.TimetableReportRequest
import net.mehstudios.etimetable.model.ActivityType
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Professor
import net.mehstudios.etimetable.schedule.Occurrence
import net.mehstudios.etimetable.schedule.courseProfessors

/** Everything known about one class, from a tap on the timetable. Names open the person's schedule. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitySheet(
    dataset: Dataset,
    occurrence: Occurrence,
    onDismiss: () -> Unit,
    openProfessor: (String) -> Unit,
    openSubject: (String) -> Unit,
    reportProblem: suspend (TimetableReportRequest) -> ActionResult,
) {
    val activity = occurrence.activity
    val room = dataset.roomsById[activity.roomId]
    val building = room?.let { dataset.buildingsById[it.buildingId] }
    var reporting by rememberSaveable(dataset.semester.id, activity.id) { mutableStateOf(false) }
    var description by rememberSaveable(dataset.semester.id, activity.id) { mutableStateOf("") }
    var sent by rememberSaveable(dataset.semester.id, activity.id) { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ActionResult?>(null) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !sending })
    val scroll = rememberScrollState()
    LaunchedEffect(reporting) { scroll.scrollTo(0) }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    fun closeThen(then: () -> Unit) {
        scope.launch { sheet.hide() }.invokeOnCompletion {
            onDismiss()
            then()
        }
    }
    fun open(professor: Professor) = closeThen { openProfessor(professor.id) }
    ModalBottomSheet(onDismissRequest = { if (!sending) onDismiss() }, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(scroll)
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (reporting) {
                Text(stringResource(R.string.timetable_report), style = MaterialTheme.typography.headlineSmall)
                Text(dataset.subjectName(activity.subjectId), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${activity.type.label()} · ${occurrence.date.longLabel()}, " +
                        "${activity.slot.start} - ${activity.slot.end}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (sent) {
                    Text(stringResource(R.string.report_sent), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onDismiss, shape = ButtonShape) { Text(stringResource(R.string.report_done)) }
                } else {
                    Text(
                        stringResource(R.string.report_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = {
                            if (it.codePointCount(0, it.length) <= MAX_REPORT_DESCRIPTION_LENGTH) description = it
                        },
                        enabled = !sending,
                        label = { Text(stringResource(R.string.report_description)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                        supportingText = {
                            Text(
                                stringResource(
                                    R.string.report_length,
                                    description.codePointCount(0, description.length),
                                    MAX_REPORT_DESCRIPTION_LENGTH,
                                ),
                            )
                        },
                    )
                    result?.reportMessage()?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    val alreadyOpen = (result as? ActionResult.Refused)?.code == ErrorCodes.REPORT_ALREADY_OPEN
                    val trimmed = description.trim()
                    val validLength = trimmed.codePointCount(0, trimmed.length) in
                        MIN_REPORT_DESCRIPTION_LENGTH..MAX_REPORT_DESCRIPTION_LENGTH
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { reporting = false }, enabled = !sending) {
                            Text(stringResource(R.string.back))
                        }
                        Button(
                            enabled = !sending && !alreadyOpen && validLength,
                            shape = ButtonShape,
                            onClick = {
                                if (!sending) {
                                    keyboard?.hide()
                                    sending = true
                                    scope.launch {
                                        try {
                                            result = reportProblem(
                                                TimetableReportRequest(
                                                    dataset.semester.id,
                                                    activity.id,
                                                    description.trim(),
                                                ),
                                            )
                                            sent = result == ActionResult.Done
                                        } finally {
                                            sending = false
                                        }
                                    }
                                }
                            },
                        ) {
                            if (sending) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(R.string.report_send))
                            }
                        }
                    }
                }
                return@Column
            }
            Text(
                activity.type.label(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                onClick = { closeThen { openSubject(activity.subjectId) } },
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        dataset.subjectName(activity.subjectId),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        EtIcons.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
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
            TextButton(onClick = { reporting = true }) { Text(stringResource(R.string.timetable_report)) }
        }
    }
}

@Composable
private fun ActionResult.reportMessage(): String? = when (this) {
    ActionResult.NoConnection -> stringResource(R.string.report_no_connection)
    is ActionResult.Refused -> when (code) {
        ErrorCodes.REPORT_ALREADY_OPEN -> stringResource(R.string.report_already_open)
        ErrorCodes.TOO_MANY_REQUESTS -> stringResource(R.string.report_rate_limited)
        ErrorCodes.UNKNOWN_ACTIVITY, ErrorCodes.NO_SEMESTER -> stringResource(R.string.report_stale_class)
        ErrorCodes.INVALID_REPORT -> stringResource(R.string.report_invalid)
        ErrorCodes.UNAUTHORIZED -> stringResource(R.string.report_sign_in)
        else -> message()
    }
    else -> null
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
