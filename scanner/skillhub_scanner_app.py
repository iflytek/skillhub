"""Runtime safeguards around the upstream Cisco Skill Scanner ASGI application."""

import asyncio
import logging
import os
import shutil
import tempfile
from pathlib import Path
from typing import NoReturn

from fastapi import Request
from fastapi.responses import JSONResponse
from skill_scanner.api.api import app


_MAX_CONCURRENT_SCANS = max(1, int(os.getenv("SKILLHUB_SCANNER_MAX_CONCURRENT_SCANS", "1")))
_HARD_TIMEOUT_SECONDS = max(1, int(os.getenv("SKILLHUB_SCANNER_HARD_TIMEOUT_SECONDS", "930")))
_active_scans = 0
_active_scans_guard = asyncio.Lock()
_SCAN_PATHS = {"/scan", "/scan-upload"}
_log = logging.getLogger(__name__)


def _cleanup_stale_scan_directories(temp_root: Path | None = None) -> None:
    """Remove incomplete upstream extraction directories left by a process restart."""
    root = temp_root or Path(tempfile.gettempdir())
    for candidate in root.glob("skill_scanner_*"):
        if not candidate.is_dir():
            continue
        try:
            shutil.rmtree(candidate)
        except OSError as error:
            _log.warning("Could not remove stale scanner directory %s: %s", candidate, error)


def _restart_after_hard_timeout(request_path: str) -> NoReturn:
    """Terminate the single-scan worker so the container runtime can recover it."""
    _log.critical(
        "Security scan exceeded the %s second hard timeout: path=%s; restarting scanner",
        _HARD_TIMEOUT_SECONDS,
        request_path,
    )
    logging.shutdown()
    os._exit(124)


async def _await_scan_until(scan_task: asyncio.Task, deadline: float, request_path: str):
    remaining = deadline - asyncio.get_running_loop().time()
    if remaining <= 0:
        _restart_after_hard_timeout(request_path)
    try:
        return await asyncio.wait_for(asyncio.shield(scan_task), timeout=remaining)
    except asyncio.TimeoutError:
        _restart_after_hard_timeout(request_path)


app.router.add_event_handler("startup", _cleanup_stale_scan_directories)


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
    deadline = asyncio.get_running_loop().time() + _HARD_TIMEOUT_SECONDS
    try:
        # Keep the capacity slot until upstream work really ends, even if the HTTP client
        # disconnects while the scanner's worker thread is still running.
        return await _await_scan_until(scan_task, deadline, request.url.path)
    except asyncio.CancelledError:
        await _await_scan_until(scan_task, deadline, request.url.path)
        raise
    finally:
        async with _active_scans_guard:
            _active_scans -= 1
