// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.ktlint) apply false
}

// The release tag is the only place the version is written (AGENTS.md section 8).
// CI passes it as -PappVersion=1.2.3; local builds are 0.0.0.
val appVersion = providers.gradleProperty("appVersion").getOrElse("0.0.0")

subprojects {
    version = appVersion
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        // generated sources, such as SQLDelight's queries
        filter { exclude { it.file.path.contains("${File.separator}build${File.separator}") } }
    }
}
