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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import net.mehstudios.etimetable.android.data.AppLocale
import net.mehstudios.etimetable.android.data.AppTheme
import net.mehstudios.etimetable.android.data.TimetablePrefs
import net.mehstudios.etimetable.android.ui.EtRoot
import net.mehstudios.etimetable.android.ui.theme.EtTheme
import net.mehstudios.etimetable.android.ui.theme.LocalDarkTheme
import net.mehstudios.etimetable.android.ui.theme.LocalScreenFade
import net.mehstudios.etimetable.android.ui.theme.LocalThemeChoice
import net.mehstudios.etimetable.android.ui.theme.LocalTimetableChoice
import net.mehstudios.etimetable.android.ui.theme.ThemeChoice
import net.mehstudios.etimetable.android.ui.theme.TimetableChoice

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
            var timetablePrefs by remember { mutableStateOf(TimetablePrefs.read(this)) }
            val timetableChoice = TimetableChoice(timetablePrefs) {
                TimetablePrefs.save(this, it)
                timetablePrefs = it
            }
            CompositionLocalProvider(LocalThemeChoice provides choice, LocalTimetableChoice provides timetableChoice) {
                EtTheme(mode) {
                    // status bar icons follow the app's theme, not the phone's
                    val dark = LocalDarkTheme.current
                    val background = MaterialTheme.colorScheme.background
                    LaunchedEffect(dark) {
                        val bars = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                        // what shows while the screen fades for a language switch
                        window.setBackgroundDrawable(background.toArgb().toDrawable())
                    }
                    // a language switch rebuilds the activity: the old screen fades out
                    // (LanguagePicker), the new one fades in here
                    val fade = remember { Animatable(if (AppLocale.switching) 0f else 1f) }
                    LaunchedEffect(Unit) {
                        if (AppLocale.switching) {
                            AppLocale.switching = false
                            fade.animateTo(1f, tween(280))
                        }
                    }
                    CompositionLocalProvider(LocalScreenFade provides fade) {
                        Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }) { EtRoot(container) }
                    }
                }
            }
        }
    }
}
