package com.hermes.companion.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** One icon set: 24 px grid, 2 px strokes, rounded ends. Colour comes from the Icon tint. */
object HermesIcons {
    private fun icon(name: String, vararg paths: String, width: Float = 2f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    PathParser().parsePathString(d).toNodes(),
                    stroke = SolidColor(Color.Black), strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Check = icon("check", "M5 12.5l4.5 4.5L19 7.5", width = 2.2f)
    val Clock = icon("clock", "M12 3.5a8.5 8.5 0 1 0 0 17a8.5 8.5 0 1 0 0-17", "M12 7.5V12l3 2", width = 2.2f)
    val Minus = icon("minus", "M6 12h12", width = 2.2f)
    val Cross = icon("cross", "M7 7l10 10M17 7L7 17", width = 2.2f)
    val Today = icon("today", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18", "M8 12.5l3 3 5-6")
    val Status = icon("status", "M3 12h4l3-8 4 16 3-8h4")
    val Settings = icon("settings", "M4 7h10M18 7h2M4 17h2M10 17h10", "M16 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4", "M8 15a2 2 0 1 0 0 4a2 2 0 1 0 0-4")
    val Star = icon("star", "M12 3l2.6 6 6.4.6-4.8 4.3 1.4 6.3L12 17l-5.6 3.2 1.4-6.3L3 9.6 9.4 9z", width = 2.4f)
}
