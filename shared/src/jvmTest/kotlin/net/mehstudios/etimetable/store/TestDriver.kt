// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.store

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

fun inMemoryStore(): LocalStore {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    EtimetableDatabase.Schema.create(driver)
    return LocalStore(driver)
}
