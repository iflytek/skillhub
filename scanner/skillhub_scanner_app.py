"""Runtime safeguards around the upstream Cisco Skill Scanner ASGI application."""

import asyncio
import logging
import os
import shutil
import tempfile
from functools import wraps
from importlib import import_module
from pathlib import Path
from typing import NoReturn


_RUNTIME_TEMP_ROOT = Path(
    os.getenv("SKILLHUB_SCANNER_RUNTIME_TEMP_ROOT", "/tmp/skillhub-scanner-runtime")
)


def _prepare_runtime_temp_root() -> None:
    """Recreate the scanner-owned temp root before upstream allocates request directories."""
    if _RUNTIME_TEMP_ROOT.name != "skillhub-scanner-runtime" or _RUNTIME_TEMP_ROOT.is_symlink():
        raise RuntimeError("Scanner runtime temp root must be a non-symlink skillhub-scanner-runtime directory")
    if _RUNTIME_TEMP_ROOT.exists():
        if not _RUNTIME_TEMP_ROOT.is_dir():
            raise RuntimeError("Scanner runtime temp root must be a directory")
        shutil.rmtree(_RUNTIME_TEMP_ROOT)
    _RUNTIME_TEMP_ROOT.mkdir(parents=True, mode=0o700)
    _RUNTIME_TEMP_ROOT.chmod(0o700)
    tempfile.tempdir = str(_RUNTIME_TEMP_ROOT)


_prepare_runtime_temp_root()

from fastapi import Request
from fastapi.responses import JSONResponse
from skill_scanner.api.api import app
from skill_scanner.cli import cli as _upstream_cli


_upstream_router = import_module("skill_scanner.api.router")
_MAX_CONCURRENT_SCANS = max(1, int(os.getenv("SKILLHUB_SCANNER_MAX_CONCURRENT_SCANS", "1")))
_HARD_TIMEOUT_SECONDS = max(1, int(os.getenv("SKILLHUB_SCANNER_HARD_TIMEOUT_SECONDS", "930")))
_upstream_router.MAX_UPLOAD_SIZE_BYTES = max(
    1, int(os.getenv("SKILLHUB_SCANNER_MAX_UPLOAD_SIZE_BYTES", "110100480"))
)
_active_scans = 0
_active_scans_guard = asyncio.Lock()
_SCAN_PATHS = {"/scan", "/scan-upload"}
_log = logging.getLogger(__name__)


def _redact_finding_text(message: str) -> str:
    """Redact credentials without applying CLI-only truncation or control escaping."""
    redacted = _upstream_cli._STATUS_PRIVATE_KEY_RE.sub("<redacted>", message)
    for pattern in (
        _upstream_cli._STATUS_URL_USERINFO_RE,
        _upstream_cli._STATUS_URL_TOKEN_USERINFO_RE,
        _upstream_cli._STATUS_QUERY_SECRET_RE,
        _upstream_cli._STATUS_BEARER_SECRET_RE,
        _upstream_cli._STATUS_LABELED_SECRET_RE,
    ):
        redacted = pattern.sub(_upstream_cli._replace_status_secret, redacted)
    redacted = _upstream_cli._STATUS_PROVIDER_SECRET_RE.sub("<redacted>", redacted)
    return _upstream_cli._STATUS_JWT_RE.sub("<redacted>", redacted)


def _redact_supported_tokens(value):
    if isinstance(value, str):
        return _redact_finding_text(value)
    if isinstance(value, list):
        return [_redact_supported_tokens(item) for item in value]
    if isinstance(value, dict):
        return {key: _redact_supported_tokens(item) for key, item in value.items()}
    return value


def _install_scan_response_redaction() -> None:
    """Redact supported token forms before FastAPI serializes scan findings."""
    for route in _upstream_router.router.routes:
        if getattr(route, "path", None) not in _SCAN_PATHS or "POST" not in getattr(route, "methods", set()):
            continue
        endpoint = route.endpoint

        @wraps(endpoint)
        async def redacting_endpoint(*args, __endpoint=endpoint, **kwargs):
            response = await __endpoint(*args, **kwargs)
            findings = getattr(response, "findings", None)
            if isinstance(findings, list):
                response.findings = _redact_supported_tokens(findings)
            return response

        route.endpoint = redacting_endpoint
        route.dependant.call = redacting_endpoint


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


_install_scan_response_redaction()


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
