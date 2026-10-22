package com.hermes.companion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.companion.data.Action
import com.hermes.companion.data.Job

/**
 * The alarm screen is deliberately NOT soft: flat, white, very large and very high contrast, in light and dark mode,
 * because it is read half asleep or in sunlight. No shadows. The first answer is the big filled button.
 */
@Composable
fun AlarmScreen(
    job: Job?,
    answerMessage: String?,
    ringing: Boolean,
    onAction: (Action) -> Unit,
    onSilence: () -> Unit,
    onClose: () -> Unit,
) = HermesTheme(forceLight = true) {
    val c = LocalHermes.current
    Surface(Modifier.fillMaxSize(), color = Color.White) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            when {
                answerMessage != null -> {
                    Spacer(Modifier.height(80.dp))
                    Text(answerMessage, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(32.dp))
                    BigButton("OK", c.ink, Color.White, onClose, height = 72)
                }
                job == null -> Text("Loading…", style = MaterialTheme.typography.titleLarge)
                !job.isOpen -> {
                    Spacer(Modifier.height(80.dp))
                    Text(job.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Text("This reminder is no longer active.", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(32.dp))
                    BigButton("Close", c.ink, Color.White, onClose, height = 72)
                }
                else -> {
                    if (job.priority != "normal") {
                        Row(
                            Modifier.background(c.accent, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(HermesIcons.Star, null, Modifier.size(16.dp), tint = Color.White)
                            Text("PRIORITY", style = MaterialTheme.typography.labelMedium, color = Color.White)
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                    Text(formatTime(job.effectiveFireMs), style = MaterialTheme.typography.displayMedium, color = c.ink)
                    Text(job.title, style = MaterialTheme.typography.headlineLarge, color = c.ink)
                    if (job.message.isNotBlank()) {
                        Spacer(Modifier.height(20.dp))
                        Text(job.message, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 30.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = Color(0xFF2C313D))
                    }
                    Spacer(Modifier.height(40.dp))
                    job.actions.forEachIndexed { index, action ->
                        if (index == 0) {
                            val fill = if (action.type == "complete") c.done else c.ink
                            BigButton(action.label, fill, Color.White, { onAction(action) }, height = 96, size = 30)
                        } else {
                            OutlinedBigButton(action.label, { onAction(action) })
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                    if (ringing) {
                        Text(
                            "Silence the sound", style = MaterialTheme.typography.titleSmall.copy(fontSize = 17.sp), color = c.ink,
                            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onSilence).padding(vertical = 16.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BigButton(label: String, fill: Color, text: Color, onClick: () -> Unit, height: Int, size: Int = 22) {
    Box(
        Modifier.fillMaxWidth().height(height.dp).background(fill, RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.headlineMedium.copy(fontSize = size.sp, lineHeight = (size + 6).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), color = text, textAlign = TextAlign.Center) }
}

@Composable
private fun OutlinedBigButton(label: String, onClick: () -> Unit) {
    val c = LocalHermes.current
    Box(
        Modifier.fillMaxWidth().height(72.dp).border(BorderStroke(3.dp, c.ink), RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = c.ink, textAlign = TextAlign.Center) }
}
