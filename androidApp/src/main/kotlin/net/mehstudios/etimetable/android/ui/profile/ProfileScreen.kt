// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.toLocalDateTime
import net.mehstudios.etimetable.android.BuildConfig
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.data.AppState
import net.mehstudios.etimetable.android.ui.components.longLabel
import net.mehstudios.etimetable.android.ui.signin.invertInDark
import net.mehstudios.etimetable.android.ui.theme.ButtonShape
import net.mehstudios.etimetable.model.Enrollment
import net.mehstudios.etimetable.schedule.FacultyTimeZone

private const val MEH_SITE = "https://mehstudios.net/"
private const val SUPPORT_EMAIL = "support@mehstudios.net"
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
    val dark = isSystemInDarkTheme()
    val profile = state.profile
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TextButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) {
            Text(stringResource(R.string.back))
        }
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
                    stringResource(R.string.last_sync, "${synced.date.longLabel()}, ${synced.time.toString().take(5)}")
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
            Text(stringResource(R.string.about), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
            Link(stringResource(R.string.faima_site)) { uri.openUri(FAIMA_SITE) }

            HorizontalDivider(Modifier.padding(vertical = 6.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Image(
                    painter = painterResource(R.drawable.meh_mark),
                    contentDescription = null,
                    colorFilter = if (dark) null else invertInDark,
                    modifier = Modifier.size(34.dp),
                )
                Column {
                    Text(
                        stringResource(R.string.copyright_owner),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        stringResource(R.string.copyright_rights),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Link(stringResource(R.string.meh_site)) { uri.openUri(MEH_SITE) }
            Link(stringResource(R.string.support)) { uri.openUri("mailto:$SUPPORT_EMAIL") }
            Text(
                stringResource(R.string.commissioned),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 32.dp),
            )
        }
    }
}

@Composable
private fun Link(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
    )
}
