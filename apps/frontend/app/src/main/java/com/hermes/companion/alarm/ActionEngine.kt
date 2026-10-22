package com.hermes.companion.alarm

import android.content.Context
import com.hermes.companion.data.Store
import com.hermes.companion.sync.SyncScheduler

object ActionEngine {
    /** What to show the user after the tap: the server-defined follow-up message, if any. */
    data class Result(val message: String?)

    /** Records the answer locally (journal first, so it survives any failure), then updates alarms and syncs. */
    fun perform(context: Context, jobId: String, actionId: String): Result? {
        val store = Store.get(context)
        val outcome = store.applyAction(jobId, actionId, System.currentTimeMillis()) ?: return null
        store.log(
            "action",
            "job=$jobId action=$actionId type=${outcome.action.type} cancelled=${outcome.cancelledIds.size}",
        )

        AlarmService.stop(jobId)
        NotificationHelper.cancel(context, jobId)
        outcome.cancelledIds.forEach {
            AlarmScheduler.cancel(context, it)
            NotificationHelper.cancel(context, it)
        }
        AlarmScheduler.reconcile(context) // schedules a snooze/defer, drops cancelled alarms
        if (!outcome.job.isLocal) SyncScheduler.syncSoon(context)
        return Result(outcome.action.resultMessage)
    }
}
