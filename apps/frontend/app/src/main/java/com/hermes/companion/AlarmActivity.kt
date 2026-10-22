package com.hermes.companion

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.hermes.companion.alarm.ActionEngine
import com.hermes.companion.alarm.AlarmService
import com.hermes.companion.data.Job
import com.hermes.companion.data.Store
import com.hermes.companion.ui.AlarmScreen
import com.hermes.companion.ui.HermesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shows one reminder with its server-defined title, message and buttons; works over the lock screen. */
class AlarmActivity : ComponentActivity() {
    companion object {
        const val EXTRA_JOB = "job_id"
        const val EXTRA_RINGING = "ringing"
    }

    private var jobId by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        jobId = intent.getStringExtra(EXTRA_JOB) ?: run { finish(); return }
        val store = Store.get(this)

        setContent {
            HermesTheme {
                val tick by Store.changes.collectAsState()
                var job by remember { mutableStateOf<Job?>(null) }
                var answerMessage by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(tick, jobId) { job = withContext(Dispatchers.IO) { store.getJob(jobId) } }

                AlarmScreen(
                    job = job,
                    answerMessage = answerMessage,
                    ringing = AlarmService.ringingJobId == jobId,
                    onAction = { action ->
                        lifecycleScope.launch {
                            val result = withContext(Dispatchers.IO) { ActionEngine.perform(applicationContext, jobId, action.actionId) }
                            val message = result?.message
                            if (message.isNullOrBlank()) finish() else answerMessage = message
                        }
                    },
                    onSilence = { AlarmService.stop(jobId) },
                    onClose = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_JOB)?.let { jobId = it }
    }
}
