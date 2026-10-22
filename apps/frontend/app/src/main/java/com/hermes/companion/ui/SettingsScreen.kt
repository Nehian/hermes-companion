package com.hermes.companion.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import android.media.RingtoneManager
import com.hermes.companion.BuildConfig
import com.hermes.companion.data.Store

@Composable
fun SettingsScreen(state: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    var url by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var askedNotifications by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.serverUrl) {
        if (url.isBlank()) url = state.serverUrl.ifBlank { BuildConfig.DEFAULT_SERVER_URL }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Text("Connection", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = url, onValueChange = { url = it }, label = { Text("Server address (for example 100.64.0.5)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        if (!state.registered) {
            OutlinedTextField(
                value = code, onValueChange = { code = it }, label = { Text("Pairing code") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { vm.register(url, code) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.busy) "Pairing…" else "Pair with Hermes")
            }
        } else {
            Text("Paired (phone ${state.deviceId}).", style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = vm::forget, modifier = Modifier.fillMaxWidth()) { Text("Forget this pairing") }
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }

        HorizontalDivider()
        SoundSection(context)

        HorizontalDivider()
        Text("Permissions", style = MaterialTheme.typography.titleMedium)
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
        Text(
            "Alarms ring on the phone's alarm volume, even when the phone is on silent. " +
                "Keep the alarm volume up (press a volume key and open the volume panel to check it).",
            style = MaterialTheme.typography.bodyMedium,
        )

        HorizontalDivider()
        Text("Test", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = vm::testAlarm, modifier = Modifier.fillMaxWidth()) { Text("Ring a test alarm in 10 seconds") }
    }
}

private fun openAppSettings(context: Context, action: String): Intent =
    Intent(action, Uri.parse("package:${context.packageName}"))

@Composable
private fun PermRow(label: String, granted: Boolean, buttonText: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (granted) "OK" else "Needs attention",
                style = MaterialTheme.typography.labelLarge,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        if (!granted) OutlinedButton(onClick = onClick) { Text(buttonText) }
    }
}

@Composable
private fun SoundSection(context: Context) {
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

    Text("Sound", style = MaterialTheme.typography.titleMedium)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Alarm tone", style = MaterialTheme.typography.bodyLarge)
        Text(name, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                picker.launch(
                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtone?.let { Uri.parse(it) }),
                )
            }) { Text("Choose tone") }
            if (ringtone != null) OutlinedButton(onClick = { ringtone = null; store.ringtoneUri = null }) { Text("Use default") }
        }
    }
    Column {
        Text("Alarm loudness: ${volume.toInt()}%", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = volume, onValueChange = { volume = it }, valueRange = 10f..100f,
            onValueChangeFinished = { store.volumePercent = volume.toInt() },
        )
        Text("Scales the alarm volume of the phone. Use the volume keys for the overall level.", style = MaterialTheme.typography.bodyMedium)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Always vibrate", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = vibrate, onCheckedChange = { vibrate = it; store.alwaysVibrate = it })
    }
}
