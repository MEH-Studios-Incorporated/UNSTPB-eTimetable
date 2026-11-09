// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui

import android.os.Build
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
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.data.ActionResult
import net.mehstudios.etimetable.android.data.AnnouncementAlert
import net.mehstudios.etimetable.android.data.AppContainer
import net.mehstudios.etimetable.android.data.AppState
import net.mehstudios.etimetable.android.data.Push
import net.mehstudios.etimetable.android.ui.announcements.AnnouncementScreen
import net.mehstudios.etimetable.android.ui.announcements.AnnouncementsScreen
import net.mehstudios.etimetable.android.ui.components.AnnouncementPopup
import net.mehstudios.etimetable.android.ui.components.EtIcons
import net.mehstudios.etimetable.android.ui.components.OfflineBar
import net.mehstudios.etimetable.android.ui.components.WeekBadge
import net.mehstudios.etimetable.android.ui.components.appLanguage
import net.mehstudios.etimetable.android.ui.onboarding.NotificationsStep
import net.mehstudios.etimetable.android.ui.onboarding.OnboardingScreen
import net.mehstudios.etimetable.android.ui.professors.ProfessorScreen
import net.mehstudios.etimetable.android.ui.professors.ProfessorsScreen
import net.mehstudios.etimetable.android.ui.profile.ProfileScreen
import net.mehstudios.etimetable.android.ui.signin.SignInScreen
import net.mehstudios.etimetable.android.ui.subjects.SubjectScreen
import net.mehstudios.etimetable.android.ui.subjects.SubjectsScreen
import net.mehstudios.etimetable.android.ui.theme.ButtonShape
import net.mehstudios.etimetable.android.ui.timetable.TimetableScreen
import net.mehstudios.etimetable.api.TimetableReportRequest
import net.mehstudios.etimetable.model.Profile
import net.mehstudios.etimetable.schedule.StudentTimetable
import net.mehstudios.etimetable.schedule.facultyNow
import kotlin.time.Clock

private enum class Phase { LOADING, SIGN_IN, WAITING, ONBOARDING, NOTIFICATIONS, MAIN }

private enum class Tab { TIMETABLE, SUBJECTS, PROFESSORS, ANNOUNCEMENTS }

/**
 * Picks the screen from the app state: sign-in, waiting for data, onboarding or the app
 * itself. [openAnnouncement] comes from a tapped notification; [onOpened] says the main
 * screen has taken it.
 */
@Composable
fun EtRoot(container: AppContainer, openAnnouncement: String? = null, onOpened: () -> Unit = {}) {
    val repository = container.repository
    val context = LocalContext.current
    // null until read from the preferences, off the main thread
    var askForPush by remember { mutableStateOf<Boolean?>(null) }
    val state by repository.state.collectAsStateWithLifecycle()
    val online by container.connectivity.online.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editingEnrollment by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        askForPush = withContext(Dispatchers.IO) { Push.shouldAsk(context) }
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
        !state.loaded || askForPush == null -> Phase.LOADING
        !state.signedIn -> Phase.SIGN_IN
        state.dataset == null -> Phase.WAITING
        state.profile?.enrollment == null || editingEnrollment -> Phase.ONBOARDING
        askForPush == true -> Phase.NOTIFICATIONS
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
                // Push.shouldAsk is false below Android 13, which has no such permission
                Phase.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    NotificationsStep {
                        askForPush = false
                        scope.launch {
                            withContext(Dispatchers.IO) { Push.markAsked(context) }
                            // a token Firebase made while the step was open reaches the server now
                            repository.registerPush()
                        }
                    }
                }
                Phase.MAIN -> {
                    // collected only here, so the bar is offered only while this screen shows
                    val alert by container.alerts.current.collectAsStateWithLifecycle()
                    MainScreen(
                        state = state,
                        openAnnouncement = openAnnouncement,
                        onAnnouncementOpened = onOpened,
                        alert = alert,
                        onAlertDone = container.alerts::dismiss,
                        online = online,
                        onChangeEnrollment = { editingEnrollment = true },
                        onSyncNow = { scope.launch { repository.refresh() } },
                        onSignOut = { scope.launch { repository.signOut() } },
                        markRead = { id -> scope.launch { repository.markRead(id) } },
                        markAllRead = { scope.launch { repository.markAllRead() } },
                        saveProfile = repository::saveProfile,
                        reportProblem = repository::reportTimetableProblem,
                    )
                }
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

/** Where the main screen is: a tab, or the profile, an announcement, a subject or a professor over it. */
private data class Place(
    val tab: Tab,
    val profile: Boolean = false,
    val announcementId: String? = null,
    val professorId: String? = null,
    val subjectId: String? = null,
) {
    val overlay get() = profile || announcementId != null || professorId != null || subjectId != null
}

@Composable
private fun MainScreen(
    state: AppState,
    openAnnouncement: String?,
    onAnnouncementOpened: () -> Unit,
    alert: AnnouncementAlert?,
    onAlertDone: (AnnouncementAlert) -> Unit,
    online: Boolean,
    onChangeEnrollment: () -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    markRead: (String) -> Unit,
    markAllRead: () -> Unit,
    saveProfile: suspend (Profile) -> ActionResult,
    reportProblem: suspend (TimetableReportRequest) -> ActionResult,
) {
    val dataset = state.dataset ?: return
    val profile = state.profile ?: return
    var tab by rememberSaveable { mutableStateOf(Tab.TIMETABLE) }
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var announcementId by rememberSaveable { mutableStateOf<String?>(null) }
    // asked for by a notification or the in-app bar before the sync has brought it
    var pendingAnnouncement by rememberSaveable { mutableStateOf<String?>(null) }
    var professorId by rememberSaveable { mutableStateOf<String?>(null) }
    var subjectId by rememberSaveable { mutableStateOf<String?>(null) }
    // only an announcement that is on the phone counts as open, so the tabs and the tab bar
    // never wait under an announcement page that has nothing to show
    val shownAnnouncement = announcementId?.takeIf { id -> state.announcements.any { it.announcement.id == id } }
    val place = Place(tab, showProfile, shownAnnouncement, professorId, subjectId)
    val now by produceState(Clock.System.facultyNow()) {
        while (true) {
            delay(30_000)
            value = Clock.System.facultyNow()
        }
    }
    val timetable = remember(dataset, profile) { StudentTimetable(dataset, profile) }
    // each tab keeps its state (the day shown, day or week, a search) while a page sits over it;
    // the pages over a tab start fresh every time they open
    val tabs = rememberSaveableStateHolder()
    val unread = state.announcements.count { !it.read }
    val actions: @Composable () -> Unit = {
        WeekBadge(timetable.calendar, now.date)
        Avatar(initials(state)) { showProfile = true }
    }

    // the announcements tab, with nothing open over it
    fun showAnnouncements() {
        tab = Tab.ANNOUNCEMENTS
        showProfile = false
        professorId = null
        subjectId = null
        announcementId = null
        pendingAnnouncement = null
    }

    fun showAnnouncement(id: String) {
        showAnnouncements()
        pendingAnnouncement = id
    }

    fun selectTab(selected: Tab) {
        tab = selected
        pendingAnnouncement = null
    }

    // a tapped notification
    LaunchedEffect(openAnnouncement) {
        val id = openAnnouncement ?: return@LaunchedEffect
        showAnnouncement(id)
        onAnnouncementOpened()
    }
    // opens the announcement once it is on the phone; until then its tab shows
    LaunchedEffect(pendingAnnouncement, state.announcements) {
        val id = pendingAnnouncement ?: return@LaunchedEffect
        if (state.announcements.any { it.announcement.id == id }) {
            announcementId = id
            pendingAnnouncement = null
        }
    }

    BackHandler(enabled = place.overlay) {
        when {
            professorId != null -> professorId = null
            subjectId != null -> subjectId = null
            shownAnnouncement != null -> announcementId = null
            else -> showProfile = false
        }
    }

    Scaffold(
        // Scaffold puts this slot just above the tab bar
        snackbarHost = {
            AnnouncementPopup(
                alert = alert,
                onOpen = {
                    onAlertDone(it)
                    if (it.single) showAnnouncement(it.ids.first()) else showAnnouncements()
                },
                onDismiss = onAlertDone,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        },
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
                        onClick = { selectTab(Tab.TIMETABLE) },
                        icon = { Icon(EtIcons.Calendar, contentDescription = null) },
                        label = { TabLabel(stringResource(R.string.tab_timetable)) },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.SUBJECTS,
                        onClick = { selectTab(Tab.SUBJECTS) },
                        icon = { Icon(EtIcons.Book, contentDescription = null) },
                        label = { TabLabel(stringResource(R.string.tab_subjects)) },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.PROFESSORS,
                        onClick = { selectTab(Tab.PROFESSORS) },
                        icon = { Icon(EtIcons.Search, contentDescription = null) },
                        label = { TabLabel(stringResource(R.string.find_teacher)) },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.ANNOUNCEMENTS,
                        onClick = { selectTab(Tab.ANNOUNCEMENTS) },
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
                        label = { TabLabel(stringResource(R.string.tab_announcements)) },
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
                    target.subjectId != null -> SubjectScreen(
                        dataset,
                        timetable,
                        target.subjectId,
                        now,
                        saveProfile,
                        onBack = { subjectId = null },
                    )
                    // an announcement gone from the phone while its page leaves draws nothing:
                    // falling through to the tabs would use their state key twice
                    target.announcementId != null -> if (opened != null) {
                        AnnouncementScreen(opened.announcement, markRead = {
                            markRead(opened.announcement.id)
                        }, onBack = { announcementId = null })
                    }
                    target.profile -> ProfileScreen(state, onBack = {
                        showProfile = false
                    }, onChangeEnrollment, onSyncNow, onSignOut)
                    else -> tabs.SaveableStateProvider(target.tab.name) {
                        when (target.tab) {
                            Tab.TIMETABLE -> TimetableScreen(
                                dataset,
                                timetable,
                                now,
                                actions,
                                openProfessor = { professorId = it },
                                openSubject = { subjectId = it },
                                reportProblem = reportProblem,
                            )
                            Tab.SUBJECTS -> SubjectsScreen(dataset, timetable, actions) { subjectId = it }
                            Tab.PROFESSORS -> ProfessorsScreen(dataset, timetable.calendar, now, actions) {
                                professorId =
                                    it
                            }
                            Tab.ANNOUNCEMENTS -> AnnouncementsScreen(state.announcements, actions, markAllRead) {
                                announcementId = it
                            }
                        }
                    }
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

/**
 * A tab label on one line. It keeps the label size and only shrinks when a long label
 * ("Announcements") does not fit a quarter of a narrow phone.
 */
@Composable
private fun TabLabel(text: String) {
    val size = MaterialTheme.typography.labelMedium.fontSize
    Text(text, maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = size))
}
