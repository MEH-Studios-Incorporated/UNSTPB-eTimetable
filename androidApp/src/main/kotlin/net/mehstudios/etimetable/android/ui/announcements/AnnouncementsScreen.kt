// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.announcements

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.toLocalDateTime
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.ui.components.BrandBar
import net.mehstudios.etimetable.android.ui.components.EtIcons
import net.mehstudios.etimetable.android.ui.components.MarkdownText
import net.mehstudios.etimetable.android.ui.components.longLabel
import net.mehstudios.etimetable.android.ui.components.markdownPreview
import net.mehstudios.etimetable.model.Announcement
import net.mehstudios.etimetable.schedule.FacultyTimeZone
import net.mehstudios.etimetable.store.StoredAnnouncement

/** Previews, newest first and pinned on top; tapping one opens it in full. */
@Composable
fun AnnouncementsScreen(
    announcements: List<StoredAnnouncement>,
    actions: @Composable () -> Unit,
    open: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        BrandBar { actions() }
        Text(
            stringResource(R.string.tab_announcements),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        )
        if (announcements.isEmpty()) {
            Text(
                stringResource(R.string.announcements_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(announcements, key = { it.announcement.id }) { stored ->
                val a = stored.announcement
                Row(
                    Modifier.fillMaxWidth().clickable { open(a.id) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.padding(
                            top = 7.dp,
                        ).size(
                            8.dp,
                        ).background(
                            if (stored.read) Color.Transparent else MaterialTheme.colorScheme.error,
                            CircleShape,
                        ),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (a.pinned) PinnedLabel()
                        Text(
                            a.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = if (stored.read) FontWeight.Medium else FontWeight.SemiBold,
                            ),
                        )
                        Text(
                            markdownPreview(a.body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            meta(a),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

/** One announcement in full; opening it marks it read. */
@Composable
fun AnnouncementScreen(announcement: Announcement, markRead: () -> Unit, onBack: () -> Unit) {
    LaunchedEffect(announcement.id) { markRead() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TextButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) {
            Text(stringResource(R.string.back))
        }
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (announcement.pinned) PinnedLabel()
            Text(announcement.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                meta(announcement),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            MarkdownText(announcement.body)
        }
    }
}

@Composable
private fun PinnedLabel() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            EtIcons.Pin,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.pinned),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun meta(a: Announcement): String {
    val at = a.publishedAt.toLocalDateTime(FacultyTimeZone)
    return stringResource(R.string.announcement_from, "${at.date.longLabel()}, ${at.time.toString().take(5)}", a.author)
}
