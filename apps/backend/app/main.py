import ipaddress
import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from . import api_admin, api_device, dashboard
from .db import init_db
from .security import now_iso

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")


@asynccontextmanager
async def lifespan(_: FastAPI):
    init_db()
    yield


app = FastAPI(title="Hermes Companion API", version="0.1.0", lifespan=lifespan)
app.include_router(api_admin.router)
app.include_router(api_device.router)
app.include_router(dashboard.router)


@app.middleware("http")
async def network_allowlist(request: Request, call_next):
    """If ALLOWED_NETS is set (comma-separated CIDRs), refuse every client outside it before any other logic runs."""
    nets = [ipaddress.ip_network(n.strip()) for n in os.environ.get("ALLOWED_NETS", "").split(",") if n.strip()]
    if nets:
        try:
            client = ipaddress.ip_address(request.client.host if request.client else "")
        except ValueError:
            client = None
        if client is None or not any(client in n for n in nets):
            return JSONResponse({"detail": "forbidden"}, status_code=403)
    return await call_next(request)


@app.get("/health")
def health():
    return {"status": "ok", "time": now_iso()}
