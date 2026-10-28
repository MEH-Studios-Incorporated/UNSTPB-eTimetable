// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import io.ktor.http.parameters
import kotlinx.datetime.LocalDate
import net.mehstudios.etimetable.model.Parity
import net.mehstudios.etimetable.server.admin.AdminFormError
import net.mehstudios.etimetable.server.admin.CalendarForm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CalendarFormTest {
    @Test
    fun readsBreaksAndParityChanges() {
        val semester = CalendarForm.parse(
            parameters {
                append("name_ro", "Semestrul 1")
                append("first_day", "2026-09-28")
                append("last_day", "2027-01-15")
                append("breaks", "2026-12-21 2027-01-03 Vacanța de iarnă | Winter break\n\n2026-12-01 Ziua Națională")
                append("overrides", "2026-11-30 pară")
            },
            "2026-1",
        )
        assertEquals("Semestrul 1", semester.name.en)
        assertEquals(LocalDate(2027, 1, 3), semester.breaks[0].lastDay)
        assertEquals("Winter break", semester.breaks[0].name.en)
        assertEquals(LocalDate(2026, 12, 1), semester.breaks[1].lastDay)
        assertEquals("Ziua Națională", semester.breaks[1].name.en)
        assertEquals(Parity.EVEN, semester.parityOverrides.single().parity)
        assertEquals(
            semester,
            CalendarForm.parse(
                parameters {
                    append("name_ro", semester.name.ro)
                    append("name_en", semester.name.en)
                    append("first_day", semester.firstDay.toString())
                    append("last_day", semester.lastDay.toString())
                    append("breaks", CalendarForm.breaksText(semester))
                    append("overrides", CalendarForm.overridesText(semester))
                },
                "2026-1",
            ),
        )
    }

    @Test
    fun refusesNonsense() {
        val base =
            parameters {
                append("name_ro", "S")
                append("first_day", "2026-09-28")
                append("last_day", "2026-09-01")
            }
        assertFailsWith<AdminFormError> { CalendarForm.parse(base, "2026-1") }
        assertFailsWith<AdminFormError> { CalendarForm.parse(base, "bad id!") }
    }
}
