package com.hermes.companion.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hermes.companion.data.Store
import com.hermes.companion.sync.SyncScheduler
import kotlin.concurrent.thread

/** Re-creates the system alarms after anything that can clear or shift them. */
class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        thread(name = "alarm-restore") {
            try {
                Store.get(app).log("restore", "triggered by ${intent.action}")
                SyncScheduler.ensurePeriodic(app)
                AlarmScheduler.reconcile(app)
                SyncScheduler.syncSoon(app)
            } catch (e: Exception) {
                Store.get(app).log("restore", "failed: $e")
            } finally {
                pending.finish()
            }
        }
    }
}
