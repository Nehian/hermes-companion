package com.hermes.companion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val dayTimeFmt = DateTimeFormatter.ofPattern("EEE HH:mm")
private val dateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM")

fun formatTime(ms: Long, withDay: Boolean = false): String =
    (if (withDay) dayTimeFmt else timeFmt).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

@Composable
fun TodayScreen(state: UiState, onOpen: (String) -> Unit, onFixPermissions: () -> Unit) {
    val c = LocalHermes.current
    val today = buildActivities(state.today)
    val coming = buildActivities(state.upcoming)
    val done = today.count { it.state == ActState.DONE }
    val next = today.firstOrNull { it.state == ActState.NEXT }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Sp.s5, end = Sp.s5, top = Sp.s7, bottom = Sp.s5),
        verticalArrangement = Arrangement.spacedBy(Sp.s3),
    ) {
        item {
            Column(Modifier.padding(bottom = Sp.s2)) {
                Text(DateTimeFormatter.ofPattern("EEEE d MMMM").format(java.time.LocalDate.now()), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                Text("Today", style = MaterialTheme.typography.headlineMedium)
            }
        }
        if (!state.perms.criticalOk) {
            item {
                NeuCard(radius = Radius.card) {
                    Text("Alarms are not fully allowed yet", style = MaterialTheme.typography.titleMedium, color = c.missed)
                    Text("Some permissions are missing, so a reminder may not ring or show.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                    NeuButton("Fix in Settings", onFixPermissions, small = true)
                }
            }
        }
        if (!state.registered) {
            item {
                NeuCard(radius = Radius.card) {
                    Text("This phone is not paired yet", style = MaterialTheme.typography.titleMedium)
                    Text("Open Settings, type the server address and the pairing code.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                }
            }
        }
        if (today.isNotEmpty()) item { ProgressCard(done, today.size, next) }
        item { SectionLabel(if (today.isEmpty()) "Activities" else "Activities", Modifier.padding(top = Sp.s2)) }
        if (today.isEmpty()) {
            item { Text("Nothing scheduled for today. Ask your assistant to set a reminder.", style = MaterialTheme.typography.bodyLarge, color = c.muted) }
        }
        items(today, key = { "t-" + it.key }) { ActivityCard(it, withDay = false, onOpen = onOpen) }
        if (coming.isNotEmpty()) {
            item { SectionLabel("Coming up", Modifier.padding(top = Sp.s4)) }
            items(coming, key = { "u-" + it.key }) { ActivityCard(it, withDay = true, onOpen = onOpen) }
        }
    }
}

@Composable
private fun ProgressCard(done: Int, total: Int, next: Activity?) {
    val c = LocalHermes.current
    val nextRow = next?.next
    Row(
        Modifier.fillMaxWidth().raised(Radius.panel, depth = 8.dp, blur = 18.dp).padding(Sp.s5),
        horizontalArrangement = Arrangement.spacedBy(Sp.s5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(112.dp).inset(56.dp, depth = 4.dp, blur = 9.dp).semantics { contentDescription = "$done of $total done" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(112.dp)) {
                val stroke = 8.dp.toPx()
                val frac = if (total == 0) 0f else done.toFloat() / total
                if (frac > 0f) {
                    drawArc(
                        color = c.accent, startAngle = -90f, sweepAngle = 360f * frac, useCenter = false,
                        topLeft = Offset(stroke * 1.4f, stroke * 1.4f), size = Size(size.width - stroke * 2.8f, size.height - stroke * 2.8f),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Text("$done/$total", style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize.times(0.76f)))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Done today", style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize.times(0.82f)))
            Text(
                if (nextRow != null) "Next: ${next.title} at ${formatTime(nextRow.timeMs)}" else if (done == total) "All done" else "Nothing left to ring",
                style = MaterialTheme.typography.bodyMedium, color = c.muted,
            )
            if (next?.priority == true) PriorityTag()
        }
    }
}

@Composable
fun PriorityTag() {
    val c = LocalHermes.current
    Row(
        Modifier.border(BorderStroke(1.5.dp, c.accent), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(HermesIcons.Star, null, Modifier.size(12.dp), tint = c.accent)
        Text("PRIORITY", style = MaterialTheme.typography.labelSmall, color = c.accent)
    }
}

@Composable
private fun ActivityCard(a: Activity, withDay: Boolean, onOpen: (String) -> Unit) {
    val c = LocalHermes.current
    val isNext = a.state == ActState.NEXT
    val tint = when (a.state) {
        ActState.DONE -> c.done
        ActState.NEXT -> c.accent
        ActState.MISSED -> c.missed
        else -> c.muted
    }
    val icon: ImageVector = when (a.state) {
        ActState.DONE -> HermesIcons.Check
        ActState.MISSED -> HermesIcons.Cross
        ActState.OFF -> HermesIcons.Minus
        else -> HermesIcons.Clock
    }
    val word = when (a.state) {
        ActState.DONE -> "Done"
        ActState.NEXT -> "Next"
        ActState.LATER -> "Later"
        ActState.MISSED -> "Missed"
        ActState.OFF -> "Off"
    }
    val first = a.next ?: a.rows.first()
    val detail = buildString {
        append(a.rows.size).append(if (a.rows.size == 1) " reminder" else " reminders")
        when (a.state) {
            ActState.DONE -> a.answer?.let { append(" · ").append(it) }
            ActState.NEXT -> append(" · next ").append(formatTime(first.timeMs, withDay))
            ActState.LATER -> append(" · from ").append(formatTime(first.timeMs, withDay))
            else -> Unit
        }
        if (a.priority && a.state != ActState.DONE) append(" · priority")
    }
    val open = a.openRow
    val shape = RoundedCornerShape(Radius.card)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .then(if (isNext) Modifier.inset(Radius.card, 4.dp, 9.dp).border(BorderStroke(1.5.dp, c.accent), shape) else Modifier.raised(Radius.card, 5.dp, 12.dp))
            .then(if (open != null) Modifier.clickable(onClickLabel = "Open ${a.title}") { onOpen(open.jobId) } else Modifier)
            .padding(horizontal = Sp.s4, vertical = Sp.s3),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).then(if (isNext) Modifier.raised(22.dp, 3.dp, 7.dp) else Modifier.inset(22.dp, 3.dp, 7.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(22.dp), tint = tint) }
        Column(Modifier.weight(1f)) {
            Text(a.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = c.muted, maxLines = 2)
        }
        StateWord(word, tint, bold = a.state != ActState.LATER && a.state != ActState.OFF)
    }
}
