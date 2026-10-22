package com.hermes.companion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.composed

/*
 * Soft-depth building blocks. One light source, top left:
 *   raised  = cards, buttons          inset = inputs, pressed and selected things
 * Soft shadows never carry meaning alone: anything you tap also gets a 1.5 dp outline in the "edge" colour.
 */

private fun android.graphics.Paint.shadow(color: Int, blur: Float, dx: Float, dy: Float, fill: Int) = apply {
    isAntiAlias = true
    this.color = fill
    setShadowLayer(blur, dx, dy, color)
}

fun Modifier.raised(radius: Dp = Radius.card, depth: Dp = 5.dp, blur: Dp = 12.dp): Modifier = composed {
    val c = LocalHermes.current
    drawBehind {
        val r = radius.toPx()
        val d = depth.toPx()
        val b = blur.toPx()
        drawIntoCanvas { canvas ->
            val p = android.graphics.Paint()
            canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, p.shadow(c.shadowDark.toArgb(), b, d, d, c.surface.toArgb()))
            canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, p.shadow(c.shadowLight.toArgb(), b, -d, -d, c.surface.toArgb()))
        }
    }.background(c.surface, RoundedCornerShape(radius))
}

fun Modifier.inset(radius: Dp = Radius.card, depth: Dp = 3.dp, blur: Dp = 7.dp): Modifier = composed {
    val c = LocalHermes.current
    background(c.surface, RoundedCornerShape(radius)).drawBehind {
        val r = radius.toPx()
        val d = depth.toPx()
        val b = blur.toPx()
        val inner = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, r, r)) }
        val frame = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(-4 * b, -4 * b, size.width + 4 * b, size.height + 4 * b))
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, r, r))
        }
        clipPath(inner) {
            drawIntoCanvas { canvas ->
                val p = android.graphics.Paint()
                canvas.nativeCanvas.drawPath(frame.asAndroidPath(), p.shadow(c.shadowDark.toArgb(), b, d, d, c.surface.toArgb()))
                canvas.nativeCanvas.drawPath(frame.asAndroidPath(), p.shadow(c.shadowLight.toArgb(), b, -d, -d, c.surface.toArgb()))
            }
        }
    }
}

/** A raised panel for grouping content. */
@Composable
fun NeuCard(modifier: Modifier = Modifier, radius: Dp = Radius.panel, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().raised(radius, depth = 8.dp, blur = 18.dp).padding(Sp.s5),
        verticalArrangement = Arrangement.spacedBy(Sp.s3),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = LocalHermes.current.muted, modifier = modifier)
}

/** Button: raised with an outline; pressed it sinks. `primary` = filled accent, `done` = filled green. */
@Composable
fun NeuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    small: Boolean = false,
) {
    val c = LocalHermes.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(if (small) 24.dp else Radius.pill)
    val base = modifier.heightIn(min = if (small) MinTap else height)
    val fill = when {
        primary -> Modifier.background(c.accent, shape)
        pressed -> Modifier.inset(if (small) 24.dp else Radius.pill, 3.dp, 7.dp)
        else -> Modifier.raised(if (small) 24.dp else Radius.pill, 4.dp, 10.dp)
    }
    Box(
        base.then(fill).then(if (primary) Modifier else Modifier.border(BorderStroke(1.5.dp, c.edge), shape))
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 20.dp else Sp.s5),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, style = MaterialTheme.typography.titleSmall.copy(fontSize = if (small) MaterialTheme.typography.titleSmall.fontSize else MaterialTheme.typography.bodyLarge.fontSize),
            color = (if (primary) c.onAccent else c.ink).let { if (enabled) it else it.copy(alpha = 0.45f) },
            textAlign = TextAlign.Center,
        )
    }
}

/** Single-line text field: inset, with an outline. */
@Composable
fun NeuField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = LocalHermes.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.ink)
        BasicTextField(
            value = value, onValueChange = onValueChange, enabled = enabled, singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = c.ink),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).inset(Radius.control)
                        .border(BorderStroke(1.5.dp, c.edge), RoundedCornerShape(Radius.control)).padding(horizontal = Sp.s4),
                    contentAlignment = Alignment.CenterStart,
                ) { inner() }
            },
        )
    }
}

/** On/off switch: inset track (accent when on) and a white knob. */
@Composable
fun NeuSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    val c = LocalHermes.current
    Box(
        Modifier.size(width = 64.dp, height = MinTap).clickable(role = Role.Switch, onClick = { onCheckedChange(!checked) })
            .semantics { contentDescription = label + if (checked) ", on" else ", off" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(width = 64.dp, height = 36.dp)
                .background(if (checked) c.accent else c.surface, RoundedCornerShape(18.dp))
                .then(if (checked) Modifier else Modifier.border(BorderStroke(1.5.dp, c.edge), RoundedCornerShape(18.dp))),
        ) {
            Box(
                Modifier.align(if (checked) Alignment.CenterEnd else Alignment.CenterStart).padding(3.dp).size(30.dp)
                    .background(if (checked) androidx.compose.ui.graphics.Color.White else c.edge, RoundedCornerShape(15.dp)),
            )
        }
    }
}

/** Small status word: always text, colour only reinforces it. */
@Composable
fun StateWord(text: String, color: androidx.compose.ui.graphics.Color, bold: Boolean = true) {
    Text(
        text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal),
        color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** A row with a label on the left and content on the right. */
@Composable
fun SettingRow(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Sp.s3), verticalAlignment = Alignment.CenterVertically, content = content)
}
