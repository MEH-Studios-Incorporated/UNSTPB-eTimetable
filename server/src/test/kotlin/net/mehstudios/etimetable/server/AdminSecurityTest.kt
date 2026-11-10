// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import net.mehstudios.etimetable.server.admin.AdminRole
import net.mehstudios.etimetable.server.admin.Totp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class AdminSecurityTest {
    private fun withDashboard(block: suspend ApplicationTestBuilder.(Services) -> Unit) = testApplication {
        val services = Services(TestPostgres.freshDatabase())
        services.admins.bootstrap("admin@faima.test", "parola-admin")
        application { module(services) }
        block(services)
    }

    private fun ApplicationTestBuilder.browser(): HttpClient = createClient {
        install(HttpCookies)
        sameSite()
        followRedirects = false
    }

    private suspend fun HttpClient.login(email: String = "admin@faima.test", password: String = "parola-admin") =
        submitForm(
            "/admin/login",
            parameters {
                append("email", email)
                append("password", password)
            },
        )

    private fun HttpResponse.location() = headers[HttpHeaders.Location]

    @Test
    fun fiveWrongPasswordsLockTheAccount() = withDashboard { _ ->
        val browser = browser()
        repeat(5) { assertEquals(HttpStatusCode.Unauthorized, browser.login(password = "gresit-$it").status) }
        val locked = browser.login()
        assertEquals(HttpStatusCode.TooManyRequests, locked.status)
        assertTrue("blocat încă" in locked.bodyAsText())
        // another account is not affected
        assertEquals(HttpStatusCode.Unauthorized, browser.login(email = "altcineva@faima.test").status)
    }

    @Test
    fun postsMustComeFromTheDashboardItself() = withDashboard { _ ->
        val browser = browser()
        browser.login()
        val noOrigin = createClient {
            install(HttpCookies)
            followRedirects = false
        }
        assertEquals(HttpStatusCode.Forbidden, noOrigin.login().status)
        val crossSite = browser.submitForm("/admin/account/sessions/end-others", parameters { }) {
            header("Sec-Fetch-Site", "cross-site")
        }
        assertEquals(HttpStatusCode.Forbidden, crossSite.status)
        val otherOrigin = noOrigin.submitForm("/admin/login", parameters { }) {
            header(HttpHeaders.Origin, "https://evil.example")
        }
        assertEquals(HttpStatusCode.Forbidden, otherOrigin.status)
    }

    @Test
    fun twoStepSignInWithAnAppCodeAndARecoveryCode() = withDashboard { services ->
        val browser = browser()
        browser.login()
        val setup = browser.get("/admin/account/two-step").bodyAsText()
        val secretText = Regex("name=\"secret\" value=\"([A-Z2-7]+)\"").find(setup)!!.groupValues[1]
        val secret = base32(secretText)
        val now = Clock.System.now().epochSeconds
        val enabled = browser.submitForm(
            "/admin/account/two-step",
            parameters {
                append("secret", secretText)
                append("code", Totp.code(secret, Totp.step(now)))
            },
        ).bodyAsText()
        val recovery = Regex("<li>([A-Z2-9]{5}-[A-Z2-9]{5})</li>").findAll(enabled).map { it.groupValues[1] }.toList()
        assertEquals(10, recovery.size)

        val other = browser()
        assertEquals("/admin/login/code", other.login().location())
        // the password alone opens nothing
        assertEquals("/admin/login", other.get("/admin").location())
        // the code that turned it on was used up
        val reused = other.submitForm(
            "/admin/login/code",
            parameters {
                append("code", Totp.code(secret, Totp.step(now)))
            },
        )
        assertEquals(HttpStatusCode.Unauthorized, reused.status)
        val ok = other.submitForm("/admin/login/code", parameters { append("code", recovery.first().lowercase()) })
        assertEquals("/admin", ok.location())
        assertEquals(HttpStatusCode.OK, other.get("/admin").status)

        // a recovery code works once
        val third = browser()
        third.login()
        assertEquals(
            HttpStatusCode.Unauthorized,
            third.submitForm("/admin/login/code", parameters { append("code", recovery.first()) }).status,
        )
        val log = browser.get("/admin/audit").bodyAsText()
        assertTrue("a pornit autentificarea în doi pași" in log)
        assertEquals(9, services.admins.recoveryCodesLeft(services.admins.list().single().id))
    }

    @Test
    fun aNewAccountStartsWithATemporaryPassword() = withDashboard { _ ->
        val admin = browser()
        admin.login()
        val created = admin.submitForm(
            "/admin/admins",
            parameters {
                append("email", "secretariat@faima.test")
                append("role", "editor")
            },
        ).bodyAsText()
        val temporary = Regex("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}").find(created)!!.value

        val editor = browser()
        assertEquals("/admin", editor.login("secretariat@faima.test", temporary).location())
        // until a new password is chosen, every page leads to the account page
        assertEquals("/admin/account", editor.get("/admin").location())
        assertEquals("/admin/account", editor.get("/admin/announcements").location())
        val weak = editor.submitForm(
            "/admin/account/password",
            parameters {
                append("current", temporary)
                append("password", "secretariat-2026")
                append("confirm", "secretariat-2026")
            },
        )
        assertTrue("err=" in weak.location().orEmpty())
        editor.submitForm(
            "/admin/account/password",
            parameters {
                append("current", temporary)
                append("password", "orar nou de toamnă")
                append("confirm", "orar nou de toamnă")
            },
        )
        assertEquals(HttpStatusCode.OK, editor.get("/admin/announcements").status)
        // editors do not see accounts or the log
        assertEquals(HttpStatusCode.Forbidden, editor.get("/admin/audit").status)
        assertEquals(HttpStatusCode.Forbidden, editor.get("/admin/admins").status)
    }

    @Test
    fun theLastAdminCannotBeDemotedDisabledOrDeleted() = withDashboard { services ->
        val browser = browser()
        browser.login()
        val self = services.admins.list().single()
        // an admin cannot manage their own account from the list
        assertEquals(
            HttpStatusCode.Forbidden,
            browser.submitForm(
                "/admin/admins/${self.id}/disable",
                parameters {
                },
            ).status,
        )
        val editor = services.admins.create("editor@faima.test", "parola-de-test", AdminRole.EDITOR)
        val second = services.admins.create("al-doilea@faima.test", "parola-de-test", AdminRole.ADMIN)
        // with a second admin, the first can be demoted by them, but not both
        services.admins.setRole(self.id, AdminRole.EDITOR)
        val refused = runCatching { services.admins.setDisabled(second.id, true) }
        assertTrue(refused.isFailure)
        services.admins.setRole(self.id, AdminRole.ADMIN)

        val disabled = browser.submitForm("/admin/admins/${editor.id}/disable", parameters { })
        assertTrue("msg=" in disabled.location().orEmpty())
        val login = browser().login("editor@faima.test", "parola-de-test")
        assertEquals(HttpStatusCode.Forbidden, login.status)
        assertTrue("dezactivat" in login.bodyAsText())
        val log = browser.get("/admin/audit?q=dezactivat").bodyAsText()
        assertTrue("a dezactivat contul editor@faima.test" in log)
        assertFalse("a creat contul" in log)
    }

    @Test
    fun sessionsCanBeClosedFromAnotherBrowser() = withDashboard { services ->
        val laptop = browser()
        laptop.login()
        val phone = browser()
        phone.login()
        val account = laptop.get("/admin/account").bodyAsText()
        assertEquals(2, Regex("/admin/account/sessions/[0-9a-f-]{36}/end").findAll(account).count())
        laptop.submitForm("/admin/account/sessions/end-others", parameters { })
        assertEquals("/admin/login", phone.get("/admin").location())
        assertEquals(HttpStatusCode.OK, laptop.get("/admin").status)
        assertNotNull(services.admins.list().single().lastSignIn)
    }

    @Test
    fun contentChangesAreLogged() = withDashboard { _ ->
        val browser = browser()
        browser.login()
        browser.submitForm(
            "/admin/announcements",
            parameters {
                append("title", "Program secretariat")
                append("body", "Luni-vineri, 9-12.")
                append("author", "Secretariat FAIMA")
            },
        )
        val log = browser.get("/admin/audit?category=announcements").bodyAsText()
        assertTrue("a publicat anunțul Program secretariat" in log)
        assertTrue("admin@faima.test" in log)
        assertTrue("1-1 din 1" in log)
    }

    @Test
    fun totpMatchesTheRfcTestVector() {
        // RFC 6238 appendix B, SHA-1, T = 59 s: 94287082, of which an app shows the last six digits
        val secret = "12345678901234567890".toByteArray()
        assertEquals("287082", Totp.code(secret, Totp.step(59)))
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Totp.base32(secret))
        assertEquals(Totp.step(59), Totp.verify(secret, "287 082", 59, usedStep = null))
        assertEquals(null, Totp.verify(secret, "287082", 59, usedStep = Totp.step(59)))
    }

    private fun base32(text: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0
        var bits = 0
        val out = java.io.ByteArrayOutputStream()
        for (ch in text) {
            buffer = (buffer shl 5) or alphabet.indexOf(ch)
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xff)
                bits -= 8
            }
        }
        return out.toByteArray()
    }
}
