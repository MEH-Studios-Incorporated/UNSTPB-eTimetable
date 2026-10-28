// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.server

/** Settings read from the environment; `deploy/.env.example` lists them all. */
data class ServerConfig(val port: Int) {
    companion object {
        /** From the jar manifest, so it is the release tag; "dev" when run from the IDE. */
        val version: String = ServerConfig::class.java.`package`?.implementationVersion ?: "dev"

        fun fromEnvironment(env: Map<String, String> = System.getenv()) = ServerConfig(
            port = env["ETT_PORT"]?.toIntOrNull() ?: 8080,
        )
    }
}
