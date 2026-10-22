#!/usr/bin/env python3
"""hc: send and manage Hermes Companion reminders from the computer that runs the server.

It reads the admin key from docker/.env by itself and never prints it. Every failure prints one line starting with
"ERROR:" and exits with status 1."""
import argparse
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(os.environ.get("HC_DIR") or Path(__file__).resolve().parent)
URL = os.environ.get("HC_URL", "http://127.0.0.1:8020").rstrip("/")

STYLES = {  # style -> (delivery, sound_profile)
    "alarm": ("alarm", "default"),
    "alarm-silent": ("alarm", "silent"),
    "banner": ("notification", "default"),
    "banner-silent": ("notification", "silent"),
}
DEFAULT_ACTIONS = [
    {"action_id": "done", "label": "Done", "type": "complete", "outcome": "completed", "cancels_group": True},
    {"action_id": "snooze", "label": "Snooze 10 min", "type": "snooze", "duration_min": 10},
]


def fail(message: str):
    print(f"ERROR: {message}")
    sys.exit(1)


def admin_key() -> str:
    env_file = ROOT / "docker" / ".env"
    if env_file.is_file():
        for line in env_file.read_text().splitlines():
            if line.startswith("ADMIN_KEY="):
                return line.split("=", 1)[1].strip()
    if os.environ.get("HC_ADMIN_KEY"):
        return os.environ["HC_ADMIN_KEY"]
    fail(f"no admin key found (looked in {env_file}). Run install.sh first.")


def call(method: str, path: str, body=None):
    request = urllib.request.Request(
        URL + path, method=method, data=None if body is None else json.dumps(body).encode(),
        headers={"X-Admin-Key": admin_key(), "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=15) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        detail = e.read().decode(errors="replace")
        try:
            detail = json.dumps(json.loads(detail).get("detail", detail))
        except ValueError:
            pass
        fail(f"the server answered {e.code}: {detail}")
    except urllib.error.URLError as e:
        fail(f"cannot reach the server at {URL} ({e.reason}). Check it is running with: docker ps")


def local_tz_name() -> str:
    try:
        return Path("/etc/timezone").read_text().strip() or "UTC"
    except OSError:
        return "UTC"


def parse_when(at: str | None, within: str | None) -> datetime:
    now = datetime.now().astimezone()
    if within:
        m = re.fullmatch(r"(\d+)([mhd])", within.strip().lower())
        if not m:
            fail("--in must look like 30m, 2h or 1d")
        n, unit = int(m.group(1)), m.group(2)
        return now.replace(second=0, microsecond=0) + timedelta(**{{"m": "minutes", "h": "hours", "d": "days"}[unit]: n})
    if not at:
        fail("give a time with --at \"2026-10-23 08:00\" (or --at 08:00) or --in 30m")
    for fmt in ("%Y-%m-%d %H:%M", "%Y-%m-%dT%H:%M"):
        try:
            return datetime.strptime(at.strip(), fmt).astimezone()
        except ValueError:
            pass
    try:
        t = datetime.strptime(at.strip(), "%H:%M")
    except ValueError:
        fail("--at must look like \"2026-10-23 08:00\" or 08:00 (24-hour clock)")
    when = now.replace(hour=t.hour, minute=t.minute, second=0, microsecond=0)
    return when if when > now else when + timedelta(days=1)


def utc(dt: datetime) -> str:
    return dt.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def local(iso: str) -> str:
    return datetime.fromisoformat(iso.replace("Z", "+00:00")).astimezone().strftime("%a %d %b %H:%M")


def slug(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")[:30] or "reminder"


def cmd_send(a):
    if a.style not in STYLES:
        fail(f"--style must be one of: {', '.join(STYLES)}")
    first = parse_when(a.at, a.__dict__["in"])
    if first <= datetime.now().astimezone():
        fail("that time has already passed")
    days = 1
    if a.repeat:
        if a.repeat != "daily":
            fail("--repeat only supports: daily")
        days = max(1, min(a.days, 60))
    delivery, sound = STYLES[a.style]
    made = []
    for i in range(days):
        when = first.replace(tzinfo=None) + timedelta(days=i)  # same wall-clock time every day
        when = when.astimezone() if i else first
        body = {
            "title": a.title, "message": a.message or "", "fire_at": utc(when), "tz": a.tz or local_tz_name(),
            "category": a.category, "delivery": delivery, "sound_profile": sound, "actions": DEFAULT_ACTIONS,
            "priority": "high" if a.priority else "normal",
        }
        if not a.no_expiry:
            body["expires_at"] = utc(when + timedelta(minutes=a.expires))
        if a.group or days > 1:
            body["cancel_group"] = f"{a.group or slug(a.title)}-{when.strftime('%Y-%m-%d')}"[:64]
        job = call("POST", "/admin/jobs", body)
        made.append({"job_id": job["job_id"], "at": local(job["fire_at"])})
    print(json.dumps({"ok": True, "created": len(made), "first": made[0], "style": a.style}))


def jobs(state=None, group=None):
    query = "&".join(f"{k}={v}" for k, v in (("state", state), ("cancel_group", group)) if v)
    return call("GET", "/admin/jobs" + (f"?{query}" if query else ""))["jobs"]


def cmd_list(a):
    rows = jobs(None if a.all else "scheduled")
    if a.json:
        print(json.dumps(rows))
        return
    if not rows:
        print("No reminders.")
    for j in sorted(rows, key=lambda j: j["fire_at"]):
        print(f"{j['job_id']}  {local(j['fire_at'])}  {j['state']:<9} {j['title']}" + (f"  [{j['cancel_group']}]" if j["cancel_group"] else ""))


def cmd_cancel(a):
    if bool(a.job_id) == bool(a.group):
        fail("give either a job id or --group NAME")
    if a.group:
        done = call("POST", f"/admin/groups/{a.group}/cancel")
        print(json.dumps({"ok": True, "cancelled": len(done.get("cancelled", []))}))
    else:
        call("POST", f"/admin/jobs/{a.job_id}/cancel")
        print(json.dumps({"ok": True, "cancelled": 1}))


def cmd_answers(a):
    rows = call("GET", f"/admin/responses?since_seq={a.since}")["responses"]
    titles = {}
    for r in rows[-a.last:]:
        if r["job_id"] not in titles:
            try:
                titles[r["job_id"]] = call("GET", f"/admin/jobs/{r['job_id']}")["title"]
            except SystemExit:
                titles[r["job_id"]] = r["job_id"]
        print(f"{local(r['responded_at'])}  {r.get('outcome') or r['action_id']:<10} {titles[r['job_id']]}")
    if not rows:
        print("No answers yet.")


def tailscale_ip() -> str:
    try:
        return subprocess.run(["tailscale", "ip", "-4"], capture_output=True, text=True, timeout=5).stdout.split()[0]
    except (OSError, IndexError, subprocess.SubprocessError):
        return ""


def cmd_pair(_):
    code = call("POST", "/admin/pairing-codes?minutes=60")["pairing_code"]
    print(f"Server address: {tailscale_ip() or '<your Tailscale address>'}\nPairing code:   {code}   (valid 60 minutes, works once)")


def cmd_doctor(a):
    problems = []

    def step(ok: bool, good: str, bad: str):
        print(("OK    " if ok else "FAIL  ") + (good if ok else bad))
        if not ok:
            problems.append(bad)

    step(bool((ROOT / "docker" / ".env").is_file() or os.environ.get("HC_ADMIN_KEY")), "admin key found", "no admin key (run install.sh)")
    try:
        urllib.request.urlopen(URL + "/health", timeout=5).read()
        up = True
    except (urllib.error.URLError, OSError):
        up = False
    step(up, f"server answers at {URL}", f"server does not answer at {URL} (run: docker ps)")
    if up:
        devices = [d for d in call("GET", "/admin/devices")["devices"] if not d.get("revoked_at")]
        step(bool(devices), f"{len(devices)} phone(s) paired", "no phone is paired (run: pair.sh, then enter the code in the app)")
        if devices:
            seen = [d["last_seen_at"] for d in devices if d.get("last_seen_at")]
            step(bool(seen), "a phone has synced at least once", "no phone has synced yet (open the app once)")
    ip = tailscale_ip()
    step(bool(ip), f"Tailscale address {ip}", "Tailscale is not signed in on this computer")
    if a.ring and up and not problems:
        job = call("POST", "/admin/jobs", {
            "title": "Hermes Companion test", "message": "If you hear this, everything works.",
            "fire_at": utc(datetime.now().astimezone() + timedelta(minutes=1)), "tz": local_tz_name(),
            "expires_at": utc(datetime.now().astimezone() + timedelta(minutes=10)), "actions": DEFAULT_ACTIONS,
        })
        print(f"OK    test alarm set for {local(job['fire_at'])}; open the app on the phone so it syncs")
    print("ALL GOOD" if not problems else "PROBLEM: " + problems[0])
    sys.exit(0 if not problems else 1)


def main():
    p = argparse.ArgumentParser(prog="hc", description=__doc__.split("\n")[0])
    sub = p.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("send", help="create a reminder (or a daily series)")
    s.add_argument("--title", required=True)
    s.add_argument("--message")
    s.add_argument("--at", help='"2026-10-23 08:00" or 08:00 (computer\'s local time, 24-hour)')
    s.add_argument("--in", dest="in", help="30m, 2h or 1d from now")
    s.add_argument("--style", default="alarm", help=" | ".join(STYLES))
    s.add_argument("--category", default="generic")
    s.add_argument("--priority", action="store_true", help="open full screen on the phone even while it is in use")
    s.add_argument("--group", help="reminders sharing a group cancel each other when one is answered Done")
    s.add_argument("--repeat", help="daily")
    s.add_argument("--days", type=int, default=7, help="how many days with --repeat daily (max 60)")
    s.add_argument("--expires", type=int, default=120, help="minutes after which a missed reminder stops ringing")
    s.add_argument("--no-expiry", action="store_true")
    s.add_argument("--tz")
    s.set_defaults(fn=cmd_send)
    s = sub.add_parser("list", help="show reminders waiting to ring")
    s.add_argument("--all", action="store_true")
    s.add_argument("--json", action="store_true")
    s.set_defaults(fn=cmd_list)
    s = sub.add_parser("cancel", help="cancel one reminder or a whole group")
    s.add_argument("job_id", nargs="?")
    s.add_argument("--group")
    s.set_defaults(fn=cmd_cancel)
    s = sub.add_parser("answers", help="show what the phone answered")
    s.add_argument("--since", type=int, default=0)
    s.add_argument("--last", type=int, default=20)
    s.set_defaults(fn=cmd_answers)
    sub.add_parser("pair", help="print a new pairing code").set_defaults(fn=cmd_pair)
    s = sub.add_parser("doctor", help="check that everything works")
    s.add_argument("--ring", action="store_true", help="also ring a test alarm in one minute")
    s.set_defaults(fn=cmd_doctor)
    a = p.parse_args()
    a.fn(a)


if __name__ == "__main__":
    main()
