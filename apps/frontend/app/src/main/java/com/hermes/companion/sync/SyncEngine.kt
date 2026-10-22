package com.hermes.companion.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hermes.companion.alarm.AlarmScheduler
import com.hermes.companion.data.Store
import com.hermes.companion.net.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

object SyncEngine {
    private val mutex = Mutex()

    /** [retry] is true when trying again later can help (offline, server error); false for "nothing to do" or auth failure. */
    data class Result(val ok: Boolean, val message: String, val retry: Boolean = false)

    /** With [waitForLock] false, returns at once if another sync is running (used by the time-critical pre-fire check). */
    suspend fun syncNow(
        context: Context,
        reason: String,
        connectMs: Int = 6000,
        readMs: Int = 15000,
        waitForLock: Boolean = true,
    ): Result {
        if (!waitForLock && mutex.isLocked) return Result(false, "Another sync is running", retry = true)
        return mutex.withLock { withContext(Dispatchers.IO) { run(context.applicationContext, reason, connectMs, readMs) } }
    }

    private fun run(context: Context, reason: String, connectMs: Int, readMs: Int): Result {
        val store = Store.get(context)
        val base = store.serverUrl?.takeIf { it.isNotBlank() } ?: return Result(false, "Server address not set")
        val token = store.token ?: return Result(false, "This phone is not paired yet")
        try {
            val pending = store.outboxPending(100)
            if (pending.isNotEmpty()) {
                val statuses = Api.submitResponses(base, token, pending, connectMs, readMs)
                statuses.forEach { (id, status) -> store.markUploaded(id, status) }
                store.log("sync", "uploaded ${statuses.size} response(s) ($reason)")
            }

            val payload = Api.sync(base, token, store.lastRev, connectMs, readMs)
            val newIds = store.applyServerJobs(payload.jobs)
            // Cursor = highest revision actually applied, so a change racing the server read is never skipped.
            payload.jobs.maxOfOrNull { it.getLong("revision") }?.let { if (it > store.lastRev) store.lastRev = it }
            store.log("sync", "pulled ${payload.jobs.size} job(s), ${newIds.size} new ($reason)")

            AlarmScheduler.reconcile(context, fireOverdue = reason != "prefire")

            if (newIds.isNotEmpty()) runCatching { Api.ack(base, token, newIds) }
            store.putKv("last_sync_ok_ms", System.currentTimeMillis().toString())
            store.putKv("last_error", null)
            return Result(true, "Synced")
        } catch (e: Api.ApiException) {
            val auth = e.code == 401
            val msg = if (auth) "Server rejected this phone's credentials. Pair again in Settings." else "Server error: ${e.message}"
            fail(store, reason, msg, e.message)
            return Result(false, msg, retry = !auth)
        } catch (e: IOException) {
            val msg = "Server not reachable"
            fail(store, reason, msg, e.javaClass.simpleName + ": " + e.message)
            return Result(false, msg, retry = true)
        }
    }

    private fun fail(store: Store, reason: String, userMessage: String, detail: String?) {
        store.putKv("last_error", userMessage)
        store.noteUploadFailure(userMessage)
        store.log("sync", "failed ($reason): $detail")
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val r = SyncEngine.syncNow(applicationContext, "worker")
        return if (r.ok || !r.retry) Result.success() else Result.retry()
    }
}

object SyncScheduler {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(online).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("periodic-sync", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Runs a sync as soon as the network allows, retrying with backoff until it succeeds. */
    fun syncSoon(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("sync-soon", ExistingWorkPolicy.REPLACE, request)
    }
}
