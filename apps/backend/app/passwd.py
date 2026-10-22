"""Dashboard password hashing. Run as `python -m app.passwd` with the password on stdin; prints the stored form."""
import hashlib
import hmac
import os
import sys

ITERATIONS = 200_000
MIN_LENGTH = 8


def hash_password(password: str) -> str:
    salt = os.urandom(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, ITERATIONS)
    return f"pbkdf2:{ITERATIONS}:{salt.hex()}:{digest.hex()}"


def verify(password: str, stored: str) -> bool:
    try:
        scheme, iterations, salt, digest = stored.split(":")
        if scheme != "pbkdf2":
            return False
        candidate = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), bytes.fromhex(salt), int(iterations))
        return hmac.compare_digest(candidate, bytes.fromhex(digest))
    except ValueError:
        return False


if __name__ == "__main__":
    pw = sys.stdin.read().rstrip("\n")
    if len(pw) < MIN_LENGTH:
        sys.exit(f"The password must be at least {MIN_LENGTH} characters.")
    print(hash_password(pw))
