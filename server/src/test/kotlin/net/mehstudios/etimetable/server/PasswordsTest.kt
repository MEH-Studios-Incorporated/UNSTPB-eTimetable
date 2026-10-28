// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import net.mehstudios.etimetable.server.accounts.Passwords
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PasswordsTest {
    @Test
    fun hashesVerifyAndAreSalted() {
        val a = Passwords.hash("parola-buna")
        val b = Passwords.hash("parola-buna")
        assertTrue(a.startsWith("\$argon2id\$v=19\$m=19456,t=2,p=1\$"), a)
        assertNotEquals(a, b)
        assertTrue(Passwords.verify("parola-buna", a))
        assertFalse(Passwords.verify("parola-gresita", a))
        assertFalse(Passwords.verify("parola-buna", "garbage"))
    }
}
