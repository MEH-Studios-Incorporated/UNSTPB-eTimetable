// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

/** Settings read from the environment; `deploy/.env.example` lists them all. */
data class ServerConfig(
    val port: Int,
    val database: DatabaseConfig,
    /** Address the server is reached at; an https:// one makes the dashboard cookie Secure. */
    val publicUrl: String? = null,
    /** First admin account, created only while there is none. */
    val firstAdminEmail: String? = null,
    val firstAdminPassword: String? = null,
) {
    companion object {
        /** From the jar manifest, so it is the release tag; "dev" when run from the IDE. */
        val version: String = ServerConfig::class.java.`package`?.implementationVersion ?: "dev"

        fun fromEnvironment(env: Map<String, String> = System.getenv()): ServerConfig {
            fun required(name: String) = env[name]?.takeIf { it.isNotBlank() }
                ?: error("$name is not set; see deploy/.env.example")
            return ServerConfig(
                port = env["ETT_PORT"]?.toIntOrNull() ?: 8080,
                database = DatabaseConfig(
                    url = required("ETT_DB_URL"),
                    user = required("ETT_DB_USER"),
                    password = required("ETT_DB_PASSWORD"),
                ),
                publicUrl = env["ETT_PUBLIC_URL"]?.takeIf { it.isNotBlank() },
                firstAdminEmail = env["ETT_ADMIN_EMAIL"],
                firstAdminPassword = env["ETT_ADMIN_PASSWORD"],
            )
        }
    }
}

data class DatabaseConfig(val url: String, val user: String, val password: String)
