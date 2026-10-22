package com.hermes.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StatusScreen(state: UiState, onSync: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Status", style = MaterialTheme.typography.headlineMedium)
        StatusRow("Paired with Hermes", if (state.registered) "Yes (phone ${state.deviceId})" else "No")
        StatusRow("Server", state.serverUrl.ifBlank { "Not set" })
        StatusRow(
            "Last successful sync",
            if (state.lastSyncOkMs > 0) formatTime(state.lastSyncOkMs, withDay = true) else "Never",
        )
        StatusRow("Connection problem", state.lastError ?: "None")
        StatusRow("Answers waiting to upload", state.pendingUploads.toString())
        StatusRow("Next alarm", state.nextAlarmMs?.let { formatTime(it, withDay = true) } ?: "None scheduled")
        Button(onClick = onSync, enabled = state.registered && !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.busy) "Syncing…" else "Sync now")
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
    }
}
