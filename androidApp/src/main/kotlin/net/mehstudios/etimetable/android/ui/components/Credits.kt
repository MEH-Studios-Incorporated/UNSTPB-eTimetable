// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.components

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.data.AppLocale
import net.mehstudios.etimetable.android.ui.signin.invertInDark
import net.mehstudios.etimetable.android.ui.theme.LocalDarkTheme
import net.mehstudios.etimetable.android.ui.theme.LocalScreenFade

const val MEH_SITE = "https://mehstudios.net/"
const val SUPPORT_EMAIL = "support@mehstudios.net"

/**
 * The Multimedia Entertainment Hub block: mark, copyright, site and support address.
 * [small] is the quiet footer version, without the links.
 */
@Composable
fun MehCredit(modifier: Modifier = Modifier, small: Boolean = false) {
    val uri = LocalUriHandler.current
    Column(
        modifier,
        horizontalAlignment = if (small) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (small) 8.dp else 14.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.meh_mark),
                contentDescription = null,
                colorFilter = if (LocalDarkTheme.current) null else invertInDark,
                modifier = Modifier.size(if (small) 20.dp else 34.dp),
            )
            Column {
                Text(
                    stringResource(R.string.copyright_owner),
                    style = if (small) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    },
                    color = if (small) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                )
                Text(
                    stringResource(R.string.copyright_rights),
                    style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (small) return@Column
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            LinkText(stringResource(R.string.meh_site), MaterialTheme.typography.bodyMedium) { uri.openUri(MEH_SITE) }
            LinkText(SUPPORT_EMAIL, MaterialTheme.typography.bodyMedium) { uri.openUri("mailto:$SUPPORT_EMAIL") }
        }
    }
}

@Composable
fun LinkText(text: String, style: TextStyle = MaterialTheme.typography.bodyLarge, onClick: () -> Unit) {
    Text(
        text,
        style = style,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
    )
}

/**
 * Switches the app's language: "ro", "en", or null for the phone's. The screen fades out,
 * the activity is drawn again in the new language and fades back in (MainActivity).
 */
@Composable
fun rememberLanguageSwitch(): (String?) -> Unit {
    val activity = LocalActivity.current
    val fade = LocalScreenFade.current
    val scope = rememberCoroutineScope()
    return { language ->
        if (activity != null) {
            scope.launch {
                fade?.animateTo(0f, tween(160))
                AppLocale.switching = true
                AppLocale.pick(activity, language)
                // nothing rebuilt the activity (the language did not really change): show it again
                delay(800)
                AppLocale.switching = false
                fade?.animateTo(1f, tween(200))
            }
        }
    }
}

/** The language picked in the app, or null when it follows the phone. */
@Composable
fun pickedLanguage(): String? {
    val activity = LocalActivity.current ?: return null
    return remember(activity) { AppLocale.picked(activity) }
}

/** RO and EN on the sign-in page, where there is room for nothing more. */
@Composable
fun LanguageToggle(modifier: Modifier = Modifier) {
    val current = pickedLanguage() ?: appLanguage()
    val switch = rememberLanguageSwitch()
    SingleChoiceSegmentedButtonRow(modifier) {
        AppLocale.supported.forEachIndexed { index, language ->
            SegmentedButton(
                selected = language == current,
                onClick = { if (language != current) switch(language) },
                shape = SegmentedButtonDefaults.itemShape(index, AppLocale.supported.size),
                icon = {},
            ) { Text(language.uppercase()) }
        }
    }
}
