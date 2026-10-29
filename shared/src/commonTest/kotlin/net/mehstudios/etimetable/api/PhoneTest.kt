// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.api

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhoneTest {
    @Test
    fun acceptsNumbersAsPeopleWriteThem() {
        listOf("0712345678", "+40 712 345 678", "+40 (21) 402-9100", "0712.345.678", "+49 151 23456789").forEach {
            assertTrue(isValidPhone(it), it)
        }
    }

    @Test
    fun refusesWhatIsNotANumber() {
        listOf("", "12345", "call me", "+40 7123 4567 8901 2345", "0712345678 ext 2", "++40712345678").forEach {
            assertFalse(isValidPhone(it), it)
        }
    }
}
