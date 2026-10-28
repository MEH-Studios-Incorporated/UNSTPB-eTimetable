// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import io.ktor.client.call.body
import io.ktor.client.request.get
import net.mehstudios.etimetable.model.Announcement
import net.mehstudios.etimetable.server.announcements.ANNOUNCEMENT_PAGE_SIZE
import kotlin.test.Test
import kotlin.test.assertEquals

class AnnouncementRoutesTest {
    @Test
    fun pagesNewestFirst() = withServer { services, client ->
        val published = (1..25).map { services.announcements.publish("Anunț $it", "Text", "Secretariat") }

        val first = client.get("/api/v1/announcements").body<List<Announcement>>()
        assertEquals(ANNOUNCEMENT_PAGE_SIZE, first.size)
        assertEquals(published.last().id, first.first().id)

        val second = client.get("/api/v1/announcements?before=${first.last().id}").body<List<Announcement>>()
        assertEquals(5, second.size)
        assertEquals(published.first().id, second.last().id)
        assertEquals(25, (first + second).map { it.id }.toSet().size)
    }
}
