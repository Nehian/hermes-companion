package com.hermes.companion

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import com.hermes.companion.alarm.AlarmScheduler

data class PermStatus(
    val notifications: Boolean = true,
    val fullScreen: Boolean = true,
    val exactAlarm: Boolean = true,
    val batteryUnrestricted: Boolean = true,
    /** Needed only so priority reminders can open full screen while the phone is in use. */
    val overlay: Boolean = true,
) {
    /** Without these the alarm cannot ring or show; battery settings only affect reliability on some phones. */
    val criticalOk get() = notifications && fullScreen && exactAlarm
}

object Perms {
    fun status(context: Context): PermStatus = PermStatus(
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        fullScreen = Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
        exactAlarm = AlarmScheduler.canScheduleExact(context),
        batteryUnrestricted = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
        overlay = android.provider.Settings.canDrawOverlays(context),
    )
}
