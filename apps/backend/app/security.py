import hashlib
import hmac
import os
import sqlite3
from datetime import datetime, timezone

from fastapi import Depends, Header, HTTPException

from .db import get_db


def sha256(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def require_admin(x_admin_key: str | None = Header(default=None)) -> None:
    key = os.environ.get("ADMIN_KEY", "")
    if not key:
        raise HTTPException(status_code=503, detail="admin key not configured")
    if not x_admin_key or not hmac.compare_digest(x_admin_key.encode("utf-8"), key.encode("utf-8")):
        raise HTTPException(status_code=401, detail="invalid admin key")


def current_device(
    authorization: str | None = Header(default=None),
    conn: sqlite3.Connection = Depends(get_db),
) -> sqlite3.Row:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status_code=401, detail="missing device token")
    token = authorization[7:].strip()
    row = conn.execute("SELECT * FROM devices WHERE token_hash = ?", (sha256(token),)).fetchone()
    if row is None or row["revoked_at"] is not None:
        raise HTTPException(status_code=401, detail="invalid or revoked device token")
    conn.execute("UPDATE devices SET last_seen_at = ? WHERE device_id = ?", (now_iso(), row["device_id"]))
    return row
