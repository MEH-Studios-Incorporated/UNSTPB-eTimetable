// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.model.Parity
import net.mehstudios.etimetable.schedule.Occurrence
import net.mehstudios.etimetable.schedule.StudentTimetable
import net.mehstudios.etimetable.schedule.facultyNow
import net.mehstudios.etimetable.schedule.mondayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Keeps the widgets current without the app: after every change the app makes, then again
 * at the next moment their text changes (a class starting or ending), every 5 minutes while
 * a class starts within the hour (for "În 25 min"), and at least hourly.
 */
object WidgetUpdates {
    private const val WORK = "widget"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun refresh(context: Context) {
        scope.launch { update(context.applicationContext) }
    }

    internal suspend fun update(context: Context) {
        val widgets = GlanceAppWidgetManager(context).getGlanceIds(TimetableWidget::class.java)
        if (widgets.isEmpty()) return cancel(context)
        TimetableWidget().updateAll(context)
        val next = withContext(Dispatchers.IO) { WidgetData.load(context).refreshIn }
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WidgetWorker>().setInitialDelay(next.inWholeSeconds, TimeUnit.SECONDS).build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }
}

class WidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdates.update(applicationContext)
        return Result.success()
    }
}

/** What the widget shows at one moment, worked out from the offline copy. */
internal class WidgetData(
    /** Null until the student is signed in and has chosen a group or subjects. */
    val dataset: Dataset?,
    val dateLabel: String = "",
    val week: String? = null,
    /** The class happening now, or else the next one. */
    val focus: Occurrence? = null,
    val focusLabel: String? = null,
    val focusIsSoon: Boolean = false,
    /** Today's classes that have not ended. */
    val today: List<Occurrence> = emptyList(),
    val nextDayLabel: String? = null,
    val nextDay: List<Occurrence> = emptyList(),
    val refreshIn: Duration = 60.minutes,
) {
    companion object {
        fun load(context: Context): WidgetData {
            val store = context.store()
            val dataset = store.dataset()?.dataset
            val profile = store.profile()?.profile
            if (dataset == null || profile?.enrollment == null) return WidgetData(null)
            val at = Clock.System.facultyNow()
            val timetable = StudentTimetable(dataset, profile)
            val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()

            val today = timetable.on(at.date).filter { it.end > at }
            val (current, next) = timetable.nowAndNext(at).let { it.current to it.next }
            val focus = current ?: next
            val minutesToNext = next?.takeIf { it.date == at.date }?.let { minutesBetween(at, it.start) }
            val focusLabel = when {
                current != null -> context.getString(R.string.widget_now, current.activity.slot.end.toString())
                next == null -> null
                minutesToNext != null && minutesToNext < 60 -> context.getString(
                    R.string.widget_in_minutes,
                    minutesToNext,
                )
                next.date == at.date -> context.getString(R.string.widget_today)
                next.date == at.date.plus(1, DateTimeUnit.DAY) -> context.getString(R.string.widget_tomorrow)
                else -> dayName(next.date, locale)
            }

            // the next day with classes, for the large widget
            val nextDay = (1..14).map { at.date.plus(it, DateTimeUnit.DAY) }
                .firstNotNullOfOrNull { date -> timetable.on(date).takeIf { it.isNotEmpty() }?.let { date to it } }
            val nextDayLabel = nextDay?.first?.let {
                if (it ==
                    at.date.plus(1, DateTimeUnit.DAY)
                ) {
                    context.getString(R.string.widget_tomorrow)
                } else {
                    dayName(it, locale)
                }
            }

            // on Saturday and Sunday the week that matters is the coming one, as in the app
            val weekDay = if (at.date.dayOfWeek.isoDayNumber >=
                6
            ) {
                at.date.mondayOfWeek().plus(7, DateTimeUnit.DAY)
            } else {
                at.date
            }
            val week = timetable.calendar.weekOf(weekDay)?.let {
                context.getString(
                    R.string.widget_week,
                    it.number,
                    context.getString(if (it.parity == Parity.ODD) R.string.parity_odd else R.string.parity_even),
                )
            } ?: timetable.calendar.breakOn(weekDay)?.let { context.getString(R.string.on_break) }

            val boundary = timetable.on(at.date).flatMap { listOf(it.start, it.end) }.filter { it > at }.minOrNull()
            val refreshIn = when {
                minutesToNext != null && minutesToNext < 60 -> 5.minutes
                boundary != null -> minutesBetween(at, boundary).minutes.coerceIn(1.minutes, 60.minutes)
                else -> 60.minutes
            }
            return WidgetData(
                dataset = dataset,
                dateLabel = DateTimeFormatter.ofPattern(
                    "EEEE, d MMM",
                    locale,
                ).format(at.date.toJava()).capitalized(locale),
                week = week,
                focus = focus,
                focusLabel = focusLabel,
                focusIsSoon = current != null || (minutesToNext != null && minutesToNext < 60),
                today = today,
                nextDayLabel = nextDayLabel,
                nextDay = nextDay?.second.orEmpty(),
                refreshIn = refreshIn,
            )
        }

        private fun minutesBetween(from: LocalDateTime, to: LocalDateTime): Int {
            val days = to.date.toEpochDays() - from.date.toEpochDays()
            return ((days * 86_400 + to.time.toSecondOfDay() - from.time.toSecondOfDay()) / 60).toInt()
        }

        private fun dayName(date: LocalDate, locale: Locale) =
            java.time.DayOfWeek.of(date.dayOfWeek.isoDayNumber).getDisplayName(java.time.format.TextStyle.FULL, locale)
                .capitalized(locale)

        private fun LocalDate.toJava() = java.time.LocalDate.of(year, month.ordinal + 1, day)

        private fun String.capitalized(locale: Locale) = replaceFirstChar { it.titlecase(locale) }
    }
}
