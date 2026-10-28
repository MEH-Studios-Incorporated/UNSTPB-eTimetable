// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.admin

import net.mehstudios.etimetable.api.MIN_PASSWORD_LENGTH
import net.mehstudios.etimetable.server.accounts.Passwords
import net.mehstudios.etimetable.server.db.query
import net.mehstudios.etimetable.server.db.transaction
import net.mehstudios.etimetable.server.db.update
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.SQLException
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

enum class AdminRole { ADMIN, EDITOR }

data class Admin(val id: UUID, val email: String, val role: AdminRole)

/** Thrown with a message meant for the person filling in the form, in Romanian. */
class AdminFormError(message: String) : RuntimeException(message)

class AdminAccounts(private val db: DataSource, private val sessionLength: Duration = 12.hours) {
    private val random = SecureRandom()

    /** Creates the first admin from the environment, only while there is none. */
    suspend fun bootstrap(email: String?, password: String?): Boolean {
        if (email.isNullOrBlank() || password.isNullOrBlank()) return false
        if (list().isNotEmpty()) return false
        create(email, password, AdminRole.ADMIN)
        return true
    }

    suspend fun create(email: String, password: String, role: AdminRole): Admin {
        val clean = email.trim()
        if ('@' !in clean) throw AdminFormError("Adresa de email nu este validă.")
        if (password.length <
            MIN_PASSWORD_LENGTH
        ) {
            throw AdminFormError("Parola trebuie să aibă cel puțin $MIN_PASSWORD_LENGTH caractere.")
        }
        val admin = Admin(UUID.randomUUID(), clean, role)
        db.transaction { c ->
            try {
                c.update(
                    "INSERT INTO admin (id, email, password_hash, role) VALUES (?, ?, ?, ?)",
                    admin.id,
                    clean,
                    Passwords.hash(password),
                    role.name.lowercase(),
                )
            } catch (e: SQLException) {
                if (e.sqlState == "23505") throw AdminFormError("Există deja un cont cu adresa $clean.")
                throw e
            }
        }
        return admin
    }

    suspend fun list(): List<Admin> = db.transaction { c ->
        c.query("SELECT id, email, role FROM admin ORDER BY lower(email)") { it.toAdmin() }
    }

    /** Returns a session token, or null for a wrong email or password. */
    suspend fun login(email: String, password: String): String? = db.transaction { c ->
        val row = c.query("SELECT id, password_hash FROM admin WHERE lower(email) = lower(?)", email.trim()) {
            it.getObject(1, UUID::class.java) to it.getString(2)
        }.singleOrNull()
        if (!Passwords.verify(password, row?.second ?: DUMMY_HASH) || row == null) return@transaction null
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        c.update(
            "INSERT INTO admin_session (token_hash, admin_id, expires_at) VALUES (?, ?, now() + make_interval(secs => ?))",
            sha256(token),
            row.first,
            sessionLength.inWholeSeconds.toDouble(),
        )
        c.update("DELETE FROM admin_session WHERE expires_at < now()")
        token
    }

    suspend fun adminFor(token: String): Admin? = db.transaction { c ->
        c.query(
            """
            SELECT a.id, a.email, a.role FROM admin_session s JOIN admin a ON a.id = s.admin_id
            WHERE s.token_hash = ? AND s.expires_at > now()
            """.trimIndent(),
            sha256(token),
        ) { it.toAdmin() }.singleOrNull()
    }

    suspend fun logout(token: String) {
        db.transaction { c -> c.update("DELETE FROM admin_session WHERE token_hash = ?", sha256(token)) }
    }

    suspend fun changePassword(id: UUID, password: String) {
        if (password.length <
            MIN_PASSWORD_LENGTH
        ) {
            throw AdminFormError("Parola trebuie să aibă cel puțin $MIN_PASSWORD_LENGTH caractere.")
        }
        db.transaction { c ->
            c.update("UPDATE admin SET password_hash = ? WHERE id = ?", Passwords.hash(password), id)
            // other browsers signed in with the old password are signed out
            c.update("DELETE FROM admin_session WHERE admin_id = ?", id)
        }
    }

    /** Removes an account, refusing to leave the dashboard without an admin. */
    suspend fun delete(id: UUID) {
        db.transaction { c ->
            val admins = c.query("SELECT id FROM admin WHERE role = 'admin'") { it.getObject(1, UUID::class.java) }
            if (admins == listOf(id)) throw AdminFormError("Ultimul administrator nu poate fi șters.")
            c.update("DELETE FROM admin WHERE id = ?", id)
        }
    }

    private fun java.sql.ResultSet.toAdmin() =
        Admin(getObject(1, UUID::class.java), getString(2), AdminRole.valueOf(getString(3).uppercase()))

    private fun sha256(token: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())

    private companion object {
        val DUMMY_HASH = Passwords.hash("not a real password")
    }
}
