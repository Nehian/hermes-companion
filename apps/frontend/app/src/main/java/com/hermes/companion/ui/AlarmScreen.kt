package com.hermes.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.companion.data.Action
import com.hermes.companion.data.Job

@Composable
fun AlarmScreen(
    job: Job?,
    answerMessage: String?,
    ringing: Boolean,
    onAction: (Action) -> Unit,
    onSilence: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                answerMessage != null -> {
                    Text(answerMessage, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("OK", fontSize = 20.sp) }
                }
                job == null -> Text("Loading…", style = MaterialTheme.typography.titleLarge)
                !job.isOpen -> {
                    Text(job.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text("This reminder is no longer active.", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("Close", fontSize = 20.sp) }
                }
                else -> {
                    Text(formatTime(job.effectiveFireMs), style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(job.title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                    if (job.message.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(job.message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(32.dp))
                    job.actions.forEachIndexed { index, action ->
                        val modifier = Modifier.fillMaxWidth().height(64.dp)
                        if (index == 0) {
                            Button(onClick = { onAction(action) }, modifier = modifier) { Text(action.label, fontSize = 20.sp) }
                        } else {
                            FilledTonalButton(onClick = { onAction(action) }, modifier = modifier) { Text(action.label, fontSize = 20.sp) }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    if (ringing) TextButton(onClick = onSilence) { Text("Silence the sound") }
                }
            }
        }
    }
}
