// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.data.ActionResult
import net.mehstudios.etimetable.android.data.AppContainer
import net.mehstudios.etimetable.android.data.AppState
import net.mehstudios.etimetable.android.ui.announcements.AnnouncementScreen
import net.mehstudios.etimetable.android.ui.announcements.AnnouncementsScreen
import net.mehstudios.etimetable.android.ui.components.EtIcons
import net.mehstudios.etimetable.android.ui.components.OfflineBar
import net.mehstudios.etimetable.android.ui.components.WeekBadge
import net.mehstudios.etimetable.android.ui.components.appLanguage
import net.mehstudios.etimetable.android.ui.onboarding.OnboardingScreen
import net.mehstudios.etimetable.android.ui.professors.ProfessorScreen
import net.mehstudios.etimetable.android.ui.profile.ProfileScreen
import net.mehstudios.etimetable.android.ui.signin.SignInScreen
import net.mehstudios.etimetable.android.ui.theme.ButtonShape
import net.mehstudios.etimetable.android.ui.timetable.TimetableScreen
import net.mehstudios.etimetable.schedule.StudentTimetable
import net.mehstudios.etimetable.schedule.facultyNow
import kotlin.time.Clock

private enum class Phase { LOADING, SIGN_IN, WAITING, ONBOARDING, MAIN }

private enum class Tab { TIMETABLE, ANNOUNCEMENTS }

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
    // the server keeps the student's language for notifications; follow the app's
    val language = appLanguage()
    LaunchedEffect(state.profile, language) {
        val profile = state.profile ?: return@LaunchedEffect
        if (state.signedIn && profile.enrollment != null && profile.language != language) {
            repository.saveProfile(profile.copy(language = language))
        }
    }

    val phase = when {
        !state.loaded -> Phase.LOADING
        !state.signedIn -> Phase.SIGN_IN
        state.dataset == null -> Phase.WAITING
        state.profile?.enrollment == null || editingEnrollment -> Phase.ONBOARDING
        else -> Phase.MAIN
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = phase,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
            label = "phase",
        ) { current ->
            when (current) {
                Phase.LOADING -> Box(Modifier.fillMaxSize())
                Phase.SIGN_IN -> SignInScreen(signIn = repository::signIn, signUp = repository::signUp)
                Phase.WAITING -> WaitingForData(state, online, retry = {
                    scope.launch { repository.refresh() }
                }, signOut = { scope.launch { repository.signOut() } })
                Phase.ONBOARDING -> state.dataset?.let { dataset ->
                    OnboardingScreen(
                        dataset = dataset,
                        profile = state.profile,
                        save = { profile ->
                            repository.saveProfile(profile).also {
                                if (it == ActionResult.Done ||
                                    it == ActionResult.SavedOffline
                                ) {
                                    editingEnrollment = false
                                }
                            }
                        },
                        onCancel = if (state.profile?.enrollment != null) ({ editingEnrollment = false }) else null,
                    )
                }
                Phase.MAIN -> MainScreen(
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

/** Where the main screen is: a tab, or the profile, an announcement or a professor over it. */
private data class Place(
    val tab: Tab,
    val profile: Boolean = false,
    val announcementId: String? = null,
    val professorId: String? = null,
) {
    val overlay get() = profile || announcementId != null || professorId != null
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
    val profile = state.profile ?: return
    var tab by rememberSaveable { mutableStateOf(Tab.TIMETABLE) }
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var announcementId by rememberSaveable { mutableStateOf<String?>(null) }
    var professorId by rememberSaveable { mutableStateOf<String?>(null) }
    val place = Place(tab, showProfile, announcementId, professorId)
    val now by produceState(Clock.System.facultyNow()) {
        while (true) {
            delay(30_000)
            value = Clock.System.facultyNow()
        }
    }
    val timetable = remember(dataset, profile) { StudentTimetable(dataset, profile) }
    val unread = state.announcements.count { !it.read }
    val actions: @Composable () -> Unit = {
        WeekBadge(timetable.calendar, now.date)
        Avatar(initials(state)) { showProfile = true }
    }

    BackHandler(enabled = place.overlay) {
        when {
            professorId != null -> professorId = null
            announcementId != null -> announcementId = null
            else -> showProfile = false
        }
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = !online,
                enter = slideInVertically() + fadeIn(),
                exit =
                slideOutVertically() + fadeOut(),
            ) { OfflineBar(state.checkedAt) }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = !place.overlay,
                enter = slideInVertically { it } + fadeIn(),
                exit =
                slideOutVertically { it } + fadeOut(),
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = tab == Tab.TIMETABLE,
                        onClick = { tab = Tab.TIMETABLE },
                        icon = { Icon(EtIcons.Calendar, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_timetable)) },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.ANNOUNCEMENTS,
                        onClick = { tab = Tab.ANNOUNCEMENTS },
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
        AnimatedContent(
            targetState = place,
            transitionSpec = {
                when {
                    // opening a page over a tab slides it in from the right, going back the other way
                    targetState.overlay && !initialState.overlay ->
                        (slideInHorizontally(tween(250)) { it / 3 } + fadeIn(tween(250))) togetherWith
                            fadeOut(tween(150))
                    !targetState.overlay && initialState.overlay ->
                        fadeIn(tween(200)) togetherWith
                            (slideOutHorizontally(tween(250)) { it / 3 } + fadeOut(tween(200)))
                    else -> fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                }
            },
            label = "place",
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { target ->
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val opened = target.announcementId?.let { id ->
                    state.announcements.firstOrNull {
                        it.announcement.id ==
                            id
                    }
                }
                when {
                    target.professorId != null -> ProfessorScreen(
                        dataset,
                        timetable.calendar,
                        target.professorId,
                        now,
                        onBack = { professorId = null },
                    )
                    opened != null -> AnnouncementScreen(opened.announcement, markRead = {
                        markRead(opened.announcement.id)
                    }, onBack = {
                        announcementId =
                            null
                    })
                    target.profile -> ProfileScreen(state, onBack = {
                        showProfile = false
                    }, onChangeEnrollment, onSyncNow, onSignOut)
                    target.tab == Tab.TIMETABLE -> TimetableScreen(dataset, timetable, now, actions) {
                        professorId = it
                    }
                    else -> AnnouncementsScreen(state.announcements, actions) { announcementId = it }
                }
            }
        }
    }
}

@Composable
private fun Avatar(initials: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(initials, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

private fun initials(state: AppState): String {
    val profile = state.profile
    val letters = listOfNotNull(profile?.firstName?.firstOrNull(), profile?.lastName?.firstOrNull())
        .ifEmpty { listOfNotNull(state.email?.firstOrNull()) }
    return letters.joinToString("").uppercase()
}
