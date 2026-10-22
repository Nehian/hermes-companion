package com.hermes.companion.data

import org.json.JSONObject
import java.time.Instant

fun JSONObject.str(key: String): String? = if (isNull(key)) null else getString(key)

fun parseIso(value: String): Long = Instant.parse(value).toEpochMilli()

data class Action(
    val actionId: String,
    val label: String,
    val type: String,
    val durationMin: Int?,
    val resultState: String?,
    val cancelsGroup: Boolean,
    val resultMessage: String?,
) {
    val isTimed get() = type == "snooze" || type == "defer"

    companion object {
        fun fromJson(o: JSONObject) = Action(
            actionId = o.getString("action_id"),
            label = o.getString("label"),
            type = o.getString("type"),
            durationMin = if (o.isNull("duration_min")) null else o.getInt("duration_min"),
            resultState = o.str("result_state"),
            cancelsGroup = !o.isNull("cancels_group") && o.getBoolean("cancels_group"),
            resultMessage = o.str("result_message"),
        )
    }
}

data class Job(
    val jobId: String,
    val category: String,
    val title: String,
    val message: String,
    val fireAtMs: Long,
    val expiresAtMs: Long?,
    val priority: String,
    val delivery: String,
    val soundProfile: String,
    val actions: List<Action>,
    val cancelGroup: String?,
    val revision: Long,
    val localState: String,
    val effectiveFireMs: Long,
    val resolvedActionId: String?,
    val isLocal: Boolean,
) {
    val isOpen get() = localState == STATE_SCHEDULED || localState == STATE_FIRED

    companion object {
        const val STATE_SCHEDULED = "scheduled"
        const val STATE_FIRED = "fired"
        const val STATE_RESOLVED = "resolved"
        const val STATE_CANCELLED = "cancelled"
        const val STATE_EXPIRED = "expired"
        const val STATE_MISSED = "missed"

        fun fromJson(
            o: JSONObject,
            localState: String,
            effectiveFireMs: Long,
            resolvedActionId: String?,
            isLocal: Boolean,
        ): Job {
            val actions = o.getJSONArray("actions")
            return Job(
                jobId = o.getString("job_id"),
                category = o.optString("category", "generic"),
                title = o.getString("title"),
                message = o.optString("message", ""),
                fireAtMs = parseIso(o.getString("fire_at")),
                expiresAtMs = o.str("expires_at")?.let(::parseIso),
                priority = o.optString("priority", "normal"),
                delivery = o.optString("delivery", "alarm"),
                soundProfile = o.optString("sound_profile", "default"),
                actions = List(actions.length()) { Action.fromJson(actions.getJSONObject(it)) },
                cancelGroup = o.str("cancel_group"),
                revision = o.optLong("revision", 0),
                localState = localState,
                effectiveFireMs = effectiveFireMs,
                resolvedActionId = resolvedActionId,
                isLocal = isLocal,
            )
        }
    }
}

data class OutboxRow(
    val responseId: String,
    val jobId: String,
    val actionId: String,
    val respondedAt: String,
)

data class ActionOutcome(val job: Job, val action: Action, val cancelledIds: List<String>)
