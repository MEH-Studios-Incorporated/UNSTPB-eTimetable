// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.data

import android.util.Log
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
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
import net.mehstudios.etimetable.api.SignupRequest
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
) {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()
    private val syncLock = Mutex()

    // true once per process: a new process means the app was closed in between
    private var started = false

    suspend fun load() {
        if (!started) {
            started = true
            if (session.token() != null && !session.remember()) withContext(Dispatchers.IO) { signOutLocally() }
        }
        val token = session.token()
        readStore(signedIn = token != null, email = session.email())
    }

    /** Downloads what changed. Returns false when the server could not be reached. */
    suspend fun refresh(): Boolean = syncLock.withLock {
        _state.update { it.copy(syncing = true) }
        val ok = withContext(Dispatchers.IO) {
            try {
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
                true
            } catch (e: Exception) {
                Log.w(TAG, "Sync failed", e)
                false
            }
        }
        readStore(signedIn = session.token() != null, email = session.email())
        _state.update { it.copy(syncing = false, lastSyncFailed = !ok) }
        ok
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

    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            session.token()?.let { token -> runCatching { api.logout(token) } }
            signOutLocally()
        }
        readStore(signedIn = false, email = null)
    }

    suspend fun markRead(announcementId: String) {
        withContext(Dispatchers.IO) { store.markRead(announcementId) }
        readStore(signedIn = _state.value.signedIn, email = _state.value.email)
    }

    private suspend fun signOutLocally() {
        session.clear()
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
    }

    private suspend fun ResponseException.toRefused(): ActionResult.Refused {
        val error = decodeApiError(response.bodyAsText())
        return ActionResult.Refused(error?.code ?: ErrorCodes.INTERNAL, error?.details.orEmpty())
    }

    private companion object {
        const val TAG = "eTimetable"
    }
}
