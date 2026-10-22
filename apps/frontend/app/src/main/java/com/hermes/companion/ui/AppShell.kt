package com.hermes.companion.ui

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hermes.companion.AlarmActivity

private val tabs = listOf("Today", "Status", "Settings")

@Composable
fun AppShell(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var selected by rememberSaveable { mutableIntStateOf(0) }

    LifecycleResumeEffect(Unit) {
        vm.onForeground()
        onPauseOrDispose { }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = {},
                        label = { Text(title) },
                        alwaysShowLabel = true,
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                0 -> TodayScreen(
                    state,
                    onOpen = { id -> context.startActivity(Intent(context, AlarmActivity::class.java).putExtra(AlarmActivity.EXTRA_JOB, id)) },
                    onFixPermissions = { selected = 2 },
                )
                1 -> StatusScreen(state, onSync = vm::syncNow)
                else -> SettingsScreen(state, vm)
            }
        }
    }
}
