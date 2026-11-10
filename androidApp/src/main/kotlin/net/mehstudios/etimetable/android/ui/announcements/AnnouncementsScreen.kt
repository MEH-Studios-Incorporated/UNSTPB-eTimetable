// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.announcements

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.ui.components.BrandBar
import net.mehstudios.etimetable.android.ui.components.EtIcons
import net.mehstudios.etimetable.android.ui.components.MarkdownText
import net.mehstudios.etimetable.android.ui.components.PageBar
import net.mehstudios.etimetable.android.ui.components.RefreshBox
import net.mehstudios.etimetable.android.ui.components.SectionHeading
import net.mehstudios.etimetable.android.ui.components.longLabel
import net.mehstudios.etimetable.android.ui.components.markdownPreview
import net.mehstudios.etimetable.android.ui.components.reducedMotion
import net.mehstudios.etimetable.model.Announcement
import net.mehstudios.etimetable.schedule.FacultyTimeZone
import net.mehstudios.etimetable.store.StoredAnnouncement

/** Previews, pinned on top, then newest first under a heading per day; tapping one opens it in full. */
@Composable
fun AnnouncementsScreen(
    announcements: List<StoredAnnouncement>,
    today: LocalDate,
    actions: @Composable () -> Unit,
    markAllRead: () -> Unit,
    refresh: suspend () -> Unit,
    open: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    val visible = announcements.filter {
        (!unreadOnly || !it.read) &&
            (it.announcement.title + " " + markdownPreview(it.announcement.body) + " " + it.announcement.author)
                .contains(query.trim(), ignoreCase = true)
    }
    val (pinned, rest) = visible.partition { it.announcement.pinned }
    // the store keeps them newest first, so the days come out in order
    val days = rest.groupBy { it.announcement.publishedAt.toLocalDateTime(FacultyTimeZone).date }
    val still = reducedMotion()
    Column(Modifier.fillMaxSize()) {
        BrandBar { actions() }
        Text(
            stringResource(R.string.tab_announcements),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text(stringResource(R.string.announcements_search)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = unreadOnly, onClick = {
                unreadOnly = !unreadOnly
            }, label = { Text(stringResource(R.string.announcements_unread)) })
            TextButton(
                onClick = markAllRead,
                enabled = announcements.any {
                    !it.read
                },
            ) { Text(stringResource(R.string.announcements_mark_all)) }
        }
        RefreshBox(refresh, Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (visible.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(
                                if (announcements.isEmpty()) {
                                    R.string.announcements_empty
                                } else {
                                    R.string.announcements_no_matches
                                },
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                if (pinned.isNotEmpty()) {
                    item(key = "pinned") {
                        DayHeading(stringResource(R.string.announcements_pinned), Modifier.moving(this, still))
                    }
                    items(pinned, key = { it.announcement.id }) {
                        AnnouncementRow(it, Modifier.moving(this, still)) { open(it.announcement.id) }
                    }
                }
                days.forEach { (day, list) ->
                    item(key = "day-$day") { DayHeading(dayHeading(day, today), Modifier.moving(this, still)) }
                    items(list, key = { it.announcement.id }) {
                        AnnouncementRow(it, Modifier.moving(this, still)) { open(it.announcement.id) }
                    }
                }
            }
        }
    }
}

/** List items fade and slide into place in 150 ms, or jump when the phone has animations off. */
private fun Modifier.moving(scope: LazyItemScope, still: Boolean): Modifier = if (still) {
    this
} else {
    with(scope) { animateItem(fadeInSpec = tween(150), placementSpec = tween(150), fadeOutSpec = tween(150)) }
}

/** "Azi", "Ieri", then "Luni, 9 noiembrie". */
@Composable
private fun dayHeading(day: LocalDate, today: LocalDate): String = when (day) {
    today -> stringResource(R.string.today)
    today.minus(1, DateTimeUnit.DAY) -> stringResource(R.string.yesterday)
    else -> day.longLabel()
}

@Composable
private fun DayHeading(text: String, modifier: Modifier) {
    SectionHeading(text, modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 2.dp))
}

@Composable
private fun AnnouncementRow(stored: StoredAnnouncement, modifier: Modifier, onClick: () -> Unit) {
    val a = stored.announcement
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.padding(top = 7.dp).size(8.dp).background(
                    if (stored.read) Color.Transparent else MaterialTheme.colorScheme.error,
                    CircleShape,
                ),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

/** One announcement in full; opening it marks it read. */
@Composable
fun AnnouncementScreen(announcement: Announcement, markRead: () -> Unit, onBack: () -> Unit) {
    LaunchedEffect(announcement.id) { markRead() }
    Column(Modifier.fillMaxSize()) {
        PageBar(stringResource(R.string.page_announcement), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
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
