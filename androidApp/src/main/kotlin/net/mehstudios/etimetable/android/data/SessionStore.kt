// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionData by preferencesDataStore("session")
private val TOKEN = stringPreferencesKey("token")
private val EMAIL = stringPreferencesKey("email")
private val REMEMBER = booleanPreferencesKey("remember")
private val PUSH_TOKEN = stringPreferencesKey("push_token")
private val ENDED_SESSION = stringPreferencesKey("ended_session")

/** The student's session token, in the app's private storage. */
class SessionStore(private val context: Context) {
    suspend fun token(): String? = context.sessionData.data.map { it[TOKEN] }.first()

    suspend fun email(): String? = context.sessionData.data.map { it[EMAIL] }.first()

    /** Whether the student ticked "remember me"; otherwise the session ends when the app is closed. */
    suspend fun remember(): Boolean = context.sessionData.data.map { it[REMEMBER] ?: false }.first()

    suspend fun save(token: String, email: String, remember: Boolean) {
        context.sessionData.edit {
            it[TOKEN] = token
            it[EMAIL] = email
            it[REMEMBER] = remember
        }
    }

    /** The push token the server has for this session, so it is sent only when it changes. */
    suspend fun pushToken(): String? = context.sessionData.data.map { it[PUSH_TOKEN] }.first()

    suspend fun savePushToken(token: String) {
        context.sessionData.edit { it[PUSH_TOKEN] = token }
    }

    /**
     * A session that ended on the phone but not yet on the server, as after signing out
     * offline. Ending it there also removes this phone's push token.
     */
    suspend fun endedSession(): String? = context.sessionData.data.map { it[ENDED_SESSION] }.first()

    suspend fun forgetEndedSession() {
        context.sessionData.edit { it.remove(ENDED_SESSION) }
    }

    /** Forgets the session; [ended] is kept for [endedSession] when the server still has it. */
    suspend fun clear(ended: String? = null) {
        context.sessionData.edit {
            it.clear()
            if (ended != null) it[ENDED_SESSION] = ended
        }
    }
}
