import secrets
import sqlite3
import time
import uuid
from collections import defaultdict, deque

from fastapi import APIRouter, Depends, HTTPException, Query, Request

from . import services
from .db import get_db, tx
from .schemas import AckIn, RegisterIn, ResponsesIn
from .security import current_device, now_iso, sha256

router = APIRouter(prefix="/v1", tags=["device"])

MAX_FAILS = 5
WINDOW_SEC = 60
_failures: dict[str, deque] = defaultdict(deque)


def _rate_limited(client: str) -> bool:
    now = time.monotonic()
    q = _failures[client]
    while q and now - q[0] > WINDOW_SEC:
        q.popleft()
    return len(q) >= MAX_FAILS


@router.post("/devices/register")
def register(body: RegisterIn, request: Request, conn: sqlite3.Connection = Depends(get_db)):
    client = request.client.host if request.client else "unknown"
    if _rate_limited(client):
        raise HTTPException(status_code=429, detail="too many attempts, try again later")
    code = body.pairing_code.strip().upper().replace("-", "").replace(" ", "")
    with tx(conn):
        row = conn.execute(
            "SELECT code_hash FROM pairing_codes WHERE code_hash = ? AND used_at IS NULL AND expires_at > ?",
            (sha256(code), now_iso()),
        ).fetchone()
        if row is None:
            _failures[client].append(time.monotonic())
            raise HTTPException(status_code=400, detail="invalid or expired pairing code")
        conn.execute("UPDATE pairing_codes SET used_at = ? WHERE code_hash = ?", (now_iso(), row["code_hash"]))
        device_id = uuid.uuid4().hex
        token = secrets.token_urlsafe(32)
        conn.execute(
            "INSERT INTO devices(device_id, name, token_hash, created_at, last_seen_at) VALUES (?, ?, ?, ?, ?)",
            (device_id, body.name, sha256(token), now_iso(), now_iso()),
        )
        services.log_event(conn, "device_registered", device_id, detail=body.name)
    return {"device_id": device_id, "token": token}


@router.get("/sync")
def sync(since: int = Query(default=0, ge=0), device=Depends(current_device),
         conn: sqlite3.Connection = Depends(get_db)):
    with tx(conn):
        services.expire_due(conn)
    # Read the revision first and bound the query by it, so a concurrent change can never fall between the two reads.
    rev = services.current_rev(conn)
    jobs = services.changes_since(conn, since, rev)
    return {"server_rev": rev, "server_time": now_iso(), "jobs": jobs}


@router.post("/responses")
def submit_responses(body: ResponsesIn, device=Depends(current_device),
                     conn: sqlite3.Connection = Depends(get_db)):
    results = []
    with tx(conn):
        for r in body.responses:
            results.append({"response_id": r.response_id,
                            "status": services.apply_response(conn, device["device_id"], r)})
    return {"results": results, "server_rev": services.current_rev(conn)}


@router.post("/ack")
def ack(body: AckIn, device=Depends(current_device), conn: sqlite3.Connection = Depends(get_db)):
    with tx(conn):
        for job_id in body.received:
            services.log_event(conn, "job_received", device["device_id"], job_id)
        for job_id in body.fired:
            conn.execute("UPDATE jobs SET state = 'fired' WHERE job_id = ? AND state = 'scheduled'", (job_id,))
            services.log_event(conn, "job_fired", device["device_id"], job_id)
    return {"ok": True}


@router.get("/status")
def status(device=Depends(current_device), conn: sqlite3.Connection = Depends(get_db)):
    return {
        "device_id": device["device_id"],
        "name": device["name"],
        "server_rev": services.current_rev(conn),
        "server_time": now_iso(),
    }
