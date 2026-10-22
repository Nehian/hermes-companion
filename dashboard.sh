#!/bin/sh
# Hermes Companion: turns the dashboard on and asks you to choose its password.
# Run it on the computer that runs the server. Run it again any time to change the password.
set -eu

DIR="$(cd "$(dirname "$0")" && pwd)"
ENV="$DIR/docker/.env"
stop() { printf '\nStopped: %s\n' "$1" >&2; exit 1; }

[ -f "$ENV" ] || stop "No server found here. Run install.sh first."
[ -t 0 ] || stop "Run this in a terminal, so it can ask for your password."
command -v docker >/dev/null 2>&1 || stop "Docker is not installed."

ask() { printf '%s' "$1"; stty -echo; read -r "$2"; stty echo; printf '\n'; }
ask "Choose a dashboard password (at least 8 characters): " P1
ask "Type it again: " P2
[ "$P1" = "$P2" ] || stop "The two passwords were not the same. Nothing was changed. Run dashboard.sh again."
[ "${#P1}" -ge 8 ] || stop "The password must be at least 8 characters. Nothing was changed."

cd "$DIR/docker"
export HC_UID="$(id -u)" HC_GID="$(id -g)"
echo "Preparing (about a minute the first time) ..."
docker compose build -q api >/dev/null 2>&1 || stop "Could not build the server. Run 'docker compose build api' in $DIR/docker to see why."
HASH="$(printf '%s' "$P1" | docker compose run --rm -T --no-deps api python -m app.passwd)" || stop "Could not prepare the password."
unset P1 P2
case "$HASH" in pbkdf2:*) ;; *) stop "Could not prepare the password." ;; esac

umask 077
grep -v '^DASHBOARD_PASSWORD_HASH=' "$ENV" > "$ENV.new" || true
printf 'DASHBOARD_PASSWORD_HASH=%s\n' "$HASH" >> "$ENV.new"
mv "$ENV.new" "$ENV"

docker compose up -d >/dev/null 2>&1 || stop "The server did not restart. Run 'docker compose up -d' in $DIR/docker to see why."
n=0
until curl -fsS "http://127.0.0.1:8020/health" >/dev/null 2>&1; do
  n=$((n + 1)); [ "$n" -le 30 ] || stop "The server restarted but is not answering. Run 'docker logs hermes-companion-api'."
  sleep 2
done
IP="$(tailscale ip -4 2>/dev/null | head -n 1 || true)"
printf '\nDashboard is on.\n\n  Open:  http://%s:8020/dashboard\n\nSign in with the password you just chose. It is never stored, only a scrambled copy.\n' "${IP:-<your Tailscale address>}"
