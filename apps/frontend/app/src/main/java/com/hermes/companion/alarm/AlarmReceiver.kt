package com.hermes.companion.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hermes.companion.data.Job
import com.hermes.companion.data.Store
import com.hermes.companion.sync.SyncEngine
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val jobId = intent.data?.lastPathSegment ?: return
        val pending = goAsync()
        val app = context.applicationContext
        thread(name = "alarm-fire") {
            try {
                fire(app, jobId)
            } catch (e: Exception) {
                Store.get(app).log("alarm", "fire failed for $jobId: $e")
            } finally {
                pending.finish()
            }
        }
    }

    private fun fire(context: Context, jobId: String) {
        val store = Store.get(context)
        var job = store.getJob(jobId) ?: return
        if (job.localState != Job.STATE_SCHEDULED) return
        if (job.effectiveFireMs > System.currentTimeMillis() + 2_000) {
            AlarmScheduler.reconcile(context) // alarm for an old time; the current one is already scheduled
            return
        }

        // Pre-fire check: ask the server whether this reminder was cancelled meanwhile (e.g. confirmed by message).
        // Short timeouts and no waiting on a running sync; on any failure the alarm rings anyway.
        if (!job.isLocal && store.token != null) {
            runBlocking { SyncEngine.syncNow(context, "prefire", connectMs = 1000, readMs = 1500, waitForLock = false) }
            job = store.getJob(jobId) ?: return
            if (job.localState != Job.STATE_SCHEDULED) {
                store.log("alarm", "$jobId no longer scheduled after pre-fire check (${job.localState}); not ringing")
                return
            }
        }

        if (!store.markFiredIfScheduled(jobId)) return
        AlarmService.start(context, jobId)
    }
}
