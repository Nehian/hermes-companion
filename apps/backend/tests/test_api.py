from .conftest import ADMIN, make_job


def dev_headers(device):
    return {"Authorization": device["Authorization"]}


def test_health(client):
    assert client.get("/health").json()["status"] == "ok"


def test_admin_requires_key(client):
    assert client.post("/admin/pairing-codes").status_code == 401
    assert client.post("/admin/pairing-codes", headers={"X-Admin-Key": "wrong"}).status_code == 401
    assert client.get("/admin/jobs").status_code == 401


def test_admin_fails_closed_without_configured_key(client, monkeypatch):
    monkeypatch.delenv("ADMIN_KEY")
    assert client.get("/admin/jobs", headers=ADMIN).status_code == 503


def test_pairing_and_device_auth(client):
    code = client.post("/admin/pairing-codes", headers=ADMIN).json()["pairing_code"]
    ok = client.post("/v1/devices/register", json={"pairing_code": code.lower(), "name": "p"})
    assert ok.status_code == 200
    token = ok.json()["token"]
    # code is single use
    assert client.post("/v1/devices/register", json={"pairing_code": code, "name": "p2"}).status_code == 400
    # token works, bad token and missing token do not
    assert client.get("/v1/sync", headers={"Authorization": f"Bearer {token}"}).status_code == 200
    assert client.get("/v1/sync", headers={"Authorization": "Bearer nope"}).status_code == 401
    assert client.get("/v1/sync").status_code == 401
    # admin key is not a device token
    assert client.get("/v1/sync", headers={"Authorization": "Bearer test-admin-key"}).status_code == 401


def test_wrong_pairing_code_and_rate_limit(client):
    for _ in range(5):
        assert client.post("/v1/devices/register", json={"pairing_code": "WRONGCODE", "name": "x"}).status_code == 400
    assert client.post("/v1/devices/register", json={"pairing_code": "WRONGCODE", "name": "x"}).status_code == 429


def test_revoked_device_is_rejected(client, device):
    assert client.get("/v1/sync", headers=dev_headers(device)).status_code == 200
    assert client.post(f"/admin/devices/{device['device_id']}/revoke", headers=ADMIN).status_code == 200
    assert client.get("/v1/sync", headers=dev_headers(device)).status_code == 401


def test_job_validation(client):
    def post(**over):
        return client.post("/admin/jobs", json=make_job(**over), headers=ADMIN)

    assert post().status_code == 201
    assert post(fire_at="2030-01-01T10:00:00").status_code == 422  # naive time
    assert post(tz="Mars/Base").status_code == 422
    assert post(actions=[]).status_code == 422
    assert post(actions=[{"action_id": "x", "label": "X", "type": "rm_rf"}]).status_code == 422
    assert post(actions=[{"action_id": "x", "label": "X", "type": "snooze"}]).status_code == 422  # no duration
    dup = {"action_id": "x", "label": "X", "type": "dismiss"}
    assert post(actions=[dup, dup]).status_code == 422
    assert post(expires_at="2029-01-01T10:00:00Z").status_code == 422
    assert post(job_id="same").status_code == 201
    assert post(job_id="same").status_code == 409


def test_create_sync_respond_cancel_flow(client, device):
    a = client.post("/admin/jobs", json=make_job(job_id="a", cancel_group="g1"), headers=ADMIN).json()
    b = client.post("/admin/jobs", json=make_job(job_id="b", cancel_group="g1",
                                                 fire_at="2030-01-01T11:00:00Z"), headers=ADMIN).json()
    other = client.post("/admin/jobs", json=make_job(job_id="c", cancel_group="g2"), headers=ADMIN).json()
    assert a["revision"] < b["revision"] < other["revision"]

    sync = client.get("/v1/sync?since=0", headers=dev_headers(device)).json()
    assert [j["job_id"] for j in sync["jobs"]] == ["a", "b", "c"]
    assert sync["jobs"][0]["actions"][0]["label"] == "Done"
    rev = sync["server_rev"]

    r = client.post("/v1/responses", headers=dev_headers(device), json={"responses": [
        {"response_id": "resp-0001", "job_id": "a", "action_id": "done",
         "responded_at": "2030-01-01T10:00:05+00:00"}]})
    assert r.json()["results"] == [{"response_id": "resp-0001", "status": "stored"}]

    # same upload again is a duplicate and changes nothing
    again = client.post("/v1/responses", headers=dev_headers(device), json={"responses": [
        {"response_id": "resp-0001", "job_id": "a", "action_id": "done",
         "responded_at": "2030-01-01T10:00:05+00:00"}]})
    assert again.json()["results"][0]["status"] == "duplicate"

    changed = {j["job_id"]: j for j in client.get(f"/v1/sync?since={rev}", headers=dev_headers(device)).json()["jobs"]}
    assert changed["a"]["state"] == "resolved"
    assert changed["b"]["state"] == "cancelled"  # same group, cancelled by the action
    assert "c" not in changed  # other group untouched

    stored = client.get("/admin/responses", headers=ADMIN).json()["responses"]
    assert len(stored) == 1 and stored[0]["outcome"] == "completed" and stored[0]["applied"] == 1


def test_snooze_keeps_job_open(client, device):
    client.post("/admin/jobs", json=make_job(job_id="s"), headers=ADMIN)
    client.post("/v1/responses", headers=dev_headers(device), json={"responses": [
        {"response_id": "resp-snz1", "job_id": "s", "action_id": "later",
         "responded_at": "2030-01-01T10:00:05Z"}]})
    assert client.get("/admin/jobs/s", headers=ADMIN).json()["state"] == "scheduled"


def test_late_and_invalid_responses_are_stored_not_applied(client, device):
    client.post("/admin/jobs", json=make_job(job_id="x"), headers=ADMIN)
    client.post("/admin/jobs/x/cancel", headers=ADMIN)

    def send(rid, job, action):
        return client.post("/v1/responses", headers=dev_headers(device), json={"responses": [
            {"response_id": rid, "job_id": job, "action_id": action,
             "responded_at": "2030-01-01T10:00:05Z"}]}).json()["results"][0]["status"]

    assert send("resp-late1", "x", "done") == "stored"
    assert client.get("/admin/jobs/x", headers=ADMIN).json()["state"] == "cancelled"
    assert send("resp-bad01", "x", "nope") == "unknown_action"
    assert send("resp-bad02", "missing", "done") == "unknown_job"
    applied = {r["response_id"]: r["applied"] for r in client.get("/admin/responses", headers=ADMIN).json()["responses"]}
    assert applied == {"resp-late1": 0, "resp-bad01": 0, "resp-bad02": 0}


def test_update_and_cancel(client, device):
    client.post("/admin/jobs", json=make_job(job_id="u", expires_at="2030-01-01T12:00:00Z"), headers=ADMIN)
    before = client.get("/admin/jobs/u", headers=ADMIN).json()
    upd = client.patch("/admin/jobs/u", json={"fire_at": "2030-01-01T10:30:00Z", "title": "Moved"}, headers=ADMIN)
    assert upd.status_code == 200
    assert upd.json()["fire_at"] == "2030-01-01T10:30:00Z" and upd.json()["revision"] > before["revision"]
    assert client.patch("/admin/jobs/u", json={"fire_at": "2030-01-01T13:00:00Z"}, headers=ADMIN).status_code == 409
    assert client.post("/admin/jobs/u/cancel", headers=ADMIN).json()["state"] == "cancelled"
    assert client.patch("/admin/jobs/u", json={"title": "again"}, headers=ADMIN).status_code == 409
    assert client.post("/admin/jobs/missing/cancel", headers=ADMIN).status_code == 404


def test_admin_cancel_group(client, device):
    for i in (1, 2):
        client.post("/admin/jobs", json=make_job(job_id=f"g{i}", cancel_group="grp"), headers=ADMIN)
    assert sorted(client.post("/admin/groups/grp/cancel", headers=ADMIN).json()["cancelled"]) == ["g1", "g2"]
    jobs = client.get("/admin/jobs?cancel_group=grp", headers=ADMIN).json()["jobs"]
    assert {j["state"] for j in jobs} == {"cancelled"}


def test_expiry_on_sync(client, device):
    client.post("/admin/jobs", json=make_job(job_id="old", fire_at="2020-01-01T10:00:00Z",
                                            expires_at="2020-01-01T11:00:00Z"), headers=ADMIN)
    jobs = client.get("/v1/sync", headers=dev_headers(device)).json()["jobs"]
    assert jobs[0]["state"] == "expired"


def test_ack_marks_fired(client, device):
    client.post("/admin/jobs", json=make_job(job_id="f"), headers=ADMIN)
    assert client.post("/v1/ack", headers=dev_headers(device), json={"received": ["f"], "fired": ["f"]}).status_code == 200
    assert client.get("/admin/jobs/f", headers=ADMIN).json()["state"] == "fired"


def test_resolve_group_records_answer_and_cancels(client, device):
    for i in (1, 2):
        client.post("/admin/jobs", json=make_job(job_id=f"m{i}", cancel_group="msg"), headers=ADMIN)
    r = client.post("/admin/groups/msg/resolve", json={"outcome": "completed", "note": "said so in chat"}, headers=ADMIN)
    assert r.status_code == 200 and sorted(r.json()["cancelled"]) == ["m1", "m2"]
    jobs = client.get("/admin/jobs?cancel_group=msg", headers=ADMIN).json()["jobs"]
    assert {j["state"] for j in jobs} == {"cancelled"}
    resp = client.get("/admin/responses", headers=ADMIN).json()["responses"]
    assert resp[0]["outcome"] == "completed" and resp[0]["device_id"] == "hermes"
    # the phone learns about the cancellation on its next sync
    synced = client.get("/v1/sync", headers=dev_headers(device)).json()["jobs"]
    assert {j["state"] for j in synced} == {"cancelled"}
    assert client.post("/admin/groups/nope/resolve", json={"outcome": "completed"}, headers=ADMIN).status_code == 404
    assert client.post("/admin/groups/msg/resolve", json={"outcome": "BAD OUTCOME"}, headers=ADMIN).status_code == 422


def test_network_allowlist(tmp_path, monkeypatch):
    from fastapi.testclient import TestClient

    from app.main import app

    monkeypatch.setenv("DB_PATH", str(tmp_path / "net.db"))
    monkeypatch.setenv("ADMIN_KEY", "test-admin-key")
    monkeypatch.setenv("ALLOWED_NETS", "127.0.0.0/8,100.64.0.0/10,172.16.0.0/12")
    cases = {"100.96.174.77": 200, "127.0.0.1": 200, "172.31.0.1": 200, "192.168.0.50": 403, "8.8.8.8": 403}

    def from_ip(ip):  # present the request as coming from this client address
        async def wrapped(scope, receive, send):
            if scope["type"] == "http":
                scope = {**scope, "client": (ip, 5000)}
            await app(scope, receive, send)
        return wrapped

    for ip, expected in cases.items():
        with TestClient(from_ip(ip)) as c:
            assert c.get("/health").status_code == expected, ip
            assert c.post("/admin/pairing-codes", headers=ADMIN).status_code == (200 if expected == 200 else 403), ip


def test_pairing_code_lifetime_is_adjustable_and_capped(client):
    from datetime import datetime, timezone

    def minutes_left(url):
        r = client.post(url, headers=ADMIN).json()["expires_at"]
        return (datetime.fromisoformat(r.replace("Z", "+00:00")) - datetime.now(timezone.utc)).total_seconds() / 60

    assert 14 < minutes_left("/admin/pairing-codes") <= 15
    assert 59 < minutes_left("/admin/pairing-codes?minutes=60") <= 60
    assert client.post("/admin/pairing-codes?minutes=121", headers=ADMIN).status_code == 422
    assert client.post("/admin/pairing-codes?minutes=0", headers=ADMIN).status_code == 422
