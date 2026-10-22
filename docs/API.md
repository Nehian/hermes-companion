# API reference

Base URL: your server, for example `http://127.0.0.1:8020`. All bodies are JSON. Times are ISO 8601 and **must include a timezone offset** (`Z` or `+01:00`); the server stores UTC.

Two kinds of caller:
- **Sender / admin** (your assistant or script): header `X-Admin-Key: <ADMIN_KEY>` on every `/admin/*` call.
- **Phone**: header `Authorization: Bearer <device token>` on every `/v1/*` call (except registration). The token is issued once, at pairing.

If `ALLOWED_NETS` is set, clients outside those networks get `403` before anything else happens.

## Job

| field | notes |
|---|---|
| `job_id` | optional, `[A-Za-z0-9_.:-]` up to 64 chars; generated if omitted; reusing one returns `409` |
| `title` | required, up to 120 chars |
| `message` | optional, up to 1000 chars |
| `fire_at` | required, when to ring |
| `tz` | optional IANA zone name, informational (default `UTC`) |
| `expires_at` | optional; must be after `fire_at`. A reminder never rings after it |
| `priority` | `normal` (default), `high`, `critical` (high and critical also vibrate) |
| `delivery` | `alarm` (default: full alarm) or `notification` |
| `sound_profile` | `default` or `silent` |
| `category`, `routine_id` | free labels you can filter on |
| `cancel_group` | jobs sharing a group can cancel each other (see actions) |
| `actions` | 1 to 6 buttons, see below |

Server-set: `state` (`scheduled`, `fired`, `resolved`, `cancelled`, `expired`), `revision`, `created_at`, `updated_at`.

### Action
| field | notes |
|---|---|
| `action_id` | unique within the job |
| `label` | button text, up to 40 chars |
| `type` | one of `complete`, `defer`, `snooze`, `dismiss`, `missed`, `acknowledge`, `custom`. `snooze`/`defer` need `duration_min` (1 to 1440) and ring again later |
| `outcome` | optional short tag stored with the answer (for example `completed`) |
| `result_message` | optional text shown on the phone after the tap |
| `cancels_group` | `true` cancels every other open job in the same `cancel_group` |
| `result_state` | optional `resolved` or `cancelled` for the job after this action |

The action `type` is validated against that list: nothing else can be sent to the phone.

## Admin endpoints (`X-Admin-Key`)
| call | purpose |
|---|---|
| `POST /admin/pairing-codes` | one-time 8-character code, valid 15 minutes |
| `POST /admin/jobs` | create a job (`201`) |
| `GET /admin/jobs?routine_id=&state=&cancel_group=` | list |
| `GET /admin/jobs/{id}` | one job |
| `PATCH /admin/jobs/{id}` | change title, message, `fire_at`, `expires_at`, `priority`, `delivery`, `sound_profile`, `actions`, `cancel_group` (only while the job is open) |
| `POST /admin/jobs/{id}/cancel` | cancel one job |
| `POST /admin/groups/{group}/cancel` | cancel all open jobs in a group |
| `POST /admin/groups/{group}/resolve` | body `{"outcome": "completed", "note": "..."}`: cancels the group's open jobs and records the answer as if given by the sender (for answers you got some other way) |
| `GET /admin/responses?since_seq=N` | answers in order; each has `seq`, `job_id`, `action_id`, `outcome`, `responded_at`, `device_id`, `applied` |
| `GET /admin/devices`, `POST /admin/devices/{id}/revoke` | list / cut off a phone |

## Phone endpoints (`Bearer` token)
| call | purpose |
|---|---|
| `POST /v1/devices/register` | body `{"pairing_code", "name"}` returns `{"device_id", "token"}`. Five wrong codes per minute from one address are rate-limited |
| `GET /v1/sync?since=<revision>` | jobs changed after that revision (including cancelled/expired ones) |
| `POST /v1/responses` | batch of `{"response_id", "job_id", "action_id", "responded_at"}`. `response_id` is the idempotency key: repeats return `duplicate` and change nothing |
| `POST /v1/ack` | informational receipts |
| `GET /v1/status` | device and server info |

`/health` needs no credentials.

## Behaviour worth knowing
- A response to a job that is already resolved/cancelled is stored (so nothing is lost) but not applied.
- Answers that name an unknown job or action are stored with `applied: 0`.
- Jobs expire server-side lazily, on the next sync or admin list.
- Revisions only increase; the phone keeps the highest one it applied as its cursor.


## Pairing codes: lifetime

`POST /admin/pairing-codes` creates a single-use code. By default it is valid for 15 minutes; add `?minutes=N` (1 to 120) for longer. The installer and `pair.sh` ask for 60.

## Dashboard (optional)

Served at `/dashboard` only when `DASHBOARD_PASSWORD_HASH` is set (run `dashboard.sh`). Sign in with `POST /dashboard/login` `{ "password": "…" }`, which sets a signed `HttpOnly`, `SameSite=Strict` cookie. Then:

| call | purpose |
|---|---|
| `GET /dashboard/api/state` | reminders (last 14 days to next 8), phone answers, paired phones (no keys or tokens) |
| `PATCH /dashboard/api/reminders/{job_id}` | change `title`, `fire_at` (or `time` as `HH:MM` in the reminder's own timezone), `style` (`alarm`, `alarm-silent`, `banner`, `banner-silent`) or `enabled` |
| `POST /dashboard/api/reminders/{job_id}/done` | mark the reminder's whole group done (records a "completed" answer) |

Switching a reminder off cancels it; switching it back on is allowed until its time has passed.
