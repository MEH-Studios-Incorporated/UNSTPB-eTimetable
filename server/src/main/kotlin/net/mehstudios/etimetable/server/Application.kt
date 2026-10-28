// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import kotlinx.coroutines.runBlocking
import net.mehstudios.etimetable.api.ApiError
import net.mehstudios.etimetable.api.AppJson
import net.mehstudios.etimetable.api.ErrorCodes
import net.mehstudios.etimetable.server.accounts.Accounts
import net.mehstudios.etimetable.server.admin.AdminAccounts
import net.mehstudios.etimetable.server.admin.AdminCookie
import net.mehstudios.etimetable.server.admin.adminRoutes
import net.mehstudios.etimetable.server.announcements.AnnouncementRepository
import net.mehstudios.etimetable.server.dataset.DatasetRepository
import net.mehstudios.etimetable.server.db.createDataSource
import net.mehstudios.etimetable.server.db.migrate
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import javax.sql.DataSource
import kotlin.time.Duration.Companion.minutes

fun main() {
    val config = ServerConfig.fromEnvironment()
    val db = createDataSource(config.database)
    migrate(db)
    val services = Services(db)
    if (runBlocking { services.admins.bootstrap(config.firstAdminEmail, config.firstAdminPassword) }) {
        LoggerFactory.getLogger("eTimetable").info("Created the first admin account, {}", config.firstAdminEmail)
    }
    val secureCookies = config.publicUrl?.startsWith("https://") == true
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { module(services, secureCookies) }.start(wait = true)
}

class Services(val db: DataSource) {
    val datasets = DatasetRepository(db)
    val announcements = AnnouncementRepository(db)
    val accounts = Accounts(db)
    val admins = AdminAccounts(db)
}

fun Application.module(services: Services, secureCookies: Boolean = false) {
    install(ContentNegotiation) { json(AppJson) }
    install(Compression) { gzip() }
    install(CallLogging) {
        level = Level.INFO
        filter { it.request.path().startsWith("/api") }
    }
    install(StatusPages) {
        exception<ApiProblem> { call, e -> call.respond(e.status, e.error) }
        exception<BadRequestException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, ApiError(ErrorCodes.BAD_REQUEST, e.message.orEmpty()))
        }
        exception<Throwable> { call, e ->
            call.application.environment.log.error("Unhandled error on ${call.request.path()}", e)
            call.respond(HttpStatusCode.InternalServerError, ApiError(ErrorCodes.INTERNAL))
        }
    }
    install(RateLimit) {
        // Per client address. Behind a reverse proxy every request has the
        // proxy's address; see docs/deploy.md before deploying that way.
        register(AUTH_RATE_LIMIT) { rateLimiter(limit = 10, refillPeriod = 1.minutes) }
    }
    install(Authentication) {
        bearer(STUDENT_AUTH) {
            authenticate { credential -> services.accounts.studentFor(credential.token)?.let(::StudentPrincipal) }
        }
    }
    install(Sessions) {
        cookie<AdminCookie>("ett_admin") {
            cookie.path = "/admin"
            cookie.httpOnly = true
            cookie.secure = secureCookies
            cookie.maxAgeInSeconds = 12 * 60 * 60
            cookie.extensions["SameSite"] = "Lax"
        }
    }
    routing {
        apiRoutes(services)
        adminRoutes(services)
    }
}
