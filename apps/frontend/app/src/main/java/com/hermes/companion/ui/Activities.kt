package com.hermes.companion.ui

/** What one activity (a group of reminders that cancel each other, or a single reminder) looks like today. */
enum class ActState { DONE, NEXT, LATER, MISSED, OFF }

data class Activity(
    val key: String,
    val title: String,
    val rows: List<JobRow>,
    val state: ActState,
    val next: JobRow?,
    val answer: String?,
    val priority: Boolean,
) {
    /** The reminder to open when the card is tapped: the one waiting for an answer, else the next one. */
    val openRow: JobRow? get() = rows.firstOrNull { it.state == "fired" } ?: rows.firstOrNull { it.open }
}

private fun commonTitle(rows: List<JobRow>): String {
    val words = rows.map { it.title.split(Regex("\\s+")) }
    val shared = mutableListOf<String>()
    for (i in words.first().indices) {
        val w = words.first()[i]
        if (words.all { it.getOrNull(i) == w }) shared += w else break
    }
    val joined = shared.joinToString(" ").trimEnd(':', '-', ',', ' ')
    return joined.ifBlank { rows.first().title }
}

/** Groups reminders into activities, in time order. The earliest still-open activity is the "next" one. */
fun buildActivities(rows: List<JobRow>): List<Activity> {
    val groups = rows.groupBy { it.group ?: it.jobId }.values.map { it.sortedBy { r -> r.timeMs } }.sortedBy { it.first().timeMs }
    var nextTaken = false
    return groups.map { g ->
        val resolved = g.firstOrNull { it.state == "resolved" }
        val open = g.filter { it.open }
        val state = when {
            resolved != null -> ActState.DONE
            open.isNotEmpty() -> if (!nextTaken) ActState.NEXT.also { nextTaken = true } else ActState.LATER
            g.all { it.state == "cancelled" } -> ActState.OFF
            else -> ActState.MISSED
        }
        Activity(
            key = g.first().group ?: g.first().jobId,
            title = if (g.size > 1) commonTitle(g) else g.first().title,
            rows = g,
            state = state,
            next = open.firstOrNull(),
            answer = resolved?.answerLabel,
            priority = g.filter { it.open || it.state == "cancelled" }.let { live -> live.isNotEmpty() && live.all { it.priority } },
        )
    }
}
