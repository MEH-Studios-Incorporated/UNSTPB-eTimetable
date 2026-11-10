// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.store

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import net.mehstudios.etimetable.SampleData
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class MigrationTest {
    /** A phone with the first release installed: its database is version 1, with a timetable in it. */
    @Test
    fun anOldDatabaseKeepsItsTimetableAndGetsEventsAndChangeNotes() {
        val copy = File.createTempFile("etimetable-v1", ".db").apply { deleteOnExit() }
        File("src/commonMain/sqldelight/databases/1.db").copyTo(copy, overwrite = true)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${copy.path}")
        val old = LocalStore(driver)
        old.saveDataset(SampleData.dataset, "\"v1\"", Instant.fromEpochSeconds(1_000))

        EtimetableDatabase.Schema.migrate(driver, 1, EtimetableDatabase.Schema.version)
        val store = LocalStore(driver)

        assertEquals(SampleData.dataset, store.dataset()?.dataset)
        assertEquals(null, store.events())
        store.saveEvents(emptyList(), "\"events\"")
        assertEquals("\"events\"", store.events()?.etag)
        assertEquals(emptyList(), store.changeNotes())
        assertEquals(3L, EtimetableDatabase.Schema.version)
    }
}
