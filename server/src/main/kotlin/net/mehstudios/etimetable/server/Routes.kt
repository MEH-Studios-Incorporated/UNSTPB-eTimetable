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
import net.mehstudios.etimetable.api.ApiError
import net.mehstudios.etimetable.api.ErrorCodes
import net.mehstudios.etimetable.api.Health
import net.mehstudios.etimetable.api.LoginRequest
import net.mehstudios.etimetable.api.MAX_PUSH_TOKEN
import net.mehstudios.etimetable.api.PushTokenRequest
import net.mehstudios.etimetable.api.SignupRequest
import net.mehstudios.etimetable.api.TimetableReportRequest
import net.mehstudios.etimetable.model.Profile
import net.mehstudios.etimetable.server.accounts.checkProfile
import net.mehstudios.etimetable.server.db.query
import net.mehstudios.etimetable.server.db.transaction
import net.mehstudios.etimetable.server.reports.ReportLimitExceeded
import java.util.UUID

const val STUDENT_AUTH = "student"
val AUTH_RATE_LIMIT = RateLimitName("auth")
val PUSH_TOKEN_RATE_LIMIT = RateLimitName("push-token")

data class StudentPrincipal(val studentId: UUID)

fun Route.apiRoutes(services: Services) {
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
            call.respond(
                services.announcements.page(
                    call.request.queryParameters["before"],
                    pinnedOnly =
                    call.request.queryParameters["pinned"] == "true",
                ),
            )
        }
        rateLimit(AUTH_RATE_LIMIT) {
            post("/auth/signup") {
                call.respond(HttpStatusCode.Created, services.accounts.signup(call.receive<SignupRequest>()))
            }
            post("/auth/login") { call.respond(services.accounts.login(call.receive<LoginRequest>())) }
        }

        authenticate(STUDENT_AUTH) {
            post("/timetable-reports") {
                val request = call.receive<TimetableReportRequest>()
                try {
                    val receipt = services.reports.submit(call.studentId(), request, services.datasets.current())
                    call.respond(HttpStatusCode.Created, receipt)
                } catch (e: ReportLimitExceeded) {
                    call.response.header(HttpHeaders.RetryAfter, e.retryAfterSeconds)
                    call.respond(HttpStatusCode.TooManyRequests, ApiError(ErrorCodes.TOO_MANY_REQUESTS))
                }
            }
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
            rateLimit(PUSH_TOKEN_RATE_LIMIT) {
                put("/me/push-tokens/{token}") {
                    val token = call.pushToken()
                    val request = call.receive<PushTokenRequest>()
                    val session = call.bearerToken()
                        ?: throw ApiProblem(HttpStatusCode.Unauthorized, ApiError(ErrorCodes.UNAUTHORIZED))
                    services.pushTokens.register(call.studentId(), session, token, request.platform)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
            delete("/me/push-tokens/{token}") {
                services.pushTokens.remove(call.studentId(), call.pushToken())
                call.respond(HttpStatusCode.NoContent)
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

private fun RoutingCall.pushToken(): String {
    val token = parameters["token"].orEmpty()
    if (token.isBlank() || token.length > MAX_PUSH_TOKEN) {
        throw ApiProblem(HttpStatusCode.BadRequest, ErrorCodes.BAD_REQUEST)
    }
    return token
}

private fun RoutingCall.bearerToken(): String? =
    request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")?.trim()
