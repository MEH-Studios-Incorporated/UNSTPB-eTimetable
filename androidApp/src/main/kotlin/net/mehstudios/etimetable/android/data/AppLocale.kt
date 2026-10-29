// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

/**
 * The language the student picked in the app: "ro", "en", or null to follow the phone.
 *
 * Android 13 and newer keep it themselves (it also shows under the phone's "App languages"
 * setting). Older versions read it from our preferences in [wrap], before the activity draws.
 */
object AppLocale {
    val supported = listOf("ro", "en")

    private const val PREFS = "locale"
    private const val KEY = "language"

    fun picked(context: Context): String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java).applicationLocales.takeIf { !it.isEmpty }
            ?.get(0)?.language?.takeIf { it in supported }
    } else {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
    }

    /** Switches the app to [language]; the activity is drawn again in it. */
    fun pick(activity: Activity, language: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // the system restarts the activity with the new configuration
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (language == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, language) }
            activity.recreate()
        }
    }

    /** For Activity.attachBaseContext on Android 12 and older. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val language = picked(base) ?: return base
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(configuration)
    }
}
