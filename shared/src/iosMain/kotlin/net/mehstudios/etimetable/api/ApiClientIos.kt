// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.api

import io.ktor.client.engine.darwin.Darwin

fun createApiClient(baseUrl: String): ApiClient = ApiClient(Darwin.create(), baseUrl)
