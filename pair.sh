#!/bin/sh
# Prints a fresh pairing code for a new phone. Run it on the server's computer.
set -eu
DIR="$(cd "$(dirname "$0")" && pwd)"
[ -f "$DIR/docker/.env" ] || { echo "Stopped: no server found here. Run install.sh first." >&2; exit 1; }
KEY="$(sed -n 's/^ADMIN_KEY=//p' "$DIR/docker/.env")"
IP="$(tailscale ip -4 2>/dev/null | head -n 1 || true)"
CODE="$(curl -fsS -X POST -H "X-Admin-Key: $KEY" "http://127.0.0.1:8020/admin/pairing-codes?minutes=60" \
  | sed -n 's/.*"pairing_code":"\([A-Z0-9]*\)".*/\1/p')" || true
[ -n "$CODE" ] || { echo "Stopped: the server is not answering. Run 'docker ps' to check it is running." >&2; exit 1; }
printf '\n  Server address:  %s\n  Pairing code:    %s     (valid for 60 minutes, works once)\n\n' "${IP:-<Tailscale address>}" "$CODE"
