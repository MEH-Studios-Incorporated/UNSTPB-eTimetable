// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionData by preferencesDataStore("session")
private val TOKEN = stringPreferencesKey("token")
private val EMAIL = stringPreferencesKey("email")

/** The student's session token, in the app's private storage. */
class SessionStore(private val context: Context) {
    suspend fun token(): String? = context.sessionData.data.map { it[TOKEN] }.first()

    suspend fun email(): String? = context.sessionData.data.map { it[EMAIL] }.first()

    suspend fun save(token: String, email: String) {
        context.sessionData.edit {
            it[TOKEN] = token
            it[EMAIL] = email
        }
    }

    suspend fun clear() {
        context.sessionData.edit { it.clear() }
    }
}
