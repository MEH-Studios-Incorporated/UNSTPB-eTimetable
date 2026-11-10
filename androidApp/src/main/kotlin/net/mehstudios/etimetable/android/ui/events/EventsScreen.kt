// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.events

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.datetime.toLocalDateTime
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.data.imageUrl
import net.mehstudios.etimetable.android.ui.components.BrandBar
import net.mehstudios.etimetable.android.ui.components.MarkdownText
import net.mehstudios.etimetable.android.ui.components.PageBar
import net.mehstudios.etimetable.android.ui.components.longLabel
import net.mehstudios.etimetable.android.ui.theme.ButtonShape
import net.mehstudios.etimetable.model.Event
import net.mehstudios.etimetable.schedule.FacultyTimeZone
import kotlin.time.Instant

/**
 * The card's picture shape: a poster up to 4:5 shows whole (its title is often at the top),
 * anything taller or wider than 2.4:1 is cut, so one card never fills the screen.
 */
private const val MIN_RATIO = 0.8f
private const val MAX_RATIO = 2.4f

/** "Joi, 20 noiembrie, 10:00 - 12:00 · Aula BN", in the app's language. */
fun Event.whenAndWhere(withPlace: Boolean = true): String {
    val start = startsAt.toLocalDateTime(FacultyTimeZone)
    val end = endsAt?.toLocalDateTime(FacultyTimeZone)
    fun time(t: kotlinx.datetime.LocalTime) = t.toString().take(5)
    val span = when {
        end == null -> "${start.date.longLabel()}, ${time(start.time)}"
        end.date == start.date -> "${start.date.longLabel()}, ${time(start.time)} - ${time(end.time)}"
        else -> "${start.date.longLabel()}, ${time(start.time)} - ${end.date.longLabel()}, ${time(end.time)}"
    }
    return listOfNotNull(span, place?.takeIf { withPlace }).joinToString(" · ")
}

/** The Events tab: cards with a picture and a title, the coming ones first, then the past ones. */
@Composable
fun EventsScreen(events: List<Event>, now: Instant, actions: @Composable () -> Unit, open: (String) -> Unit) {
    val (past, upcoming) = events.partition { it.over < now }
    Column(Modifier.fillMaxSize()) {
        BrandBar { actions() }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text(
                    stringResource(R.string.tab_events),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (upcoming.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.events_none),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(upcoming, key = { it.id }) { EventCard(it, past = false) { open(it.id) } }
            if (past.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.events_past)) }
                // the latest past one first: the one students may still look for
                items(past.reversed(), key = { it.id }) { EventCard(it, past = true) { open(it.id) } }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun EventCard(event: Event, past: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().alpha(if (past) 0.7f else 1f),
    ) {
        Column {
            Picture(event, Modifier.fillMaxWidth().aspectRatio(event.imageRatio.coerceIn(MIN_RATIO, MAX_RATIO)))
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(
                    event.whenAndWhere(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** The picture from the cache or the server, on a plain surface while it loads. */
@Composable
private fun Picture(event: Event, modifier: Modifier) {
    AsyncImage(
        model = imageUrl(event.image),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** One event in full: the whole picture, when and where, the description and the link. */
@Composable
fun EventScreen(event: Event, onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxSize()) {
        PageBar(stringResource(R.string.page_event), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            // the whole picture here, a poster too: nothing is cut off
            Picture(
                event,
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .aspectRatio(event.imageRatio),
            )
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(event.title, style = MaterialTheme.typography.headlineSmall)
                Text(event.whenAndWhere(withPlace = false), style = MaterialTheme.typography.titleSmall)
                event.place?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (event.body.isNotBlank()) MarkdownText(event.body, Modifier.padding(top = 8.dp))
                event.link?.let { link ->
                    OutlinedButton(
                        onClick = { uri.openUri(link) },
                        shape = ButtonShape,
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text(stringResource(R.string.event_open_link)) }
                }
            }
        }
    }
}
