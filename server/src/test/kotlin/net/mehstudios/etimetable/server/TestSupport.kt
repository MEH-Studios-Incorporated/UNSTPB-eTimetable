// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import net.mehstudios.etimetable.api.AppJson
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.server.db.migrate
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

/** One real PostgreSQL for the whole test run, with a new database per test. */
object TestPostgres {
    private val postgres: EmbeddedPostgres by lazy { EmbeddedPostgres.start() }
    private val counter = AtomicInteger()

    fun freshDatabase(): DataSource {
        val name = "test_${counter.incrementAndGet()}"
        postgres.postgresDatabase.connection.use { it.createStatement().execute("CREATE DATABASE $name") }
        return postgres.getDatabase("postgres", name).also(::migrate)
    }
}

fun sampleDataset(): Dataset =
    AppJson.decodeFromString(Dataset.serializer(), TestPostgres::class.java.getResource("/dev-seed.json")!!.readText())

fun withServer(block: suspend ApplicationTestBuilder.(services: Services, client: HttpClient) -> Unit) =
    testApplication {
        val services = Services(TestPostgres.freshDatabase())
        application { module(services) }
        val client = createClient { install(ContentNegotiation) { json(AppJson) } }
        block(services, client)
    }
