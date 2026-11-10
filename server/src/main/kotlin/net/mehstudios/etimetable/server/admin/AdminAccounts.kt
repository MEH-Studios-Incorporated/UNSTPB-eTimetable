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
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

enum class AdminRole { ADMIN, EDITOR }

data class Admin(
    val id: UUID,
    val email: String,
    val role: AdminRole,
    val shell: Shell? = null,
    val mustChangePassword: Boolean = false,
    val sessionId: UUID? = null,
)

/**
 * What the page frame shows around every dashboard page: the teaching week as the app
 * shows it, the current semester, the open reports, and the theme the admin picked.
 */
data class Shell(
    val week: Int? = null,
    val weekLabel: String? = null,
    val semester: String? = null,
    val openReports: Int = 0,
    val theme: String? = null,
)

/** An account as the Administratori page lists it. */
data class AdminAccount(
    val id: UUID,
    val email: String,
    val role: AdminRole,
    val disabled: Boolean,
    val twoStep: Boolean,
    val lastSignIn: Instant?,
    val createdAt: Instant,
)

data class AdminSession(
    val id: UUID,
    val createdAt: Instant,
    val lastSeenAt: Instant,
    val userAgent: String?,
    val ip: String?,
)

data class LoginAttempt(val at: Instant, val ok: Boolean, val ip: String?, val userAgent: String?)

/** Where the browser is signing in from, for throttling and the session list. */
data class Client(val ip: String?, val userAgent: String?)

sealed interface LoginResult {
    /** Signed in; [needsCode] when the second step is still to come. */
    data class Ok(val token: String, val needsCode: Boolean) : LoginResult
    data object Wrong : LoginResult
    data object Disabled : LoginResult
    data class Locked(val minutes: Int) : LoginResult
}

/** Thrown with a message meant for the person filling in the form, in Romanian. */
class AdminFormError(message: String) : RuntimeException(message)

/** Dashboard passwords are longer than students': these accounts can change everything. */
const val ADMIN_MIN_PASSWORD_LENGTH = 12

class AdminAccounts(
    private val db: DataSource,
    private val sessionLength: Duration = 12.hours,
    private val clock: Clock = Clock.System,
) {
    private val random = SecureRandom()

    /**
     * Creates the first admin from the environment, only while there is none. A password
     * shorter than the dashboard asks for has to be changed at the first sign-in.
     */
    suspend fun bootstrap(email: String?, password: String?): Boolean {
        if (email.isNullOrBlank() || password.isNullOrBlank()) return false
        if (list().isNotEmpty()) return false
        val admin =
            insert(
                email,
                password,
                AdminRole.ADMIN,
                minLength = MIN_PASSWORD_LENGTH,
                temporary = false,
                fromSetup = true,
            )
        if (password.length < ADMIN_MIN_PASSWORD_LENGTH) {
            db.transaction { c -> c.update("UPDATE admin SET must_change_password = true WHERE id = ?", admin.id) }
        }
        return true
    }

    /** An account with a password its owner chose. */
    suspend fun create(email: String, password: String, role: AdminRole): Admin =
        insert(email, password, role, minLength = ADMIN_MIN_PASSWORD_LENGTH, temporary = false)

    /**
     * An account with a temporary password, shown once to the admin who creates it; the
     * new person picks their own at the first sign-in.
     */
    suspend fun createWithTemporaryPassword(email: String, role: AdminRole): Pair<Admin, String> {
        val password = temporaryPassword()
        return insert(email, password, role, minLength = ADMIN_MIN_PASSWORD_LENGTH, temporary = true) to password
    }

    private suspend fun insert(
        email: String,
        password: String,
        role: AdminRole,
        minLength: Int,
        temporary: Boolean,
        fromSetup: Boolean = false,
    ): Admin {
        val clean = email.trim()
        if (!EMAIL.matches(clean)) throw AdminFormError("Adresa de email nu este validă.")
        // the deployment's own setting is only held to the length, so the server always starts
        if (fromSetup) {
            if (password.length <
                minLength
            ) {
                throw AdminFormError("Parola trebuie să aibă cel puțin $minLength caractere.")
            }
        } else {
            checkPassword(password, clean, minLength)
        }
        val admin = Admin(UUID.randomUUID(), clean, role)
        db.transaction { c ->
            try {
                c.update(
                    "INSERT INTO admin (id, email, password_hash, role, must_change_password) VALUES (?, ?, ?, ?, ?)",
                    admin.id,
                    clean,
                    Passwords.hash(password),
                    role.name.lowercase(),
                    temporary,
                )
            } catch (e: SQLException) {
                if (e.sqlState == "23505") throw AdminFormError("Există deja un cont cu adresa $clean.")
                throw e
            }
        }
        return admin
    }

    suspend fun list(): List<AdminAccount> = db.transaction { c ->
        c.query(
            """
            SELECT a.id, a.email, a.role, a.disabled_at IS NOT NULL, a.totp_enabled_at IS NOT NULL,
                (SELECT max(at) FROM admin_login_attempt l WHERE l.admin_id = a.id AND l.ok), a.created_at
            FROM admin a ORDER BY lower(a.email)
            """.trimIndent(),
        ) {
            AdminAccount(
                it.getObject(1, UUID::class.java),
                it.getString(2),
                AdminRole.valueOf(it.getString(3).uppercase()),
                it.getBoolean(4),
                it.getBoolean(5),
                it.instant(6),
                it.instant(7)!!,
            )
        }
    }

    suspend fun account(id: UUID): AdminAccount? = list().firstOrNull { it.id == id }

    /**
     * Checks the password and opens a session. Five wrong passwords or codes for one
     * account within 15 minutes lock it for 15 minutes from the first of them; attempts
     * during the lock are not counted, so it ends on time.
     */
    suspend fun login(email: String, password: String, client: Client = Client(null, null)): LoginResult {
        val clean = email.trim()
        val locked = db.transaction { c -> lockedFor(c, clean) }
        if (locked != null) return LoginResult.Locked(locked)
        val row = db.transaction { c ->
            c.query(
                "SELECT id, password_hash, disabled_at IS NOT NULL, totp_enabled_at IS NOT NULL FROM admin WHERE lower(email) = lower(?)",
                clean,
            ) { r -> LoginRow(r.getObject(1, UUID::class.java), r.getString(2), r.getBoolean(3), r.getBoolean(4)) }
                .singleOrNull()
        }
        // the hash is checked even for an unknown email, so timing does not tell which emails exist
        val passwordOk = Passwords.verify(password, row?.hash ?: DUMMY_HASH) && row != null
        db.transaction { c ->
            c.update(
                "INSERT INTO admin_login_attempt (email, admin_id, ip, user_agent, ok) VALUES (?, ?, ?, ?, ?)",
                clean,
                row?.id,
                client.ip,
                client.userAgent?.take(300),
                passwordOk && row?.disabled == false,
            )
            c.update("DELETE FROM admin_login_attempt WHERE at < now() - interval '180 days'")
        }
        if (!passwordOk || row == null) return LoginResult.Wrong
        if (row.disabled) return LoginResult.Disabled
        val token = newToken()
        db.transaction { c ->
            // the second step has 10 minutes; a verified session gets the full length
            val length = if (row.twoStep) 10.minutes else sessionLength
            c.update(
                """
                INSERT INTO admin_session (token_hash, admin_id, expires_at, user_agent, ip, verified)
                VALUES (?, ?, now() + make_interval(secs => ?), ?, ?, ?)
                """.trimIndent(),
                sha256(token),
                row.id,
                length.inWholeSeconds.toDouble(),
                client.userAgent?.take(300),
                client.ip,
                !row.twoStep,
            )
            c.update("DELETE FROM admin_session WHERE expires_at < now()")
        }
        return LoginResult.Ok(token, needsCode = row.twoStep)
    }

    private class LoginRow(val id: UUID, val hash: String, val disabled: Boolean, val twoStep: Boolean)

    /**
     * Minutes left of a lock on [email], or null. Locks are per account: behind the
     * university's reverse proxy every request has the proxy's address (docs/deploy.md).
     */
    private fun lockedFor(c: Connection, email: String): Int? {
        // failures since the last success, inside the window
        val (failures, first) = c.query(
            """
            SELECT count(*), min(at) FROM admin_login_attempt
            WHERE lower(email) = lower(?) AND NOT ok AND at > now() - interval '15 minutes'
              AND at > coalesce((SELECT max(at) FROM admin_login_attempt WHERE lower(email) = lower(?) AND ok), '-infinity')
            """.trimIndent(),
            email,
            email,
        ) { it.getInt(1) to it.instant(2) }.single()
        if (failures < MAX_FAILURES || first == null) return null
        val left = (first + 15.minutes) - clock.now()
        return left.inWholeMinutes.toInt().coerceAtLeast(0) + 1
    }

    /** The signed-in admin for a verified, unexpired session; also notes the session as seen. */
    suspend fun adminFor(token: String): Admin? = db.transaction { c ->
        val admin = c.query(
            """
            SELECT a.id, a.email, a.role, a.must_change_password, s.id
            FROM admin_session s JOIN admin a ON a.id = s.admin_id
            WHERE s.token_hash = ? AND s.expires_at > now() AND s.verified AND a.disabled_at IS NULL
            """.trimIndent(),
            sha256(token),
        ) {
            Admin(
                it.getObject(1, UUID::class.java),
                it.getString(2),
                AdminRole.valueOf(it.getString(3).uppercase()),
                mustChangePassword = it.getBoolean(4),
                sessionId = it.getObject(5, UUID::class.java),
            )
        }.singleOrNull()
        if (admin != null) {
            c.update(
                "UPDATE admin_session SET last_seen_at = now() WHERE token_hash = ? AND last_seen_at < now() - interval '1 minute'",
                sha256(token),
            )
        }
        admin
    }

    /** The account waiting for its second step under [token], if any. */
    suspend fun pendingFor(token: String): UUID? = db.transaction { c ->
        c.query(
            "SELECT admin_id FROM admin_session WHERE token_hash = ? AND expires_at > now() AND NOT verified",
            sha256(token),
        ) { it.getObject(1, UUID::class.java) }.singleOrNull()
    }

    /**
     * Completes the second step with a code from the app or an unused recovery code. Wrong
     * codes count like wrong passwords.
     */
    suspend fun verifySecondStep(token: String, code: String, client: Client): LoginResult {
        val adminId = pendingFor(token) ?: return LoginResult.Wrong
        val email = db.transaction { c ->
            c.query("SELECT email FROM admin WHERE id = ?", adminId) { it.getString(1) }.single()
        }
        val locked = db.transaction { c -> lockedFor(c, email) }
        if (locked != null) return LoginResult.Locked(locked)
        val ok = checkCode(adminId, code)
        db.transaction { c ->
            c.update(
                "INSERT INTO admin_login_attempt (email, admin_id, ip, user_agent, ok) VALUES (?, ?, ?, ?, ?)",
                email,
                adminId,
                client.ip,
                client.userAgent?.take(300),
                ok,
            )
            if (ok) {
                c.update(
                    "UPDATE admin_session SET verified = true, expires_at = now() + make_interval(secs => ?) WHERE token_hash = ?",
                    sessionLength.inWholeSeconds.toDouble(),
                    sha256(token),
                )
            }
        }
        return if (ok) LoginResult.Ok(token, needsCode = false) else LoginResult.Wrong
    }

    /** True for a current app code or an unused recovery code, which is then used up. */
    private suspend fun checkCode(adminId: UUID, code: String): Boolean = db.transaction { c ->
        val (secret, lastStep) = c.query("SELECT totp_secret, totp_last_step FROM admin WHERE id = ?", adminId) {
            it.getBytes(1) to it.getObject(2)?.let { s -> (s as Number).toLong() }
        }.single()
        if (secret == null) return@transaction false
        val step = Totp.verify(secret, code, clock.now().epochSeconds, lastStep)
        if (step != null) {
            c.update("UPDATE admin SET totp_last_step = ? WHERE id = ?", step, adminId)
            return@transaction true
        }
        val recovery = normalizeRecovery(code) ?: return@transaction false
        c.update(
            "UPDATE admin_recovery_code SET used_at = now() WHERE admin_id = ? AND code_hash = ? AND used_at IS NULL",
            adminId,
            sha256(recovery),
        ) == 1
    }

    suspend fun logout(token: String) {
        db.transaction { c -> c.update("DELETE FROM admin_session WHERE token_hash = ?", sha256(token)) }
    }

    suspend fun sessions(adminId: UUID): List<AdminSession> = db.transaction { c ->
        c.query(
            """
            SELECT id, created_at, last_seen_at, user_agent, ip FROM admin_session
            WHERE admin_id = ? AND verified AND expires_at > now() ORDER BY last_seen_at DESC
            """.trimIndent(),
            adminId,
        ) {
            AdminSession(
                it.getObject(1, UUID::class.java),
                it.instant(2)!!,
                it.instant(3)!!,
                it.getString(4),
                it.getString(5),
            )
        }
    }

    /** Ends one of [adminId]'s sessions; false when it is not theirs. */
    suspend fun endSession(adminId: UUID, sessionId: UUID): Boolean = db.transaction { c ->
        c.update("DELETE FROM admin_session WHERE admin_id = ? AND id = ?", adminId, sessionId) == 1
    }

    /** Ends every session of [adminId] except [keep]; returns how many ended. */
    suspend fun endSessions(adminId: UUID, keep: UUID? = null): Int = db.transaction { c ->
        if (keep == null) {
            c.update("DELETE FROM admin_session WHERE admin_id = ?", adminId)
        } else {
            c.update("DELETE FROM admin_session WHERE admin_id = ? AND id <> ?", adminId, keep)
        }
    }

    suspend fun recentLogins(adminId: UUID, limit: Int = 10): List<LoginAttempt> = db.transaction { c ->
        c.query(
            "SELECT at, ok, ip, user_agent FROM admin_login_attempt WHERE admin_id = ? ORDER BY at DESC LIMIT ?",
            adminId,
            limit,
        ) { LoginAttempt(it.instant(1)!!, it.getBoolean(2), it.getString(3), it.getString(4)) }
    }

    /** Wrong passwords and codes in the last 24 hours, for every account. */
    suspend fun failedLoginsToday(): Int = db.transaction { c ->
        c.query("SELECT count(*) FROM admin_login_attempt WHERE NOT ok AND at > now() - interval '24 hours'") {
            it.getInt(1)
        }.single()
    }

    /**
     * The owner changes their own password; [current] must be right. Every other session
     * ends, [keepSession] stays signed in.
     */
    suspend fun changePassword(id: UUID, current: String, password: String, keepSession: UUID?) {
        val (email, hash) = db.transaction { c ->
            c.query("SELECT email, password_hash FROM admin WHERE id = ?", id) {
                it.getString(1) to it.getString(2)
            }.single()
        }
        if (!Passwords.verify(current, hash)) throw AdminFormError("Parola actuală nu este corectă.")
        if (current == password) throw AdminFormError("Parola nouă trebuie să fie diferită de cea actuală.")
        checkPassword(password, email, ADMIN_MIN_PASSWORD_LENGTH)
        db.transaction { c ->
            c.update(
                "UPDATE admin SET password_hash = ?, must_change_password = false WHERE id = ?",
                Passwords.hash(password),
                id,
            )
            if (keepSession == null) {
                c.update("DELETE FROM admin_session WHERE admin_id = ?", id)
            } else {
                c.update("DELETE FROM admin_session WHERE admin_id = ? AND id <> ?", id, keepSession)
            }
        }
    }

    /** An admin gives [id] a new temporary password; the person is signed out everywhere. */
    suspend fun resetPassword(id: UUID): String {
        val password = temporaryPassword()
        db.transaction { c ->
            c.update(
                "UPDATE admin SET password_hash = ?, must_change_password = true WHERE id = ?",
                Passwords.hash(password),
                id,
            )
            c.update("DELETE FROM admin_session WHERE admin_id = ?", id)
        }
        return password
    }

    suspend fun setRole(id: UUID, role: AdminRole) {
        db.transaction { c ->
            if (role ==
                AdminRole.EDITOR
            ) {
                requireAnotherAdmin(c, id, "Ultimul administrator activ nu poate deveni editor.")
            }
            c.update("UPDATE admin SET role = ? WHERE id = ?", role.name.lowercase(), id)
        }
    }

    /** A disabled account cannot sign in and its sessions end; nothing else is lost. */
    suspend fun setDisabled(id: UUID, disabled: Boolean) {
        db.transaction { c ->
            if (disabled) {
                requireAnotherAdmin(c, id, "Ultimul administrator activ nu poate fi dezactivat.")
                c.update("UPDATE admin SET disabled_at = now() WHERE id = ?", id)
                c.update("DELETE FROM admin_session WHERE admin_id = ?", id)
            } else {
                c.update("UPDATE admin SET disabled_at = NULL WHERE id = ?", id)
            }
        }
    }

    /** Removes an account, refusing to leave the dashboard without an active admin. */
    suspend fun delete(id: UUID) {
        db.transaction { c ->
            requireAnotherAdmin(c, id, "Ultimul administrator activ nu poate fi șters.")
            c.update("DELETE FROM admin WHERE id = ?", id)
        }
    }

    private fun requireAnotherAdmin(c: Connection, id: UUID, message: String) {
        val others = c.query(
            "SELECT count(*) FROM admin WHERE role = 'admin' AND disabled_at IS NULL AND id <> ?",
            id,
        ) { it.getInt(1) }.single()
        if (others == 0) throw AdminFormError(message)
    }

    /** A new secret for the setup page; two-step sign-in starts only once a code matches it. */
    fun newTotpSecret(): ByteArray = Totp.newSecret()

    /**
     * Turns on two-step sign-in when [code] matches [secret], and returns ten recovery codes
     * to show once. Other sessions end, since they were opened without the second step.
     */
    suspend fun enableTotp(id: UUID, secret: ByteArray, code: String, keepSession: UUID?): List<String> {
        val step = Totp.verify(secret, code, clock.now().epochSeconds, usedStep = null)
            ?: throw AdminFormError("Codul nu se potrivește. Verifică ora telefonului și încearcă din nou.")
        val codes = List(10) { recoveryCode() }
        db.transaction { c ->
            c.update(
                "UPDATE admin SET totp_secret = ?, totp_enabled_at = now(), totp_last_step = ? WHERE id = ?",
                secret,
                step,
                id,
            )
            c.update("DELETE FROM admin_recovery_code WHERE admin_id = ?", id)
            codes.forEach { code ->
                c.update(
                    "INSERT INTO admin_recovery_code (admin_id, code_hash) VALUES (?, ?)",
                    id,
                    sha256(normalizeRecovery(code)!!),
                )
            }
            if (keepSession !=
                null
            ) {
                c.update("DELETE FROM admin_session WHERE admin_id = ? AND id <> ?", id, keepSession)
            }
        }
        return codes
    }

    /** The owner turns it off with their password; an admin can reset it for someone who lost their phone. */
    suspend fun disableTotp(id: UUID, password: String?) {
        if (password != null) {
            val hash = db.transaction { c ->
                c.query("SELECT password_hash FROM admin WHERE id = ?", id) { it.getString(1) }.single()
            }
            if (!Passwords.verify(password, hash)) throw AdminFormError("Parola nu este corectă.")
        }
        db.transaction { c ->
            c.update(
                "UPDATE admin SET totp_secret = NULL, totp_enabled_at = NULL, totp_last_step = NULL WHERE id = ?",
                id,
            )
            c.update("DELETE FROM admin_recovery_code WHERE admin_id = ?", id)
        }
    }

    suspend fun twoStepEnabled(id: UUID): Boolean = db.transaction { c ->
        c.query("SELECT totp_enabled_at IS NOT NULL FROM admin WHERE id = ?", id) { it.getBoolean(1) }.single()
    }

    suspend fun recoveryCodesLeft(id: UUID): Int = db.transaction { c ->
        c.query("SELECT count(*) FROM admin_recovery_code WHERE admin_id = ? AND used_at IS NULL", id) {
            it.getInt(1)
        }.single()
    }

    private fun checkPassword(password: String, email: String, minLength: Int) {
        if (password.length < minLength) throw AdminFormError("Parola trebuie să aibă cel puțin $minLength caractere.")
        if (password.length > 200) throw AdminFormError("Parola poate avea cel mult 200 de caractere.")
        val local = email.substringBefore('@').lowercase()
        if (password.lowercase() == email.lowercase() || (local.length >= 4 && local in password.lowercase())) {
            throw AdminFormError("Parola nu poate conține adresa de email.")
        }
        if (password.toSet().size < 5) throw AdminFormError("Parola are prea puține caractere diferite.")
    }

    /** Four groups of four letters and digits, without look-alikes such as 0 and O. */
    private fun temporaryPassword(): String =
        List(4) { List(4) { SAFE_CHARS[random.nextInt(SAFE_CHARS.length)] }.joinToString("") }.joinToString("-")

    private fun recoveryCode(): String =
        List(2) { List(5) { SAFE_CHARS[random.nextInt(SAFE_CHARS.length)] }.joinToString("") }.joinToString("-")

    private fun normalizeRecovery(code: String): String? =
        code.uppercase().filter { it.isLetterOrDigit() }.takeIf { it.length == 10 }

    private fun newToken() =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

    private fun sha256(token: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())

    private fun ResultSet.instant(column: Int): Instant? =
        getObject(column, OffsetDateTime::class.java)?.toInstant()?.toKotlinInstant()

    private companion object {
        const val MAX_FAILURES = 5
        const val SAFE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        val EMAIL = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
        val DUMMY_HASH = Passwords.hash("not a real password")
    }
}
