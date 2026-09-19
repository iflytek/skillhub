import asyncio
import importlib.util
import os
import re
import shutil
import sys
import tempfile
import types
import unittest
from pathlib import Path
from unittest.mock import patch


class _FakeRouter:
    def __init__(self):
        self.handlers = []

    def add_event_handler(self, _event, _handler):
        self.handlers.append((_event, _handler))


class _FakeApp:
    def __init__(self):
        self.router = _FakeRouter()
        self.routes = [object()]
        github_canary = "ghp_" + "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8"
        openai_canary = "sk-proj-" + "Z9y8X7w6V5u4T3s2R1q0" * 3
        aws_canary = "AKIA1234567890ABCDEF"
        jwt_canary = "eyJabcde.abcdefgh.ijklmnop"
        labeled_canary = "custom-secret-1234567890"
        private_key_canary = "-----BEGIN PRIVATE KEY-----\nabc123\n-----END PRIVATE KEY-----"
        self.upstream_routes = [
            _FakeRoute(
                "/scan",
                _FakeScanResponse(
                    [
                        {
                            "description": f"YARA match: {github_canary}",
                            "metadata": {
                                "openai": openai_canary,
                                "aws": aws_canary,
                                "authorization": f"Bearer {jwt_canary}",
                                "labeled": f"api_key={labeled_canary}",
                                "private_key": private_key_canary,
                            },
                        }
                    ]
                ),
            ),
            _FakeRoute(
                "/scan-upload",
                _FakeScanResponse([{"description": "No credentials", "metadata": {"safe": True}}]),
            ),
        ]

    def middleware(self, _kind):
        return lambda function: function


class _FakeResponse:
    def __init__(self, status_code, content, headers):
        self.status_code = status_code
        self.content = content
        self.headers = headers


class _FakeScanResponse:
    def __init__(self, findings):
        self.findings = findings
        self.status_code = 200
        self.headers = {"X-Contract": "preserved"}


class _FakeRoute:
    def __init__(self, path, response):
        self.path = path
        self.methods = {"POST"}

        async def endpoint():
            return response

        self.endpoint = endpoint
        self.dependant = types.SimpleNamespace(call=endpoint)


class _Request:
    method = "POST"
    url = types.SimpleNamespace(path="/scan-upload")


def _load_module(environment=None):
    fastapi = types.ModuleType("fastapi")
    fastapi.Request = object
    responses = types.ModuleType("fastapi.responses")
    responses.JSONResponse = _FakeResponse
    api = types.ModuleType("skill_scanner.api.api")
    api.app = _FakeApp()
    router = types.ModuleType("skill_scanner.api.router")
    router.MAX_UPLOAD_SIZE_BYTES = -1
    router.router = types.SimpleNamespace(routes=api.app.upstream_routes)
    cli = types.ModuleType("skill_scanner.cli.cli")
    cli._STATUS_PRIVATE_KEY_RE = re.compile(
        r"-----BEGIN PRIVATE KEY-----[\s\S]*?-----END PRIVATE KEY-----"
    )
    cli._STATUS_URL_USERINFO_RE = re.compile(r"(?!)")
    cli._STATUS_URL_TOKEN_USERINFO_RE = re.compile(r"(?!)")
    cli._STATUS_QUERY_SECRET_RE = re.compile(r"(?!)")
    cli._STATUS_BEARER_SECRET_RE = re.compile(
        r"(?i)(?P<prefix>\bBearer\s+)(?P<value>[A-Za-z0-9._-]+)"
    )
    cli._STATUS_LABELED_SECRET_RE = re.compile(
        r"(?i)(?P<prefix>\bapi_key=)(?P<value>[^\s]+)"
    )
    cli._STATUS_PROVIDER_SECRET_RE = re.compile(
        r"\b(?:AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9]{20,255}|"
        r"github_pat_[A-Za-z0-9_]{20,255}|sk-(?:proj-)?[A-Za-z0-9_-]{20,255})\b"
    )
    cli._STATUS_JWT_RE = re.compile(r"\beyJ[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]{5,}\b")

    def replace_status_secret(match):
        return f"{match.group('prefix')}<redacted>{match.groupdict().get('suffix', '')}"

    cli._replace_status_secret = replace_status_secret
    stubs = {
        "fastapi": fastapi,
        "fastapi.responses": responses,
        "skill_scanner": types.ModuleType("skill_scanner"),
        "skill_scanner.api": types.ModuleType("skill_scanner.api"),
        "skill_scanner.api.api": api,
        "skill_scanner.api.router": router,
        "skill_scanner.cli": types.ModuleType("skill_scanner.cli"),
        "skill_scanner.cli.cli": cli,
    }
    test_temp_parent = Path(tempfile.mkdtemp(prefix="skillhub-scanner-wrapper-test-"))
    runtime_temp_root = test_temp_parent / "skillhub-scanner-runtime"
    runtime_temp_root.mkdir()
    (runtime_temp_root / "stale-upload.zip").write_text("stale", encoding="utf-8")
    (test_temp_parent / "outside.txt").write_text("keep", encoding="utf-8")
    module_environment = {
        "SKILLHUB_SCANNER_RUNTIME_TEMP_ROOT": str(runtime_temp_root),
        **(environment or {}),
    }
    previous_tempdir = tempfile.tempdir
    with patch.dict(os.environ, module_environment, clear=True), patch.dict(sys.modules, stubs):
        module_path = Path(__file__).parents[1] / "skillhub_scanner_app.py"
        spec = importlib.util.spec_from_file_location("skillhub_scanner_app_under_test", module_path)
        module = importlib.util.module_from_spec(spec)
        try:
            spec.loader.exec_module(module)
        finally:
            tempfile.tempdir = previous_tempdir
        module._router_stub = router
        module._test_temp_parent = test_temp_parent
        return module


class SkillHubScannerAppTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.module = _load_module()

    def tearDown(self):
        shutil.rmtree(self.module._test_temp_parent)

    async def test_excess_scan_is_rejected(self):
        self.module._active_scans = 1

        response = await self.module.limit_concurrent_scans(_Request(), lambda _request: None)

        self.assertEqual(503, response.status_code)
        self.assertEqual("30", response.headers["Retry-After"])

    async def test_client_disconnect_keeps_slot_until_scan_finishes(self):
        release = asyncio.Event()

        async def scan(_request):
            await release.wait()
            return "done"

        request_task = asyncio.create_task(self.module.limit_concurrent_scans(_Request(), scan))
        await asyncio.sleep(0)
        request_task.cancel()
        await asyncio.sleep(0)

        self.assertEqual(1, self.module._active_scans)
        response = await self.module.limit_concurrent_scans(_Request(), scan)
        self.assertEqual(503, response.status_code)

        release.set()
        with self.assertRaises(asyncio.CancelledError):
            await request_task
        self.assertEqual(0, self.module._active_scans)

    async def test_hard_timeout_requests_process_restart(self):
        self.module._HARD_TIMEOUT_SECONDS = 0.01

        async def stuck_scan(_request):
            await asyncio.Event().wait()

        with patch.object(
                self.module,
                "_restart_after_hard_timeout",
                side_effect=RuntimeError("restart requested")) as restart:
            with self.assertRaisesRegex(RuntimeError, "restart requested"):
                await self.module.limit_concurrent_scans(_Request(), stuck_scan)

        restart.assert_called_once_with("/scan-upload")
        self.assertEqual(0, self.module._active_scans)

    async def test_default_upload_limit_matches_skillhub_package_limit(self):
        self.assertEqual(110100480, self.module._router_stub.MAX_UPLOAD_SIZE_BYTES)

    async def test_upload_limit_can_be_overridden_by_environment(self):
        module = _load_module({"SKILLHUB_SCANNER_MAX_UPLOAD_SIZE_BYTES": "123456"})
        self.addCleanup(shutil.rmtree, module._test_temp_parent)

        self.assertEqual(123456, module._router_stub.MAX_UPLOAD_SIZE_BYTES)

    async def test_upload_limit_is_at_least_one_byte(self):
        module = _load_module({"SKILLHUB_SCANNER_MAX_UPLOAD_SIZE_BYTES": "0"})
        self.addCleanup(shutil.rmtree, module._test_temp_parent)

        self.assertEqual(1, module._router_stub.MAX_UPLOAD_SIZE_BYTES)

    async def test_scan_findings_redact_credentials_without_changing_response_contract(self):
        route = next(route for route in self.module._router_stub.router.routes if route.path == "/scan")

        response = await route.endpoint()

        self.assertNotIn("ghp_", str(response.findings))
        self.assertNotIn("sk-proj-", str(response.findings))
        self.assertNotIn("AKIA1234567890ABCDEF", str(response.findings))
        self.assertNotIn("eyJabcde.abcdefgh.ijklmnop", str(response.findings))
        self.assertNotIn("custom-secret-1234567890", str(response.findings))
        self.assertNotIn("BEGIN PRIVATE KEY", str(response.findings))
        self.assertEqual(200, response.status_code)
        self.assertEqual({"X-Contract": "preserved"}, response.headers)

    async def test_safe_scan_findings_are_unchanged(self):
        route = next(route for route in self.module._router_stub.router.routes if route.path == "/scan-upload")
        expected = [{"description": "No credentials", "metadata": {"safe": True}}]

        response = await route.dependant.call()

        self.assertEqual(expected, response.findings)

    async def test_redaction_preserves_safe_multiline_and_long_finding_text(self):
        safe_multiline = "line one\n\tline two"
        safe_long = "x" * 5000

        redacted = self.module._redact_supported_tokens([safe_multiline, safe_long])

        self.assertEqual([safe_multiline, safe_long], redacted)

    async def test_redaction_preserves_non_secret_control_characters_around_secret(self):
        value = "before\napi_key=custom-secret-1234567890\tafter"

        redacted = self.module._redact_supported_tokens(value)

        self.assertEqual("before\napi_key=<redacted>\tafter", redacted)

    async def test_startup_does_not_register_global_temp_directory_cleanup(self):
        self.assertEqual([], self.module.app.router.handlers)

    async def test_import_recreates_private_runtime_temp_root_without_touching_parent(self):
        runtime_root = self.module._RUNTIME_TEMP_ROOT
        outside = self.module._test_temp_parent / "outside.txt"

        self.assertTrue(runtime_root.is_dir())
        self.assertEqual(0o700, runtime_root.stat().st_mode & 0o777)
        self.assertFalse((runtime_root / "stale-upload.zip").exists())
        self.assertEqual("keep", outside.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
