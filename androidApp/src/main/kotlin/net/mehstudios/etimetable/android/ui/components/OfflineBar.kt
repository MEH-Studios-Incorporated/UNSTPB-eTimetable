// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.datetime.toLocalDateTime
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.schedule.FacultyTimeZone
import kotlin.time.Instant

/** The thin bar of docs/screens.md, shown only while there is no connection. */
@Composable
fun OfflineBar(checkedAt: Instant?, modifier: Modifier = Modifier) {
    val text = if (checkedAt == null) {
        stringResource(R.string.offline_never)
    } else {
        val at = checkedAt.toLocalDateTime(FacultyTimeZone)
        stringResource(R.string.offline_since, "${at.date.longLabel()}, ${at.time.toString().take(5)}")
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFFEDEDED),
        modifier = modifier.fillMaxWidth().background(Color(0xFF292929)).padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
