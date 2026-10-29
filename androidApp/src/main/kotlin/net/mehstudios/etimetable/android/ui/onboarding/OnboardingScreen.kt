// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.data.ActionResult
import net.mehstudios.etimetable.android.ui.components.BrandBar
import net.mehstudios.etimetable.android.ui.components.EtIcons
import net.mehstudios.etimetable.android.ui.components.appLanguage
import net.mehstudios.etimetable.android.ui.components.label
import net.mehstudios.etimetable.android.ui.components.message
import net.mehstudios.etimetable.android.ui.components.subjectColor
import net.mehstudios.etimetable.android.ui.components.subjectName
import net.mehstudios.etimetable.android.ui.theme.ButtonShape
import net.mehstudios.etimetable.model.Activity
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Enrollment
import net.mehstudios.etimetable.model.Profile
import net.mehstudios.etimetable.model.WeekRule
import net.mehstudios.etimetable.schedule.StudentTimetable
import java.time.format.TextStyle
import java.util.Locale

private enum class Mode { NATIONAL, ERASMUS }

/**
 * The first start, and later "change group or subjects" from the profile.
 * [onCancel] is null on the first start, where there is nothing to go back to.
 */
@Composable
fun OnboardingScreen(
    dataset: Dataset,
    profile: Profile?,
    save: suspend (Profile) -> ActionResult,
    onCancel: (() -> Unit)?,
) {
    val current = profile?.enrollment
    var mode by rememberSaveable {
        mutableStateOf(
            when (current) {
                is Enrollment.National -> Mode.NATIONAL
                is Enrollment.Erasmus -> Mode.ERASMUS
                null -> null
            },
        )
    }
    var lastResult by remember { mutableStateOf<ActionResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit(enrollment: Enrollment) {
        busy = true
        scope.launch {
            lastResult = save((profile ?: Profile(language = appLanguage())).copy(enrollment = enrollment))
            busy = false
        }
    }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BrandBar {
                if (onCancel != null) TextButton(onClick = onCancel) { Text(stringResource(R.string.back)) }
            }
            Text(
                stringResource(R.string.onb_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeCard(
                    stringResource(R.string.onb_national),
                    stringResource(R.string.onb_national_hint),
                    mode == Mode.NATIONAL,
                    Modifier.weight(1f),
                ) {
                    mode = Mode.NATIONAL
                }
                ModeCard(
                    stringResource(R.string.onb_erasmus),
                    stringResource(R.string.onb_erasmus_hint),
                    mode == Mode.ERASMUS,
                    Modifier.weight(1f),
                ) {
                    mode = Mode.ERASMUS
                }
            }
            lastResult?.message()?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            Box(Modifier.weight(1f)) {
                when (mode) {
                    Mode.NATIONAL -> NationalPicker(dataset, current as? Enrollment.National, busy, ::submit)
                    Mode.ERASMUS -> ErasmusPicker(dataset, current as? Enrollment.Erasmus, busy, ::submit)
                    null -> Text(
                        stringResource(R.string.onb_change_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeCard(title: String, hint: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NationalPicker(
    dataset: Dataset,
    current: Enrollment.National?,
    busy: Boolean,
    submit: (Enrollment) -> Unit,
) {
    val currentGroup = current?.let { dataset.groupsById[it.groupId] }
    val currentSeries = currentGroup?.let { g -> dataset.series.firstOrNull { it.id == g.seriesId } }
    var programmeId by rememberSaveable { mutableStateOf(currentSeries?.programmeId) }
    var year by rememberSaveable { mutableStateOf(currentSeries?.year) }
    var groupId by rememberSaveable { mutableStateOf(current?.groupId) }
    var halfId by rememberSaveable { mutableStateOf(current?.halfGroupId) }

    val years = dataset.series.filter { it.programmeId == programmeId }.map { it.year }.distinct().sorted()
    val seriesIds = dataset.series.filter { it.programmeId == programmeId && it.year == year }.map { it.id }.toSet()
    val groups = dataset.groups.filter { it.seriesId in seriesIds }.sortedBy { it.code }
    val halves = dataset.halfGroups.filter { it.groupId == groupId }.sortedBy { it.suffix }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Section(stringResource(R.string.onb_programme))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dataset.programmes.forEach { p ->
                    FilterChip(
                        selected = p.id == programmeId,
                        onClick = {
                            programmeId = p.id
                            year = null
                            groupId = null
                            halfId = null
                        },
                        label = { Text(p.code) },
                    )
                }
            }
            Reveal(programmeId != null) {
                Section(stringResource(R.string.onb_year))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    years.forEach { y ->
                        FilterChip(
                            selected = y == year,
                            onClick = {
                                year = y
                                groupId = null
                                halfId = null
                            },
                            label = { Text(stringResource(R.string.onb_year_n, y)) },
                        )
                    }
                }
            }
            Reveal(year != null) {
                Section(stringResource(R.string.onb_group))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { g ->
                        FilterChip(
                            selected = g.id == groupId,
                            onClick = {
                                groupId = g.id
                                halfId = null
                            },
                            label = { Text(g.code) },
                        )
                    }
                }
            }
            Reveal(groupId != null && halves.isNotEmpty()) {
                Section(stringResource(R.string.onb_half))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    halves.forEach { h ->
                        FilterChip(selected = h.id == halfId, onClick = { halfId = h.id }, label = { Text(h.suffix) })
                    }
                    FilterChip(selected = halfId == null, onClick = {
                        halfId = null
                    }, label = { Text(stringResource(R.string.onb_both_halves)) })
                }
            }
        }
        BottomAction(enabled = groupId != null && !busy, label = stringResource(R.string.save)) {
            submit(Enrollment.National(groupId!!, halfId))
        }
    }
}

@Composable
private fun ErasmusPicker(dataset: Dataset, current: Enrollment.Erasmus?, busy: Boolean, submit: (Enrollment) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf(current?.activityIds.orEmpty()) }
    val clashes = remember { mutableStateMapOf<String, String>() }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val language = appLanguage()

    val subjects = dataset.subjects.filter {
        query.isBlank() ||
            listOf(it.name.ro, it.name.en, it.code).any { name -> name.contains(query.trim(), ignoreCase = true) }
    }

    fun toggle(activity: Activity) {
        if (activity.id in picked) {
            picked = picked - activity.id
            clashes.remove(activity.id)
            return
        }
        val clash = StudentTimetable(dataset, Profile(enrollment = Enrollment.Erasmus(picked))).clashFor(activity)
        if (clash == null) {
            picked = picked + activity.id
            clashes.remove(activity.id)
        } else {
            clashes[activity.id] = "${dataset.subjectName(clash.subjectId)}, ${slotText(clash)}"
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(EtIcons.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.onb_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        LazyColumn(Modifier.weight(1f)) {
            items(subjects, key = { it.id }) { subject ->
                val activities = dataset.activities.filter {
                    it.subjectId == subject.id
                }.sortedWith(compareBy({ it.type }, { it.slot.isoDay }, { it.slot.start }))
                val count = activities.count { it.id in picked }
                val open = expanded[subject.id] ?: (count > 0)
                Column(
                    Modifier.fillMaxWidth().clickable {
                        expanded[subject.id] = !open
                    }.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.width(10.dp).height(10.dp).padding(0.dp)) {
                            Surface(
                                color = dataset.subjectColor(subject.id),
                                shape = RoundedCornerShape(3.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                            }
                        }
                        Text(
                            subject.name.get(language),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (count >
                            0
                        ) {
                            Text(
                                count.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    val programme = dataset.programmes.firstOrNull { it.id == subject.programmeId }?.code.orEmpty()
                    Text(
                        "$programme · ${stringResource(R.string.onb_year_n, subject.year)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (open) {
                    activities.forEach { activity ->
                        SlotRow(dataset, activity, activity.id in picked, clashes[activity.id]) { toggle(activity) }
                    }
                }
                HorizontalDivider()
            }
        }
        BottomAction(
            enabled = !busy && picked.isNotEmpty(),
            label = stringResource(R.string.save),
            note = pluralStringResource(R.plurals.onb_subjects_picked, picked.size, picked.size),
        ) { submit(Enrollment.Erasmus(picked)) }
    }
}

@Composable
private fun SlotRow(dataset: Dataset, activity: Activity, selected: Boolean, clash: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(
            onClick = onClick,
        ).padding(start = 36.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(checked = selected, onCheckedChange = { onClick() })
        Column(Modifier.weight(1f).padding(top = 12.dp)) {
            Text("${activity.type.label()} · ${slotText(activity)}", style = MaterialTheme.typography.bodyLarge)
            val details = listOfNotNull(
                audienceText(dataset, activity),
                dataset.roomsById[activity.roomId]?.code,
                activity.slot.weeks.takeIf { it != WeekRule.All }?.label(),
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (activity.openToErasmus) {
                Text(
                    stringResource(R.string.onb_open_to_erasmus),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (clash != null) {
                Text(
                    stringResource(R.string.onb_clash, clash),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Slides a step of the form in once the step before it is answered. */
@Composable
private fun Reveal(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun BottomAction(enabled: Boolean, label: String, note: String? = null, onClick: () -> Unit) {
    HorizontalDivider()
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Button(shape = ButtonShape, onClick = onClick, enabled = enabled, modifier = Modifier.height(48.dp)) {
            Text(label)
        }
    }
}

/** "Mi 12:00-14:00" */
fun slotText(activity: Activity): String {
    val day = java.time.DayOfWeek.of(activity.slot.isoDay).getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2)
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    return "$day ${activity.slot.start}-${activity.slot.end}"
}

/** "1541E/A", "1541E 1542E" or "Seria E" */
@Composable
fun audienceText(dataset: Dataset, activity: Activity): String? {
    val resources = LocalResources.current
    val a = activity.audience
    val series = a.seriesIds.mapNotNull { id -> dataset.series.firstOrNull { it.id == id } }
    if (series.isNotEmpty()) return series.joinToString(" ") { resources.getString(R.string.whole_series, it.name) }
    val groups = a.groupIds.mapNotNull { dataset.groupsById[it]?.code }
    val halves = a.halfGroupIds.mapNotNull { id ->
        dataset.halfGroupsById[id]?.let { h -> "${dataset.groupsById[h.groupId]?.code ?: h.groupId}/${h.suffix}" }
    }
    return (groups + halves).sorted().joinToString(" ").ifEmpty { null }
}
