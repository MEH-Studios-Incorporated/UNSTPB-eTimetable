// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import kotlinx.coroutines.runBlocking
import net.mehstudios.etimetable.api.ApiError
import net.mehstudios.etimetable.api.AppJson
import net.mehstudios.etimetable.api.ErrorCodes
import net.mehstudios.etimetable.api.PushPlatform
import net.mehstudios.etimetable.server.accounts.Accounts
import net.mehstudios.etimetable.server.admin.AdminAccounts
import net.mehstudios.etimetable.server.admin.AdminCookie
import net.mehstudios.etimetable.server.admin.AuditLog
import net.mehstudios.etimetable.server.admin.adminRoutes
import net.mehstudios.etimetable.server.announcements.AnnouncementRepository
import net.mehstudios.etimetable.server.dataset.DatasetRepository
import net.mehstudios.etimetable.server.db.createDataSource
import net.mehstudios.etimetable.server.db.migrate
import net.mehstudios.etimetable.server.events.EventRepository
import net.mehstudios.etimetable.server.push.ClockCheck
import net.mehstudios.etimetable.server.push.FcmSender
import net.mehstudios.etimetable.server.push.GOOGLE_TOKEN_URL
import net.mehstudios.etimetable.server.push.HttpDateClock
import net.mehstudios.etimetable.server.push.InvalidServiceAccountKey
import net.mehstudios.etimetable.server.push.PushNotifier
import net.mehstudios.etimetable.server.push.PushSender
import net.mehstudios.etimetable.server.push.PushTokens
import net.mehstudios.etimetable.server.push.ServiceAccountKey
import net.mehstudios.etimetable.server.reports.TimetableReports
import net.mehstudios.etimetable.server.site.siteRoutes
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import javax.sql.DataSource
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

fun main() {
    val config = ServerConfig.fromEnvironment()
    val db = createDataSource(config.database)
    migrate(db)
    val services = Services(db, pushSenders(config.fcmServiceAccount), googleClockCheck())
    if (runBlocking { services.admins.bootstrap(config.firstAdminEmail, config.firstAdminPassword) }) {
        LoggerFactory.getLogger("eTimetable").info("Created the first admin account, {}", config.firstAdminEmail)
    }
    val secureCookies = config.publicUrl?.startsWith("https://") == true
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(services, secureCookies, config.downloads)
    }.start(wait = true)
}

/**
 * The push gateways this deployment has credentials for; none turns push off.
 * [fcmServiceAccount] is the value of ETT_FCM_SERVICE_ACCOUNT.
 */
internal fun pushSenders(fcmServiceAccount: String?): Map<PushPlatform, PushSender> {
    val log = LoggerFactory.getLogger("eTimetable")
    val fcm = fcmServiceAccount?.takeIf { it.isNotBlank() }
    if (fcm == null) {
        log.info("Push notifications are off: ETT_FCM_SERVICE_ACCOUNT is not set")
        return emptyMap()
    }
    // a key that is not JSON, or whose private key is not an RSA key, fails here and not at
    // the first announcement; the message names the problem and quotes nothing of the key
    val sender = try {
        FcmSender(ServiceAccountKey.parse(fcm))
    } catch (e: InvalidServiceAccountKey) {
        error("ETT_FCM_SERVICE_ACCOUNT is not a Firebase service account key: ${e.message}")
    }
    log.info("Push notifications go to Android phones through Firebase project {}", sender.projectId)
    return mapOf(PushPlatform.ANDROID to sender)
}

private val PUSH_TOKEN_PATH = Regex("(/me/push-tokens/)[^/?]+")

/** [path] as the request log shows it: a phone's push token stays out of the logs. */
internal fun loggedPath(path: String): String = PUSH_TOKEN_PATH.replace(path, "$1<token>")

/** Compares this server's clock with Google's, which push needs to match. */
internal fun googleClockCheck() = ClockCheck(HttpDateClock(GOOGLE_TOKEN_URL))

/** [clockCheck] lets publishing report a wrong clock; null skips the check, as in tests. */
class Services(
    val db: DataSource,
    pushSenders: Map<PushPlatform, PushSender> = emptyMap(),
    clockCheck: ClockCheck? = null,
) {
    val datasets = DatasetRepository(db)
    val announcements = AnnouncementRepository(db)
    val events = EventRepository(db)
    val accounts = Accounts(db)
    val admins = AdminAccounts(db)
    val audit = AuditLog(db)
    val reports = TimetableReports(db)
    val pushTokens = PushTokens(db)
    val push = PushNotifier(pushTokens, pushSenders, clockCheck)
}

fun Application.module(services: Services, secureCookies: Boolean = false, downloads: Downloads = Downloads()) {
    install(ContentNegotiation) { json(AppJson) }
    install(Compression) { gzip() }
    install(CallLogging) {
        level = Level.INFO
        filter { it.request.path().startsWith("/api") }
        format { call ->
            val status = call.response.status()?.toString() ?: "Unhandled"
            "$status: ${call.request.httpMethod.value} - ${loggedPath(call.request.path())} " +
                "in ${call.processingTimeMillis()}ms"
        }
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
        // per student: the app sends its token only when Firebase changes it
        register(PUSH_TOKEN_RATE_LIMIT) {
            rateLimiter(limit = 10, refillPeriod = 1.hours)
            requestKey { call -> call.principal<StudentPrincipal>()?.studentId ?: "" }
        }
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
    monitor.subscribe(ApplicationStopped) { services.push.close() }
    routing {
        siteRoutes(services, downloads)
        apiRoutes(services)
        adminRoutes(services)
    }
}
