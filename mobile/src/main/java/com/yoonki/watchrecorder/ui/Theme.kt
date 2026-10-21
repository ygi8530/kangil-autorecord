package com.yoonki.watchrecorder.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** iOS system palette + "inset grouped" surfaces (same look as the kangil-train app). */
object IosColors {
    val blue = Color(0xFF007AFF)
    val blueDark = Color(0xFF0A84FF)
    val green = Color(0xFF34C759)
    val orange = Color(0xFFFF9500)
    val red = Color(0xFFFF3B30)
    val purple = Color(0xFFAF52DE)
    val indigo = Color(0xFF5856D6)
    val teal = Color(0xFF30B0C7)
    val pink = Color(0xFFFF2D55)
    val yellow = Color(0xFFFFCC00)
    val gray = Color(0xFF8E8E93)
}

@Immutable
data class Palette(
    val background: Color,   // grouped page background
    val card: Color,         // section cards
    val fill: Color,         // search field, segmented control track
    val separator: Color,
    val label: Color,
    val secondary: Color,
    val accent: Color,
)

private val light = Palette(
    background = Color(0xFFF2F2F7), card = Color.White, fill = Color(0x1F767680),
    separator = Color(0xFFD1D1D6), label = Color.Black, secondary = Color(0x993C3C43), accent = IosColors.blue,
)
private val dark = Palette(
    background = Color.Black, card = Color(0xFF1C1C1E), fill = Color(0x3D767680),
    separator = Color(0xFF38383A), label = Color.White, secondary = Color(0x99EBEBF5), accent = IosColors.blueDark,
)

val LocalPalette = staticCompositionLocalOf { light }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val p = if (isDark) dark else light
    val scheme = if (isDark) {
        darkColorScheme(primary = p.accent, background = p.background, surface = p.card, onSurface = p.label, onBackground = p.label, error = IosColors.red)
    } else {
        lightColorScheme(primary = p.accent, background = p.background, surface = p.card, onSurface = p.label, onBackground = p.label, error = IosColors.red)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
