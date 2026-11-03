// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.datetime.toLocalDateTime
import net.mehstudios.etimetable.android.BuildConfig
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.data.AppState
import net.mehstudios.etimetable.android.data.StartView
import net.mehstudios.etimetable.android.data.TimetableLayout
import net.mehstudios.etimetable.android.ui.components.Choices
import net.mehstudios.etimetable.android.ui.components.LanguagePicker
import net.mehstudios.etimetable.android.ui.components.LinkText
import net.mehstudios.etimetable.android.ui.components.MehCredit
import net.mehstudios.etimetable.android.ui.components.PageBar
import net.mehstudios.etimetable.android.ui.components.ThemePicker
import net.mehstudios.etimetable.android.ui.components.longLabel
import net.mehstudios.etimetable.android.ui.signin.invertInDark
import net.mehstudios.etimetable.android.ui.theme.ButtonShape
import net.mehstudios.etimetable.android.ui.theme.LocalDarkTheme
import net.mehstudios.etimetable.android.ui.theme.LocalTimetableChoice
import net.mehstudios.etimetable.model.Enrollment
import net.mehstudios.etimetable.schedule.FacultyTimeZone

private const val FAIMA_SITE = "https://faima.upb.ro/"

@Composable
fun ProfileScreen(
    state: AppState,
    onBack: () -> Unit,
    onChangeEnrollment: () -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
) {
    val uri = LocalUriHandler.current
    val dark = LocalDarkTheme.current
    val profile = state.profile
    Column(Modifier.fillMaxSize()) {
        PageBar(stringResource(R.string.profile), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    profile?.fullName?.ifBlank {
                        null
                    } ?: stringResource(R.string.profile),
                    style = MaterialTheme.typography.headlineSmall,
                )
                state.email?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                profile?.phone?.ifBlank { null }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                val enrollment = when (val e = profile?.enrollment) {
                    is Enrollment.National -> {
                        val dataset = state.dataset
                        val half = e.halfGroupId?.let { dataset?.halfGroupsById?.get(it) }
                        val group = dataset?.groupsById?.get(e.groupId)?.code ?: e.groupId
                        stringResource(R.string.enrolled_group, if (half != null) "$group/${half.suffix}" else group)
                    }
                    is Enrollment.Erasmus -> pluralStringResource(
                        R.plurals.enrolled_erasmus,
                        e.activityIds.size,
                        e.activityIds.size,
                    )
                    null -> null
                }
                enrollment?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                }
                OutlinedButton(shape = ButtonShape, onClick = onChangeEnrollment) {
                    Text(stringResource(R.string.change_enrollment))
                }
                if (state.profileUnsent) {
                    Text(
                        stringResource(R.string.unsent_changes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val synced = state.checkedAt?.toLocalDateTime(FacultyTimeZone)
                Text(
                    if (synced == null) {
                        stringResource(R.string.never_synced)
                    } else {
                        stringResource(
                            R.string.last_sync,
                            "${synced.date.longLabel()}, ${synced.time.toString().take(5)}",
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(shape = ButtonShape, onClick = onSyncNow, enabled = !state.syncing) {
                        Text(stringResource(R.string.sync_now))
                    }
                    OutlinedButton(shape = ButtonShape, onClick = onSignOut) { Text(stringResource(R.string.sign_out)) }
                }
            }

            HorizontalDivider(Modifier.padding(16.dp))

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
                LanguagePicker(Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.theme),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                ThemePicker(Modifier.fillMaxWidth())
            }

            HorizontalDivider(Modifier.padding(16.dp))

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val choice = LocalTimetableChoice.current
                Text(stringResource(R.string.timetable_section), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.timetable_layout), style = MaterialTheme.typography.labelLarge)
                Choices(
                    TimetableLayout.entries,
                    choice.prefs.layout,
                    names = {
                        stringResource(
                            when (it) {
                                TimetableLayout.TIMELINE -> R.string.layout_timeline
                                TimetableLayout.LIST -> R.string.layout_list
                                TimetableLayout.TABLE -> R.string.layout_table
                            },
                        )
                    },
                    Modifier.fillMaxWidth(),
                ) { choice.change(choice.prefs.copy(layout = it)) }
                Text(
                    stringResource(
                        when (choice.prefs.layout) {
                            TimetableLayout.TIMELINE -> R.string.layout_timeline_hint
                            TimetableLayout.LIST -> R.string.layout_list_hint
                            TimetableLayout.TABLE -> R.string.layout_table_hint
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.timetable_opens_on),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Choices(
                    StartView.entries,
                    choice.prefs.start,
                    names = { stringResource(if (it == StartView.DAY) R.string.day else R.string.week) },
                    Modifier.fillMaxWidth(),
                ) { choice.change(choice.prefs.copy(start = it)) }
            }

            HorizontalDivider(Modifier.padding(16.dp))

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.about), style = MaterialTheme.typography.titleMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.faima_seal),
                        contentDescription = stringResource(R.string.faima_seal),
                        colorFilter = if (dark) invertInDark else null,
                        modifier = Modifier.width(72.dp),
                    )
                    Column {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.version, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(R.string.privacy_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinkText(stringResource(R.string.faima_site)) { uri.openUri(FAIMA_SITE) }

                HorizontalDivider(Modifier.padding(vertical = 6.dp))

                MehCredit()
                Text(
                    stringResource(R.string.commissioned),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 32.dp),
                )
            }
        }
    }
}
