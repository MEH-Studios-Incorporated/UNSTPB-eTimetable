// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "net.mehstudios.etimetable.server.ApplicationKt"
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.html.builder)
    implementation(libs.ktor.server.sessions)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.postgresql)
    implementation(libs.bouncycastle)
    implementation(libs.logback.classic)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.embedded.postgres)
    testImplementation(platform(libs.embedded.postgres.binaries.bom))
}

tasks.jar {
    manifest {
        attributes("Implementation-Version" to project.version)
    }
}

// The development server: its own PostgreSQL under build/dev-postgres and the
// sample semester. Started by tools/dev-server.sh.
tasks.register<JavaExec>("runDev") {
    group = "application"
    description = "Runs the server with an embedded PostgreSQL and sample data."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "net.mehstudios.etimetable.server.DevServerKt"
    workingDir = projectDir
}
