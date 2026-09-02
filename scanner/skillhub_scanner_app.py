"""Runtime safeguards around the upstream Cisco Skill Scanner ASGI application."""

import asyncio
import os

from fastapi import Request
from fastapi.responses import JSONResponse
from skill_scanner.api.api_server import app


_MAX_CONCURRENT_SCANS = max(1, int(os.getenv("SKILLHUB_SCANNER_MAX_CONCURRENT_SCANS", "1")))
_active_scans = 0
_active_scans_guard = asyncio.Lock()
_SCAN_PATHS = {"/scan", "/scan-upload"}


@app.middleware("http")
async def limit_concurrent_scans(request: Request, call_next):
    """Reject excess scan work so timed-out client retries cannot multiply memory use."""
    global _active_scans
    if request.method != "POST" or request.url.path not in _SCAN_PATHS:
        return await call_next(request)

    async with _active_scans_guard:
        if _active_scans >= _MAX_CONCURRENT_SCANS:
            return JSONResponse(
                status_code=503,
                content={"detail": "Scanner is busy; retry later"},
                headers={"Retry-After": "30"},
            )
        _active_scans += 1

    scan_task = asyncio.create_task(call_next(request))
    try:
        # Keep the capacity slot until upstream work really ends, even if the HTTP client
        # disconnects while the scanner's worker thread is still running.
        return await asyncio.shield(scan_task)
    except asyncio.CancelledError:
        await scan_task
        raise
    finally:
        async with _active_scans_guard:
            _active_scans -= 1
