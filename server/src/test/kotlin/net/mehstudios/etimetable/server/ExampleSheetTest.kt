// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import net.mehstudios.etimetable.server.timetable.TimetableSheet
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** docs/examples/orar-exemplu.csv is the sample semester exported; refresh with UPDATE_EXAMPLE_SHEET=1. */
class ExampleSheetTest {
    @Test
    fun exampleSheetIsCurrent() {
        val file = File("../docs/examples/orar-exemplu.csv")
        val expected = TimetableSheet.export(sampleDataset())
        if (System.getenv("UPDATE_EXAMPLE_SHEET") == "1") file.writeText(expected)
        assertEquals(expected, file.readText(), "Run the server tests with UPDATE_EXAMPLE_SHEET=1")
    }
}
