// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.push

import net.mehstudios.etimetable.api.PushPlatform
import net.mehstudios.etimetable.server.accounts.sessionHash
import net.mehstudios.etimetable.server.db.query
import net.mehstudios.etimetable.server.db.transaction
import net.mehstudios.etimetable.server.db.update
import java.util.UUID
import javax.sql.DataSource

/** Most phones one student receives announcements on; registering another drops the oldest. */
const val MAX_PUSH_TOKENS_PER_STUDENT = 10

/** A phone that receives announcements. */
data class PushDevice(val token: String, val platform: PushPlatform)

/**
 * The phones registered for push, one row per token. Each token hangs on the session that
 * registered it, so signing out, or an admin ending the sessions, stops the announcements.
 */
class PushTokens(private val db: DataSource) {
    /**
     * Registers [token] for the student of [session], taking it over from another session if
     * needed. The student row is locked, so parallel requests cannot pass the limit.
     */
    suspend fun register(studentId: UUID, session: String, token: String, platform: PushPlatform) {
        db.transaction { c ->
            c.query("SELECT 1 FROM student WHERE id = ? FOR UPDATE", studentId) { it.getInt(1) }
            c.update(
                """
                INSERT INTO push_token (token, session_token_hash, platform) VALUES (?, ?, ?)
                ON CONFLICT (token) DO UPDATE
                SET session_token_hash = excluded.session_token_hash,
                    platform = excluded.platform, updated_at = now()
                """.trimIndent(),
                token,
                sessionHash(session),
                platform.column,
            )
            c.update(
                """
                DELETE FROM push_token WHERE token IN (
                    SELECT p.token FROM push_token p
                    JOIN student_session s ON s.token_hash = p.session_token_hash
                    WHERE s.student_id = ?
                    ORDER BY p.updated_at DESC, p.token
                    OFFSET ?
                )
                """.trimIndent(),
                studentId,
                MAX_PUSH_TOKENS_PER_STUDENT,
            )
        }
    }

    /** Removes [token] if one of [studentId]'s sessions has it; another student's token stays. */
    suspend fun remove(studentId: UUID, token: String) {
        db.transaction { c ->
            c.update(
                """
                DELETE FROM push_token WHERE token = ? AND session_token_hash IN (
                    SELECT token_hash FROM student_session WHERE student_id = ?
                )
                """.trimIndent(),
                token,
                studentId,
            )
        }
    }

    /** Removes tokens the gateway no longer knows, such as an uninstalled app's. */
    suspend fun forget(tokens: Collection<String>) {
        if (tokens.isEmpty()) return
        db.transaction { c ->
            tokens.forEach { c.update("DELETE FROM push_token WHERE token = ?", it) }
        }
    }

    suspend fun all(): List<PushDevice> = db.transaction { c ->
        c.query("SELECT token, platform FROM push_token") {
            PushDevice(it.getString(1), platformOf(it.getString(2)))
        }
    }

    suspend fun of(studentId: UUID): List<PushDevice> = db.transaction { c ->
        c.query(
            """
            SELECT p.token, p.platform FROM push_token p
            JOIN student_session s ON s.token_hash = p.session_token_hash
            WHERE s.student_id = ?
            """.trimIndent(),
            studentId,
        ) {
            PushDevice(it.getString(1), platformOf(it.getString(2)))
        }
    }

    private val PushPlatform.column get() = name.lowercase()

    private fun platformOf(column: String) = PushPlatform.valueOf(column.uppercase())
}
