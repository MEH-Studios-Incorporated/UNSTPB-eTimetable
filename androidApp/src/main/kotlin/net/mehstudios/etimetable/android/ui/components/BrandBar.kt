// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.ui.signin.invertInDark

/** The seal and the app's name at the top of the main screens; [trailing] sits on the right. */
@Composable
fun BrandBar(modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    val dark = isSystemInDarkTheme()
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.faima_seal),
            contentDescription = null,
            colorFilter = if (dark) invertInDark else null,
            modifier = Modifier.size(30.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                stringResource(R.string.brand_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}
