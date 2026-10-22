package com.hermes.companion.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

private class Db(context: Context) : SQLiteOpenHelper(context, "hermes.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE jobs (
                job_id TEXT PRIMARY KEY,
                json TEXT NOT NULL,
                fire_at_ms INTEGER NOT NULL,
                effective_fire_ms INTEGER NOT NULL,
                expires_at_ms INTEGER,
                local_state TEXT NOT NULL,
                revision INTEGER NOT NULL,
                cancel_group TEXT,
                resolved_action_id TEXT,
                is_local INTEGER NOT NULL DEFAULT 0,
                updated_ms INTEGER NOT NULL
            )""",
        )
        db.execSQL("CREATE INDEX jobs_state ON jobs(local_state)")
        db.execSQL("CREATE INDEX jobs_group ON jobs(cancel_group)")
        db.execSQL(
            """CREATE TABLE outbox (
                response_id TEXT PRIMARY KEY,
                job_id TEXT NOT NULL,
                action_id TEXT NOT NULL,
                responded_at TEXT NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                last_error TEXT,
                created_ms INTEGER NOT NULL,
                uploaded_ms INTEGER
            )""",
        )
        db.execSQL("CREATE TABLE kv (key TEXT PRIMARY KEY, value TEXT)")
        db.execSQL("CREATE TABLE log (id INTEGER PRIMARY KEY AUTOINCREMENT, ts_ms INTEGER NOT NULL, tag TEXT NOT NULL, msg TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

class Store private constructor(context: Context) {
    private val db: SQLiteDatabase = Db(context.applicationContext).writableDatabase

    companion object {
        @Volatile
        private var instance: Store? = null
        val changes = MutableStateFlow(0L)

        fun get(context: Context): Store =
            instance ?: synchronized(this) { instance ?: Store(context).also { instance = it } }

        private val OPEN = "('${Job.STATE_SCHEDULED}','${Job.STATE_FIRED}')"
    }

    fun notifyChanged() = changes.update { it + 1 }

    // ---- key/value (credentials, sync state) ----

    fun getKv(key: String): String? =
        db.rawQuery("SELECT value FROM kv WHERE key = ?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }

    fun putKv(key: String, value: String?) {
        if (value == null) db.delete("kv", "key = ?", arrayOf(key))
        else db.insertWithOnConflict("kv", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE)
        notifyChanged()
    }

    var serverUrl: String?
        get() = getKv("server_url")
        set(v) = putKv("server_url", v)
    /** Sound settings chosen on this phone (the sender only says "alarm" or "silent"). */
    var ringtoneUri: String?
        get() = getKv("ringtone_uri")
        set(v) = putKv("ringtone_uri", v)
    var volumePercent: Int
        get() = getKv("ring_volume")?.toIntOrNull()?.coerceIn(10, 100) ?: 100
        set(v) = putKv("ring_volume", v.coerceIn(10, 100).toString())
    var alwaysVibrate: Boolean
        get() = getKv("always_vibrate") == "1"
        set(v) = putKv("always_vibrate", if (v) "1" else "0")

    var deviceId: String?
        get() = getKv("device_id")
        set(v) = putKv("device_id", v)
    var token: String?
        get() = getKv("token")
        set(v) = putKv("token", v)
    var lastRev: Long
        get() = getKv("last_rev")?.toLongOrNull() ?: 0L
        set(v) = putKv("last_rev", v.toString())

    // ---- log ----

    fun log(tag: String, msg: String) {
        Log.i("Hermes/$tag", msg)
        db.insert("log", null, ContentValues().apply { put("ts_ms", System.currentTimeMillis()); put("tag", tag); put("msg", msg) })
        db.execSQL("DELETE FROM log WHERE id <= (SELECT MAX(id) - 1000 FROM log)")
    }

    // ---- jobs ----

    private fun Cursor.toJob(): Job = Job.fromJson(
        JSONObject(getString(getColumnIndexOrThrow("json"))),
        localState = getString(getColumnIndexOrThrow("local_state")),
        effectiveFireMs = getLong(getColumnIndexOrThrow("effective_fire_ms")),
        resolvedActionId = getString(getColumnIndexOrThrow("resolved_action_id")),
        isLocal = getInt(getColumnIndexOrThrow("is_local")) == 1,
    )

    private fun queryJobs(where: String, args: Array<String> = emptyArray(), order: String = "effective_fire_ms"): List<Job> =
        db.rawQuery("SELECT * FROM jobs WHERE $where ORDER BY $order", args).use { c ->
            buildList { while (c.moveToNext()) add(c.toJob()) }
        }

    fun getJob(jobId: String): Job? = queryJobs("job_id = ?", arrayOf(jobId)).firstOrNull()

    fun openJobs(): List<Job> = queryJobs("local_state IN $OPEN")

    fun firedOpenJobs(): List<Job> = queryJobs("local_state = '${Job.STATE_FIRED}'")

    fun jobsBetween(fromMs: Long, toMs: Long): List<Job> =
        queryJobs("effective_fire_ms >= ? AND effective_fire_ms < ?", arrayOf(fromMs.toString(), toMs.toString()))

    fun overdueFired(beforeMs: Long): List<Job> =
        queryJobs("local_state = '${Job.STATE_FIRED}' AND effective_fire_ms < ?", arrayOf(beforeMs.toString()))

    fun recentClosedJobIds(sinceMs: Long): List<String> =
        db.rawQuery("SELECT job_id FROM jobs WHERE local_state NOT IN $OPEN AND updated_ms >= ?", arrayOf(sinceMs.toString())).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }

    fun setState(jobId: String, state: String) {
        db.execSQL("UPDATE jobs SET local_state = ?, updated_ms = ? WHERE job_id = ?", arrayOf<Any?>(state, System.currentTimeMillis(), jobId))
        notifyChanged()
    }

    /** True only for the caller that moved the job from scheduled to fired (guards against double ringing). */
    fun markFiredIfScheduled(jobId: String): Boolean {
        val changed = db.update(
            "jobs",
            ContentValues().apply { put("local_state", Job.STATE_FIRED); put("updated_ms", System.currentTimeMillis()) },
            "job_id = ? AND local_state = ?",
            arrayOf(jobId, Job.STATE_SCHEDULED),
        ) > 0
        if (changed) notifyChanged()
        return changed
    }

    /** Applies server jobs; returns the ids of jobs this device had not seen before. */
    fun applyServerJobs(list: List<JSONObject>): List<String> {
        val newIds = mutableListOf<String>()
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            for (o in list) {
                val id = o.getString("job_id")
                val rev = o.getLong("revision")
                val serverState = o.getString("state")
                val fireMs = parseIso(o.getString("fire_at"))
                val expMs = o.str("expires_at")?.let(::parseIso)
                val group = o.str("cancel_group")
                val cur = db.rawQuery(
                    "SELECT local_state, revision, fire_at_ms, effective_fire_ms FROM jobs WHERE job_id = ?", arrayOf(id),
                ).use { c -> if (c.moveToFirst()) listOf(c.getString(0), c.getLong(1), c.getLong(2), c.getLong(3)) else null }

                if (cur == null) {
                    val state = if (serverState == "fired") Job.STATE_SCHEDULED else serverState
                    db.insert("jobs", null, ContentValues().apply {
                        put("job_id", id); put("json", o.toString()); put("fire_at_ms", fireMs); put("effective_fire_ms", fireMs)
                        put("expires_at_ms", expMs); put("local_state", state); put("revision", rev); put("cancel_group", group)
                        put("is_local", 0); put("updated_ms", now)
                    })
                    newIds += id
                } else if (rev > cur[1] as Long) {
                    var state = cur[0] as String
                    var effective = cur[3] as Long
                    if (state == Job.STATE_SCHEDULED || state == Job.STATE_FIRED) {
                        if (serverState in listOf(Job.STATE_CANCELLED, Job.STATE_EXPIRED, Job.STATE_RESOLVED)) {
                            state = serverState
                        } else if (fireMs != cur[2] as Long) {
                            effective = fireMs
                            state = Job.STATE_SCHEDULED
                        }
                    } else if (state == Job.STATE_CANCELLED && serverState == Job.STATE_SCHEDULED && fireMs > now) {
                        // The owner switched this reminder back on (for example from the dashboard).
                        effective = fireMs
                        state = Job.STATE_SCHEDULED
                    }
                    db.update("jobs", ContentValues().apply {
                        put("json", o.toString()); put("fire_at_ms", fireMs); put("effective_fire_ms", effective)
                        put("expires_at_ms", expMs); put("local_state", state); put("revision", rev); put("cancel_group", group)
                        put("updated_ms", now)
                    }, "job_id = ?", arrayOf(id))
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
        return newIds
    }

    /** Records the user's choice locally: state change, outbox row, group cancellation, snooze/defer. */
    fun applyAction(jobId: String, actionId: String, nowMs: Long): ActionOutcome? {
        val outcome = applyActionInTransaction(jobId, actionId, nowMs)
        if (outcome != null) notifyChanged()
        return outcome
    }

    private fun applyActionInTransaction(jobId: String, actionId: String, nowMs: Long): ActionOutcome? {
        db.beginTransaction()
        try {
            val job = getJob(jobId) ?: return null
            if (!job.isOpen) return null
            val action = job.actions.firstOrNull { it.actionId == actionId } ?: return null

            if (!job.isLocal) {
                db.insert("outbox", null, ContentValues().apply {
                    put("response_id", "r-" + UUID.randomUUID()); put("job_id", jobId); put("action_id", actionId)
                    put("responded_at", Instant.ofEpochMilli(nowMs).toString()); put("created_ms", nowMs)
                })
            }
            val state: String
            var effective = job.effectiveFireMs
            var resolvedAction: String? = null
            if (action.isTimed) {
                effective = nowMs + (action.durationMin ?: 5) * 60_000L
                state = Job.STATE_SCHEDULED
            } else {
                state = action.resultState ?: Job.STATE_RESOLVED
                resolvedAction = actionId
            }
            db.execSQL(
                "UPDATE jobs SET local_state = ?, effective_fire_ms = ?, resolved_action_id = ?, updated_ms = ? WHERE job_id = ?",
                arrayOf<Any?>(state, effective, resolvedAction, nowMs, jobId),
            )

            val cancelled = mutableListOf<String>()
            if (action.cancelsGroup && job.cancelGroup != null) {
                db.rawQuery(
                    "SELECT job_id FROM jobs WHERE cancel_group = ? AND job_id != ? AND local_state IN $OPEN",
                    arrayOf(job.cancelGroup, jobId),
                ).use { c -> while (c.moveToNext()) cancelled += c.getString(0) }
                for (id in cancelled) {
                    db.execSQL("UPDATE jobs SET local_state = ?, updated_ms = ? WHERE job_id = ?", arrayOf<Any?>(Job.STATE_CANCELLED, nowMs, id))
                }
            }
            db.setTransactionSuccessful()
            return ActionOutcome(job, action, cancelled)
        } finally {
            db.endTransaction()
        }
    }

    fun insertLocalTestJob(fireAtMs: Long): String {
        val id = "local-test-$fireAtMs"
        val o = JSONObject()
            .put("job_id", id).put("category", "test").put("title", "Test alarm")
            .put("message", "If you can hear this, alarms work on this phone.")
            .put("fire_at", Instant.ofEpochMilli(fireAtMs).toString()).put("priority", "critical")
            .put("delivery", "alarm").put("sound_profile", "default").put("revision", 0)
            .put(
                "actions",
                JSONArray()
                    .put(JSONObject().put("action_id", "done").put("label", "Done").put("type", "complete"))
                    .put(JSONObject().put("action_id", "snooze").put("label", "Snooze 1 min").put("type", "snooze").put("duration_min", 1)),
            )
        db.insert("jobs", null, ContentValues().apply {
            put("job_id", id); put("json", o.toString()); put("fire_at_ms", fireAtMs); put("effective_fire_ms", fireAtMs)
            put("local_state", Job.STATE_SCHEDULED); put("revision", 0); put("is_local", 1); put("updated_ms", System.currentTimeMillis())
        })
        notifyChanged()
        return id
    }

    // ---- outbox ----

    fun outboxPending(limit: Int): List<OutboxRow> =
        db.rawQuery(
            "SELECT response_id, job_id, action_id, responded_at FROM outbox WHERE uploaded_ms IS NULL ORDER BY created_ms LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(OutboxRow(c.getString(0), c.getString(1), c.getString(2), c.getString(3))) } }

    fun pendingUploadCount(): Int =
        db.rawQuery("SELECT COUNT(*) FROM outbox WHERE uploaded_ms IS NULL", null).use { it.moveToFirst(); it.getInt(0) }

    fun markUploaded(responseId: String, status: String) {
        db.execSQL(
            "UPDATE outbox SET uploaded_ms = ?, last_error = ? WHERE response_id = ?",
            arrayOf<Any?>(System.currentTimeMillis(), if (status == "stored" || status == "duplicate") null else status, responseId),
        )
        notifyChanged()
    }

    fun noteUploadFailure(error: String) {
        db.execSQL("UPDATE outbox SET attempts = attempts + 1, last_error = ? WHERE uploaded_ms IS NULL", arrayOf(error))
    }

    fun recentLog(limit: Int): List<String> =
        db.rawQuery("SELECT ts_ms, tag, msg FROM log ORDER BY id DESC LIMIT ?", arrayOf(limit.toString())).use { c ->
            buildList { while (c.moveToNext()) add("${c.getLong(0)} ${c.getString(1)} ${c.getString(2)}") }
        }
}
