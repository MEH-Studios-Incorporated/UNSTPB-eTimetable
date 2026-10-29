// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// "1.2.3" -> 10203; versionCode must grow with every release
val versionCodeFromName = version.toString().split('.').map { it.toIntOrNull() ?: 0 }
    .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }
    .coerceAtLeast(1)

// Development builds reach the server through `adb reverse` (tools/dev-server.sh).
// Release builds get the university's server address through -PapiBaseUrl.
val apiBaseUrl = providers.gradleProperty("apiBaseUrl").getOrElse("http://127.0.0.1:8080")

android {
    namespace = "net.mehstudios.etimetable.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "net.mehstudios.etimetable"
        minSdk = 26
        targetSdk = 37
        versionCode = versionCodeFromName
        versionName = version.toString()
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        manifestPlaceholders["usesCleartextTraffic"] = apiBaseUrl.startsWith("http://").toString()
    }

    // Set only in CI and the release workflow; without it release builds are unsigned.
    val keystore = providers.environmentVariable("ETT_KEYSTORE_FILE").orNull
    if (keystore != null) {
        signingConfigs.create("release") {
            storeFile = file(keystore)
            storePassword = providers.environmentVariable("ETT_KEYSTORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("ETT_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("ETT_KEY_PASSWORD").get()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // The app speaks only these two; libraries' other translations stay out of the APK.
    androidResources {
        localeFilters += listOf("en", "ro")
    }

    // The language is picked inside the app, so every install needs both, not a Play split.
    bundle {
        language {
            enableSplit = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.core.ktx)
    implementation(libs.ktor.client.okhttp)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
