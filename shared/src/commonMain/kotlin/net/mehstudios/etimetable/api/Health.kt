// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.api

import kotlinx.serialization.Serializable

const val API_VERSION = 1

/** Answer of `GET /api/v1/health`. */
@Serializable
data class Health(val status: String, val version: String, val apiVersion: Int = API_VERSION)
