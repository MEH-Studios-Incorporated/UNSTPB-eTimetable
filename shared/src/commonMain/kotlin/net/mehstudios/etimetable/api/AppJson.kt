// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.api

import kotlinx.serialization.json.Json

/** JSON settings shared by the app, the offline store and the server. */
val AppJson = Json {
    // fields added by newer servers must not break older apps
    ignoreUnknownKeys = true
    encodeDefaults = true
}
