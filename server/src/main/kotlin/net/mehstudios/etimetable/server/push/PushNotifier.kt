// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.push

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import net.mehstudios.etimetable.api.PushFields
import net.mehstudios.etimetable.api.PushPlatform
import net.mehstudios.etimetable.model.Announcement
import org.slf4j.LoggerFactory
import kotlin.time.Duration

/** Most messages in flight at once; FCM answers each in about 100 ms. */
private const val PARALLEL_SENDS = 8

/** The notification shows two lines of the text; the app has the rest after its sync. */
private const val PREVIEW_LENGTH = 180

/**
 * Sends every new announcement to every registered phone. [senders] has one entry per
 * platform the deployment has credentials for; phones of the other platforms are skipped.
 * [clockCheck] compares this server's clock with Google's; null skips the comparison.
 */
class PushNotifier(
    private val tokens: PushTokens,
    private val senders: Map<PushPlatform, PushSender>,
    private val clockCheck: ClockCheck? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val log = LoggerFactory.getLogger(PushNotifier::class.java)

    val enabled: Boolean get() = senders.isNotEmpty()

    /**
     * How far this server's clock is off Google's, when it is too far for any message to go
     * out. Null when push is off or the clock is fine.
     */
    suspend fun clockDrift(): Duration? = if (enabled) clockCheck?.drift() else null

    /** Starts sending in the background, so publishing does not wait for the gateways. */
    fun announce(announcement: Announcement): Job = scope.launch {
        if (!enabled) return@launch
        val devices = tokens.all().filter { it.platform in senders }
        val data = messageOf(announcement)
        val permits = Semaphore(PARALLEL_SENDS)
        val results = try {
            coroutineScope {
                devices.map { device ->
                    async { device.token to permits.withPermit { sendOne(device, data) } }
                }.awaitAll().toMap()
            }
        } catch (e: ServiceAccountRefused) {
            // every other phone would get the same answer, so the run stops here
            log.error("Announcement {} was not sent: {}", announcement.id, e.message)
            return@launch
        }
        val gone = results.filterValues { it == PushResult.GONE }.keys
        tokens.forget(gone)
        val failed = results.count { it.value == PushResult.FAILED }
        log.info(
            "Announcement {} sent to {} phones, {} gone, {} failed",
            announcement.id,
            devices.size - gone.size - failed,
            gone.size,
            failed,
        )
    }

    /** Stops the sends still running, when the server shuts down. */
    fun close() {
        scope.cancel()
    }

    private suspend fun sendOne(device: PushDevice, data: Map<String, String>): PushResult {
        val sender = senders.getValue(device.platform)
        return try {
            sender.send(device.token, data)
        } catch (e: ServiceAccountRefused) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Push to a {} phone failed", device.platform, e)
            PushResult.FAILED
        }
    }

    companion object {
        fun messageOf(announcement: Announcement): Map<String, String> = mapOf(
            PushFields.KIND to PushFields.KIND_ANNOUNCEMENT,
            PushFields.ID to announcement.id,
            PushFields.TITLE to announcement.title,
            PushFields.TEXT to preview(announcement.body),
        )

        /** The body as plain text: Markdown marks dropped, lines joined, cut at a word. */
        fun preview(markdown: String): String {
            val plain = markdown
                .replace(Regex("""\[([^\]]*)]\([^)]*\)"""), "$1")
                .replace(Regex("""(?m)^\s*(#+|[-*+]|\d+\.)\s+"""), "")
                .replace(Regex("""[*_`]"""), "")
                .replace(Regex("""\s+"""), " ")
                .trim()
            if (plain.length <= PREVIEW_LENGTH) return plain
            val cut = plain.take(PREVIEW_LENGTH).substringBeforeLast(' ')
            return "$cut…"
        }
    }
}
