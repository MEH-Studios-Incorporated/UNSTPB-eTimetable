// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import net.mehstudios.etimetable.server.db.migrate
import net.mehstudios.etimetable.server.timetable.TimetableSheet
import java.io.File

/**
 * The development server (`./gradlew :server:runDev`): PostgreSQL runs
 * embedded with its data in server/build/dev-postgres, and an empty
 * database gets the sample semester, two announcements and a dashboard
 * account (DEV_ADMIN_EMAIL, DEV_ADMIN_PASSWORD). When build/faima-timetable.csv
 * exists (tools/faima-timetable/update.sh), FAIMA's real timetable replaces
 * the sample one on every start.
 */
const val DEV_ADMIN_EMAIL = "admin@etimetable.test"
const val DEV_ADMIN_PASSWORD = "parola-dev"

fun main() {
    // A second instance (for example a test run next to your own) needs its own folder and port.
    val env = System.getenv()
    val postgres = EmbeddedPostgres.builder()
        .setDataDirectory(File(env["ETT_DEV_DB_DIR"] ?: "build/dev-postgres"))
        .setCleanDataDirectory(false)
        .setPort(env["ETT_DEV_DB_PORT"]?.toIntOrNull() ?: 54329)
        // the first start creates the data folder, which can take a while
        .setPGStartupWait(java.time.Duration.ofSeconds(60))
        .start()
    Runtime.getRuntime().addShutdownHook(Thread { postgres.close() })

    val db = postgres.postgresDatabase
    migrate(db)
    val services = Services(db)
    runBlocking {
        if (services.admins.bootstrap(DEV_ADMIN_EMAIL, DEV_ADMIN_PASSWORD)) {
            println("Dashboard: http://localhost:8080/admin, $DEV_ADMIN_EMAIL / $DEV_ADMIN_PASSWORD")
        }
        val faima = File("build/faima-timetable.csv")
        if (faima.exists()) {
            // FAIMA's real timetable from tools/faima-timetable/update.sh, on the sample calendar
            val result = TimetableSheet.import(faima.readText(), sampleDataset().semester)
            val dataset = result.dataset
            if (dataset == null) {
                println("faima-timetable.csv not imported:\n  " + result.errors.joinToString("\n  "))
            } else {
                services.datasets.replace(dataset, makeCurrent = true)
                println(
                    "Imported FAIMA's timetable: ${dataset.activities.size} activities, ${dataset.groups.size} groups, ${result.warnings.size} double bookings",
                )
            }
        }
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
