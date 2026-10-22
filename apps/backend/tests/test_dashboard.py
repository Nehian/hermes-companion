from datetime import datetime, timedelta, timezone

import pytest

from app import dashboard, passwd

from .conftest import ADMIN, make_job

PW = "correct horse"


@pytest.fixture()
def dash(client, monkeypatch):
    monkeypatch.setenv("DASHBOARD_PASSWORD_HASH", passwd.hash_password(PW))
    dashboard._failures.clear()
    return client


def login(c, pw=PW):
    return c.post("/dashboard/login", json={"password": pw})


def soon(minutes):
    return (datetime.now(timezone.utc) + timedelta(minutes=minutes)).strftime("%Y-%m-%dT%H:%M:%SZ")


def new_job(c, **over):
    r = c.post("/admin/jobs", headers=ADMIN, json=make_job(**over))
    assert r.status_code == 201, r.text
    return r.json()


def test_off_until_a_password_is_set(client):
    assert client.get("/dashboard").status_code == 404
    assert client.get("/dashboard/api/state").status_code == 404
    assert client.post("/dashboard/login", json={"password": "x"}).status_code == 404


def test_sign_in_and_out(dash):
    assert dash.get("/dashboard").status_code == 200
    assert dash.get("/dashboard/api/state").status_code == 401
    assert dash.get("/dashboard/api/state", cookies={"hd": "forged.value"}).status_code == 401
    assert login(dash, "nope").status_code == 401
    assert login(dash).status_code == 200
    assert dash.get("/dashboard/api/state").status_code == 200
    dash.post("/dashboard/logout")
    assert dash.get("/dashboard/api/state").status_code == 401


def test_admin_key_is_not_the_password(dash):
    assert login(dash, "test-admin-key").status_code == 401


def test_login_rate_limit(dash):
    for _ in range(5):
        assert login(dash, "bad").status_code == 401
    assert login(dash).status_code == 429


def test_password_hash_roundtrip():
    h = passwd.hash_password("abcdefgh")
    assert passwd.verify("abcdefgh", h) and not passwd.verify("abcdefgX", h)
    assert not passwd.verify("abcdefgh", "garbage")
    assert "abcdefgh" not in h


def test_state_lists_jobs_answers_and_phones_without_secrets(dash, device):
    job = new_job(dash, fire_at=soon(60), cancel_group="g1", category="tablet")
    assert login(dash).status_code == 200
    s = dash.get("/dashboard/api/state").json()
    assert [j["job_id"] for j in s["jobs"]] == [job["job_id"]]
    assert s["jobs"][0]["style"] == "alarm" and s["jobs"][0]["category"] == "tablet"
    assert s["phones"][0]["name"] == "test phone"
    text = dash.get("/dashboard/api/state").text
    assert device["Authorization"].split()[1] not in text and "token" not in text and "actions" not in text


def test_edit_time_title_style_and_switch_off_on(dash):
    job = new_job(dash, fire_at=soon(60), expires_at=soon(90))
    jid = job["job_id"]
    assert login(dash).status_code == 200
    later = soon(120)
    r = dash.patch(f"/dashboard/api/reminders/{jid}", json={"title": "New title", "fire_at": later, "style": "banner-silent"})
    assert r.status_code == 200, r.text
    got = r.json()
    assert got["title"] == "New title" and got["fire_at"] == later[:19] + "Z"
    assert got["delivery"] == "notification" and got["sound_profile"] == "silent" and got["style"] == "banner-silent"
    # the expiry moved with the time, so the reminder is still valid
    assert dash.patch(f"/dashboard/api/reminders/{jid}", json={"enabled": False}).json()["state"] == "cancelled"
    assert dash.patch(f"/dashboard/api/reminders/{jid}", json={"enabled": True}).json()["state"] == "scheduled"
    # the phone sees every change through the normal revision cursor
    assert dash.get(f"/admin/jobs/{jid}", headers=ADMIN).json()["revision"] > job["revision"]


def test_edit_rejects_bad_input_and_past_reminders(dash):
    job = new_job(dash, fire_at=soon(60))
    jid = job["job_id"]
    assert login(dash).status_code == 200
    assert dash.patch(f"/dashboard/api/reminders/{jid}", json={"style": "loud"}).status_code == 422
    assert dash.patch(f"/dashboard/api/reminders/{jid}", json={"title": ""}).status_code == 422
    assert dash.patch(f"/dashboard/api/reminders/{jid}", json={"fire_at": "2030-01-01T10:00:00"}).status_code == 422
    assert dash.patch("/dashboard/api/reminders/nope", json={"enabled": False}).status_code == 404
    dash.patch(f"/dashboard/api/reminders/{jid}", json={"enabled": False})
    assert dash.patch(f"/dashboard/api/reminders/{jid}", json={"title": "x"}).status_code == 409


def test_mark_done_answers_the_whole_group(dash):
    a = new_job(dash, fire_at=soon(60), cancel_group="g2", title="A")
    b = new_job(dash, fire_at=soon(90), cancel_group="g2", title="B")
    assert login(dash).status_code == 200
    assert dash.post(f"/dashboard/api/reminders/{a['job_id']}/done").status_code == 200
    s = dash.get("/dashboard/api/state").json()
    assert {j["state"] for j in s["jobs"]} == {"cancelled"}
    assert {x["outcome"] for x in s["answers"]} == {"completed"}
    assert b["job_id"] in [j["job_id"] for j in s["jobs"]]


def test_mark_done_on_a_single_reminder(dash):
    a = new_job(dash, fire_at=soon(60))
    assert login(dash).status_code == 200
    assert dash.post(f"/dashboard/api/reminders/{a['job_id']}/done").status_code == 200
    s = dash.get("/dashboard/api/state").json()
    assert s["jobs"][0]["state"] == "cancelled" and s["answers"][0]["outcome"] == "completed"


def test_editing_needs_sign_in(dash):
    job = new_job(dash, fire_at=soon(60))
    assert dash.patch(f"/dashboard/api/reminders/{job['job_id']}", json={"enabled": False}).status_code == 401
    assert dash.post(f"/dashboard/api/reminders/{job['job_id']}/done").status_code == 401


def test_edit_by_clock_time_uses_the_reminders_own_timezone(dash):
    job = new_job(dash, fire_at="2030-07-01T08:00:00Z", tz="Europe/London", expires_at="2030-07-01T09:00:00Z")
    assert login(dash).status_code == 200
    r = dash.patch(f"/dashboard/api/reminders/{job['job_id']}", json={"time": "13:05"})
    assert r.status_code == 200, r.text
    assert r.json()["fire_at"] == "2030-07-01T12:05:00Z"  # 13:05 British Summer Time
    assert dash.get(f"/admin/jobs/{job['job_id']}", headers=ADMIN).json()["expires_at"] == "2030-07-01T13:05:00Z"
    assert dash.patch(f"/dashboard/api/reminders/{job['job_id']}", json={"time": "25:00"}).status_code == 422
