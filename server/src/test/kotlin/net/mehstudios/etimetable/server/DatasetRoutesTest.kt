// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import net.mehstudios.etimetable.api.ApiError
import net.mehstudios.etimetable.api.ErrorCodes
import net.mehstudios.etimetable.api.Health
import net.mehstudios.etimetable.model.Dataset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DatasetRoutesTest {
    @Test
    fun healthChecksTheDatabase() = withServer { _, client ->
        val response = client.get("/api/v1/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.body<Health>().status)
    }

    @Test
    fun noSemesterYet() = withServer { _, client ->
        val response = client.get("/api/v1/dataset")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ErrorCodes.NO_SEMESTER, response.body<ApiError>().code)
    }

    @Test
    fun servesTheCurrentSemesterWithAnETag() = withServer { services, client ->
        services.datasets.replace(sampleDataset(), makeCurrent = true)

        val first = client.get("/api/v1/dataset")
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(sampleDataset(), first.body<Dataset>())
        val etag = first.headers[HttpHeaders.ETag]!!

        val again = client.get("/api/v1/dataset") { header(HttpHeaders.IfNoneMatch, etag) }
        assertEquals(HttpStatusCode.NotModified, again.status)

        // any change gives a new version, so the app downloads again
        val changed = sampleDataset().let { it.copy(rooms = it.rooms.drop(1)) }
        services.datasets.replace(changed, makeCurrent = true)
        val after = client.get("/api/v1/dataset") { header(HttpHeaders.IfNoneMatch, etag) }
        assertEquals(HttpStatusCode.OK, after.status)
        assertNotEquals(etag, after.headers[HttpHeaders.ETag])
        assertEquals(changed, after.body<Dataset>())
    }

    @Test
    fun onlyOneSemesterIsCurrent() = withServer { services, client ->
        val sample = sampleDataset()
        services.datasets.replace(sample, makeCurrent = true)
        services.datasets.replace(sample.copy(semester = sample.semester.copy(id = "2026-2")), makeCurrent = true)
        assertEquals("2026-2", client.get("/api/v1/dataset").body<Dataset>().semester.id)
    }
}
