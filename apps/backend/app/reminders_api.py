"""Dashboard API for seeing and customising reminders. The caller supplies the sign-in check, so the same endpoints
serve every dashboard. It never exposes tokens, keys or phone credentials."""
import sqlite3
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field, field_validator

from . import services
from .db import get_db, tx
from .schemas import JobPatch, fmt, to_utc
from .security import now_iso

# What the owner picks -> what the phone is told (delivery, sound_profile).
STYLES = {
    "alarm": ("alarm", "default"),
    "alarm-silent": ("alarm", "silent"),
    "banner": ("notification", "default"),
    "banner-silent": ("notification", "silent"),
}
HISTORY_DAYS = 14
FUTURE_DAYS = 8
VISIBLE = ("job_id", "title", "message", "fire_at", "tz", "expires_at", "delivery", "sound_profile", "state",
           "category", "cancel_group", "routine_id")


class ReminderEdit(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=120)
    fire_at: datetime | None = None
    # "HH:MM" on the same day, in the reminder's own timezone (used instead of fire_at when only the clock time changes)
    time: str | None = Field(default=None, pattern=r"^([01]\d|2[0-3]):[0-5]\d$")
    style: Literal["alarm", "alarm-silent", "banner", "banner-silent"] | None = None
    enabled: bool | None = None

    @field_validator("fire_at")
    @classmethod
    def utc(cls, v):
        return None if v is None else to_utc(v)


def style_of(job: dict) -> str:
    for name, (delivery, sound) in STYLES.items():
        if job["delivery"] == delivery and (job["sound_profile"] == "silent") == (sound == "silent"):
            return name
    return "alarm"


def _guard(fn):
    try:
        return fn()
    except services.NotFound:
        raise HTTPException(status_code=404, detail="not found")
    except services.Conflict as exc:
        raise HTTPException(status_code=409, detail=str(exc))


def build_router(require_session) -> APIRouter:
    router = APIRouter(prefix="/api", dependencies=[Depends(require_session)])

    @router.get("/state")
    def state(conn: sqlite3.Connection = Depends(get_db)):
        with tx(conn):
            services.expire_due(conn)
        now = datetime.now(timezone.utc)
        start, end = fmt(now - timedelta(days=HISTORY_DAYS)), fmt(now + timedelta(days=FUTURE_DAYS))
        rows = conn.execute(
            "SELECT * FROM jobs WHERE (fire_at >= ? AND fire_at <= ?) OR state IN ('scheduled','fired') "
            "ORDER BY fire_at LIMIT 3000", (start, end)).fetchall()
        jobs = []
        for r in rows:
            d = services.job_to_dict(r)
            jobs.append({**{k: d[k] for k in VISIBLE}, "style": style_of(d)})
        ids = [j["job_id"] for j in jobs]
        answers = []
        for i in range(0, len(ids), 500):
            chunk = ids[i:i + 500]
            answers += [dict(a) for a in conn.execute(
                f"SELECT job_id, action_id, outcome, responded_at FROM responses WHERE job_id IN ({','.join('?' * len(chunk))}) "
                "ORDER BY responded_at", chunk)]
        phones = [dict(p) for p in conn.execute(
            "SELECT name, created_at, last_seen_at FROM devices WHERE revoked_at IS NULL ORDER BY created_at")]
        return {"now": now_iso(), "jobs": jobs, "answers": answers, "phones": phones}

    @router.patch("/reminders/{job_id}")
    def edit(job_id: str, body: ReminderEdit, conn: sqlite3.Connection = Depends(get_db)):
        with tx(conn):
            def run():
                row = services.get_job(conn, job_id)
                if body.enabled is False:
                    return services.cancel_job(conn, job_id)
                if body.enabled is True and row["state"] == "cancelled":
                    services.enable_job(conn, job_id)
                patch: dict = {}
                if body.title is not None:
                    patch["title"] = body.title
                if body.style is not None:
                    patch["delivery"], patch["sound_profile"] = STYLES[body.style]
                new_fire = body.fire_at
                if new_fire is None and body.time is not None:
                    zone = ZoneInfo(row["tz"])
                    old = datetime.fromisoformat(row["fire_at"].replace("Z", "+00:00")).astimezone(zone)
                    hour, minute = map(int, body.time.split(":"))
                    new_fire = old.replace(hour=hour, minute=minute, second=0, microsecond=0).astimezone(timezone.utc)
                if new_fire is not None:
                    patch["fire_at"] = new_fire
                    if row["expires_at"]:
                        keep = datetime.fromisoformat(row["expires_at"].replace("Z", "+00:00")) - \
                            datetime.fromisoformat(row["fire_at"].replace("Z", "+00:00"))
                        patch["expires_at"] = new_fire + keep
                if not patch:
                    return services.job_to_dict(services.get_job(conn, job_id))
                return services.update_job(conn, job_id, JobPatch(**patch))
            job = _guard(run)
        return {k: job[k] for k in VISIBLE} | {"style": style_of(job)}

    @router.post("/reminders/{job_id}/done")
    def done(job_id: str, conn: sqlite3.Connection = Depends(get_db)):
        with tx(conn):
            return _guard(lambda: services.mark_done(conn, job_id))

    return router
