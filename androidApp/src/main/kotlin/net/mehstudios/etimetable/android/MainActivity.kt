// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import net.mehstudios.etimetable.android.data.AppLocale
import net.mehstudios.etimetable.android.data.AppTheme
import net.mehstudios.etimetable.android.ui.EtRoot
import net.mehstudios.etimetable.android.ui.theme.EtTheme
import net.mehstudios.etimetable.android.ui.theme.LocalDarkTheme
import net.mehstudios.etimetable.android.ui.theme.LocalThemeChoice
import net.mehstudios.etimetable.android.ui.theme.ThemeChoice

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as EtimetableApp).container
        setContent {
            var mode by remember { mutableStateOf(AppTheme.read(this)) }
            val choice = ThemeChoice(mode) {
                AppTheme.save(this, it)
                mode = it
            }
            CompositionLocalProvider(LocalThemeChoice provides choice) {
                EtTheme(mode) {
                    // status bar icons follow the app's theme, not the phone's
                    val dark = LocalDarkTheme.current
                    LaunchedEffect(dark) {
                        val bars = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                    }
                    EtRoot(container)
                }
            }
        }
    }
}
