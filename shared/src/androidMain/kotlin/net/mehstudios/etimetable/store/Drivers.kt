// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.store

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

fun createDriver(context: Context): SqlDriver = AndroidSqliteDriver(EtimetableDatabase.Schema, context, DATABASE_NAME)

fun createLocalStore(context: Context): LocalStore = LocalStore(createDriver(context))
