// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mehstudios.etimetable.android.EtimetableApp
import net.mehstudios.etimetable.android.MainActivity
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.ui.components.subjectColor
import net.mehstudios.etimetable.android.ui.components.subjectName
import net.mehstudios.etimetable.android.ui.theme.DarkTokens
import net.mehstudios.etimetable.android.ui.theme.LightTokens
import net.mehstudios.etimetable.model.ActivityType
import net.mehstudios.etimetable.model.Dataset
import net.mehstudios.etimetable.schedule.Occurrence

private val SMALL = DpSize(110.dp, 110.dp)
private val MEDIUM = DpSize(250.dp, 110.dp)
private val LARGE = DpSize(250.dp, 260.dp)

/**
 * The home screen widget from design/mockups/Widgets.png. One widget, three layouts by
 * size: the next class, the rest of today, today and the next day with classes.
 */
class TimetableWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { WidgetData.load(context) }
        provideContent { Widget(data) }
    }
}

class TimetableWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimetableWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdates.refresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdates.cancel(context)
    }
}

// the app's tokens (docs/design.md), light and dark
private val page = ColorProvider(day = LightTokens.surface, night = DarkTokens.surface)
private val text = ColorProvider(day = LightTokens.text, night = DarkTokens.text)
private val muted = ColorProvider(day = LightTokens.textMuted, night = DarkTokens.textMuted)
private val border = ColorProvider(day = LightTokens.border, night = DarkTokens.border)
private val now = ColorProvider(day = LightTokens.now, night = DarkTokens.now)

@Composable
private fun Widget(data: WidgetData) {
    val size = LocalSize.current
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(24.dp).background(page)
            .clickable(actionStartActivity<MainActivity>()).padding(16.dp),
    ) {
        when {
            data.dataset == null -> Message(LocalContext.current.getString(R.string.widget_no_group))
            size.width < MEDIUM.width -> Small(data, data.dataset)
            size.height < LARGE.height -> Medium(data, data.dataset)
            else -> Large(data, data.dataset)
        }
    }
}

@Composable
private fun Message(message: String) {
    Text(message, style = style(14.sp, muted))
}

/** The class happening now or the next one: when, what and where. */
@Composable
private fun Small(data: WidgetData, dataset: Dataset) {
    val focus = data.focus
    if (focus == null) return Message(LocalContext.current.getString(R.string.widget_nothing))
    Column(GlanceModifier.fillMaxSize()) {
        Text(data.focusLabel.orEmpty(), style = style(14.sp, if (data.focusIsSoon) now else muted, bold = true))
        Spacer(GlanceModifier.defaultWeight())
        Text(focus.activity.slot.start.toString(), style = style(34.sp, text, bold = true))
        Text(dataset.subjectName(focus.activity.subjectId), style = style(15.sp, text, bold = true), maxLines = 2)
        Text(details(dataset, focus), style = style(13.sp, muted), maxLines = 1)
    }
}

/** The rest of today, one line per class. */
@Composable
private fun Medium(data: WidgetData, dataset: Dataset) {
    Column(GlanceModifier.fillMaxSize()) {
        Header(data.dateLabel, data.week)
        Spacer(GlanceModifier.height(8.dp))
        // once today is over, the next day with classes, so the widget is still useful in the evening
        val rows = data.today.ifEmpty { data.nextDay }
        if (data.today.isEmpty()) {
            val context = LocalContext.current
            Message(context.getString(R.string.widget_done_today))
            if (rows.isNotEmpty()) {
                Spacer(GlanceModifier.height(6.dp))
                Text(data.nextDayLabel.orEmpty(), style = style(13.sp, muted, bold = true))
            }
        }
        rows.take(if (data.today.isEmpty()) 2 else 4).forEach { occurrence ->
            Row(
                GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Bar(dataset.subjectColor(occurrence.activity.subjectId), 20)
                Text(
                    occurrence.activity.slot.start.toString(),
                    style = style(14.sp, text, bold = true),
                    modifier = GlanceModifier.padding(start = 10.dp).width(52.dp),
                )
                Text(
                    dataset.subjectName(occurrence.activity.subjectId),
                    style = style(14.sp, text, bold = true),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(
                    dataset.roomsById[occurrence.activity.roomId]?.code.orEmpty(),
                    style = style(13.sp, muted),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Today's remaining classes in full, then the start of the next day with classes. */
@Composable
private fun Large(data: WidgetData, dataset: Dataset) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize()) {
        Header(context.getString(R.string.widget_today), data.week)
        Spacer(GlanceModifier.height(6.dp))
        if (data.today.isEmpty()) Message(context.getString(R.string.widget_done_today))
        data.today.take(3).forEach { Detailed(dataset, it) }
        if (data.nextDay.isNotEmpty()) {
            Spacer(GlanceModifier.height(8.dp))
            Box(GlanceModifier.fillMaxWidth().height(1.dp).background(border)) {}
            Spacer(GlanceModifier.height(8.dp))
            Text(data.nextDayLabel.orEmpty(), style = style(13.sp, muted, bold = true))
            Spacer(GlanceModifier.height(4.dp))
            data.nextDay.take(2).forEach { Detailed(dataset, it) }
        }
    }
}

@Composable
private fun Detailed(dataset: Dataset, occurrence: Occurrence) {
    val slot = occurrence.activity.slot
    Row(GlanceModifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Bar(dataset.subjectColor(occurrence.activity.subjectId), 52)
        Column(GlanceModifier.padding(start = 10.dp)) {
            Text("${slot.start} - ${slot.end}", style = style(13.sp, muted, bold = true))
            Text(
                dataset.subjectName(occurrence.activity.subjectId),
                style = style(15.sp, text, bold = true),
                maxLines = 1,
            )
            Text(details(dataset, occurrence), style = style(13.sp, muted), maxLines = 1)
        }
    }
}

@Composable
private fun Header(left: String, right: String?) {
    Row(GlanceModifier.fillMaxWidth()) {
        Text(left, style = style(13.sp, muted, bold = true), modifier = GlanceModifier.defaultWeight())
        if (right != null) Text(right, style = style(13.sp, muted, bold = true))
    }
}

@Composable
private fun Bar(color: Color, height: Int) {
    Box(GlanceModifier.width(4.dp).height(height.dp).cornerRadius(2.dp).background(ColorProvider(color))) {}
}

/** "Laborator · BN212b" */
@Composable
private fun details(dataset: Dataset, occurrence: Occurrence): String {
    val context = LocalContext.current
    val type = context.getString(
        when (occurrence.activity.type) {
            ActivityType.COURSE -> R.string.type_course
            ActivityType.SEMINAR -> R.string.type_seminar
            ActivityType.LAB -> R.string.type_lab
            ActivityType.PROJECT -> R.string.type_project
        },
    )
    return listOfNotNull(type, dataset.roomsById[occurrence.activity.roomId]?.code).joinToString(" · ")
}

private fun style(size: TextUnit, color: ColorProvider, bold: Boolean = false) =
    TextStyle(color = color, fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

/** The widget reads the same offline copy as the app; the app does not have to be open. */
internal fun Context.store() = (applicationContext as EtimetableApp).container.store
