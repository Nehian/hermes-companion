package com.hermes.companion.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.hermes.companion.AlarmActivity
import com.hermes.companion.R
import com.hermes.companion.data.Job
import com.hermes.companion.data.Store
import java.util.concurrent.TimeUnit

object NotificationHelper {
    private const val CH_ALARM = "alarm"
    private const val CH_PENDING = "pending"
    const val RING_ID = 1

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ALARM, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rings and opens the alarm screen. Sound is played by the app on the alarm volume."
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_PENDING, "Reminders waiting for you", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Reminders that rang and still need an answer."
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    fun notifId(jobId: String): Int = 100 + (jobId.hashCode() and 0x0FFFFFFF)

    private fun openIntent(context: Context, job: Job, ringing: Boolean): PendingIntent {
        val intent = Intent(context, AlarmActivity::class.java)
            .putExtra(AlarmActivity.EXTRA_JOB, job.jobId)
            .putExtra(AlarmActivity.EXTRA_RINGING, ringing)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, notifId(job.jobId), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun ringingNotification(context: Context, job: Job): Notification =
        NotificationCompat.Builder(context, CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(job.title)
            .setContentText(job.message.ifBlank { "Tap to respond" })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            // Without this Android delays a foreground-service notification (and its full-screen intent) by up to 10 s.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openIntent(context, job, true))
            .setFullScreenIntent(openIntent(context, job, true), true)
            .build()

    private fun pendingNotification(context: Context, job: Job): Notification =
        NotificationCompat.Builder(context, CH_PENDING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(job.title)
            .setContentText(job.message.ifBlank { "Tap to respond" })
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setContentIntent(openIntent(context, job, false))
            .build()

    fun postPending(context: Context, job: Job) {
        context.getSystemService(NotificationManager::class.java).notify(notifId(job.jobId), pendingNotification(context, job))
    }

    fun cancel(context: Context, jobId: String) {
        context.getSystemService(NotificationManager::class.java).cancel(notifId(jobId))
    }

    /** Keeps one quiet notification per ring-but-unanswered job, and removes those of closed jobs. */
    fun refreshPending(context: Context) {
        val store = Store.get(context)
        val ringing = AlarmService.ringingJobId
        store.firedOpenJobs().filter { it.jobId != ringing }.forEach { postPending(context, it) }
        store.recentClosedJobIds(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3)).forEach { cancel(context, it) }
    }
}
