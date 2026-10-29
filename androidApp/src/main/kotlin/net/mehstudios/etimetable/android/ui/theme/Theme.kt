// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

package net.mehstudios.etimetable.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = with(LightTokens) {
    lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surfaceAlt,
        onSurfaceVariant = textMuted,
        outline = border,
        outlineVariant = border,
        error = now,
        primaryContainer = Color(0xFFDCEAF4),
        onPrimaryContainer = text,
        secondary = textMuted,
        onSecondary = surface,
        secondaryContainer = Color(0xFFE6E6E3),
        onSecondaryContainer = text,
        tertiary = primary,
        onTertiary = onPrimary,
        surfaceTint = surface,
        surfaceBright = surface,
        surfaceDim = surfaceAlt,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceAlt,
        surfaceContainerHighest = surfaceAlt,
        inverseSurface = text,
        inverseOnSurface = background,
    )
}

private val DarkColors = with(DarkTokens) {
    darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surfaceAlt,
        onSurfaceVariant = textMuted,
        outline = border,
        outlineVariant = border,
        error = now,
        primaryContainer = Color(0xFF13384F),
        onPrimaryContainer = text,
        secondary = textMuted,
        onSecondary = surface,
        secondaryContainer = Color(0xFF2E2E2E),
        onSecondaryContainer = text,
        tertiary = primary,
        onTertiary = onPrimary,
        surfaceTint = surface,
        surfaceBright = surfaceAlt,
        surfaceDim = background,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceAlt,
        surfaceContainerHighest = surfaceAlt,
        inverseSurface = text,
        inverseOnSurface = background,
    )
}

/** Buttons have 6 dp corners (docs/design.md), not Material's pill. */
val ButtonShape = RoundedCornerShape(6.dp)

private val EtShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
)

@Composable
fun EtTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = EtTypography,
        shapes = EtShapes,
        content = content,
    )
}
