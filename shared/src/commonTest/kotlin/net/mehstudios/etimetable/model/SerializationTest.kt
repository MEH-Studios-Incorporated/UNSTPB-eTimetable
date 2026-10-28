// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.model

import net.mehstudios.etimetable.SampleData
import net.mehstudios.etimetable.api.AppJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SerializationTest {
    @Test
    fun datasetRoundTrips() {
        val json = AppJson.encodeToString(SampleData.dataset)
        assertEquals(SampleData.dataset, AppJson.decodeFromString<Dataset>(json))
    }

    @Test
    fun weekRulesAndSlotsHaveAReadableShape() {
        val slot = SampleData.activity("mkt-p-1541").slot
        val json = AppJson.encodeToString(slot)
        assertTrue("\"isoDay\":3" in json, json)
        assertTrue("\"start\":\"16:00\"" in json, json)
        assertTrue("\"type\":\"odd\"" in json, json)
        assertEquals(
            WeekRule.Only(setOf(2, 4)),
            AppJson.decodeFromString<WeekRule>("""{"type":"only","weeks":[2,4]}"""),
        )
    }

    @Test
    fun enrollmentKinds() {
        val profile = Profile(enrollment = Enrollment.National("1541E", "1541E/A"))
        val json = AppJson.encodeToString(profile)
        assertTrue("\"type\":\"national\"" in json, json)
        assertEquals(profile, AppJson.decodeFromString<Profile>(json))
    }
}
