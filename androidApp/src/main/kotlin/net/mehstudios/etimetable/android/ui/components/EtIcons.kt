// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The stroke icons of the mockups (design/mockups), drawn on a 24-unit grid. */
object EtIcons {
    val Calendar =
        icon("Calendar", "M8 3v4M16 3v4M3 10h18M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z")
    val Bell = icon("Bell", "M6 16v-5a6 6 0 0 1 12 0v5l2 2H4zM10 21h4")
    val Book = icon("Book", "M5 4h11a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3zM5 17a3 3 0 0 1 3-3h11")
    val Person = icon("Person", "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21c0-4 4-6 8-6s8 2 8 6")
    val ChevronLeft = icon("ChevronLeft", "M15 6l-6 6 6 6", width = 2f)
    val ChevronRight = icon("ChevronRight", "M9 6l6 6-6 6", width = 2f)
    val Search = icon("Search", "M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM20 20l-4-4", width = 2f)
    val Pin = icon("Pin", "M12 17v5M5 17h14l-2-4V5H7v8z", width = 2f)

    private fun icon(name: String, path: String, width: Float = 1.75f) = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(path),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()
}
