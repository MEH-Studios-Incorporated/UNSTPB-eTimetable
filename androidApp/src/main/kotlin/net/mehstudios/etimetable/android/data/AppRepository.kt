// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.data

import android.util.Log
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.mehstudios.etimetable.api.ApiClient
import net.mehstudios.etimetable.api.ErrorCodes
import net.mehstudios.etimetable.api.LoginRequest
import net.mehstudios.etimetable.api.PushPlatform
import net.mehstudios.etimetable.api.SignupRequest
import net.mehstudios.etimetable.api.TimetableReportRequest
import net.mehstudios.etimetable.api.decodeApiError
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Profile
import net.mehstudios.etimetable.store.LocalStore
import net.mehstudios.etimetable.store.StoredAnnouncement
import net.mehstudios.etimetable.sync.DatasetUpdate
import net.mehstudios.etimetable.sync.Sync
import kotlin.time.Instant

data class AppState(
    val loaded: Boolean = false,
    val signedIn: Boolean = false,
    val email: String? = null,
    val dataset: Dataset? = null,
    val checkedAt: Instant? = null,
    val noSemester: Boolean = false,
    val profile: Profile? = null,
    val profileUnsent: Boolean = false,
    val announcements: List<StoredAnnouncement> = emptyList(),
    val syncing: Boolean = false,
    val lastSyncFailed: Boolean = false,
)

sealed interface ActionResult {
    data object Done : ActionResult

    /** Saved on the phone; it reaches the server at the next sync. */
    data object SavedOffline : ActionResult

    /** [code] is one of [ErrorCodes]; [details] as in the server's error body. */
    data class Refused(val code: String, val details: List<String> = emptyList()) : ActionResult

    data object NoConnection : ActionResult
}

/** The app's state: the offline copy plus the session, and every change to them. */
class AppRepository(
    private val store: LocalStore,
    private val api: ApiClient,
    private val sync: Sync,
    private val session: SessionStore,
    /** This phone's push token, or null when the build has no push. */
    private val pushToken: suspend () -> String? = { null },
    /** The announcement notifications in the status bar. */
    private val notifications: PostedNotifications = PostedNotifications.None,
    /** Called after every change to what the app shows, so the widgets can follow. */
    private val changed: () -> Unit = {},
) {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()
    private val syncLock = Mutex()

    // true once per process: a new process means the app was closed in between; Firebase's
    // thread reads it in signedIn()
    @Volatile
    private var started = false

    suspend fun load() {
        if (!started) {
            started = true
            val token = session.token()
            if (token != null && !session.remember()) {
                withContext(Dispatchers.IO) { signOutLocally(endOnServer = token) }
            }
        }
        val token = session.token()
        readStore(signedIn = token != null, email = session.email())
    }

    /** Downloads what changed. Returns false when the server could not be reached. */
    suspend fun refresh(): Boolean = syncLock.withLock {
        _state.update { it.copy(syncing = true) }
        try {
            syncOnce()
        } finally {
            // a sync cancelled halfway (WorkManager replacing its job) must not stay "syncing"
            _state.update { it.copy(syncing = false) }
        }
    }

    private suspend fun syncOnce(): Boolean {
        val ok = withContext(Dispatchers.IO) {
            try {
                endSessionOnServer()
                val update = sync.refreshDataset()
                _state.update { it.copy(noSemester = update == DatasetUpdate.NO_SEMESTER) }
                sync.refreshAnnouncements()
                session.token()?.let { token ->
                    try {
                        sync.syncProfile(token)
                    } catch (e: ResponseException) {
                        // a refused offline change stays marked as unsent; the session may also have ended
                        if (e.response.status.value == 401) signOutLocally()
                        Log.w(TAG, "Profile sync refused", e)
                    }
                }
                registerPush()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Sync failed", e)
                false
            }
        }
        readStore(signedIn = session.token() != null, email = session.email())
        _state.update { it.copy(lastSyncFailed = !ok) }
        return ok
    }

    suspend fun signIn(email: String, password: String, remember: Boolean): ActionResult =
        authenticate(email, remember) { api.login(LoginRequest(email.trim(), password)).token }

    suspend fun signUp(form: SignupRequest, remember: Boolean): ActionResult =
        authenticate(form.email, remember) { api.signup(form).token }

    private suspend fun authenticate(email: String, remember: Boolean, call: suspend () -> String): ActionResult {
        val result = withContext(Dispatchers.IO) {
            try {
                session.save(call(), email.trim(), remember)
                ActionResult.Done
            } catch (e: ResponseException) {
                e.toRefused()
            } catch (e: Exception) {
                Log.w(TAG, "Sign-in failed", e)
                ActionResult.NoConnection
            }
        }
        if (result == ActionResult.Done) refresh()
        return result
    }

    /** Saves on the phone first, then on the server when it is reachable. */
    suspend fun saveProfile(profile: Profile): ActionResult {
        val token = session.token() ?: return ActionResult.Refused(ErrorCodes.UNAUTHORIZED)
        val result = withContext(Dispatchers.IO) {
            store.saveProfile(profile, dirty = true)
            try {
                store.saveProfile(api.saveProfile(token, profile), dirty = false)
                ActionResult.Done
            } catch (e: ResponseException) {
                // refused: the phone keeps what it had before
                store.saveProfile(_state.value.profile ?: Profile(), dirty = false)
                e.toRefused()
            } catch (e: Exception) {
                ActionResult.SavedOffline
            }
        }
        readStore(signedIn = true, email = session.email())
        return result
    }

    /**
     * Whether a student is signed in on this phone; push messages are shown only then. A
     * session without "remember me" ends when the app closes, which [load] only notices when
     * the app opens again; a push that starts the app in the background counts it as ended.
     */
    suspend fun signedIn(): Boolean = session.token() != null && (started || session.remember())

    /** Tells the server where to send announcements, when it does not have this phone's token yet. */
    suspend fun registerPush() {
        val token = session.token() ?: return
        withContext(Dispatchers.IO) {
            try {
                val push = pushToken() ?: return@withContext
                if (push == session.pushToken()) return@withContext
                api.registerPushToken(token, push, PushPlatform.ANDROID)
                session.savePushToken(push)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // the next sync tries again
                Log.w(TAG, "Push registration failed", e)
            }
        }
    }

    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            val token = session.token()
            val ended = token != null &&
                run {
                    // so this phone stops getting announcements for an account that left it
                    session.pushToken()?.let { push ->
                        runCatching { api.removePushToken(token, push) }
                    }
                    runCatching { api.logout(token) }.isSuccess
                }
            // offline, the next sync ends the session, which also removes the push token
            signOutLocally(endOnServer = if (ended) null else token)
        }
        readStore(signedIn = false, email = null)
    }

    /** Reports need a server receipt; a failed submission stays in the form for retry. */
    suspend fun reportTimetableProblem(request: TimetableReportRequest): ActionResult {
        val token = session.token() ?: return ActionResult.Refused(ErrorCodes.UNAUTHORIZED)
        return withContext(Dispatchers.IO) {
            try {
                api.reportTimetableProblem(token, request)
                ActionResult.Done
            } catch (e: CancellationException) {
                throw e
            } catch (e: ResponseException) {
                if (e.response.status.value == 401) ActionResult.Refused(ErrorCodes.UNAUTHORIZED) else e.toRefused()
            } catch (e: Exception) {
                ActionResult.NoConnection
            }
        }
    }

    suspend fun markRead(announcementId: String) {
        notifications.cancel(announcementId)
        withContext(Dispatchers.IO) { store.markRead(announcementId) }
        readStore(signedIn = _state.value.signedIn, email = _state.value.email)
    }

    suspend fun markAllRead() {
        notifications.cancelAll()
        withContext(Dispatchers.IO) { store.markAllRead() }
        readStore(signedIn = _state.value.signedIn, email = _state.value.email)
    }

    /** Ends a session left over from signing out offline; tried again on every sync until it works. */
    private suspend fun endSessionOnServer() {
        val ended = session.endedSession() ?: return
        try {
            api.logout(ended)
            session.forgetEndedSession()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ResponseException) {
            // 401: the server has already ended it
            if (e.response.status.value == 401) session.forgetEndedSession()
        } catch (e: Exception) {
            Log.w(TAG, "Ending the old session failed", e)
        }
    }

    /** [endOnServer] is a session the server still has, ended at the next sync. */
    private suspend fun signOutLocally(endOnServer: String? = null) {
        notifications.cancelAll()
        session.clear(endOnServer)
        store.clearAccount()
    }

    private suspend fun readStore(signedIn: Boolean, email: String?) {
        val (cached, profile, announcements) = withContext(Dispatchers.IO) {
            Triple(store.dataset(), store.profile(), store.announcements())
        }
        _state.update {
            it.copy(
                loaded = true,
                signedIn = signedIn,
                email = email,
                dataset = cached?.dataset,
                checkedAt = cached?.checkedAt,
                profile = profile?.profile,
                profileUnsent = profile?.dirty == true,
                announcements = announcements,
            )
        }
        changed()
    }

    private suspend fun ResponseException.toRefused(): ActionResult.Refused {
        val error = decodeApiError(response.bodyAsText())
        return ActionResult.Refused(error?.code ?: ErrorCodes.INTERNAL, error?.details.orEmpty())
    }

    private companion object {
        const val TAG = "eTimetable"
    }
}
