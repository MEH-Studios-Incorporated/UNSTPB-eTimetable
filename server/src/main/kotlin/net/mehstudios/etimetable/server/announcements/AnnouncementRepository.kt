// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server.announcements

import net.mehstudios.etimetable.model.Announcement
import net.mehstudios.etimetable.server.db.query
import net.mehstudios.etimetable.server.db.transaction
import net.mehstudios.etimetable.server.db.update
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource
import kotlin.time.Clock
import kotlin.time.Instant

const val ANNOUNCEMENT_PAGE_SIZE = 20

class AnnouncementRepository(private val db: DataSource, private val clock: Clock = Clock.System) {
    /** Newest first; [before] is the id of the last announcement of the previous page. */
    suspend fun page(before: String?, limit: Int = ANNOUNCEMENT_PAGE_SIZE): List<Announcement> = db.transaction { c ->
        val columns = "id, title, body, author, pinned, published_at"
        val rows = if (before == null) {
            c.query(
                "SELECT $columns FROM announcement ORDER BY published_at DESC, id DESC LIMIT ?",
                limit,
                row = ::toAnnouncement,
            )
        } else {
            c.query(
                """
                SELECT $columns FROM announcement
                WHERE (published_at, id) < (SELECT published_at, id FROM announcement WHERE id = ?)
                ORDER BY published_at DESC, id DESC LIMIT ?
                """.trimIndent(),
                before,
                limit,
                row = ::toAnnouncement,
            )
        }
        rows
    }

    suspend fun publish(title: String, body: String, author: String, pinned: Boolean = false): Announcement {
        val announcement = Announcement(UUID.randomUUID().toString(), title, body, clock.now(), author, pinned)
        db.transaction { c ->
            c.update(
                "INSERT INTO announcement (id, title, body, author, pinned, published_at) VALUES (?, ?, ?, ?, ?, ?)",
                announcement.id,
                title,
                body,
                author,
                pinned,
                OffsetDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(announcement.publishedAt.toEpochMilliseconds()),
                    ZoneOffset.UTC,
                ),
            )
        }
        return announcement
    }

    private fun toAnnouncement(rs: java.sql.ResultSet) = Announcement(
        id = rs.getString("id"),
        title = rs.getString("title"),
        body = rs.getString("body"),
        author = rs.getString("author"),
        pinned = rs.getBoolean("pinned"),
        publishedAt = Instant.fromEpochMilliseconds(
            rs.getObject("published_at", OffsetDateTime::class.java).toInstant().toEpochMilli(),
        ),
    )
}
