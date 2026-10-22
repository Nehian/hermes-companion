# Security

## Model
- The **admin key** (`ADMIN_KEY`) controls everything on the server. Keep it secret; rotate it by changing `docker/.env` and restarting.
- Each **phone** has its own random token, stored only as a hash on the server. Revoke a phone any time with `POST /admin/devices/{id}/revoke`.
- Pairing codes are single-use, expire after 15 minutes, and wrong guesses are rate-limited.
- Buttons can only carry a fixed set of action types; the server rejects anything else, so a sender cannot make the phone run code.
- `ALLOWED_NETS` limits which networks the server answers at all. Release app builds accept only HTTPS unless you explicitly allow a plain-HTTP address (for example a Tailscale address).
- The phone stores its token and reminders in the app's private storage.

## Reporting a problem
Please report vulnerabilities privately to the maintainers (use the repository's private security advisory feature) instead of opening a public issue. Include what you found and how to reproduce it.
