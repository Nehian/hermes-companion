import pytest
from fastapi.testclient import TestClient

ADMIN = {"X-Admin-Key": "test-admin-key"}


@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("DB_PATH", str(tmp_path / "test.db"))
    monkeypatch.setenv("ADMIN_KEY", "test-admin-key")
    from app import api_device
    from app.main import app

    api_device._failures.clear()
    with TestClient(app) as c:
        yield c


@pytest.fixture()
def device(client):
    """A registered device: returns auth headers."""
    code = client.post("/admin/pairing-codes", headers=ADMIN).json()["pairing_code"]
    r = client.post("/v1/devices/register", json={"pairing_code": code, "name": "test phone"})
    assert r.status_code == 200
    body = r.json()
    return {"Authorization": f"Bearer {body['token']}", "device_id": body["device_id"]}


def make_job(**over):
    job = {
        "title": "Test alarm",
        "message": "hello",
        "fire_at": "2030-01-01T10:00:00Z",
        "tz": "Europe/London",
        "actions": [
            {"action_id": "done", "label": "Done", "type": "complete", "outcome": "completed",
             "cancels_group": True},
            {"action_id": "later", "label": "Snooze 5 min", "type": "snooze", "duration_min": 5},
        ],
    }
    job.update(over)
    return job
