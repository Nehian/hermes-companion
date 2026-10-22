import secrets
import sqlite3
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Query

from . import services
from .db import get_db, tx
from .schemas import JobIn, JobPatch, ResolveIn, fmt
from .security import now_iso, require_admin, sha256

router = APIRouter(prefix="/admin", dependencies=[Depends(require_admin)], tags=["admin"])

CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
PAIRING_TTL_MIN = 15
PAIRING_TTL_MAX_MIN = 120


def _guard(fn):
    try:
        return fn()
    except services.NotFound:
        raise HTTPException(status_code=404, detail="not found")
    except services.Conflict as exc:
        raise HTTPException(status_code=409, detail=str(exc))


@router.post("/pairing-codes")
def create_pairing_code(
    minutes: int = Query(default=PAIRING_TTL_MIN, ge=1, le=PAIRING_TTL_MAX_MIN),
    conn: sqlite3.Connection = Depends(get_db),
):
    code = "".join(secrets.choice(CODE_ALPHABET) for _ in range(8))
    now = datetime.now(timezone.utc)
    expires = fmt(now + timedelta(minutes=minutes))
    with tx(conn):
        conn.execute(
            "INSERT INTO pairing_codes(code_hash, created_at, expires_at) VALUES (?, ?, ?)",
            (sha256(code), fmt(now), expires),
        )
        services.log_event(conn, "pairing_code_created")
    return {"pairing_code": code, "expires_at": expires}


@router.post("/jobs", status_code=201)
def create_job(body: JobIn, conn: sqlite3.Connection = Depends(get_db)):
    with tx(conn):
        return _guard(lambda: services.create_job(conn, body))


@router.patch("/jobs/{job_id}")
def update_job(job_id: str, body: JobPatch, conn: sqlite3.Connection = Depends(get_db)):
    with tx(conn):
        return _guard(lambda: services.update_job(conn, job_id, body))


@router.post("/jobs/{job_id}/cancel")
def cancel_job(job_id: str, conn: sqlite3.Connection = Depends(get_db)):
    with tx(conn):
        return _guard(lambda: services.cancel_job(conn, job_id))


@router.post("/groups/{group}/cancel")
def cancel_group(group: str, conn: sqlite3.Connection = Depends(get_db)):
    with tx(conn):
        return {"cancelled": services.cancel_group(conn, group)}


@router.post("/groups/{group}/resolve")
def resolve_group(group: str, body: ResolveIn, conn: sqlite3.Connection = Depends(get_db)):
    with tx(conn):
        return _guard(lambda: services.resolve_group(conn, group, body.outcome, body.note))


@router.get("/jobs")
def list_jobs(
    routine_id: str | None = None,
    state: str | None = None,
    cancel_group: str | None = None,
    conn: sqlite3.Connection = Depends(get_db),
):
    with tx(conn):
        services.expire_due(conn)
    sql, args = "SELECT * FROM jobs WHERE 1=1", []
    for column, value in (("routine_id", routine_id), ("state", state), ("cancel_group", cancel_group)):
        if value is not None:
            sql += f" AND {column} = ?"
            args.append(value)
    rows = conn.execute(sql + " ORDER BY fire_at", args).fetchall()
    return {"jobs": [services.job_to_dict(r) for r in rows]}


@router.get("/jobs/{job_id}")
def get_job(job_id: str, conn: sqlite3.Connection = Depends(get_db)):
    return _guard(lambda: services.job_to_dict(services.get_job(conn, job_id)))


@router.get("/responses")
def list_responses(since_seq: int = Query(default=0, ge=0), conn: sqlite3.Connection = Depends(get_db)):
    return {"responses": services.list_responses(conn, since_seq)}


@router.get("/devices")
def list_devices(conn: sqlite3.Connection = Depends(get_db)):
    rows = conn.execute(
        "SELECT device_id, name, created_at, last_seen_at, revoked_at FROM devices ORDER BY created_at"
    ).fetchall()
    return {"devices": [dict(r) for r in rows]}


@router.post("/devices/{device_id}/revoke")
def revoke_device(device_id: str, conn: sqlite3.Connection = Depends(get_db)):
    with tx(conn):
        row = conn.execute("SELECT 1 FROM devices WHERE device_id = ?", (device_id,)).fetchone()
        if row is None:
            raise HTTPException(status_code=404, detail="not found")
        conn.execute(
            "UPDATE devices SET revoked_at = ? WHERE device_id = ? AND revoked_at IS NULL",
            (now_iso(), device_id),
        )
        services.log_event(conn, "device_revoked", device_id)
    return {"revoked": device_id}
