package com.hermes.companion.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hermes.companion.AlarmActivity

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Today", HermesIcons.Today),
    Tab("Status", HermesIcons.Status),
    Tab("Settings", HermesIcons.Settings),
)

@Composable
fun AppShell(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val c = LocalHermes.current
    var selected by rememberSaveable { mutableIntStateOf(0) }

    LifecycleResumeEffect(Unit) {
        vm.onForeground()
        onPauseOrDispose { }
    }

    Scaffold(
        containerColor = c.surface,
        bottomBar = { FloatingNav(selected) { selected = it } },
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

/** Floating raised bar; the current tab sits pressed in and is coloured and bold, so it never relies on colour alone. */
@Composable
private fun FloatingNav(selected: Int, onSelect: (Int) -> Unit) {
    val c = LocalHermes.current
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Sp.s4, vertical = Sp.s3)) {
        Row(
            Modifier.fillMaxWidth().raised(Radius.panel, depth = 6.dp, blur = 14.dp).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tabs.forEachIndexed { index, tab ->
                val on = index == selected
                Column(
                    Modifier.weight(1f).heightIn(min = 56.dp)
                        .then(if (on) Modifier.inset(Radius.card, 3.dp, 7.dp) else Modifier)
                        .clickable(role = Role.Tab) { onSelect(index) }
                        .semantics { this.selected = on }
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(tab.icon, null, Modifier.size(22.dp), tint = if (on) c.accent else c.muted)
                    Text(
                        tab.title,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.SemiBold),
                        color = if (on) c.accent else c.muted,
                    )
                }
            }
        }
    }
}
