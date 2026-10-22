package com.hermes.companion.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.hermes.companion.MainActivity
import com.hermes.companion.data.Job
import com.hermes.companion.data.Store
import java.util.concurrent.TimeUnit

object AlarmScheduler {
    const val ACTION_FIRE = "com.hermes.companion.ALARM_FIRE"

    /** A reminder whose time passed while the phone was off still rings if it is this recent (or not yet expired). */
    private val GRACE_MS = TimeUnit.MINUTES.toMillis(30)
    private val CLOSED_LOOKBACK_MS = TimeUnit.DAYS.toMillis(3)

    private fun fireIntent(context: Context, jobId: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(Uri.parse("hermes://job/$jobId"))
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun showIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun canScheduleExact(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun schedule(context: Context, job: Job) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = fireIntent(context, job.jobId)
        try {
            if (job.delivery == "alarm") {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(job.effectiveFireMs, showIntent(context)), pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, job.effectiveFireMs, pi)
            }
        } catch (e: SecurityException) {
            Store.get(context).log("alarm", "cannot schedule ${job.jobId}: exact alarms not permitted")
        }
    }

    fun cancel(context: Context, jobId: String) {
        val pi = fireIntent(context, jobId)
        context.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }

    /**
     * Brings the system alarms in line with the local database. Safe to call at any time (idempotent):
     * schedules future jobs, expires/cancels closed ones, and rings recently overdue ones.
     */
    fun reconcile(context: Context, fireOverdue: Boolean = true) {
        val store = Store.get(context)
        val now = System.currentTimeMillis()
        var scheduled = 0
        for (job in store.openJobs()) {
            val expires = job.expiresAtMs
            when {
                expires != null && now >= expires -> {
                    store.setState(job.jobId, Job.STATE_EXPIRED)
                    cancel(context, job.jobId)
                }
                job.localState == Job.STATE_FIRED -> Unit // already rang; waits for the user's answer
                job.effectiveFireMs > now -> {
                    schedule(context, job)
                    scheduled++
                }
                fireOverdue -> {
                    if (now < (expires ?: (job.effectiveFireMs + GRACE_MS))) {
                        store.log("alarm", "overdue ${job.jobId}, ringing now")
                        context.sendBroadcast(
                            Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE).setData(Uri.parse("hermes://job/${job.jobId}")),
                        )
                    } else {
                        store.setState(job.jobId, Job.STATE_MISSED)
                        cancel(context, job.jobId)
                    }
                }
            }
        }
        store.recentClosedJobIds(now - CLOSED_LOOKBACK_MS).forEach { cancel(context, it) }
        NotificationHelper.refreshPending(context)
        store.log("alarm", "reconciled: $scheduled alarm(s) scheduled")
    }

    fun nextAlarmMs(context: Context): Long? =
        Store.get(context).openJobs().filter { it.localState == Job.STATE_SCHEDULED && it.effectiveFireMs > System.currentTimeMillis() }
            .minOfOrNull { it.effectiveFireMs }
}
