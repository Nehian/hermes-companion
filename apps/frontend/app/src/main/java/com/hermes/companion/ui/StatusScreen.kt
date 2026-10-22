package com.hermes.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun StatusScreen(state: UiState, onSync: () -> Unit) {
    val c = LocalHermes.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = Sp.s5, end = Sp.s5, top = Sp.s7, bottom = Sp.s5),
        verticalArrangement = Arrangement.spacedBy(Sp.s5),
    ) {
        Text("Status", style = MaterialTheme.typography.headlineMedium)
        NeuCard {
            StatusRow("Paired with Hermes", if (state.registered) "Yes (phone ${state.deviceId})" else "No", ok = state.registered)
            StatusRow("Server", state.serverUrl.ifBlank { "Not set" })
            StatusRow("Last successful sync", if (state.lastSyncOkMs > 0) formatTime(state.lastSyncOkMs, withDay = true) else "Never")
            StatusRow("Connection problem", state.lastError ?: "None", ok = state.lastError == null, bad = state.lastError != null)
            StatusRow("Answers waiting to upload", state.pendingUploads.toString())
            StatusRow("Next alarm", state.nextAlarmMs?.let { formatTime(it, withDay = true) } ?: "None scheduled")
        }
        NeuButton(if (state.busy) "Syncing…" else "Sync now", onSync, Modifier.fillMaxWidth(), primary = true, enabled = state.registered && !state.busy)
        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.muted) }
    }
}

@Composable
private fun StatusRow(label: String, value: String, ok: Boolean = false, bad: Boolean = false) {
    val c = LocalHermes.current
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.muted)
        Text(
            value, style = MaterialTheme.typography.bodyLarge,
            color = when { bad -> c.missed; ok -> c.done; else -> c.ink },
        )
    }
}
