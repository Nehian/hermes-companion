# Hermes Companion

**Schedule alarms and notifications on your Android phone from anything that can make an HTTP request, so you never lose contact with your assistant ("Hermes").**

Normal notifications get missed: the phone is on silent, battery saving delays them, the app is asleep. Hermes Companion puts the schedule *on the phone*. Your assistant, script or service sends reminders ahead of time; the phone rings them itself with a real alarm, shows buttons you defined, and reports what you pressed. If the server or the internet is down at that moment, nothing is lost.

"Hermes" is just the name of the sender in this project. It can be an AI assistant, a cron job, a home-automation hub, or you with `curl`.

## What it does
- **Rings like an alarm**: exact-time alarm on the alarm volume (silent mode does not mute it), wakes the screen and shows over the lock screen. When you are using the phone it appears as a banner instead.
- **The sender decides everything**: title, message, time, buttons, snooze, follow-up messages, and which other reminders a button should cancel. The app has no hard-coded routines.
- **Works offline**: reminders already on the phone ring with no connection; your answers are stored and upload later by themselves.
- **Never rings what you already handled**: a short server check just before each alarm drops reminders that were cancelled meanwhile (for example you confirmed by chat).
- **Survives restarts**: alarms are restored after reboot, time/timezone change and app update.
- **Safe by design**: buttons map to a fixed set of actions (no remote code), each phone has its own revocable credential, and the server only talks to networks you allow.

```
 sender (assistant / script)           your server                      your phone
   POST /admin/jobs  ───────────────►  FastAPI + SQLite  ◄── sync ───►  Hermes Companion app
   GET  /admin/responses ◄──────────   (Docker)                         exact alarms, answers
```

## Requirements
- Android 12 or newer phone.
- A machine for the server with Docker (a Raspberry Pi or home server is plenty).
- To build the app: JDK 17+ and the Android SDK (platform 36, build-tools 35/36). Gradle is downloaded by the wrapper.
- A private way for the phone to reach the server (Tailscale, WireGuard, a reverse proxy with HTTPS, or the same Wi-Fi).

## Quick start

### 1. Run the server
```sh
cd docker
cp .env.example .env
sed -i "s/^ADMIN_KEY=.*/ADMIN_KEY=$(openssl rand -hex 32)/" .env   # your admin secret; keep it private
mkdir -p ../data                                                  # must be writable by uid 1000 (the container user)
docker compose up -d --build
curl http://127.0.0.1:8020/health
```
By default it listens on localhost only. To let your phone reach it, set `BIND_ADDRESS=0.0.0.0` in `.env` and set `ALLOWED_NETS` to the networks that may connect (the default allows localhost, Docker, and Tailscale `100.64.0.0/10`). Everything also requires the admin key or a paired phone's token.

### 2. Build the app
```sh
cd apps/frontend
# a release key of your own (keep it safe: updates must be signed with the same key)
mkdir -p signing && keytool -genkeypair -keystore signing/hermes-release.jks -alias hermes -keyalg RSA -keysize 2048 -validity 10000
printf 'storeFile=signing/hermes-release.jks\nstorePassword=YOUR_PASSWORD\nkeyAlias=hermes\nkeyPassword=YOUR_PASSWORD\n' > signing/keystore.properties

ANDROID_HOME=/path/to/android-sdk ./gradlew assembleRelease -Phermes.defaultServerUrl=https://your-server.example
# output: app/build/outputs/apk/release/app-release.apk
```
Release builds only allow HTTPS. If your server is on a private encrypted network without a certificate (for example Tailscale), allow plain HTTP for that one address in `app/src/release/res/xml/network_security_config.xml` (an example is in the file). For quick local testing, `./gradlew assembleDebug` talks to `http://10.0.2.2:8020` (the host, as seen from the Android emulator).

### 3. Install and pair
Install the APK on the phone. Create a one-time pairing code (valid 15 minutes) and type it into the app under **Settings → Pairing code**:
```sh
curl -s -X POST -H "X-Admin-Key: $ADMIN_KEY" http://127.0.0.1:8020/admin/pairing-codes
```
In **Settings → Permissions** tap **Allow** on every row that says "Needs attention", then try **Ring a test alarm**. Keep the phone's alarm volume up. On some phones (for example Samsung) also exclude the app from battery optimisation.

### 4. Send your first reminder
```sh
curl -s -X POST http://127.0.0.1:8020/admin/jobs -H "X-Admin-Key: $ADMIN_KEY" -H 'Content-Type: application/json' -d '{
  "title": "Stand up",
  "message": "Time for a short walk.",
  "fire_at": "'"$(date -u -d '+3 minutes' +%Y-%m-%dT%H:%M:%SZ)"'",
  "actions": [
    {"action_id": "done",   "label": "Done",        "type": "complete"},
    {"action_id": "snooze", "label": "Snooze 5 min", "type": "snooze", "duration_min": 5}
  ]
}'
```
Open the app (or wait up to ~15 minutes) so the phone syncs, lock it, and wait. Read what you answered with `GET /admin/responses`. The full reference is in [docs/API.md](docs/API.md).

## Good to know
- Do not **Force stop** the app in Android settings: Android clears its alarms until the app is opened again.
- The phone syncs when the app opens, about every 15 minutes in the background, and just before each alarm, so send schedules ahead of time (hours, not seconds).
- It uses Android's exact-alarm permission, which Google Play restricts to alarm and calendar apps. Expect to sideload or build it yourself.
- It is built for one person: one admin key, one server per user, as many paired phones as you like.
- Not tested on real hardware by the author at the time of writing beyond the Android 16 emulator; reports from real phones are very welcome.

## Develop and test
```sh
./scripts/test-backend.sh          # server tests in a throwaway Python container
cd apps/frontend && ./gradlew assembleDebug
```

## License
Apache License 2.0, see [LICENSE](LICENSE) and [NOTICE](NOTICE). Contributions are welcome, see [CONTRIBUTING.md](CONTRIBUTING.md). Security issues: see [SECURITY.md](SECURITY.md).
