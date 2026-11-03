// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import net.mehstudios.etimetable.android.data.StartView
import net.mehstudios.etimetable.android.data.ThemeMode
import net.mehstudios.etimetable.android.data.TimetableLayout
import net.mehstudios.etimetable.android.ui.components.MehCredit
import net.mehstudios.etimetable.android.ui.components.PageBar
import net.mehstudios.etimetable.android.ui.components.longLabel
import net.mehstudios.etimetable.android.ui.components.pickedLanguage
import net.mehstudios.etimetable.android.ui.components.rememberLanguageSwitch
import net.mehstudios.etimetable.android.ui.signin.invertInDark
import net.mehstudios.etimetable.android.ui.theme.LocalDarkTheme
import net.mehstudios.etimetable.android.ui.theme.LocalThemeChoice
import net.mehstudios.etimetable.android.ui.theme.LocalTimetableChoice
import net.mehstudios.etimetable.model.Enrollment
import net.mehstudios.etimetable.schedule.FacultyTimeZone

private const val FAIMA_SITE = "https://faima.upb.ro/"

/** Where the profile is: its menu, the settings, or about the app. */
private enum class ProfilePage { MENU, SETTINGS, ABOUT }

@Composable
fun ProfileScreen(
    state: AppState,
    onBack: () -> Unit,
    onChangeEnrollment: () -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(ProfilePage.MENU) }
    BackHandler(enabled = page != ProfilePage.MENU) { page = ProfilePage.MENU }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val deeper = targetState != ProfilePage.MENU
            (slideInHorizontally(tween(250)) { if (deeper) it / 3 else -it / 3 } + fadeIn(tween(250))) togetherWith
                (slideOutHorizontally(tween(200)) { if (deeper) -it / 3 else it / 3 } + fadeOut(tween(150)))
        },
        label = "profile",
    ) { current ->
        when (current) {
            ProfilePage.MENU -> ProfileMenu(state, onBack, onChangeEnrollment, onSyncNow, onSignOut) { page = it }
            ProfilePage.SETTINGS -> SettingsPage { page = ProfilePage.MENU }
            ProfilePage.ABOUT -> AboutPage { page = ProfilePage.MENU }
        }
    }
}

@Composable
private fun ProfileMenu(
    state: AppState,
    onBack: () -> Unit,
    onChangeEnrollment: () -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    open: (ProfilePage) -> Unit,
) {
    val profile = state.profile
    Column(Modifier.fillMaxSize()) {
        PageBar(stringResource(R.string.profile), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val initials = listOfNotNull(profile?.firstName?.firstOrNull(), profile?.lastName?.firstOrNull())
                    .ifEmpty { listOfNotNull(state.email?.firstOrNull()) }.joinToString("").uppercase()
                Box(
                    Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(initials, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                }
                Column {
                    Text(
                        profile?.fullName?.ifBlank { null } ?: stringResource(R.string.profile),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    state.email?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    profile?.phone?.ifBlank { null }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            MenuGroup(stringResource(R.string.menu_studies)) {
                MenuRow(
                    stringResource(R.string.change_enrollment),
                    first = true,
                    value = enrollmentText(state),
                    onClick = onChangeEnrollment,
                )
                val synced = state.checkedAt?.toLocalDateTime(FacultyTimeZone)
                MenuRow(
                    stringResource(R.string.sync_now),
                    subtitle = when {
                        state.profileUnsent -> stringResource(R.string.unsent_changes)
                        synced == null -> stringResource(R.string.never_synced)
                        else -> stringResource(
                            R.string.last_sync,
                            "${synced.date.longLabel()}, ${synced.time.toString().take(5)}",
                        )
                    },
                    chevron = false,
                    enabled = !state.syncing,
                    onClick = onSyncNow,
                )
            }

            MenuGroup(stringResource(R.string.menu_app)) {
                MenuRow(
                    stringResource(R.string.settings),
                    first = true,
                    subtitle = stringResource(R.string.settings_hint),
                ) { open(ProfilePage.SETTINGS) }
                MenuRow(stringResource(R.string.about)) { open(ProfilePage.ABOUT) }
            }

            MenuGroup(null) {
                MenuRow(
                    stringResource(R.string.sign_out),
                    first = true,
                    danger = true,
                    chevron = false,
                    onClick = onSignOut,
                )
            }
        }
    }
}

@Composable
private fun enrollmentText(state: AppState): String? = when (val e = state.profile?.enrollment) {
    is Enrollment.National -> {
        val dataset = state.dataset
        val half = e.halfGroupId?.let { dataset?.halfGroupsById?.get(it) }
        val group = dataset?.groupsById?.get(e.groupId)?.code ?: e.groupId
        if (half != null) "$group/${half.suffix}" else group
    }
    is Enrollment.Erasmus -> pluralStringResource(R.plurals.enrolled_erasmus, e.activityIds.size, e.activityIds.size)
    null -> null
}

/** Language, theme and how the timetable looks; each row opens its options in a sheet. */
@Composable
private fun SettingsPage(onBack: () -> Unit) {
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val theme = LocalThemeChoice.current
    val timetable = LocalTimetableChoice.current
    val language = pickedLanguage()
    val switchLanguage = rememberLanguageSwitch()

    Column(Modifier.fillMaxSize()) {
        PageBar(stringResource(R.string.settings), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            MenuGroup(stringResource(R.string.settings_general)) {
                MenuRow(stringResource(R.string.language), first = true, value = languageName(language)) {
                    sheet = "language"
                }
                MenuRow(stringResource(R.string.theme), value = themeName(theme.mode)) { sheet = "theme" }
            }
            MenuGroup(stringResource(R.string.timetable_section)) {
                MenuRow(
                    stringResource(R.string.timetable_layout),
                    first = true,
                    subtitle = layoutHint(timetable.prefs.layout),
                    value = layoutName(timetable.prefs.layout),
                ) { sheet = "layout" }
                MenuRow(stringResource(R.string.timetable_opens_on), value = startName(timetable.prefs.start)) {
                    sheet = "start"
                }
            }
        }
    }

    when (sheet) {
        "language" -> ChoiceSheet(
            stringResource(R.string.language),
            listOf(null, "ro", "en"),
            language,
            name = { languageName(it) },
            onDismiss = { sheet = null },
            pick = switchLanguage,
        )
        "theme" -> ChoiceSheet(
            stringResource(R.string.theme),
            ThemeMode.entries,
            theme.mode,
            name = { themeName(it) },
            onDismiss = { sheet = null },
            pick = theme.change,
        )
        "layout" -> ChoiceSheet(
            stringResource(R.string.timetable_layout),
            TimetableLayout.entries,
            timetable.prefs.layout,
            name = { layoutName(it) },
            hint = { layoutHint(it) },
            onDismiss = { sheet = null },
        ) { timetable.change(timetable.prefs.copy(layout = it)) }
        "start" -> ChoiceSheet(
            stringResource(R.string.timetable_opens_on),
            StartView.entries,
            timetable.prefs.start,
            name = { startName(it) },
            onDismiss = { sheet = null },
        ) { timetable.change(timetable.prefs.copy(start = it)) }
    }
}

@Composable
private fun languageName(code: String?) = when (code) {
    null -> stringResource(R.string.language_phone)
    "ro" -> "Română"
    else -> "English"
}

@Composable
private fun themeName(mode: ThemeMode) = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.language_phone
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
private fun layoutName(layout: TimetableLayout) = stringResource(
    when (layout) {
        TimetableLayout.TIMELINE -> R.string.layout_timeline
        TimetableLayout.LIST -> R.string.layout_list
        TimetableLayout.TABLE -> R.string.layout_table
    },
)

@Composable
private fun layoutHint(layout: TimetableLayout) = stringResource(
    when (layout) {
        TimetableLayout.TIMELINE -> R.string.layout_timeline_hint
        TimetableLayout.LIST -> R.string.layout_list_hint
        TimetableLayout.TABLE -> R.string.layout_table_hint
    },
)

@Composable
private fun startName(start: StartView) = stringResource(if (start == StartView.DAY) R.string.day else R.string.week)

/** The app, its privacy note, FAIMA and Multimedia Entertainment Hub. */
@Composable
private fun AboutPage(onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxSize()) {
        PageBar(stringResource(R.string.about), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.faima_seal),
                    contentDescription = stringResource(R.string.faima_seal),
                    colorFilter = if (LocalDarkTheme.current) invertInDark else null,
                    modifier = Modifier.width(96.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            MenuGroup(stringResource(R.string.privacy_title)) {
                Text(
                    stringResource(R.string.privacy_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }

            MenuGroup(null) {
                MenuRow(stringResource(R.string.faima_site), first = true, value = "faima.upb.ro") {
                    uri.openUri(FAIMA_SITE)
                }
            }

            MenuGroup(null) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MehCredit()
                        Text(
                            stringResource(R.string.commissioned),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
