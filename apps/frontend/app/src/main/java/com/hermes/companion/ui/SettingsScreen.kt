package com.hermes.companion.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hermes.companion.BuildConfig
import com.hermes.companion.data.Store

@Composable
fun SettingsScreen(state: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    val c = LocalHermes.current
    var url by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var askedNotifications by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.serverUrl) {
        if (url.isBlank()) url = state.serverUrl.ifBlank { BuildConfig.DEFAULT_SERVER_URL }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = Sp.s5, end = Sp.s5, top = Sp.s7, bottom = Sp.s5),
        verticalArrangement = Arrangement.spacedBy(Sp.s5),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        NeuCard {
            SectionLabel("Connection")
            NeuField(url, { url = it }, "Server address (for example 100.64.0.5)")
            if (!state.registered) {
                NeuField(code, { code = it }, "Pairing code")
                NeuButton(if (state.busy) "Pairing…" else "Pair with Hermes", { vm.register(url, code) }, Modifier.fillMaxWidth(), primary = true, enabled = !state.busy)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(10.dp).border(BorderStroke(5.dp, c.done), CircleShape))
                    Text("Paired", style = MaterialTheme.typography.bodyLarge, color = c.done)
                    Text("phone ${state.deviceId}", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                }
                NeuButton("Forget this pairing", vm::forget, Modifier.fillMaxWidth())
            }
            state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.accent) }
        }

        NeuCard { SoundSection(context) }

        NeuCard {
            SectionLabel("Permissions")
            PermRow("Notifications", state.perms.notifications, "Allow") {
                if (Build.VERSION.SDK_INT >= 33 && !askedNotifications) {
                    askedNotifications = true
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
            }
            PermRow("Full-screen alarm over the lock screen", state.perms.fullScreen, "Allow") {
                context.startActivity(openAppSettings(context, Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT))
            }
            PermRow("Exact alarm timing", state.perms.exactAlarm, "Open") {
                context.startActivity(openAppSettings(context, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
            PermRow("Unrestricted battery use", state.perms.batteryUnrestricted, "Allow") {
                context.startActivity(openAppSettings(context, Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))
            }
            PermRow("Show priority alarms over other apps", state.perms.overlay, "Allow") {
                context.startActivity(openAppSettings(context, Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }
            Text(
                "Reminders marked Priority on the dashboard open full screen even while you are using the phone. " +
                    "Without the \"over other apps\" permission they appear as a banner at the top.",
                style = MaterialTheme.typography.bodyMedium, color = c.muted,
            )
            Text(
                "Alarms ring on the phone's alarm volume, even when the phone is on silent. " +
                    "Keep the alarm volume up (press a volume key and open the volume panel to check it).",
                style = MaterialTheme.typography.bodyMedium, color = c.muted,
            )
        }

        NeuCard {
            SectionLabel("Test")
            NeuButton("Ring a test alarm in 10 seconds", { vm.testAlarm(false) }, Modifier.fillMaxWidth())
            NeuButton("Ring a priority test alarm", { vm.testAlarm(true) }, Modifier.fillMaxWidth(), primary = true)
        }
    }
}

private fun openAppSettings(context: Context, action: String): Intent =
    Intent(action, Uri.parse("package:${context.packageName}"))

@Composable
private fun PermRow(label: String, granted: Boolean, buttonText: String, onClick: () -> Unit) {
    val c = LocalHermes.current
    SettingRow {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            StateWord(if (granted) "OK" else "Needs attention", if (granted) c.done else c.missed)
        }
        if (!granted) NeuButton(buttonText, onClick, small = true)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SoundSection(context: Context) {
    val c = LocalHermes.current
    val store = remember { Store.get(context) }
    var ringtone by remember { mutableStateOf(store.ringtoneUri) }
    var volume by remember { mutableFloatStateOf(store.volumePercent.toFloat()) }
    var vibrate by remember { mutableStateOf(store.alwaysVibrate) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            ringtone = uri?.toString()
            store.ringtoneUri = ringtone
        }
    }
    val name = remember(ringtone) {
        ringtone?.let { runCatching { RingtoneManager.getRingtone(context, Uri.parse(it)).getTitle(context) }.getOrNull() }
            ?: "Phone default alarm sound"
    }

    SectionLabel("Sound")
    SettingRow {
        Column(Modifier.weight(1f)) {
            Text("Alarm tone", style = MaterialTheme.typography.bodyLarge)
            Text(name, style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
        NeuButton("Choose", {
            picker.launch(
                Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtone?.let { Uri.parse(it) }),
            )
        }, small = true)
    }
    if (ringtone != null) NeuButton("Use the default tone", { ringtone = null; store.ringtoneUri = null }, Modifier.fillMaxWidth(), small = true)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Alarm loudness", style = MaterialTheme.typography.bodyLarge)
            Text("${volume.toInt()}%", style = MaterialTheme.typography.bodyLarge, color = c.accent)
        }
        Slider(
            value = volume, onValueChange = { volume = it }, valueRange = 10f..100f,
            onValueChangeFinished = { store.volumePercent = volume.toInt() },
            colors = SliderDefaults.colors(
                thumbColor = c.surface, activeTrackColor = c.accent, inactiveTrackColor = c.edge.copy(alpha = 0.35f),
            ),
            thumb = {
                Box(Modifier.size(40.dp).raised(20.dp, 3.dp, 8.dp).border(BorderStroke(1.5.dp, c.edge), CircleShape))
            },
        )
        Text("Scales the phone's alarm volume. Use the volume keys for the overall level.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
    }
    SettingRow {
        Text("Always vibrate", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        NeuSwitch(vibrate, { vibrate = it; store.alwaysVibrate = it }, "Always vibrate")
    }
}
