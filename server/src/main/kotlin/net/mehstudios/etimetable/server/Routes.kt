// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import net.mehstudios.etimetable.AppInfo
import net.mehstudios.etimetable.api.ApiError
import net.mehstudios.etimetable.api.ErrorCodes
import net.mehstudios.etimetable.api.Health
import net.mehstudios.etimetable.api.LoginRequest
import net.mehstudios.etimetable.api.SignupRequest
import net.mehstudios.etimetable.model.Profile
import net.mehstudios.etimetable.server.accounts.checkProfile
import net.mehstudios.etimetable.server.db.query
import net.mehstudios.etimetable.server.db.transaction
import java.util.UUID

const val STUDENT_AUTH = "student"
val AUTH_RATE_LIMIT = RateLimitName("auth")

data class StudentPrincipal(val studentId: UUID)

fun Route.apiRoutes(services: Services) {
    get("/") { call.respondText("${AppInfo.NAME} server ${ServerConfig.version}") }

    route("/api/v1") {
        get("/health") {
            val databaseUp = runCatching {
                services.db.transaction { c -> c.query("SELECT 1") { it.getInt(1) } }
            }.isSuccess
            val status = if (databaseUp) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
            call.respond(
                status,
                Health(status = if (databaseUp) "ok" else "database_unavailable", version = ServerConfig.version),
            )
        }

        get("/dataset") {
            val snapshot = services.datasets.current()
                ?: throw ApiProblem(HttpStatusCode.NotFound, ErrorCodes.NO_SEMESTER)
            call.response.header(HttpHeaders.ETag, snapshot.etag)
            call.response.header(HttpHeaders.CacheControl, "no-cache")
            if (call.request.header(HttpHeaders.IfNoneMatch) == snapshot.etag) {
                call.respond(HttpStatusCode.NotModified)
            } else {
                call.respondText(snapshot.json, ContentType.Application.Json)
            }
        }

        get("/announcements") {
            call.respond(services.announcements.page(call.request.queryParameters["before"]))
        }

        rateLimit(AUTH_RATE_LIMIT) {
            post("/auth/signup") {
                call.respond(HttpStatusCode.Created, services.accounts.signup(call.receive<SignupRequest>()))
            }
            post("/auth/login") { call.respond(services.accounts.login(call.receive<LoginRequest>())) }
        }

        authenticate(STUDENT_AUTH) {
            post("/auth/logout") {
                call.bearerToken()?.let { services.accounts.logout(it) }
                call.respond(HttpStatusCode.NoContent)
            }
            get("/me") { call.respond(services.accounts.profile(call.studentId())) }
            put("/me") {
                val profile = call.receive<Profile>()
                checkProfile(profile, services.datasets.current()?.dataset)
                services.accounts.saveProfile(call.studentId(), profile)
                call.respond(profile)
            }
            delete("/me") {
                services.accounts.delete(call.studentId())
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun RoutingCall.studentId(): UUID = principal<StudentPrincipal>()?.studentId
    ?: throw ApiProblem(HttpStatusCode.Unauthorized, ApiError(ErrorCodes.UNAUTHORIZED))

private fun RoutingCall.bearerToken(): String? =
    request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")?.trim()
