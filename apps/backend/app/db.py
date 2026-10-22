import os
import sqlite3
from contextlib import closing, contextmanager

SCHEMA = """
CREATE TABLE IF NOT EXISTS meta (
    key TEXT PRIMARY KEY,
    value INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS devices (
    device_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    token_hash TEXT NOT NULL UNIQUE,
    created_at TEXT NOT NULL,
    last_seen_at TEXT,
    revoked_at TEXT
);

CREATE TABLE IF NOT EXISTS pairing_codes (
    code_hash TEXT PRIMARY KEY,
    created_at TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    used_at TEXT
);

CREATE TABLE IF NOT EXISTS jobs (
    job_id TEXT PRIMARY KEY,
    routine_id TEXT,
    category TEXT NOT NULL,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    fire_at TEXT NOT NULL,
    tz TEXT NOT NULL,
    expires_at TEXT,
    priority TEXT NOT NULL,
    delivery TEXT NOT NULL,
    sound_profile TEXT NOT NULL,
    actions_json TEXT NOT NULL,
    state TEXT NOT NULL,
    cancel_group TEXT,
    revision INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_jobs_revision ON jobs(revision);
CREATE INDEX IF NOT EXISTS idx_jobs_group ON jobs(cancel_group);

-- Owner's priority choice per category (set from the dashboard). It wins over what the sender asked for.
CREATE TABLE IF NOT EXISTS category_priority (
    category TEXT PRIMARY KEY,
    priority TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS responses (
    response_id TEXT PRIMARY KEY,
    job_id TEXT NOT NULL,
    device_id TEXT NOT NULL,
    action_id TEXT NOT NULL,
    outcome TEXT,
    responded_at TEXT NOT NULL,
    received_at TEXT NOT NULL,
    note TEXT,
    applied INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sync_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ts TEXT NOT NULL,
    event TEXT NOT NULL,
    device_id TEXT,
    job_id TEXT,
    detail TEXT
);
"""


def db_path() -> str:
    return os.environ.get("DB_PATH", "/data/hermes.db")


def connect() -> sqlite3.Connection:
    conn = sqlite3.connect(db_path(), timeout=10, isolation_level=None)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA busy_timeout=10000")
    return conn


def init_db() -> None:
    with closing(connect()) as conn:
        conn.executescript(SCHEMA)
        conn.execute("INSERT OR IGNORE INTO meta(key, value) VALUES ('rev', 0)")


def get_db():
    conn = connect()
    try:
        yield conn
    finally:
        conn.close()


@contextmanager
def tx(conn: sqlite3.Connection):
    conn.execute("BEGIN IMMEDIATE")
    try:
        yield
        conn.execute("COMMIT")
    except BaseException:
        conn.execute("ROLLBACK")
        raise
