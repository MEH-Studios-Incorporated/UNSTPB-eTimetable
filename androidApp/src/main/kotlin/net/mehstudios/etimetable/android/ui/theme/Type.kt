// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import net.mehstudios.etimetable.android.R

// One variable font file, instantiated at the weights the design uses.
private fun franklin(weight: Int) = Font(
    R.font.libre_franklin,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val LibreFranklin = FontFamily(franklin(400), franklin(500), franklin(600), franklin(700))

private fun style(size: Int, line: Int, weight: Int) = TextStyle(
    fontFamily = LibreFranklin,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
)

// docs/design.md, "Type"
val EtTypography = Typography(
    headlineSmall = style(22, 28, 600),
    titleMedium = style(17, 22, 600),
    bodyLarge = style(15, 21, 400),
    bodyMedium = style(15, 21, 400),
    labelLarge = style(13, 18, 500),
    labelMedium = style(13, 18, 500),
    bodySmall = style(12, 16, 400),
)

/** Times in the timetable: tabular figures so hours line up. */
val TimeStyle = style(13, 16, 600).copy(fontFeatureSettings = "tnum")
