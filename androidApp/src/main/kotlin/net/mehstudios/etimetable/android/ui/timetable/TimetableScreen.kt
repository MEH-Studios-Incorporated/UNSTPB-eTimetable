// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.timetable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.data.ActionResult
import net.mehstudios.etimetable.android.data.StartView
import net.mehstudios.etimetable.android.data.TimetableLayout
import net.mehstudios.etimetable.android.ui.components.BrandBar
import net.mehstudios.etimetable.android.ui.components.EtIcons
import net.mehstudios.etimetable.android.ui.components.RefreshBox
import net.mehstudios.etimetable.android.ui.components.appLanguage
import net.mehstudios.etimetable.android.ui.components.dayInitials
import net.mehstudios.etimetable.android.ui.components.dayName
import net.mehstudios.etimetable.android.ui.components.label
import net.mehstudios.etimetable.android.ui.components.longLabel
import net.mehstudios.etimetable.android.ui.components.subjectColor
import net.mehstudios.etimetable.android.ui.components.subjectName
import net.mehstudios.etimetable.android.ui.components.subjectShortName
import net.mehstudios.etimetable.android.ui.components.weekRangeLabel
import net.mehstudios.etimetable.android.ui.theme.ButtonShape
import net.mehstudios.etimetable.android.ui.theme.LocalTimetableChoice
import net.mehstudios.etimetable.android.ui.theme.TimeStyle
import net.mehstudios.etimetable.api.TimetableReportRequest
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Parity
import net.mehstudios.etimetable.model.WeekRule
import net.mehstudios.etimetable.schedule.Occurrence
import net.mehstudios.etimetable.schedule.StudentTimetable
import net.mehstudios.etimetable.schedule.mondayOfWeek

private enum class Mode { DAY, WEEK }

/** Hours shown: the ones with classes, plus the current hour on today, never less than four. */
private data class HourRange(val first: Int, val last: Int) {
    val count get() = last - first
}

private fun hourRange(occurrences: List<Occurrence>, now: LocalDateTime?): HourRange {
    var first = occurrences.minOfOrNull { it.activity.slot.start.hour } ?: 8
    var last =
        occurrences.maxOfOrNull { it.activity.slot.end.let { e -> if (e.minute > 0) e.hour + 1 else e.hour } } ?: 12
    if (now != null) {
        first = minOf(first, now.hour)
        last = maxOf(last, now.hour + 1)
    }
    if (last - first < 4) last = first + 4
    return HourRange(first.coerceIn(0, 20), last.coerceIn(4, 24))
}

@Composable
fun TimetableScreen(
    dataset: Dataset,
    timetable: StudentTimetable,
    now: LocalDateTime,
    actions: @Composable () -> Unit,
    openProfessor: (String) -> Unit,
    openSubject: (String) -> Unit,
    reportProblem: suspend (TimetableReportRequest) -> ActionResult,
    refresh: suspend () -> Unit = {},
) {
    val prefs = LocalTimetableChoice.current.prefs
    var mode by rememberSaveable { mutableStateOf(if (prefs.start == StartView.WEEK) Mode.WEEK else Mode.DAY) }
    var selectedText by rememberSaveable { mutableStateOf(now.date.toString()) }
    val selected = LocalDate.parse(selectedText)
    var openedId by rememberSaveable(dataset.semester.id) { mutableStateOf<String?>(null) }
    var openedDate by rememberSaveable(dataset.semester.id) { mutableStateOf<String?>(null) }
    val opened = dataset.activitiesById[openedId]?.let { activity ->
        openedDate?.let { Occurrence(activity, LocalDate.parse(it)) }
    }
    fun open(occurrence: Occurrence) {
        openedId = occurrence.activity.id
        openedDate = occurrence.date.toString()
    }
    val calendar = timetable.calendar
    val scope = rememberCoroutineScope()

    // every weekday of the semester (and today), so the pager can swipe through them
    val pagerDays = remember(dataset, now.date) { weekdays(dataset, timetable, now.date) }
    val pagerState = rememberPagerState(initialPage = pageOf(pagerDays, selected)) { pagerDays.size }
    LaunchedEffect(pagerState.settledPage) {
        val day = pagerDays[pagerState.settledPage]
        if (day != selected) selectedText = day.toString()
    }

    fun select(date: LocalDate) {
        selectedText = date.toString()
        val page = pageOf(pagerDays, date)
        if (page != pagerState.currentPage) scope.launch { pagerState.animateScrollToPage(page) }
    }

    val monday = selected.mondayOfWeek()
    val weekDays = pagerDays.filter {
        it.mondayOfWeek() == monday
    }.ifEmpty { (0 until 5).map { monday.plus(it, DateTimeUnit.DAY) } }

    Column(Modifier.fillMaxSize()) {
        BrandBar { actions() }
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = if (mode == Mode.DAY) {
                        selected.longLabel()
                    } else {
                        weekRangeLabel(weekDays.first(), weekDays.last())
                    },
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                    label = "title",
                    modifier = Modifier.weight(1f),
                ) { Text(it, style = MaterialTheme.typography.headlineSmall) }
                // the week arrows sit by the dates they change, leaving the row below room for "Today"
                if (mode == Mode.WEEK) {
                    IconButton(onClick = { select(selected.minus(7, DateTimeUnit.DAY)) }) {
                        Icon(EtIcons.ChevronLeft, contentDescription = stringResource(R.string.previous_week))
                    }
                    IconButton(onClick = { select(selected.plus(7, DateTimeUnit.DAY)) }) {
                        Icon(EtIcons.ChevronRight, contentDescription = stringResource(R.string.next_week))
                    }
                }
            }
            val week = calendar.weekOf(selected)
            val subtitle = when {
                week == null -> calendar.breakOn(selected)?.name?.get(appLanguage()).orEmpty()
                week.parity == Parity.ODD -> stringResource(R.string.week_odd, week.number)
                else -> stringResource(R.string.week_even, week.number)
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(selected = mode == Mode.DAY, onClick = {
                    mode = Mode.DAY
                }, shape = SegmentedButtonDefaults.itemShape(0, 2), icon = {}) {
                    Text(stringResource(R.string.day), maxLines = 1)
                }
                SegmentedButton(selected = mode == Mode.WEEK, onClick = {
                    mode = Mode.WEEK
                }, shape = SegmentedButtonDefaults.itemShape(1, 2), icon = {}) {
                    Text(stringResource(R.string.week), maxLines = 1)
                }
            }
            Spacer(Modifier.weight(1f))
            if (selected != now.date) {
                OutlinedButton(
                    shape = ButtonShape,
                    onClick = { select(now.date) },
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) { Text(stringResource(R.string.today), maxLines = 1) }
            }
        }

        RefreshBox(refresh, Modifier.weight(1f)) {
            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                label = "mode",
                modifier = Modifier.fillMaxSize(),
            ) { current ->
                when (current) {
                    Mode.DAY -> Column(Modifier.fillMaxSize()) {
                        DayStrip(weekDays, selected, timetable, ::select)
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.weight(1f),
                            beyondViewportPageCount = 1,
                        ) { page ->
                            val day = pagerDays[page]
                            Column(Modifier.fillMaxSize()) {
                                if (day == now.date) NowNextCard(dataset, timetable, now, ::open)
                                RemovedClasses(
                                    dataset,
                                    calendar,
                                    day,
                                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                )
                                val occurrences = timetable.on(day)
                                when (prefs.layout) {
                                    TimetableLayout.TIMELINE ->
                                        DayTimeline(dataset, occurrences, now, Modifier.weight(1f), ::open)
                                    TimetableLayout.LIST -> DayList(
                                        dataset,
                                        occurrences,
                                        now,
                                        Modifier.weight(1f),
                                        ::open,
                                    )
                                    TimetableLayout.TABLE -> DayRows(
                                        dataset,
                                        occurrences,
                                        now,
                                        Modifier.weight(1f),
                                        ::open,
                                    )
                                }
                            }
                        }
                    }
                    Mode.WEEK -> AnimatedContent(
                        targetState = monday,
                        transitionSpec = {
                            val forward = targetState > initialState
                            (
                                slideInHorizontally(tween(250)) {
                                    if (forward) it / 4 else -it / 4
                                } + fadeIn(tween(250))
                                ) togetherWith
                                (
                                    slideOutHorizontally(tween(200)) {
                                        if (forward) -it / 4 else it / 4
                                    } + fadeOut(tween(200))
                                    )
                        },
                        label = "week",
                    ) { weekMonday ->
                        val days = pagerDays.filter {
                            it.mondayOfWeek() == weekMonday
                        }.ifEmpty { (0 until 5).map { weekMonday.plus(it, DateTimeUnit.DAY) } }
                        Column(Modifier.fillMaxSize()) {
                            // the agenda says it under each day
                            if (prefs.layout != TimetableLayout.LIST) {
                                days.forEach {
                                    RemovedClasses(
                                        dataset,
                                        calendar,
                                        it,
                                        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                                    )
                                }
                            }
                            when (prefs.layout) {
                                TimetableLayout.TIMELINE -> WeekGrid(dataset, days, timetable, now, ::open)
                                TimetableLayout.LIST -> WeekAgenda(dataset, days, timetable, now, ::open)
                                TimetableLayout.TABLE -> WeekTable(dataset, days, timetable, now, ::open)
                            }
                        }
                    }
                }
            }
        }
    }

    opened?.let {
        ActivitySheet(
            dataset,
            it,
            onDismiss = {
                openedId = null
                openedDate = null
            },
            openProfessor = openProfessor,
            openSubject = openSubject,
            reportProblem = reportProblem,
        )
    }
}

private fun weekdays(dataset: Dataset, timetable: StudentTimetable, today: LocalDate): List<LocalDate> {
    val saturday = timetable.recurring.any { it.slot.isoDay == 6 }
    val first = minOf(dataset.semester.firstDay, today).mondayOfWeek()
    val last = maxOf(dataset.semester.lastDay, today)
    return generateSequence(first) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= last }
        .filter { it.dayOfWeek.isoDayNumber <= if (saturday) 6 else 5 }.toList()
}

/** The page of [date], or of the next weekday when it falls on a weekend. */
private fun pageOf(days: List<LocalDate>, date: LocalDate): Int =
    days.indexOfFirst { it >= date }.takeIf { it >= 0 } ?: (days.size - 1)

@Composable
private fun DayStrip(
    days: List<LocalDate>,
    selected: LocalDate,
    timetable: StudentTimetable,
    select: (LocalDate) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { day ->
            val isSelected = day == selected
            val count = timetable.on(day).size
            Surface(
                onClick = { select(day) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
                contentColor = when {
                    isSelected -> MaterialTheme.colorScheme.surface
                    else -> MaterialTheme.colorScheme.onSurface
                },
                border = BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier.weight(1f).height(64.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(day.dayInitials(), style = MaterialTheme.typography.bodySmall)
                    Text(
                        day.day.toString(),
                        style = TimeStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                    )
                    Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(count.coerceAtMost(5)) {
                            Box(
                                Modifier.size(
                                    4.dp,
                                ).background(LocalContentColor.current.copy(alpha = 0.7f), CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Above today: the class on now with the time left, and the next one under it. Without a
 * class on now, the next one alone with how long until it starts.
 */
@Composable
private fun NowNextCard(dataset: Dataset, timetable: StudentTimetable, now: LocalDateTime, open: (Occurrence) -> Unit) {
    val (current, next) = timetable.nowAndNext(now)
    if (current == null && next == null) return
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
    ) {
        Column {
            if (current != null) {
                CurrentClass(dataset, current, now) { open(current) }
                next?.let {
                    HorizontalDivider()
                    NextLine(dataset, it, now) { open(it) }
                }
            } else if (next != null) {
                NextClass(dataset, next, now) { open(next) }
            }
        }
    }
}

@Composable
private fun CurrentClass(dataset: Dataset, current: Occurrence, now: LocalDateTime, onClick: () -> Unit) {
    val activity = current.activity
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onClick)
            .padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Box(
            Modifier.width(4.dp).fillMaxHeight()
                .background(dataset.subjectColor(activity.subjectId), RoundedCornerShape(2.dp)),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                Text(
                    stringResource(R.string.now_until, activity.slot.end.toString()),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                dataset.subjectName(activity.subjectId),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    activity.type.label(),
                    dataset.roomsById[activity.roomId]?.code,
                    dataset.professorsById[activity.professorId]?.displayName,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ChangeTagFor(activity.id, Modifier.padding(top = 4.dp))
        }
        Column(Modifier.padding(start = 12.dp), horizontalAlignment = Alignment.End) {
            Text(
                timeLeft(now.time, activity.slot.end),
                style = TimeStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
            )
            Text(
                stringResource(R.string.time_left),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "1 h 36" or "36 min", rounded up so the last minute does not read 0. */
@Composable
private fun timeLeft(now: LocalTime, end: LocalTime): String {
    val minutes = ((end.toSecondOfDay() - now.toSecondOfDay()) + 59) / 60
    return if (minutes >= 60) {
        stringResource(R.string.time_left_hours, minutes / 60, minutes % 60)
    } else {
        stringResource(R.string.time_left_minutes, minutes)
    }
}

/** The next class as one line under the current one. */
@Composable
private fun NextLine(dataset: Dataset, next: Occurrence, now: LocalDateTime, onClick: () -> Unit) {
    val activity = next.activity
    Row(
        Modifier.fillMaxWidth().clickable(
            onClick = onClick,
        ).padding(start = 12.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.width(4.dp).height(18.dp)
                .background(dataset.subjectColor(activity.subjectId), RoundedCornerShape(2.dp)),
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    append(stringResource(R.string.next_label))
                }
                append(" ")
                append(
                    listOfNotNull(
                        dataset.subjectName(activity.subjectId),
                        dataset.roomsById[activity.roomId]?.code,
                    ).joinToString(" · "),
                )
            },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ChangeDot(activity.id)
        Text(
            if (next.date ==
                now.date
            ) {
                activity.slot.start.toString()
            } else {
                "${next.date.dayName()}, ${activity.slot.start}"
            },
            style = TimeStyle,
        )
    }
}

@Composable
private fun NextClass(dataset: Dataset, next: Occurrence, now: LocalDateTime, onClick: () -> Unit) {
    val heading = if (next.date == now.date) {
        stringResource(R.string.next_in_minutes, minutesBetween(now.time, next.start.time))
    } else {
        stringResource(R.string.next_on, "${next.date.dayName()}, ${next.activity.slot.start}")
    }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(4.dp).fillMaxHeight()
                .background(dataset.subjectColor(next.activity.subjectId), RoundedCornerShape(2.dp)),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                heading,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                dataset.subjectName(next.activity.subjectId),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    next.activity.type.label(),
                    dataset.roomsById[next.activity.roomId]?.code,
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChangeTagFor(next.activity.id, Modifier.padding(top = 4.dp))
        }
        Text(
            next.activity.slot.start.toString(),
            style = TimeStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
        )
    }
}

private fun minutesBetween(from: LocalTime, to: LocalTime): Int = (to.toSecondOfDay() - from.toSecondOfDay()) / 60

@Composable
private fun DayTimeline(
    dataset: Dataset,
    occurrences: List<Occurrence>,
    now: LocalDateTime,
    modifier: Modifier,
    open: (Occurrence) -> Unit,
) {
    if (occurrences.isEmpty()) {
        // scrollable, so pull-to-refresh works on an empty day too
        Text(
            stringResource(R.string.nothing_today),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        )
        return
    }
    val date = occurrences.first().date
    val hours = hourRange(occurrences, now.takeIf { it.date == date })
    val hourHeight = 60.dp
    Row(modifier.verticalScroll(rememberScrollState()).padding(top = 10.dp, end = 16.dp, bottom = 24.dp)) {
        HourLabels(hours, hourHeight, Modifier.width(52.dp))
        Box(Modifier.weight(1f).height(hourHeight * hours.count)) {
            HourLines(hours, hourHeight)
            occurrences.forEach { occurrence ->
                OccurrenceBlock(
                    dataset,
                    occurrence,
                    now,
                    hours,
                    hourHeight,
                    compact = false,
                    modifier = Modifier.padding(start = 6.dp),
                ) {
                    open(occurrence)
                }
            }
            NowLine(date, now, hours, hourHeight)
        }
    }
}

@Composable
private fun WeekGrid(
    dataset: Dataset,
    days: List<LocalDate>,
    timetable: StudentTimetable,
    now: LocalDateTime,
    open: (Occurrence) -> Unit,
) {
    val all = days.flatMap { timetable.on(it) }
    val hours = hourRange(all, now.takeIf { it.date in days })
    val hourHeight = 46.dp
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 36.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            days.forEach { day ->
                val today = day == now.date
                Text(
                    "${day.dayInitials()} ${day.day}",
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        today -> MaterialTheme.colorScheme.surface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                        .background(
                            if (today) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            RoundedCornerShape(6.dp),
                        )
                        .padding(vertical = 4.dp),
                )
            }
        }
        Row(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 10.dp, end = 8.dp, bottom = 24.dp),
        ) {
            HourLabels(hours, hourHeight, Modifier.width(36.dp), short = true)
            Box(Modifier.weight(1f).height(hourHeight * hours.count)) {
                HourLines(hours, hourHeight)
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    days.forEach { day ->
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .background(
                                    if (day ==
                                        now.date
                                    ) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                    } else {
                                        Color.Transparent
                                    },
                                    RoundedCornerShape(6.dp),
                                ),
                        ) {
                            timetable.on(day).forEach { occurrence ->
                                OccurrenceBlock(dataset, occurrence, now, hours, hourHeight, compact = true) {
                                    open(occurrence)
                                }
                            }
                            NowLine(day, now, hours, hourHeight)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HourLabels(hours: HourRange, hourHeight: Dp, modifier: Modifier, short: Boolean = false) {
    Box(modifier.height(hourHeight * hours.count)) {
        for (hour in hours.first until hours.last) {
            val text = hour.toString().padStart(2, '0')
            Text(
                if (short) text else "$text:00",
                style = TimeStyle.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(
                    Alignment.TopEnd,
                ).offset(y = hourHeight * (hour - hours.first) - 7.dp).padding(end = 8.dp),
            )
        }
    }
}

@Composable
private fun HourLines(hours: HourRange, hourHeight: Dp) {
    for (hour in hours.first until hours.last) {
        Box(
            Modifier.fillMaxWidth().height(
                1.dp,
            ).offset(y = hourHeight * (hour - hours.first)).background(MaterialTheme.colorScheme.outline),
        )
    }
}

@Composable
private fun NowLine(date: LocalDate, now: LocalDateTime, hours: HourRange, hourHeight: Dp) {
    if (date != now.date) return
    val position = now.time.toSecondOfDay() / 3600f - hours.first
    if (position < 0 || position > hours.count) return
    Row(
        Modifier.fillMaxWidth().offset(y = hourHeight * position - 4.dp).zIndex(2f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).background(MaterialTheme.colorScheme.error, CircleShape))
        Box(Modifier.weight(1f).height(2.dp).background(MaterialTheme.colorScheme.error))
    }
}

/**
 * A class on the timeline. Every colour is opaque so the hour lines never show
 * through; a class that is over is faded by mixing its colours with the page.
 */
@Composable
private fun OccurrenceBlock(
    dataset: Dataset,
    occurrence: Occurrence,
    now: LocalDateTime,
    hours: HourRange,
    hourHeight: Dp,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val slot = occurrence.activity.slot
    val top = hourHeight * (slot.start.toSecondOfDay() / 3600f - hours.first)
    val height = hourHeight * ((slot.end.toSecondOfDay() - slot.start.toSecondOfDay()) / 3600f) - 3.dp
    val page = MaterialTheme.colorScheme.background
    val dark = page.red + page.green + page.blue < 1.5f
    val past = occurrence.end <= now
    val fade = if (past) 0.45f else 0f
    val subject = dataset.subjectColor(occurrence.activity.subjectId)
    val tint = lerp(page, subject, (if (dark) 0.22f else 0.13f) * (1 - fade))
    val bar = lerp(subject, page, fade)
    val text = lerp(MaterialTheme.colorScheme.onSurface, page, fade)
    val muted = lerp(MaterialTheme.colorScheme.onSurfaceVariant, page, fade)
    Row(
        modifier.zIndex(1f).fillMaxWidth().offset(y = top + 1.dp).height(height)
            .background(tint, RoundedCornerShape(if (compact) 4.dp else 8.dp)).clickable(onClick = onClick),
    ) {
        Box(
            Modifier.width(
                if (compact) 3.dp else 4.dp,
            ).fillMaxHeight().background(bar, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)),
        )
        val minutes = (slot.end.toSecondOfDay() - slot.start.toSecondOfDay()) / 60
        val room = dataset.roomsById[occurrence.activity.roomId]?.code
        val type = occurrence.activity.type.label()
        Column(
            Modifier.padding(
                horizontal = if (compact) 4.dp else 10.dp,
                vertical = if (compact || minutes < 90) 3.dp else 6.dp,
            ),
        ) {
            when {
                // week grid: short name, then as much as the height allows
                compact -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            dataset.subjectShortName(occurrence.activity.subjectId),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = text,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        ChangeDot(occurrence.activity.id, Modifier.padding(start = 2.dp))
                    }
                    if (minutes >= 90) Line(type, muted)
                    if (minutes >= 50) room?.let { Line(it, muted) }
                }
                // a full block: type, name on up to two lines, time, room and professor
                minutes >= 90 -> {
                    Line(
                        listOfNotNull(
                            type,
                            slot.weeks.takeIf {
                                it != WeekRule.All
                            }?.label(),
                        ).joinToString(" · "),
                        muted,
                    )
                    Text(
                        dataset.subjectName(occurrence.activity.subjectId),
                        style = MaterialTheme.typography.titleMedium,
                        color = text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            "${slot.start} - ${slot.end}",
                            room,
                            dataset.professorsById[occurrence.activity.professorId]?.displayName,
                        ).joinToString(" · "),
                        style = TimeStyle.copy(fontWeight = FontWeight.Normal),
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ChangeTagFor(occurrence.activity.id, Modifier.padding(top = 4.dp))
                }
                // an hour: the name, then time, type and room on one line
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            dataset.subjectName(occurrence.activity.subjectId),
                            style = MaterialTheme.typography.titleSmall,
                            color = text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        ChangeDot(occurrence.activity.id, Modifier.padding(start = 6.dp))
                    }
                    if (minutes >= 50) {
                        Line(listOfNotNull("${slot.start} - ${slot.end}", type, room).joinToString(" · "), muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun Line(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
