@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.hermes.companion.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.companion.R

/*
 * Design tokens for the whole app (see docs/DESIGN.md). Screens never use raw colours, sizes or fonts:
 * they read these, so a new screen looks like it was always there.
 *
 * Look: soft white "Neumorphism" with one light source (top left), dark text, an outline on everything you tap,
 * one amber accent. The alarm screen is the exception: flat, large and white, always (see AlarmScreen).
 */

@Immutable
class HermesColors(
    val surface: Color,
    val ink: Color,
    val muted: Color,
    /** Outline of anything you can tap: at least 3:1 against the surface. */
    val edge: Color,
    val accent: Color,
    val onAccent: Color,
    val done: Color,
    val onDone: Color,
    val missed: Color,
    val shadowDark: Color,
    val shadowLight: Color,
    val dark: Boolean,
)

val LightHermes = HermesColors(
    surface = Color(0xFFEEF0F4), ink = Color(0xFF1A1D26), muted = Color(0xFF4A5160), edge = Color(0xFF7B8396),
    accent = Color(0xFF9A5200), onAccent = Color(0xFFFFFFFF), done = Color(0xFF1B6E42), onDone = Color(0xFFFFFFFF),
    missed = Color(0xFFAE3426), shadowDark = Color(0x8CA3AEC4), shadowLight = Color(0xF2FFFFFF), dark = false,
)

val DarkHermes = HermesColors(
    surface = Color(0xFF1C1F27), ink = Color(0xFFECEEF3), muted = Color(0xFFA7AEBD), edge = Color(0xFF8790A3),
    accent = Color(0xFFE3A85A), onAccent = Color(0xFF1A1D26), done = Color(0xFF7FC49A), onDone = Color(0xFF10261A),
    missed = Color(0xFFE58A7D), shadowDark = Color(0xB3000000), shadowLight = Color(0x14FFFFFF), dark = true,
)

val LocalHermes = staticCompositionLocalOf { LightHermes }

/** Spacing steps (dp): 4, 8, 12, 16, 20, 28. Screen edge 20, gap between cards 12, between sections 20. */
object Sp {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s7 = 28.dp
}

/** Corner radii (dp). */
object Radius {
    val control = 16.dp
    val card = 22.dp
    val panel = 28.dp
    val pill = 26.dp
}

/** Tap targets are at least this tall. */
val MinTap = 48.dp

private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Jakarta = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))

private fun style(size: Int, line: Int, weight: Int, spacing: Double = 0.0) = TextStyle(
    fontFamily = Jakarta, fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight(weight),
    letterSpacing = spacing.sp, fontFeatureSettings = "tnum",
)

/** Type scale. Display 34/40, alarm time 84/88, title 17-18, body 16, secondary 14, label 13 caps. */
val HermesTypography = Typography(
    displayMedium = style(84, 88, 800, -2.0),
    headlineLarge = style(36, 42, 700, -0.5),
    headlineMedium = style(34, 40, 700, -0.5),
    titleLarge = style(22, 28, 700),
    titleMedium = style(17, 22, 600),
    titleSmall = style(15, 20, 600),
    bodyLarge = style(16, 22, 600),
    bodyMedium = style(14, 20, 400),
    bodySmall = style(12, 16, 400),
    labelLarge = style(14, 20, 600),
    labelMedium = style(13, 18, 600, 0.8),
    labelSmall = style(12, 16, 600, 0.5),
)

@Composable
fun HermesTheme(forceLight: Boolean = false, content: @Composable () -> Unit) {
    val c = if (!forceLight && isSystemInDarkTheme()) DarkHermes else LightHermes
    val scheme = (if (c.dark) darkColorScheme() else lightColorScheme()).copy(
        primary = c.accent, onPrimary = c.onAccent, background = c.surface, onBackground = c.ink,
        surface = c.surface, onSurface = c.ink, onSurfaceVariant = c.muted, outline = c.edge,
        error = c.missed, secondary = c.done, onSecondary = c.onDone,
    )
    CompositionLocalProvider(LocalHermes provides c) {
        MaterialTheme(colorScheme = scheme, typography = HermesTypography, content = content)
    }
}
