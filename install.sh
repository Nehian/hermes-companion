#!/bin/sh
# Hermes Companion: server installer. Run it on the computer that hosts your assistant.
# Needs Docker and Tailscale, both installed and signed in. Safe to run again: it keeps your existing key and data.
set -eu

REPO="Nehian/hermes-companion"
DIR="${HC_DIR:-$HOME/hermes-companion}"
PORT=8020

stop() { printf '\nStopped: %s\n' "$1" >&2; exit 1; }

command -v docker >/dev/null 2>&1 || stop "Docker is not installed. Install it from https://docs.docker.com/get-docker/ and run this again."
docker info >/dev/null 2>&1 || stop "Docker is installed but not running (or your user may not use it). Start Docker and run this again."
docker compose version >/dev/null 2>&1 || stop "Docker Compose is missing. Install the 'docker compose' plugin and run this again."
command -v tailscale >/dev/null 2>&1 || stop "Tailscale is not installed on this computer. Install it from https://tailscale.com/download, sign in, and run this again."
IP="$(tailscale ip -4 2>/dev/null | head -n 1 || true)"
[ -n "$IP" ] || stop "Tailscale is installed but not signed in. Run 'sudo tailscale up', sign in, and run this again."
command -v curl >/dev/null 2>&1 || stop "curl is missing. Install curl and run this again."
command -v openssl >/dev/null 2>&1 || stop "openssl is missing. Install openssl and run this again."

# Use the folder this script sits in if it is a copy of the repo; otherwise download it.
HERE="$(cd "$(dirname "$0")" 2>/dev/null && pwd || true)"
if [ -f "$HERE/docker/docker-compose.yml" ]; then
  DIR="$HERE"
elif [ ! -f "$DIR/docker/docker-compose.yml" ]; then
  echo "Downloading Hermes Companion to $DIR ..."
  mkdir -p "$DIR"
  curl -fsSL "https://github.com/$REPO/archive/refs/heads/main.tar.gz" | tar -xz -C "$DIR" --strip-components=1 \
    || stop "Could not download from GitHub. Check your internet connection and run this again."
fi

cd "$DIR/docker"
if [ ! -f .env ]; then
  umask 077
  printf 'ADMIN_KEY=%s\nBIND_ADDRESS=0.0.0.0\n' "$(openssl rand -hex 32)" > .env
fi
if ! grep -q '^HC_SUBNET=' .env; then
  taken="$(docker network ls -q | xargs docker network inspect --format '{{range .IPAM.Config}}{{.Subnet}} {{end}}' 2>/dev/null | tr '\n' ' ')"
  SUBNET=""
  for p in 172.16 172.31 172.30 172.29 172.28 172.27 172.26 172.25 172.24 172.23 172.22 172.21 172.20 172.19 172.18 172.17; do
    case " $taken" in *" $p."*) ;; *) SUBNET="$p.250.0/24"; break ;; esac
  done
  [ -n "$SUBNET" ] || stop "Could not find a free private network range for Docker."
  printf 'HC_SUBNET=%s\n' "$SUBNET" >> .env
fi
mkdir -p ../data
export HC_UID="$(id -u)" HC_GID="$(id -g)"

echo "Building and starting the server (about a minute the first time) ..."
docker compose up -d --build >/dev/null 2>&1 || { docker compose up -d --build 2>&1 | tail -n 15 >&2; stop "The server did not start. The last lines above say why."; }

n=0
until curl -fsS "http://127.0.0.1:$PORT/health" >/dev/null 2>&1; do
  n=$((n + 1)); [ "$n" -le 40 ] || stop "The server started but is not answering. Run 'docker logs hermes-companion-api' to see why."
  sleep 2
done

mkdir -p "$HOME/.local/bin" 2>/dev/null && ln -sf "$DIR/hc" "$HOME/.local/bin/hc" 2>/dev/null || true
KEY="$(sed -n 's/^ADMIN_KEY=//p' .env)"
CODE="$(curl -fsS -X POST -H "X-Admin-Key: $KEY" "http://127.0.0.1:$PORT/admin/pairing-codes?minutes=60" \
  | sed -n 's/.*"pairing_code":"\([A-Z0-9]*\)".*/\1/p')"
[ -n "$CODE" ] || stop "The server is running but did not give a pairing code."

cat <<MSG

Server ready.

  Server address:  $IP
  Pairing code:    $CODE     (valid for 60 minutes, works once)

Next: install the Hermes Companion app on your phone, open Settings,
type the server address and the pairing code.

Need a new code later?   $DIR/pair.sh
Command for you and your assistant:   $DIR/hc
Your secret admin key is stored in $DIR/docker/.env (keep it private).
MSG
