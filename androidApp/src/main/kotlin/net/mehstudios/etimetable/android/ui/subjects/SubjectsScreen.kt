// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.subjects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.ui.components.BrandBar
import net.mehstudios.etimetable.android.ui.components.EtIcons
import net.mehstudios.etimetable.android.ui.components.appLanguage
import net.mehstudios.etimetable.android.ui.components.subjectColor
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Enrollment
import net.mehstudios.etimetable.model.Subject
import net.mehstudios.etimetable.schedule.StudentTimetable
import java.text.Collator
import java.util.Locale

/**
 * Every subject of the semester, searchable. A national student sees their
 * programme and year first, with a switch for the rest; an Erasmus student,
 * who has no programme here, sees all of them.
 */
@Composable
fun SubjectsScreen(
    dataset: Dataset,
    timetable: StudentTimetable,
    actions: @Composable () -> Unit,
    open: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val language = appLanguage()
    val home = remember(dataset, timetable) { homeProgrammeYear(dataset, timetable.profile.enrollment) }
    val inTimetable = remember(timetable) {
        timetable.recurring.map { it.subjectId }.toSet() +
            timetable.profile.extraSlots.mapNotNull { dataset.activitiesById[it.activityId]?.subjectId }
    }
    val collator = remember(language) { Collator.getInstance(Locale.getDefault()) }
    val subjects = remember(dataset, query, showAll, home, language) {
        dataset.subjects
            .filter { showAll || home == null || (it.programmeId to it.year) == home }
            .filter { it.matches(query) }
            .sortedWith(compareBy(collator) { it.name.get(language) })
    }

    Column(Modifier.fillMaxSize()) {
        BrandBar { actions() }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(EtIcons.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.onb_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (home != null) {
            Row(
                Modifier.fillMaxWidth().clickable { showAll = !showAll }.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.subjects_show_all),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = showAll, onCheckedChange = { showAll = it })
            }
        }
        HorizontalDivider(Modifier.padding(top = 4.dp))
        if (subjects.isEmpty()) {
            Text(
                stringResource(R.string.subjects_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(subjects, key = { it.id }) { subject ->
                SubjectRow(dataset, subject, subject.id in inTimetable) { open(subject.id) }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SubjectRow(dataset: Dataset, subject: Subject, mine: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(10.dp).background(dataset.subjectColor(subject.id), RoundedCornerShape(3.dp)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(subject.name.get(appLanguage()), style = MaterialTheme.typography.titleMedium)
            Text(
                subjectSubtitle(dataset, subject),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (mine) {
            Text(
                stringResource(R.string.subject_in_timetable),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Icon(
            EtIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** "IEDEEE · Year 4" */
@Composable
fun subjectSubtitle(dataset: Dataset, subject: Subject): String = listOfNotNull(
    dataset.programmes.firstOrNull { it.id == subject.programmeId }?.code,
    stringResource(R.string.onb_year_n, subject.year),
).joinToString(" · ")

private fun Subject.matches(query: String): Boolean {
    val q = query.trim()
    return q.isEmpty() || listOf(name.ro, name.en, code).any { it.contains(q, ignoreCase = true) }
}

/** The programme and year of a national student's group; null for Erasmus students. */
private fun homeProgrammeYear(dataset: Dataset, enrollment: Enrollment?): Pair<String, Int>? {
    val national = enrollment as? Enrollment.National ?: return null
    val group = dataset.groupsById[national.groupId] ?: return null
    val series = dataset.series.firstOrNull { it.id == group.seriesId } ?: return null
    return series.programmeId to series.year
}
