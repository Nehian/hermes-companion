package com.hermes.companion.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.ServiceCompat
import com.hermes.companion.data.Job
import com.hermes.companion.data.Store

/** Foreground service that plays the alarm sound on the alarm volume until the user answers (or a timeout). */
class AlarmService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val autoStop = Runnable { stopRinging(null) }

    companion object {
        const val EXTRA_JOB = "job_id"
        private const val RING_TIMEOUT_MS = 90_000L

        @Volatile
        var instance: AlarmService? = null
            private set

        @Volatile
        var ringingJobId: String? = null
            private set

        fun start(context: Context, jobId: String) {
            context.startForegroundService(Intent(context, AlarmService::class.java).putExtra(EXTRA_JOB, jobId))
        }

        /** Stops ringing if [jobId] is the one ringing (or any, when null). Safe when the service is not running. */
        fun stop(jobId: String?) {
            instance?.let { svc -> svc.main.post { svc.stopRinging(jobId) } }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val jobId = intent?.getStringExtra(EXTRA_JOB)
        val store = Store.get(this)
        val job = jobId?.let { store.getJob(it) }
        if (job == null) {
            // startForegroundService() requires startForeground(); satisfy it, then leave.
            startAsForeground(NotificationHelper.ringingNotification(this, placeholderJob()))
            stopRinging(null)
            return START_NOT_STICKY
        }
        val previous = ringingJobId
        if (previous != null && previous != job.jobId) store.getJob(previous)?.takeIf { it.isOpen }?.let { NotificationHelper.postPending(this, it) }
        ringingJobId = job.jobId
        startAsForeground(NotificationHelper.ringingNotification(this, job))
        store.log("alarm", "ringing ${job.jobId} (${job.title})")
        startSound(job)
        main.removeCallbacks(autoStop)
        main.postDelayed(autoStop, RING_TIMEOUT_MS)
        return START_NOT_STICKY
    }

    private fun placeholderJob() = Job(
        "none", "generic", "Reminder", "", 0, null, "normal", "alarm", "default", emptyList(), null, 0, Job.STATE_FIRED, 0, null, true,
    )

    private fun startAsForeground(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, NotificationHelper.RING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(NotificationHelper.RING_ID, notification)
        }
    }

    private fun startSound(job: Job) {
        stopSound()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hermes:ring").apply { acquire(RING_TIMEOUT_MS + 5_000) }
        if (job.soundProfile == "silent") return

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val audio = getSystemService(AudioManager::class.java)
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).build()
        audio.requestAudioFocus(focus!!)

        val store = Store.get(this)
        val candidates = listOfNotNull(
            store.ringtoneUri?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() },
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        )
        for (uri in candidates) {
            try {
                player = MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setWakeMode(this@AlarmService, PowerManager.PARTIAL_WAKE_LOCK)
                    setDataSource(this@AlarmService, uri)
                    isLooping = true
                    val vol = store.volumePercent / 100f
                    setVolume(vol, vol)
                    prepare()
                    start()
                }
                Store.get(this).log("alarm", "sound started: $uri")
                break
            } catch (e: Exception) {
                Store.get(this).log("alarm", "sound failed for $uri: ${e.message}")
                player?.release()
                player = null
            }
        }

        if (job.priority != "normal" || store.alwaysVibrate) {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
                .vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0), attrs)
        }
    }

    private fun stopSound() {
        player?.runCatching { stop(); release() }
        player = null
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
        getSystemService(Vibrator::class.java).cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun stopRinging(jobId: String?) {
        if (jobId != null && jobId != ringingJobId) return
        main.removeCallbacks(autoStop)
        stopSound()
        ringingJobId = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        NotificationHelper.refreshPending(this)
    }

    override fun onDestroy() {
        stopSound()
        if (instance === this) instance = null
        ringingJobId = null
        super.onDestroy()
    }
}
