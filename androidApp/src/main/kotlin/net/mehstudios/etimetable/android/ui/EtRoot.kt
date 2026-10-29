// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.data.ActionResult
import net.mehstudios.etimetable.android.data.AppContainer
import net.mehstudios.etimetable.android.data.AppState
import net.mehstudios.etimetable.android.ui.announcements.AnnouncementsScreen
import net.mehstudios.etimetable.android.ui.components.EtIcons
import net.mehstudios.etimetable.android.ui.components.OfflineBar
import net.mehstudios.etimetable.android.ui.onboarding.OnboardingScreen
import net.mehstudios.etimetable.android.ui.profile.ProfileScreen
import net.mehstudios.etimetable.android.ui.signin.SignInScreen
import net.mehstudios.etimetable.android.ui.theme.ButtonShape
import net.mehstudios.etimetable.android.ui.timetable.TimetableScreen
import net.mehstudios.etimetable.schedule.StudentTimetable
import net.mehstudios.etimetable.schedule.facultyNow
import kotlin.time.Clock

private enum class Screen { TIMETABLE, ANNOUNCEMENTS, PROFILE }

/** Picks the screen from the app state: sign-in, waiting for data, onboarding or the app itself. */
@Composable
fun EtRoot(container: AppContainer) {
    val repository = container.repository
    val state by repository.state.collectAsStateWithLifecycle()
    val online by container.connectivity.online.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editingEnrollment by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repository.load()
        repository.refresh()
    }
    // coming back online refreshes at once instead of waiting for the next periodic sync
    LaunchedEffect(online) { if (online && state.loaded) repository.refresh() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val dataset = state.dataset
        when {
            !state.loaded -> Unit
            !state.signedIn -> SignInScreen(signIn = repository::signIn, signUp = repository::signUp)
            dataset == null -> WaitingForData(state, online, retry = {
                scope.launch { repository.refresh() }
            }, signOut = { scope.launch { repository.signOut() } })
            state.profile?.enrollment == null || editingEnrollment -> OnboardingScreen(
                dataset = dataset,
                profile = state.profile,
                save = { profile ->
                    repository.saveProfile(profile).also {
                        if (it == ActionResult.Done || it == ActionResult.SavedOffline) editingEnrollment = false
                    }
                },
                onCancel = if (state.profile?.enrollment != null) ({ editingEnrollment = false }) else null,
            )
            else -> MainScreen(
                state = state,
                online = online,
                onChangeEnrollment = { editingEnrollment = true },
                onSyncNow = { scope.launch { repository.refresh() } },
                onSignOut = { scope.launch { repository.signOut() } },
                markRead = { id -> scope.launch { repository.markRead(id) } },
            )
        }
    }
}

@Composable
private fun WaitingForData(state: AppState, online: Boolean, retry: () -> Unit, signOut: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(
                when {
                    state.noSemester -> R.string.no_semester
                    !online || state.lastSyncFailed -> R.string.error_no_connection
                    else -> R.string.offline_never
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(shape = ButtonShape, onClick = retry, enabled = !state.syncing) { Text(stringResource(R.string.retry)) }
        OutlinedButton(shape = ButtonShape, onClick = signOut) { Text(stringResource(R.string.sign_out)) }
    }
}

@Composable
private fun MainScreen(
    state: AppState,
    online: Boolean,
    onChangeEnrollment: () -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    markRead: (String) -> Unit,
) {
    val dataset = state.dataset ?: return
    var screen by rememberSaveable { mutableStateOf(Screen.TIMETABLE) }
    var lastTab by rememberSaveable { mutableStateOf(Screen.TIMETABLE) }
    val now by produceState(Clock.System.facultyNow()) {
        while (true) {
            delay(30_000)
            value = Clock.System.facultyNow()
        }
    }
    val timetable =
        remember(dataset, state.profile) { StudentTimetable(dataset, state.profile ?: return@remember null) } ?: return
    val unread = state.announcements.count { !it.read }

    BackHandler(enabled = screen == Screen.PROFILE) { screen = lastTab }

    Scaffold(
        topBar = { if (!online) OfflineBar(state.checkedAt) },
        bottomBar = {
            if (screen != Screen.PROFILE) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = screen == Screen.TIMETABLE,
                        onClick = { screen = Screen.TIMETABLE },
                        icon = { Icon(EtIcons.Calendar, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_timetable)) },
                    )
                    NavigationBarItem(
                        selected = screen == Screen.ANNOUNCEMENTS,
                        onClick = { screen = Screen.ANNOUNCEMENTS },
                        icon = {
                            BadgedBox(badge = {
                                if (unread >
                                    0
                                ) {
                                    Badge(containerColor = MaterialTheme.colorScheme.error)
                                }
                            }) {
                                Icon(EtIcons.Bell, contentDescription = null)
                            }
                        },
                        label = { Text(stringResource(R.string.tab_announcements)) },
                    )
                }
            }
        },
    ) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding), color = MaterialTheme.colorScheme.background) {
            when (screen) {
                Screen.TIMETABLE -> TimetableScreen(dataset, timetable, now, initials(state)) {
                    lastTab = Screen.TIMETABLE
                    screen = Screen.PROFILE
                }
                Screen.ANNOUNCEMENTS -> AnnouncementsScreen(state.announcements, markRead)
                Screen.PROFILE -> ProfileScreen(state, onBack = {
                    screen = lastTab
                }, onChangeEnrollment, onSyncNow, onSignOut)
            }
        }
    }
}

private fun initials(state: AppState): String {
    val name = state.profile?.displayName?.trim().orEmpty()
    val letters = if (name.isNotEmpty()) {
        name.split(' ').filter {
            it.isNotEmpty()
        }.take(2).map { it.first() }
    } else {
        listOfNotNull(state.email?.firstOrNull())
    }
    return letters.joinToString("").uppercase()
}
