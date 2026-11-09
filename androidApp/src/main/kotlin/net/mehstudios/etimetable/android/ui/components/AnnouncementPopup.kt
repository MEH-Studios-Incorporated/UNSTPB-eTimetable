// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.data.AnnouncementAlert

/** Long enough to read a title, short enough not to stay in the way. */
private const val SHOWN_MS = 6_000L

/**
 * The bar is as wide as its text, up to about three quarters of a phone's width (324 dp with
 * the icons), so it never covers the whole bottom of the screen.
 */
private val MAX_TEXT_WIDTH = 220.dp

/**
 * The bar that says new announcements arrived while the app is open. A second announcement
 * replaces it with a summary. Tapping runs [onOpen]; after a few seconds it hides itself
 * through [onDismiss].
 */
@Composable
fun AnnouncementPopup(
    alert: AnnouncementAlert?,
    onOpen: (AnnouncementAlert) -> Unit,
    onDismiss: (AnnouncementAlert) -> Unit,
    modifier: Modifier = Modifier,
) {
    // a newer alert restarts the time it stays
    LaunchedEffect(alert) {
        val current = alert ?: return@LaunchedEffect
        delay(SHOWN_MS)
        onDismiss(current)
    }
    // it appears and goes without motion: nothing animates on its own (docs/design.md)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
        if (alert != null) PopupBar(alert, onOpen)
    }
}

@Composable
private fun PopupBar(alert: AnnouncementAlert, onOpen: (AnnouncementAlert) -> Unit) {
    val count = alert.ids.size
    val label = stringResource(
        if (alert.single) R.string.popup_new_announcement else R.string.popup_new_announcements,
    )
    val summary = pluralStringResource(R.plurals.popup_to_read, count, count)
    val text = if (alert.single) alert.title else summary
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 2.dp,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .clickable { onOpen(alert) }
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(EtIcons.Bell, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            // a weight here would stretch the row to the full width
            Column(Modifier.widthIn(max = MAX_TEXT_WIDTH)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                EtIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
