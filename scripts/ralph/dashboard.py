#!/usr/bin/env python3
"""
Ralph Dashboard - 实时监控面板
启动一个本地 HTTP 服务，服务 dashboard.html 并提供 /api/state 接口。
"""

import json
import threading
import webbrowser
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

SCRIPT_DIR = Path(__file__).parent.resolve()
PRD_FILE = SCRIPT_DIR / "prd.json"
PROGRESS_FILE = SCRIPT_DIR / "progress.txt"
HTML_FILE = SCRIPT_DIR / "dashboard.html"
PIXEL_HTML_FILE = SCRIPT_DIR / "dashboard-p.html"
RALPH_LOG_FILE = Path.home() / "logs" / "skillhub" / "ralph.log"

_state: dict = {
    "iteration": 0,
    "max_iterations": 50,
    "phase": "idle",       # idle | developing | validating | done | error
    "current_story": None,
    "started_at": None,
}
_state_lock = threading.Lock()


def set_state(
    iteration: int | None = None,
    phase: str | None = None,
    current_story: str | None = None,
) -> None:
    with _state_lock:
        if iteration is not None:
            _state["iteration"] = iteration
        if phase is not None:
            _state["phase"] = phase
        if current_story is not None:
            _state["current_story"] = current_story


def _build_api_response() -> dict:
    with _state_lock:
        s = dict(_state)

    elapsed = int(time.time() - s["started_at"]) if s["started_at"] else 0

    project = ""
    branch_name = ""
    stories = []
    try:
        prd = json.loads(PRD_FILE.read_text(encoding="utf-8"))
        project = prd.get("project", "")
        branch_name = prd.get("branchName", "")
        stories = prd.get("userStories", [])
    except Exception:
        pass

    logs = ""
    try:
        if PROGRESS_FILE.exists():
            logs = PROGRESS_FILE.read_text(encoding="utf-8")
    except Exception:
        pass

    return {
        "runtime": {
            "iteration": s["iteration"],
            "max_iterations": s["max_iterations"],
            "phase": s["phase"],
            "current_story": s["current_story"],
            "elapsed": elapsed,
        },
        "project": project,
        "branchName": branch_name,
        "stories": stories,
        "logs": logs,
    }


def _tail_lines(path: Path, limit: int = 100) -> str:
    if limit <= 0:
        return ""

    with path.open("rb") as f:
        f.seek(0, 2)
        file_size = f.tell()
        block_size = 4096
        buffer = bytearray()
        line_count = 0
        position = file_size

        while position > 0 and line_count <= limit:
            read_size = min(block_size, position)
            position -= read_size
            f.seek(position)
            chunk = f.read(read_size)
            buffer[:0] = chunk
            line_count = buffer.count(b"\n")

    text = buffer.decode("utf-8", errors="replace")
    lines = text.splitlines()
    return "\n".join(lines[-limit:])


def _build_ralph_log_response(limit: int = 100) -> dict:
    limit = max(1, min(limit, 500))

    if not RALPH_LOG_FILE.exists():
        return {
            "path": str(RALPH_LOG_FILE),
            "exists": False,
            "updatedAt": None,
            "logs": "",
            "lineLimit": limit,
        }

    return {
        "path": str(RALPH_LOG_FILE),
        "exists": True,
        "updatedAt": int(RALPH_LOG_FILE.stat().st_mtime),
        "logs": _tail_lines(RALPH_LOG_FILE, limit),
        "lineLimit": limit,
    }


class _Handler(BaseHTTPRequestHandler):
    def _send_bytes(
        self,
        body: bytes,
        *,
        status: int = 200,
        content_type: str | None = None,
        extra_headers: dict[str, str] | None = None,
    ) -> None:
        try:
            self.send_response(status)
            if content_type:
                self.send_header("Content-Type", content_type)
            if extra_headers:
                for key, value in extra_headers.items():
                    self.send_header(key, value)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            # Remote clients may disconnect between headers and body write.
            pass

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        path = parsed.path

        if path == "/api/state":
            body = json.dumps(_build_api_response(), ensure_ascii=False).encode("utf-8")
            self._send_bytes(
                body,
                content_type="application/json; charset=utf-8",
                extra_headers={"Access-Control-Allow-Origin": "*"},
            )

        elif path == "/api/ralph-log":
            query = parse_qs(parsed.query)
            raw_limit = query.get("lines", ["100"])[0]
            try:
                line_limit = int(raw_limit)
            except ValueError:
                line_limit = 100

            body = json.dumps(_build_ralph_log_response(line_limit), ensure_ascii=False).encode("utf-8")
            self._send_bytes(
                body,
                content_type="application/json; charset=utf-8",
                extra_headers={"Access-Control-Allow-Origin": "*"},
            )

        elif path in ("/", "/index.html"):
            try:
                html = HTML_FILE.read_bytes()
                self._send_bytes(html, content_type="text/html; charset=utf-8")
            except Exception as e:
                msg = str(e).encode()
                self._send_bytes(msg, status=500)

        elif path in ("/p", "/p.html"):
            try:
                html = PIXEL_HTML_FILE.read_bytes()
                self._send_bytes(html, content_type="text/html; charset=utf-8")
            except Exception as e:
                msg = str(e).encode()
                self._send_bytes(msg, status=500)

        else:
            self._send_bytes(b"", status=404)

    def log_message(self, format: str, *args) -> None:  # suppress access logs
        pass


def start(port: int = 7334, max_iterations: int = 100, open_browser: bool = True, host: str = "127.0.0.1") -> None:
    with _state_lock:
        _state["started_at"] = time.time()
        _state["max_iterations"] = max_iterations

    server = HTTPServer((host, port), _Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()

    url = f"http://{host}:{port}" if host != "0.0.0.0" else f"http://localhost:{port}"
    print(f"🖥️  Dashboard: {url}")

    if open_browser:
        threading.Timer(0.8, lambda: webbrowser.open(url)).start()
