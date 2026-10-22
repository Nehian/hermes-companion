#!/bin/sh
# Runs the backend test suite in a throwaway Python 3.12 container (no host Python packages needed).
cd "$(dirname "$0")/../apps/backend" || exit 1
docker run --rm -u "$(id -u):$(id -g)" -e PYTHONDONTWRITEBYTECODE=1 -e PIP_CACHE_DIR=/tmp/pip -e HOME=/tmp \
  -v "$PWD":/work -w /work python:3.12-slim \
  sh -c "pip install -q -r requirements-dev.txt --target /tmp/deps 2>&1 | grep -v notice; PYTHONPATH=/tmp/deps python -m pytest -q -p no:cacheprovider"
