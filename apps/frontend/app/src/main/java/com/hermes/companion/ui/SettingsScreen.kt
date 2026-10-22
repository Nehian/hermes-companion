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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hermes.companion.BuildConfig

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
            value = url, onValueChange = { url = it }, label = { Text("Server address") },
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
