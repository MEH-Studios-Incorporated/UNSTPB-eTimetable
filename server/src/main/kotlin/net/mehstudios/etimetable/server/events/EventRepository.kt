// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.events

import net.mehstudios.etimetable.model.Event
import net.mehstudios.etimetable.server.db.query
import net.mehstudios.etimetable.server.db.transaction
import net.mehstudios.etimetable.server.db.update
import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** How long after it ends an event stays in the apps, under "Trecute". */
val EVENT_KEPT_AFTER = EventRepository.KEPT_DAYS.days

/** What an admin writes about an event; the picture comes separately. */
data class EventFields(
    val title: String,
    val body: String,
    val place: String?,
    val link: String?,
    val startsAt: Instant,
    val endsAt: Instant?,
)

class EventRepository(private val db: DataSource, private val clock: Clock = Clock.System) {
    /** What the apps get: upcoming and recent events, earliest first. */
    suspend fun forApps(): List<Event> = db.transaction { c ->
        c.query(
            "$SELECT WHERE coalesce(e.ends_at, e.starts_at) > ? ORDER BY e.starts_at, e.id",
            timestamp(clock.now() - EVENT_KEPT_AFTER),
            row = ::toEvent,
        )
    }

    /** Every event, newest start first, for the dashboard. */
    suspend fun all(): List<Event> = db.transaction { c ->
        c.query("$SELECT ORDER BY e.starts_at DESC, e.id", row = ::toEvent)
    }

    suspend fun byId(id: String): Event? = db.transaction { c ->
        c.query("$SELECT WHERE e.id = ?", id, row = ::toEvent).singleOrNull()
    }

    /** The picture's bytes for `GET /api/v1/images/<hash>.jpg`. */
    suspend fun image(hash: String): ByteArray? = db.transaction { c ->
        c.query("SELECT data FROM event_image WHERE hash = ?", hash) { it.getBytes(1) }.singleOrNull()
    }

    /**
     * Keeps an uploaded picture before its event is saved, so the dashboard's preview can show
     * it and the form can send it back by [StoredImage.hash]. One no event uses goes after a day.
     */
    suspend fun stage(image: StoredImage): String {
        db.transaction { c ->
            c.update(
                "INSERT INTO event_image (hash, data, width, height) VALUES (?, ?, ?, ?) ON CONFLICT (hash) DO NOTHING",
                image.hash,
                image.data,
                image.width,
                image.height,
            )
        }
        return image.hash
    }

    /** Width and height of a kept picture, or null when there is none with that hash. */
    suspend fun imageSize(hash: String): Pair<Int, Int>? = db.transaction { c ->
        c.query("SELECT width, height FROM event_image WHERE hash = ?", hash) { it.getInt(1) to it.getInt(2) }
            .singleOrNull()
    }

    /** [imageHash] is a picture kept with [stage]. */
    suspend fun publish(fields: EventFields, imageHash: String): Event {
        val id = UUID.randomUUID().toString()
        db.transaction { c ->
            dropAbandoned(c)
            c.update(
                """
                INSERT INTO event (id, title, body, place, link, starts_at, ends_at, image_hash, published_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                id,
                fields.title,
                fields.body,
                fields.place,
                fields.link,
                timestamp(fields.startsAt),
                fields.endsAt?.let(::timestamp),
                imageHash,
                timestamp(clock.now()),
            )
        }
        return byId(id)!!
    }

    /** Changes the text and, when [imageHash] differs, the picture (kept with [stage]). */
    suspend fun update(id: String, fields: EventFields, imageHash: String) {
        db.transaction { c ->
            val old = c.query("SELECT image_hash FROM event WHERE id = ?", id) { it.getString(1) }.singleOrNull()
                ?: return@transaction
            c.update(
                """
                UPDATE event SET title = ?, body = ?, place = ?, link = ?, starts_at = ?, ends_at = ?, image_hash = ?
                WHERE id = ?
                """.trimIndent(),
                fields.title,
                fields.body,
                fields.place,
                fields.link,
                timestamp(fields.startsAt),
                fields.endsAt?.let(::timestamp),
                imageHash,
                id,
            )
            if (imageHash != old) dropUnused(c, old)
            dropAbandoned(c)
        }
    }

    suspend fun delete(id: String) {
        db.transaction { c ->
            val old = c.query("SELECT image_hash FROM event WHERE id = ?", id) { it.getString(1) }.singleOrNull()
                ?: return@transaction
            c.update("DELETE FROM event WHERE id = ?", id)
            dropUnused(c, old)
        }
    }

    /** Pictures uploaded for a preview that was never published. */
    private fun dropAbandoned(c: Connection) {
        c.update(
            """
            DELETE FROM event_image i WHERE i.created_at < now() - interval '1 day'
            AND NOT EXISTS (SELECT 1 FROM event e WHERE e.image_hash = i.hash)
            """.trimIndent(),
        )
    }

    /** A picture no event shows any more goes, so the database does not keep old photos. */
    private fun dropUnused(c: Connection, hash: String) {
        c.update(
            "DELETE FROM event_image WHERE hash = ? AND NOT EXISTS (SELECT 1 FROM event WHERE image_hash = ?)",
            hash,
            hash,
        )
    }

    private fun toEvent(rs: ResultSet) = Event(
        id = rs.getString("id"),
        title = rs.getString("title"),
        startsAt = instant(rs.getObject("starts_at", OffsetDateTime::class.java))!!,
        endsAt = instant(rs.getObject("ends_at", OffsetDateTime::class.java)),
        place = rs.getString("place"),
        body = rs.getString("body"),
        link = rs.getString("link"),
        image = imagePath(rs.getString("image_hash")),
        imageRatio = rs.getInt("width").toFloat() / rs.getInt("height"),
        publishedAt = instant(rs.getObject("published_at", OffsetDateTime::class.java))!!,
    )

    companion object {
        const val KEPT_DAYS = 60

        private const val SELECT = """
            SELECT e.id, e.title, e.body, e.place, e.link, e.starts_at, e.ends_at, e.image_hash, e.published_at,
                   i.width, i.height
            FROM event e JOIN event_image i ON i.hash = e.image_hash
        """

        fun imagePath(hash: String) = "/api/v1/images/$hash.jpg"

        private fun timestamp(instant: Instant): OffsetDateTime =
            OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds()), ZoneOffset.UTC)

        private fun instant(value: OffsetDateTime?): Instant? =
            value?.let { Instant.fromEpochMilliseconds(it.toInstant().toEpochMilli()) }
    }
}
