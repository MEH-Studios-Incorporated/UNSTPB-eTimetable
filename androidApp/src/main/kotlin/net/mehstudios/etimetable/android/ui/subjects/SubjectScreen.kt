// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.subjects

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.data.ActionResult
import net.mehstudios.etimetable.android.ui.components.PageBar
import net.mehstudios.etimetable.android.ui.components.label
import net.mehstudios.etimetable.android.ui.components.message
import net.mehstudios.etimetable.android.ui.components.shortDate
import net.mehstudios.etimetable.android.ui.components.subjectColor
import net.mehstudios.etimetable.android.ui.components.subjectName
import net.mehstudios.etimetable.android.ui.components.weekdayName
import net.mehstudios.etimetable.android.ui.onboarding.audienceText
import net.mehstudios.etimetable.android.ui.theme.ButtonShape
import net.mehstudios.etimetable.model.Activity
import net.mehstudios.etimetable.model.ActivityType
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Enrollment
import net.mehstudios.etimetable.model.ExtraSlot
import net.mehstudios.etimetable.model.Profile
import net.mehstudios.etimetable.model.WeekRule
import net.mehstudios.etimetable.schedule.SlotStanding
import net.mehstudios.etimetable.schedule.StudentTimetable

/**
 * Every slot of one subject, grouped by type. A national student adds another
 * group's slot once or for the semester; an Erasmus student adds and removes
 * picks. Overlaps are refused in both cases, naming the clash.
 */
@Composable
fun SubjectScreen(
    dataset: Dataset,
    timetable: StudentTimetable,
    subjectId: String,
    now: LocalDateTime,
    save: suspend (Profile) -> ActionResult,
    onBack: () -> Unit,
) {
    val subject = dataset.subjectsById[subjectId] ?: return
    val profile = timetable.profile
    val erasmus = profile.enrollment is Enrollment.Erasmus
    var fitsOnly by rememberSaveable { mutableStateOf(false) }
    var adding by remember { mutableStateOf<Activity?>(null) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ActionResult?>(null) }
    val scope = rememberCoroutineScope()

    fun apply(change: Profile) {
        busy = true
        scope.launch {
            result = save(change)
            busy = false
        }
    }

    val slots = remember(dataset, timetable, subjectId, now.date) {
        dataset.activities.filter { it.subjectId == subjectId }
            .sortedWith(compareBy({ it.type }, { it.slot.isoDay }, { it.slot.start }))
            .map { activity ->
                val standing = timetable.standing(activity)
                val next = timetable.nextMeeting(activity, now)
                val onceClash = next?.let { timetable.clashFor(activity, it.date) }
                SlotView(
                    activity,
                    standing,
                    next?.date,
                    onceFits = next != null && onceClash == null,
                    canRemove = (standing as? SlotStanding.Extra)?.date != null || timetable.canRemove(activity),
                )
            }
    }
    val shown = slots.filter { !fitsOnly || (it.standing as? SlotStanding.Open)?.clash == null }

    Column(Modifier.fillMaxSize()) {
        PageBar(stringResource(R.string.page_subject), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(
                Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(14.dp).background(dataset.subjectColor(subject.id), RoundedCornerShape(3.dp)))
                Text(dataset.subjectName(subject.id), style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                subjectSubtitle(dataset, subject),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 42.dp, end = 16.dp, top = 2.dp),
            )
            Row(
                Modifier.fillMaxWidth().clickable {
                    fitsOnly = !fitsOnly
                }.padding(start = 16.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.subject_fits), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.subject_fits_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Checkbox(checked = fitsOnly, onCheckedChange = { fitsOnly = it })
            }
            result?.message()?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))

            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (slots.isEmpty()) Muted(stringResource(R.string.subject_no_slots))
                if (slots.isNotEmpty() && shown.isEmpty()) Muted(stringResource(R.string.subject_all_clash))
                shown.groupBy { it.activity.type }.forEach { (type, group) ->
                    SectionTitle(type, group.size)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        Column {
                            group.forEachIndexed { i, slot ->
                                if (i > 0) HorizontalDivider()
                                SlotRow(
                                    dataset,
                                    slot,
                                    erasmus,
                                    busy,
                                    onAdd = {
                                        if (erasmus) {
                                            apply(profile.withPick(slot.activity.id))
                                        } else {
                                            adding = slot.activity
                                        }
                                    },
                                    onRemove = {
                                        // the group's own slot stays in the group; the student drops it
                                        apply(
                                            if (slot.standing == SlotStanding.Group) {
                                                profile.copy(droppedSlots = profile.droppedSlots + slot.activity.id)
                                            } else {
                                                profile.without(slot.activity.id)
                                            },
                                        )
                                    },
                                    onRestore = {
                                        apply(profile.copy(droppedSlots = profile.droppedSlots - slot.activity.id))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    adding?.let { activity ->
        val slot = slots.first { it.activity.id == activity.id }
        AddSheet(
            slot,
            onDismiss = { adding = null },
            addOnce = { date -> apply(profile.withExtra(ExtraSlot(activity.id, date))) },
            addSemester = { apply(profile.withExtra(ExtraSlot(activity.id))) },
        )
    }
}

/** One slot as the page shows it: how it stands now, and its next meeting for a one-time visit. */
private data class SlotView(
    val activity: Activity,
    val standing: SlotStanding,
    val nextDate: LocalDate?,
    val onceFits: Boolean,
    /** False for the last weekly slot of its kind in the subject: one always stays. */
    val canRemove: Boolean,
) {
    val clash: Activity? get() = (standing as? SlotStanding.Open)?.clash ?: (standing as? SlotStanding.Dropped)?.clash

    /** A national student can still add a clashing slot once when that date is free. */
    fun canAdd(erasmus: Boolean): Boolean = standing is SlotStanding.Open && (clash == null || (!erasmus && onceFits))
}

@Composable
private fun SectionTitle(type: ActivityType, count: Int) {
    val text = if (type == ActivityType.COURSE || count < 2) {
        type.label()
    } else {
        type.label() + " · " + pluralStringResource(R.plurals.subject_slots, count, count)
    }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SlotRow(
    dataset: Dataset,
    slot: SlotView,
    erasmus: Boolean,
    busy: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onRestore: () -> Unit,
) {
    val activity = slot.activity
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${weekdayName(activity.slot.isoDay, capitalized = true)}, " +
                    "${activity.slot.start} - ${activity.slot.end}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                listOfNotNull(
                    dataset.roomsById[activity.roomId]?.code,
                    audienceText(dataset, activity),
                    dataset.professorsById[activity.professorId]?.displayName,
                    activity.slot.weeks.takeIf { it != WeekRule.All }?.label(),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            slot.clash?.let { clash ->
                Text(
                    stringResource(
                        R.string.subject_clash,
                        dataset.subjectName(clash.subjectId),
                        clash.type.label().lowercase(),
                        "${weekdayName(clash.slot.isoDay, capitalized = true).take(2)} ${clash.slot.start}",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        when (val standing = slot.standing) {
            SlotStanding.Group -> Column(horizontalAlignment = Alignment.End) {
                Status(
                    stringResource(
                        if (activity.type ==
                            ActivityType.COURSE
                        ) {
                            R.string.subject_in_timetable
                        } else {
                            R.string.subject_your_group
                        },
                    ),
                )
                // only once another slot of this kind is in the timetable
                if (slot.canRemove) {
                    TextButton(onClick = onRemove, enabled = !busy) { Text(stringResource(R.string.subject_remove)) }
                }
            }
            is SlotStanding.Dropped -> Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.subject_dropped),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRestore, enabled = !busy && standing.clash == null) {
                    Text(stringResource(R.string.subject_restore))
                }
            }
            SlotStanding.Picked -> Column(horizontalAlignment = Alignment.End) {
                Status(stringResource(R.string.subject_in_timetable))
                TextButton(onClick = onRemove, enabled = !busy) { Text(stringResource(R.string.subject_remove)) }
            }
            is SlotStanding.Extra -> Column(horizontalAlignment = Alignment.End) {
                Status(
                    standing.date?.let { stringResource(R.string.subject_added_once, it.shortDate()) }
                        ?: stringResource(R.string.subject_added),
                )
                if (slot.canRemove) {
                    TextButton(onClick = onRemove, enabled = !busy) { Text(stringResource(R.string.subject_remove)) }
                } else {
                    // it replaced the group's own slot, which is out of the timetable
                    Text(
                        stringResource(R.string.subject_last_one),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is SlotStanding.Open -> OutlinedButton(
                onClick = onAdd,
                enabled = !busy && slot.canAdd(erasmus),
                shape = ButtonShape,
                border = BorderStroke(
                    1.dp,
                    if (slot.canAdd(erasmus)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                ),
            ) { Text(stringResource(R.string.subject_add)) }
        }
    }
}

@Composable
private fun Status(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Once (the next meeting) or every week: the choice a national student makes when adding a slot. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheet(slot: SlotView, onDismiss: () -> Unit, addOnce: (LocalDate) -> Unit, addSemester: () -> Unit) {
    val sheet = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val activity = slot.activity
    fun close(then: () -> Unit) {
        scope.launch { sheet.hide() }.invokeOnCompletion {
            onDismiss()
            then()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val day = "${weekdayName(activity.slot.isoDay, capitalized = false)}, ${activity.slot.start}"
            Text(
                stringResource(
                    when (activity.type) {
                        ActivityType.COURSE -> R.string.subject_add_course
                        ActivityType.SEMINAR -> R.string.subject_add_seminar
                        ActivityType.LAB -> R.string.subject_add_lab
                        ActivityType.PROJECT -> R.string.subject_add_project
                    },
                    day,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(
                    if (slot.clash == null) R.string.subject_add_hint else R.string.subject_add_hint_clash,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { slot.nextDate?.let { date -> close { addOnce(date) } } },
                    enabled = slot.onceFits,
                    shape = ButtonShape,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text(
                        slot.nextDate?.let { stringResource(R.string.subject_add_once, it.shortDate()) }
                            ?: stringResource(R.string.subject_no_more_dates),
                    )
                }
                Button(
                    onClick = { close(addSemester) },
                    enabled = slot.clash == null,
                    shape = ButtonShape,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text(stringResource(R.string.subject_add_semester)) }
            }
        }
    }
}

private fun Profile.withPick(activityId: String): Profile {
    val picks = (enrollment as? Enrollment.Erasmus)?.activityIds.orEmpty()
    return copy(enrollment = Enrollment.Erasmus(picks + activityId))
}

/** Replaces any earlier addition of the same activity. */
private fun Profile.withExtra(extra: ExtraSlot): Profile =
    copy(extraSlots = extraSlots.filterNot { it.activityId == extra.activityId } + extra)

private fun Profile.without(activityId: String): Profile {
    val erasmus = enrollment as? Enrollment.Erasmus
    return copy(
        enrollment = erasmus?.copy(activityIds = erasmus.activityIds - activityId) ?: enrollment,
        extraSlots = extraSlots.filterNot { it.activityId == activityId },
    )
}
