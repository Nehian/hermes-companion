package com.hermes.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val dayTimeFmt = DateTimeFormatter.ofPattern("EEE HH:mm")

fun formatTime(ms: Long, withDay: Boolean = false): String =
    (if (withDay) dayTimeFmt else timeFmt).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

fun stateLabel(row: JobRow): String = when (row.state) {
    "scheduled" -> "Scheduled"
    "fired" -> "Needs your answer"
    "resolved" -> row.answerLabel?.let { "Done: $it" } ?: "Done"
    "cancelled" -> "Cancelled"
    "expired" -> "Expired"
    "missed" -> "Missed"
    else -> row.state
}

@Composable
fun TodayScreen(state: UiState, onOpen: (String) -> Unit, onFixPermissions: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Today", style = MaterialTheme.typography.headlineMedium) }
        if (!state.perms.criticalOk) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Alarms are not fully allowed yet", style = MaterialTheme.typography.titleMedium)
                        Text("Some permissions are missing, so a reminder may not ring or show.")
                        TextButton(onClick = onFixPermissions) { Text("Fix in Settings") }
                    }
                }
            }
        }
        if (!state.registered) {
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("This phone is not paired yet", style = MaterialTheme.typography.titleMedium)
                        Text("Open Settings and enter the pairing code from Hermes.")
                    }
                }
            }
        }
        if (state.today.isEmpty()) item { Text("Nothing scheduled for today.", style = MaterialTheme.typography.bodyLarge) }
        items(state.today, key = { "t-" + it.jobId }) { JobCard(it, false, onOpen) }
        if (state.upcoming.isNotEmpty()) {
            item { Text("Coming up", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
            items(state.upcoming, key = { "u-" + it.jobId }) { JobCard(it, true, onOpen) }
        }
    }
}

@Composable
private fun JobCard(row: JobRow, withDay: Boolean, onOpen: (String) -> Unit) {
    val content: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(formatTime(row.timeMs, withDay), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.weight(1f)) {
                Text(row.title, style = MaterialTheme.typography.titleMedium)
                if (row.message.isNotBlank()) Text(row.message, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                Text(stateLabel(row), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (row.open) Card(onClick = { onOpen(row.jobId) }) { content() } else Card { content() }
}
