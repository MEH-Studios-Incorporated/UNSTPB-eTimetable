// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import net.mehstudios.etimetable.android.R
import net.mehstudios.etimetable.android.ui.signin.invertInDark
import net.mehstudios.etimetable.android.ui.theme.LocalDarkTheme
import net.mehstudios.etimetable.android.ui.theme.TimeStyle
import net.mehstudios.etimetable.model.Parity
import net.mehstudios.etimetable.schedule.TeachingCalendar
import net.mehstudios.etimetable.schedule.mondayOfWeek

/** The seal and the app's name at the top of the main screens; [trailing] sits on the right. */
@Composable
fun BrandBar(modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    val dark = LocalDarkTheme.current
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.faima_seal),
            contentDescription = null,
            colorFilter = if (dark) invertInDark else null,
            modifier = Modifier.size(36.dp),
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

/**
 * The teaching week as a small ticket: its number in a grey block, "Săptămâna impară" beside
 * it; "Vacanță" during a break. On Saturday and Sunday it already shows the coming week.
 */
@Composable
fun WeekBadge(calendar: TeachingCalendar, today: LocalDate) {
    val day = if (today.dayOfWeek.isoDayNumber >= 6) today.mondayOfWeek().plus(7, DateTimeUnit.DAY) else today
    val week = calendar.weekOf(day)
    if (week == null && calendar.breakOn(day) == null) return
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.height(44.dp).clip(shape).border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .background(MaterialTheme.colorScheme.surface).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (week == null) {
            Text(
                stringResource(R.string.on_break),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            return@Row
        }
        Box(
            Modifier.fillMaxHeight().width(40.dp).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                week.number.toString(),
                style = TimeStyle.copy(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(Modifier.padding(start = 10.dp, end = 12.dp)) {
            Text(
                stringResource(R.string.week_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(if (week.parity == Parity.ODD) R.string.parity_odd else R.string.parity_even),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}
