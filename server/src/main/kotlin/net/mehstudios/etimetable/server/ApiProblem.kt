// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import io.ktor.http.HttpStatusCode
import net.mehstudios.etimetable.api.ApiError

/** Thrown by handlers; StatusPages turns it into [status] with [error] as the body. */
class ApiProblem(val status: HttpStatusCode, val error: ApiError) : RuntimeException(error.code) {
    constructor(status: HttpStatusCode, code: String, details: List<String> = emptyList()) :
        this(status, ApiError(code, details = details))
}
