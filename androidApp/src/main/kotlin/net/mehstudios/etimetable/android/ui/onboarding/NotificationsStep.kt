// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.ui.components.BrandBar
import net.mehstudios.etimetable.android.ui.components.EtIcons
import net.mehstudios.etimetable.android.ui.theme.ButtonShape

/**
 * The last step of the first start: says what notifications are for, then lets Android
 * ask. [onDone] runs whatever the student answers, and the step is not shown again.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun NotificationsStep(onDone: () -> Unit) {
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onDone()
    }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BrandBar()
            Column(
                Modifier.weight(1f).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    EtIcons.Bell,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 24.dp),
                )
                Text(
                    stringResource(R.string.push_ask_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(R.string.push_ask_text),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    stringResource(R.string.push_ask_later),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    shape = ButtonShape,
                    onClick = { ask.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text(stringResource(R.string.push_allow)) }
                OutlinedButton(
                    shape = ButtonShape,
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text(stringResource(R.string.push_not_now)) }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
