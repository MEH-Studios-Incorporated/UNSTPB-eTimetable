// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable

import kotlinx.serialization.json.Json
import net.mehstudios.etimetable.api.AppJson
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The server's development data and tests use SampleData as JSON. This keeps
 * the file in step: run with UPDATE_DEV_SEED=1 after changing SampleData.
 */
class DevSeedTest {
    @Test
    fun serverSeedMatchesSampleData() {
        val file = File("../server/src/test/resources/dev-seed.json")
        val expected = Json(AppJson) { prettyPrint = true }.encodeToString(SampleData.dataset) + "\n"
        if (System.getenv("UPDATE_DEV_SEED") == "1") file.writeText(expected)
        assertEquals(
            expected,
            file.readText(),
            "dev-seed.json is out of date; run the shared tests with UPDATE_DEV_SEED=1",
        )
    }
}
