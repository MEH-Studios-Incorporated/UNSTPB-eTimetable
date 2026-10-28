// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import net.mehstudios.etimetable.server.db.migrate
import java.io.File

/**
 * The development server (`./gradlew :server:runDev`): PostgreSQL runs
 * embedded with its data in server/build/dev-postgres, and an empty
 * database gets the sample semester and two announcements.
 */
fun main() {
    val postgres = EmbeddedPostgres.builder()
        .setDataDirectory(File("build/dev-postgres"))
        .setCleanDataDirectory(false)
        .setPort(54329)
        .start()
    Runtime.getRuntime().addShutdownHook(Thread { postgres.close() })

    val db = postgres.postgresDatabase
    migrate(db)
    val services = Services(db)
    runBlocking {
        if (services.datasets.current() == null) {
            services.datasets.replace(sampleDataset(), makeCurrent = true)
            services.announcements.publish(
                "Orarul semestrului 1 este publicat",
                "Verificați grupa și subgrupa din profil.",
                "Secretariat",
                pinned = true,
            )
            services.announcements.publish(
                "Laboratorul de Managementul calității se mută",
                "Începând cu 4 noiembrie, laboratoarele se țin în BN 212 a.",
                "Prodecanat",
            )
            println("Development database filled with the sample semester")
        }
    }
    val port = System.getenv("ETT_PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module(services) }.start(wait = true)
}
