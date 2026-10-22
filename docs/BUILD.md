# Build from source and run by hand

Most people should use the one-command install in the [README](../README.md). This page is for developers who want to build the app themselves or run the server without `install.sh`.

## Requirements
- Android 12 or newer phone.
- A machine for the server with Docker.
- To build the app: JDK 17+ and the Android SDK (platform 36, build-tools 35/36). Gradle is downloaded by the wrapper.
- A private way for the phone to reach the server (Tailscale, WireGuard, a reverse proxy with HTTPS, or the same Wi-Fi).

## 1. Run the server
```sh
cd docker
cp .env.example .env
sed -i "s/^ADMIN_KEY=.*/ADMIN_KEY=$(openssl rand -hex 32)/" .env   # your admin secret; keep it private
mkdir -p ../data                                                  # must be writable by uid 1000 (the container user)
docker compose up -d --build
curl http://127.0.0.1:8020/health
```
By default it listens on localhost only. To let your phone reach it, set `BIND_ADDRESS=0.0.0.0` in `.env` and set `ALLOWED_NETS` to the networks that may connect (the default allows localhost, Docker, and Tailscale `100.64.0.0/10`). Everything also requires the admin key or a paired phone's token.

## 2. Build the app
```sh
cd apps/frontend
# a release key of your own (keep it safe: updates must be signed with the same key)
mkdir -p signing && keytool -genkeypair -keystore signing/hermes-release.jks -alias hermes -keyalg RSA -keysize 2048 -validity 10000
printf 'storeFile=signing/hermes-release.jks\nstorePassword=YOUR_PASSWORD\nkeyAlias=hermes\nkeyPassword=YOUR_PASSWORD\n' > signing/keystore.properties

ANDROID_HOME=/path/to/android-sdk ./gradlew assembleRelease -Phermes.defaultServerUrl=https://your-server.example
# output: app/build/outputs/apk/release/app-release.apk
```
Release builds only allow HTTPS. If your server is on a private encrypted network without a certificate (for example Tailscale), allow plain HTTP for that one address in `app/src/release/res/xml/network_security_config.xml` (an example is in the file). For quick local testing, `./gradlew assembleDebug` talks to `http://10.0.2.2:8020` (the host, as seen from the Android emulator).

## 3. Install and pair
Install the APK on the phone. Create a one-time pairing code (valid 15 minutes) and type it into the app under **Settings → Pairing code**:
```sh
curl -s -X POST -H "X-Admin-Key: $ADMIN_KEY" http://127.0.0.1:8020/admin/pairing-codes
```
In **Settings → Permissions** tap **Allow** on every row that says "Needs attention", then try **Ring a test alarm**. Keep the phone's alarm volume up. On some phones (for example Samsung) also exclude the app from battery optimisation.

## 4. Send your first reminder
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


## Notes on addresses
The app accepts a bare address such as `100.64.0.5` (it adds `http://` and port `8020`) or a full address. Plain `http://` is allowed only to Tailscale, home-network and loopback addresses; any other server must use `https://`.

To build your own app with a default server address filled in: `./gradlew assembleRelease -Phermes.defaultServerUrl=https://your-server.example`.

## Tests
```sh
./scripts/test-backend.sh          # server tests in a throwaway Python container
cd apps/frontend && ./gradlew assembleDebug
```
