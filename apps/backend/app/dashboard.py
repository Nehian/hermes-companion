"""Optional dashboard, served by the same server. Off until DASHBOARD_PASSWORD_HASH is set (see dashboard.sh).

It shows the reminders the server holds and the answers phones sent back, and lets the owner customise reminders.
Sign-in: a password checked against its hash, then a signed, HttpOnly, SameSite=Strict cookie."""
import base64
import hashlib
import hmac
import os
import time
from pathlib import Path

from fastapi import APIRouter, HTTPException, Request, Response
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

from . import passwd
from .reminders_api import build_router

SESSION_SECONDS = 12 * 3600
MAX_FAILURES, FAILURE_WINDOW = 5, 300
_failures: dict[str, list[float]] = {}


def _stored_hash() -> str:
    return os.environ.get("DASHBOARD_PASSWORD_HASH", "")


def _key() -> bytes:
    # Derived from the stored hash: stays valid across restarts, and changes when the password changes.
    return hashlib.sha256(b"hermes-dashboard-session:" + _stored_hash().encode()).digest()


def _sign(payload: str) -> str:
    return hmac.new(_key(), payload.encode(), hashlib.sha256).hexdigest()


def require_enabled() -> None:
    if not _stored_hash():
        raise HTTPException(status_code=404, detail="the dashboard is not switched on")


def require_session(request: Request) -> None:
    require_enabled()
    payload, _, sig = request.cookies.get("hd", "").partition(".")
    try:
        ok = bool(sig) and hmac.compare_digest(sig, _sign(payload)) and int(base64.urlsafe_b64decode(payload)) > time.time()
    except ValueError:
        ok = False
    if not ok:
        raise HTTPException(status_code=401, detail="sign in required")


class LoginIn(BaseModel):
    password: str = Field(max_length=200)


router = APIRouter(prefix="/dashboard", tags=["dashboard"])


@router.get("", include_in_schema=False)
def page():
    require_enabled()
    return FileResponse(Path(__file__).parent / "static" / "dashboard.html", headers={"Cache-Control": "no-store"})


@router.post("/login")
def login(body: LoginIn, request: Request, response: Response):
    require_enabled()
    ip = request.client.host if request.client else "?"
    now = time.time()
    recent = [t for t in _failures.get(ip, []) if now - t < FAILURE_WINDOW]
    if len(recent) >= MAX_FAILURES:
        raise HTTPException(status_code=429, detail="too many attempts, wait a few minutes")
    if not passwd.verify(body.password, _stored_hash()):
        _failures[ip] = recent + [now]
        raise HTTPException(status_code=401, detail="wrong password")
    _failures.pop(ip, None)
    payload = base64.urlsafe_b64encode(str(int(now) + SESSION_SECONDS).encode()).decode()
    response.set_cookie("hd", f"{payload}.{_sign(payload)}", max_age=SESSION_SECONDS, httponly=True,
                        samesite="strict", path="/dashboard")
    return {"ok": True}


@router.post("/logout")
def logout(response: Response):
    response.delete_cookie("hd", path="/dashboard")
    return {"ok": True}


router.include_router(build_router(require_session))
