// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.ui.components.label
import net.mehstudios.etimetable.android.ui.components.subjectName
import net.mehstudios.etimetable.android.ui.components.weekdayName
import net.mehstudios.etimetable.android.ui.theme.ChangeYellow
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.schedule.ChangeKind
import net.mehstudios.etimetable.schedule.ChangeNote
import net.mehstudios.etimetable.schedule.TeachingCalendar
import net.mehstudios.etimetable.schedule.meetsOn

/** The change notes shown now, by activity id; removed classes are under their old id. */
val LocalChangeNotes = staticCompositionLocalOf<Map<String, List<ChangeNote>>> { emptyMap() }

/** Text on the `change` yellow is always the dark text colour (docs/design.md). */
private val OnChange = Color(0xFF292929)

@Composable
fun changesOf(activityId: String): List<ChangeNote> =
    LocalChangeNotes.current[activityId].orEmpty().filter { it.kind != ChangeKind.REMOVED }

/** "Sală schimbată, era BN 024" for one note. */
@Composable
fun ChangeNote.text(): String = when (kind) {
    ChangeKind.ROOM -> old?.let { stringResource(R.string.change_room_was, it) }
        ?: stringResource(R.string.change_room)
    ChangeKind.TEACHER -> old?.let { stringResource(R.string.change_teacher_was, it) }
        ?: stringResource(R.string.change_teacher)
    ChangeKind.TIME -> {
        val before = oldSlot
        val after = newSlot
        when {
            before == null || after == null -> stringResource(R.string.change_time)
            before.isoDay != after.isoDay ->
                stringResource(R.string.change_day_was, "${weekdayName(before.isoDay, false)}, ${before.start}")
            before.start != after.start || before.end != after.end ->
                stringResource(R.string.change_time_was, "${before.start} - ${before.end}")
            else -> stringResource(R.string.change_weeks_were, before.weeks.label().replaceFirstChar { it.lowercase() })
        }
    }
    ChangeKind.ADDED -> stringResource(R.string.change_added)
    ChangeKind.REMOVED -> stringResource(R.string.change_removed)
}

/** One line for a card: the note itself, or which things changed when there are several. */
@Composable
fun changeSummary(notes: List<ChangeNote>): String? = when (notes.size) {
    0 -> null
    1 -> notes.single().text()
    else -> stringResource(
        R.string.change_several,
        notes.sortedBy { it.kind }.map {
            stringResource(
                when (it.kind) {
                    ChangeKind.ROOM -> R.string.change_kind_room
                    ChangeKind.TIME -> R.string.change_kind_time
                    else -> R.string.change_kind_teacher
                },
            )
        }.joinToString(", "),
    )
}

/** The yellow tag of a changed class, as on the mockups. */
@Composable
fun ChangeTag(text: String, modifier: Modifier = Modifier, maxLines: Int = 1) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = OnChange,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.background(
            ChangeYellow,
            RoundedCornerShape(4.dp),
        ).padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

/** The tag for [activityId]'s notes, or nothing. */
@Composable
fun ChangeTagFor(activityId: String, modifier: Modifier = Modifier) {
    changeSummary(changesOf(activityId))?.let { ChangeTag(it, modifier) }
}

/** A yellow dot for blocks too small for the tag (the week grid); it reads out the change. */
@Composable
fun ChangeDot(activityId: String, modifier: Modifier = Modifier) {
    val summary = changeSummary(changesOf(activityId)) ?: return
    Box(
        modifier.size(8.dp).border(1.dp, OnChange.copy(alpha = 0.4f), CircleShape).background(ChangeYellow, CircleShape)
            .semantics { contentDescription = summary },
    )
}

/**
 * Classes taken out of the student's timetable that would have met on [day], each on a
 * yellow line ("Scos din orar: Marketing, Proiect, 16:00 - 18:00"); nothing when none.
 */
@Composable
fun RemovedClasses(dataset: Dataset, calendar: TeachingCalendar, day: LocalDate, modifier: Modifier = Modifier) {
    val removed = LocalChangeNotes.current.values.flatten().mapNotNull { it.removed }
        .filter { calendar.meetsOn(it.slot, day) }
        .sortedBy { it.slot.start }
    if (removed.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        removed.forEach {
            val what = listOf(dataset.subjectName(it.subjectId), it.type.label(), "${it.slot.start} - ${it.slot.end}")
            ChangeTag(
                stringResource(R.string.change_removed_class, what.joinToString(", ")),
                Modifier.fillMaxWidth(),
                maxLines = 2,
            )
        }
    }
}
