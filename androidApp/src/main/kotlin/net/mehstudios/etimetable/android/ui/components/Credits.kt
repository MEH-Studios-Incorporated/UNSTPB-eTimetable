// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.components

import androidx.activity.compose.LocalActivity
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.data.AppLocale
import net.mehstudios.etimetable.android.data.ThemeMode
import net.mehstudios.etimetable.android.ui.signin.invertInDark
import net.mehstudios.etimetable.android.ui.theme.LocalDarkTheme
import net.mehstudios.etimetable.android.ui.theme.LocalThemeChoice

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
 * Română or English for the whole app. With [followPhone] there is a third choice that
 * goes back to the phone's language; [short] shows "RO" and "EN" for tight spots.
 */
@Composable
fun LanguagePicker(modifier: Modifier = Modifier, followPhone: Boolean = true, short: Boolean = false) {
    val activity = LocalActivity.current ?: return
    val picked = remember(activity) { AppLocale.picked(activity) }
    val options = (if (followPhone) listOf(null) else emptyList()) + AppLocale.supported
    val current = if (followPhone) picked else picked ?: appLanguage()
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, language ->
            SegmentedButton(
                selected = language == current,
                onClick = { if (language != current) AppLocale.pick(activity, language) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
            ) {
                Text(
                    when {
                        language == null -> stringResource(R.string.language_phone)
                        short -> language.uppercase()
                        language == "ro" -> "Română"
                        else -> "English"
                    },
                )
            }
        }
    }
}

/** Same as the phone, light or dark. */
@Composable
fun ThemePicker(modifier: Modifier = Modifier) {
    val choice = LocalThemeChoice.current
    val options = ThemeMode.entries
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == choice.mode,
                onClick = { choice.change(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
            ) {
                Text(
                    stringResource(
                        when (mode) {
                            ThemeMode.SYSTEM -> R.string.language_phone
                            ThemeMode.LIGHT -> R.string.theme_light
                            ThemeMode.DARK -> R.string.theme_dark
                        },
                    ),
                )
            }
        }
    }
}
