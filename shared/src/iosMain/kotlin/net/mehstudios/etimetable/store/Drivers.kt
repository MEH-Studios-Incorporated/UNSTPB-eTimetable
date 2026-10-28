// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.store

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

fun createDriver(): SqlDriver = NativeSqliteDriver(EtimetableDatabase.Schema, DATABASE_NAME)
