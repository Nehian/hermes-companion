import json
import logging
import sqlite3
import uuid

from .schemas import JobIn, JobPatch, ResponseIn, fmt
from .security import now_iso

log = logging.getLogger("hermes.api")

OPEN_STATES = ("scheduled", "fired")
# State a job moves to after an action of this type. None = unchanged (device reschedules locally).
DEFAULT_RESULT_STATE = {
    "complete": "resolved",
    "missed": "resolved",
    "acknowledge": "resolved",
    "dismiss": "resolved",
    "custom": "resolved",
    "defer": None,
    "snooze": None,
}


class NotFound(Exception):
    pass


class Conflict(Exception):
    pass


def log_event(conn: sqlite3.Connection, event: str, device_id: str | None = None,
              job_id: str | None = None, detail: str | None = None) -> None:
    conn.execute(
        "INSERT INTO sync_log(ts, event, device_id, job_id, detail) VALUES (?, ?, ?, ?, ?)",
        (now_iso(), event, device_id, job_id, detail),
    )
    log.info("event=%s device=%s job=%s %s", event, device_id, job_id, detail or "")


def next_rev(conn: sqlite3.Connection) -> int:
    conn.execute("UPDATE meta SET value = value + 1 WHERE key = 'rev'")
    return conn.execute("SELECT value FROM meta WHERE key = 'rev'").fetchone()[0]


def current_rev(conn: sqlite3.Connection) -> int:
    return conn.execute("SELECT value FROM meta WHERE key = 'rev'").fetchone()[0]


def job_to_dict(row: sqlite3.Row) -> dict:
    return {
        "job_id": row["job_id"],
        "routine_id": row["routine_id"],
        "category": row["category"],
        "title": row["title"],
        "message": row["message"],
        "fire_at": row["fire_at"],
        "tz": row["tz"],
        "expires_at": row["expires_at"],
        "priority": row["priority"],
        "delivery": row["delivery"],
        "sound_profile": row["sound_profile"],
        "actions": json.loads(row["actions_json"]),
        "state": row["state"],
        "cancel_group": row["cancel_group"],
        "revision": row["revision"],
        "created_at": row["created_at"],
        "updated_at": row["updated_at"],
    }


def get_job(conn: sqlite3.Connection, job_id: str) -> sqlite3.Row:
    row = conn.execute("SELECT * FROM jobs WHERE job_id = ?", (job_id,)).fetchone()
    if row is None:
        raise NotFound(job_id)
    return row


def expire_due(conn: sqlite3.Connection) -> None:
    now = now_iso()
    due = conn.execute(
        "SELECT job_id FROM jobs WHERE state = 'scheduled' AND expires_at IS NOT NULL AND expires_at <= ?",
        (now,),
    ).fetchall()
    for r in due:
        conn.execute(
            "UPDATE jobs SET state = 'expired', revision = ?, updated_at = ? WHERE job_id = ?",
            (next_rev(conn), now, r["job_id"]),
        )
        log_event(conn, "job_expired", job_id=r["job_id"])


def create_job(conn: sqlite3.Connection, data: JobIn) -> dict:
    job_id = data.job_id or uuid.uuid4().hex
    if conn.execute("SELECT 1 FROM jobs WHERE job_id = ?", (job_id,)).fetchone():
        raise Conflict(job_id)
    now = now_iso()
    conn.execute(
        """INSERT INTO jobs(job_id, routine_id, category, title, message, fire_at, tz, expires_at,
                            priority, delivery, sound_profile, actions_json, state, cancel_group,
                            revision, created_at, updated_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'scheduled', ?, ?, ?, ?)""",
        (
            job_id, data.routine_id, data.category, data.title, data.message, fmt(data.fire_at), data.tz,
            fmt(data.expires_at) if data.expires_at else None, data.priority, data.delivery,
            data.sound_profile, json.dumps([a.model_dump() for a in data.actions]),
            data.cancel_group, next_rev(conn), now, now,
        ),
    )
    log_event(conn, "job_created", job_id=job_id, detail=data.category)
    return job_to_dict(get_job(conn, job_id))


def update_job(conn: sqlite3.Connection, job_id: str, patch: JobPatch) -> dict:
    row = get_job(conn, job_id)
    if row["state"] not in OPEN_STATES:
        raise Conflict(f"job is {row['state']}")
    fields = {k: v for k, v in patch.model_dump(exclude_unset=True).items()
              if v is not None or k in ("expires_at", "cancel_group")}
    if not fields:
        return job_to_dict(row)
    fire_s = fmt(fields["fire_at"]) if "fire_at" in fields else row["fire_at"]
    if "expires_at" in fields:
        exp_s = fmt(fields["expires_at"]) if fields["expires_at"] else None
    else:
        exp_s = row["expires_at"]
    if exp_s is not None and exp_s <= fire_s:
        raise Conflict("expires_at must be after fire_at")
    sets, args = [], []
    for key, value in fields.items():
        if key in ("fire_at", "expires_at"):
            value = fmt(value) if value else None
        elif key == "actions":
            value = json.dumps([a if isinstance(a, dict) else a.model_dump() for a in value])
            key = "actions_json"
        sets.append(f"{key} = ?")
        args.append(value)
    sets += ["revision = ?", "updated_at = ?"]
    args += [next_rev(conn), now_iso(), job_id]
    conn.execute(f"UPDATE jobs SET {', '.join(sets)} WHERE job_id = ?", args)
    log_event(conn, "job_updated", job_id=job_id, detail=",".join(fields))
    return job_to_dict(get_job(conn, job_id))


def cancel_job(conn: sqlite3.Connection, job_id: str) -> dict:
    row = get_job(conn, job_id)
    if row["state"] in OPEN_STATES:
        conn.execute(
            "UPDATE jobs SET state = 'cancelled', revision = ?, updated_at = ? WHERE job_id = ?",
            (next_rev(conn), now_iso(), job_id),
        )
        log_event(conn, "job_cancelled", job_id=job_id)
    return job_to_dict(get_job(conn, job_id))


def enable_job(conn: sqlite3.Connection, job_id: str) -> dict:
    """Switch a cancelled reminder back on, as long as its time has not passed."""
    row = get_job(conn, job_id)
    if row["state"] != "cancelled":
        raise Conflict(f"job is {row['state']}")
    if row["fire_at"] <= now_iso():
        raise Conflict("this reminder's time has already passed")
    conn.execute(
        "UPDATE jobs SET state = 'scheduled', revision = ?, updated_at = ? WHERE job_id = ?",
        (next_rev(conn), now_iso(), job_id),
    )
    log_event(conn, "job_enabled", job_id=job_id)
    return job_to_dict(get_job(conn, job_id))


def mark_done(conn: sqlite3.Connection, job_id: str) -> dict:
    """The owner says this is done (for example ticked on the dashboard): the whole group is answered and cancelled."""
    row = get_job(conn, job_id)
    if row["cancel_group"]:
        return resolve_group(conn, row["cancel_group"], "completed", "marked done on the dashboard")
    now = now_iso()
    cancel_job(conn, job_id)
    conn.execute(
        """INSERT INTO responses(response_id, job_id, device_id, action_id, outcome, responded_at, received_at, note, applied)
           VALUES (?, ?, 'dashboard', 'dashboard-done', 'completed', ?, ?, 'marked done on the dashboard', 1)""",
        ("dash-" + uuid.uuid4().hex, job_id, now, now),
    )
    log_event(conn, "job_marked_done", job_id=job_id)
    return {"job_id": job_id, "outcome": "completed"}


def cancel_group(conn: sqlite3.Connection, group: str, except_job: str | None = None) -> list[str]:
    rows = conn.execute(
        "SELECT job_id FROM jobs WHERE cancel_group = ? AND state IN ('scheduled', 'fired') AND job_id != ?",
        (group, except_job or ""),
    ).fetchall()
    ids = []
    for r in rows:
        conn.execute(
            "UPDATE jobs SET state = 'cancelled', revision = ?, updated_at = ? WHERE job_id = ?",
            (next_rev(conn), now_iso(), r["job_id"]),
        )
        log_event(conn, "job_cancelled", job_id=r["job_id"], detail=f"group={group}")
        ids.append(r["job_id"])
    return ids


def changes_since(conn: sqlite3.Connection, since: int, upto: int | None = None) -> list[dict]:
    upto = current_rev(conn) if upto is None else upto
    rows = conn.execute(
        "SELECT * FROM jobs WHERE revision > ? AND revision <= ? ORDER BY revision", (since, upto)
    ).fetchall()
    return [job_to_dict(r) for r in rows]


def apply_response(conn: sqlite3.Connection, device_id: str, r: ResponseIn) -> str:
    if conn.execute("SELECT 1 FROM responses WHERE response_id = ?", (r.response_id,)).fetchone():
        return "duplicate"
    received = now_iso()
    job = conn.execute("SELECT * FROM jobs WHERE job_id = ?", (r.job_id,)).fetchone()
    action = None
    if job is not None:
        action = next((a for a in json.loads(job["actions_json"]) if a["action_id"] == r.action_id), None)

    def store(applied: int, outcome: str | None) -> None:
        conn.execute(
            """INSERT INTO responses(response_id, job_id, device_id, action_id, outcome, responded_at,
                                     received_at, note, applied)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (r.response_id, r.job_id, device_id, r.action_id, outcome, fmt(r.responded_at),
             received, r.note, applied),
        )

    if job is None:
        store(0, None)
        log_event(conn, "response_unknown_job", device_id, r.job_id)
        return "unknown_job"
    if action is None:
        store(0, None)
        log_event(conn, "response_unknown_action", device_id, r.job_id, r.action_id)
        return "unknown_action"

    applied = 0
    if job["state"] in OPEN_STATES:
        applied = 1
        new_state = action.get("result_state") or DEFAULT_RESULT_STATE[action["type"]]
        if new_state:
            conn.execute(
                "UPDATE jobs SET state = ?, revision = ?, updated_at = ? WHERE job_id = ?",
                (new_state, next_rev(conn), received, r.job_id),
            )
        if action.get("cancels_group") and job["cancel_group"]:
            cancel_group(conn, job["cancel_group"], except_job=r.job_id)
    store(applied, action.get("outcome"))
    log_event(conn, "response_received", device_id, r.job_id, f"{r.action_id} applied={applied}")
    return "stored"


def list_responses(conn: sqlite3.Connection, since_seq: int, limit: int = 500) -> list[dict]:
    rows = conn.execute(
        "SELECT rowid AS seq, * FROM responses WHERE rowid > ? ORDER BY rowid LIMIT ?",
        (since_seq, limit),
    ).fetchall()
    return [dict(r) for r in rows]


def resolve_group(conn: sqlite3.Connection, group: str, outcome: str, note: str | None) -> dict:
    """Hermes-side answer (e.g. the owner confirmed by message): cancels the group's open jobs and records the answer."""
    jobs = conn.execute("SELECT job_id FROM jobs WHERE cancel_group = ? ORDER BY fire_at", (group,)).fetchall()
    if not jobs:
        raise NotFound(group)
    cancelled = cancel_group(conn, group)
    now = now_iso()
    conn.execute(
        """INSERT INTO responses(response_id, job_id, device_id, action_id, outcome, responded_at, received_at, note, applied)
           VALUES (?, ?, 'hermes', 'message', ?, ?, ?, ?, 1)""",
        ("hermes-" + uuid.uuid4().hex, jobs[0]["job_id"], outcome, now, now, note),
    )
    log_event(conn, "group_resolved", job_id=jobs[0]["job_id"], detail=f"group={group} outcome={outcome}")
    return {"group": group, "outcome": outcome, "cancelled": cancelled}
