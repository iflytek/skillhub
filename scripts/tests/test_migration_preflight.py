"""Offline behavior tests for the read-only migration preflight."""
import contextlib
import importlib.util
import io
import os
import tempfile
import urllib.error
from unittest.mock import patch
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("preflight", ROOT / "scripts/migration-preflight.py")
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
PREFIX = "server/skillhub-app/src/main/resources/db/migration/"


class PreflightTest(unittest.TestCase):
    def test_status_and_rename_boundaries(self):
        path = PREFIX + "V001__new.sql"
        self.assertEqual(m.introduced_version(dict(filename=path, status="added")), 1)
        for status in ("modified", "removed", "copied"):
            self.assertIsNone(m.introduced_version(dict(filename=path, status=status)))
        self.assertIsNone(m.introduced_version(dict(filename=path, status="renamed",
                                                     previous_filename=PREFIX + "V1__old.sql")))
        self.assertEqual(m.introduced_version(dict(filename=path, status="renamed",
                                                     previous_filename=PREFIX + "V2__old.sql")), 1)
        for path in ("tests/V1__fake.sql", PREFIX + "V1_2__other.sql", PREFIX + "V1__new.sql/no"):
            self.assertIsNone(m.introduced_version(dict(filename=path, status="added")))

    def test_multiple_collisions_sorted_and_single_pr_not_collision(self):
        class API:
            def pages(self, path, limit=None):
                if path.startswith("/pulls?"):
                    return [dict(number=n, head=dict(sha=str(n)), base=dict(sha="base")) for n in (9, 2)]
                return [dict(filename=PREFIX + f"V{v}__a.sql", status="added") for v in (20, 3, 3)]
            def get(self, path):
                return dict(head=dict(sha=path.split("/")[-1]), state="open", base=dict(sha="base"), changed_files=3)
        collisions, errors, count = m.scan(API())
        self.assertEqual(list(collisions), [3, 20])
        self.assertEqual(errors, [])
        self.assertEqual(count, 2)
        report = m.summary(collisions, errors, count)
        self.assertLess(report.index("PR #2"), report.index("PR #9"))
        self.assertEqual(report.count("PR #2:"), 2)

    def test_incomplete_never_reports_clearance(self):
        report = m.summary({}, ["API HTTP 403"], 3)
        self.assertIn("scan incomplete", report)
        self.assertNotIn("No cross-PR", report)

    def test_untrusted_filename_is_text(self):
        attack = PREFIX + "V1__</pre><script>alert(1)</script>\n::error::x.sql"
        report = m.summary({1: {2: [attack], 3: [PREFIX + "V1__a.sql"]}}, [], 2)
        self.assertNotIn("<script>", report)
        self.assertIn("&lt;script&gt;", report)
        self.assertIn("&#10;", report)

    def test_malformed_response_and_actual_files_cap(self):
        api = m.GitHub("iflytek/skillhub", "token")
        with patch.object(api, "get", return_value={"unexpected": True}):
            with self.assertRaises(m.ScanError):
                api.pages("/pulls")
        with patch.object(api, "get", return_value=[{}] * 100) as get:
            with self.assertRaises(m.ScanError):
                api.pages("/pulls/1/files", limit=3000)
            self.assertEqual(get.call_count, 30)
        with patch.dict(os.environ, {"GITHUB_REPOSITORY": "iflytek/skillhub",
                                     "GITHUB_STEP_SUMMARY": ""}):
            with patch.object(m, "scan", side_effect=KeyError("filename")):
                with contextlib.redirect_stdout(io.StringIO()) as output:
                    self.assertEqual(m.main(), 1)
                self.assertIn("Malformed GitHub API response", output.getvalue())
                self.assertNotIn("No cross-PR", output.getvalue())

    def test_pagination_and_limit(self):
        class API(m.GitHub):
            def __init__(self):
                self.calls = []
            def get(self, path):
                self.calls.append(path)
                return [dict()] * (100 if path.endswith("&page=1") else 1)
        api = API()
        self.assertEqual(len(api.pages("/pulls?state=open")), 101)
        self.assertEqual(api.calls[1], "/pulls?state=open&per_page=100&page=2")
        with self.assertRaises(m.ScanError):
            API().pages("/pulls/1/files", limit=100)


    def test_single_pr_duplicates_do_not_collide(self):
        class API:
            def pages(self, path, limit=None):
                if path.startswith("/pulls?"):
                    return [dict(number=1, head=dict(sha="sha"), base=dict(sha="base"))]
                return [dict(filename=PREFIX + "V1__a.sql", status="added"),
                        dict(filename=PREFIX + "V1__b.sql", status="added")]
            def get(self, path):
                return dict(head=dict(sha="sha"), state="open", base=dict(sha="base"), changed_files=2)
        self.assertEqual(m.scan(API()), ({}, [], 1))

    def test_api_failure_retains_confirmed_collisions_and_marks_incomplete(self):
        class API:
            def pages(self, path, limit=None):
                if path.startswith("/pulls?"):
                    return [dict(number=n, head=dict(sha="sha"), base=dict(sha="base")) for n in (1, 2, 3)]
                if path == "/pulls/3/files":
                    raise m.ScanError("GitHub API HTTP 403; scan incomplete")
                return [dict(filename=PREFIX + "V5__a.sql", status="added")]
            def get(self, path):
                return dict(head=dict(sha="sha"), state="open", base=dict(sha="base"), changed_files=1)
        collisions, errors, count = m.scan(API())
        self.assertEqual(list(collisions[5]), [1, 2])
        self.assertIn("PR #3", errors[0])
        report = m.summary(collisions, errors, count)
        self.assertIn("scan incomplete", report)
        self.assertIn("V5", report)

    def test_changed_or_closed_pr_and_truncated_files_are_incomplete(self):
        for change in (dict(head=dict(sha="changed")), dict(state="closed"), dict(base=dict(sha="changed")), dict(changed_files=2)):
            class API:
                def pages(self, path, limit=None):
                    if path.startswith("/pulls?"):
                        return [dict(number=1, head=dict(sha="sha"), base=dict(sha="base"))]
                    return [dict(filename=PREFIX + "V1__a.sql", status="added")]
                def get(self, path):
                    return dict(dict(head=dict(sha="sha"), state="open", base=dict(sha="base"), changed_files=1), **change)
            collisions, errors, _ = m.scan(API())
            self.assertEqual(collisions, {})
            self.assertEqual(len(errors), 1)

    def test_get_uses_only_read_api_and_redacts_errors(self):
        api = m.GitHub("iflytek/skillhub", "private-token")
        with patch.object(m.urllib.request, "urlopen", return_value=io.BytesIO(b'[]')) as opening:
            self.assertEqual(api.get("/pulls?state=open"), [])
            request = opening.call_args.args[0]
            self.assertEqual(request.get_method(), "GET")
            self.assertEqual(request.full_url, "https://api.github.com/repos/iflytek/skillhub/pulls?state=open")
            self.assertEqual(opening.call_args.kwargs["timeout"], 30)
        for error in (urllib.error.HTTPError("https://secret", 403, "private-token", {}, None),
                      urllib.error.URLError("private-token"), TimeoutError("private-token"),
                      ValueError("private-token")):
            with patch.object(m.urllib.request, "urlopen", side_effect=error):
                with self.assertRaises(m.ScanError) as caught:
                    api.get("/pulls")
                self.assertNotIn("private-token", str(caught.exception))
                self.assertNotIn("https://secret", str(caught.exception))
        with self.assertRaises(m.ScanError):
            m.GitHub("owner/repo/../../other", "token")

    def test_main_writes_summary_and_is_advisory_on_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory) / "summary"
            with patch.dict(os.environ, {"GITHUB_REPOSITORY": "iflytek/skillhub",
                                         "GITHUB_TOKEN": "token", "GITHUB_STEP_SUMMARY": str(destination)}):
                with patch.object(m.GitHub, "pages", side_effect=m.ScanError("API unavailable")):
                    self.assertEqual(m.main(), 1)
                self.assertIn("scan incomplete", destination.read_text())
                with patch.object(m, "scan", return_value=({}, [], 0)):
                    self.assertEqual(m.main(), 0)
                self.assertIn("No cross-PR version collisions", destination.read_text())

    def test_full_multi_page_pr_and_file_collection(self):
        class API(m.GitHub):
            def __init__(self):
                self.calls = []
            def get(self, path):
                self.calls.append(path)
                if path.startswith("/pulls?"):
                    start, end = (1, 101) if path.endswith("&page=1") else (101, 102)
                    return [dict(number=n, head=dict(sha="sha"), base=dict(sha="base")) for n in range(start, end)]
                if "/files?" in path:
                    if path.endswith("&page=1"):
                        return [dict(filename=PREFIX + f"V{i}__a.sql", status="added") for i in range(100)]
                    return [dict(filename=PREFIX + "V100__a.sql", status="added")]
                return dict(head=dict(sha="sha"), state="open", base=dict(sha="base"), changed_files=101)
        api = API()
        collisions, errors, count = m.scan(api)
        self.assertEqual(count, 101)
        self.assertEqual(len(collisions), 101)
        self.assertEqual(len(collisions[100]), 101)
        self.assertEqual(errors, [])
        self.assertIn("/pulls/101/files?per_page=100&page=2", api.calls)

    def test_workflow_trust_permissions_failure_fallback_and_ci_wiring(self):
        workflow = (ROOT / ".github/workflows/migration-preflight.yml").read_text()
        for expected in ("pull_request_target:", "schedule:", "workflow_dispatch:",
                         "contents: read", "pull-requests: read", "continue-on-error: true",
                         "ref: ${{ github.event.repository.default_branch }}", "persist-credentials: false",
                         "No clearance issued", "python3 scripts/migration-preflight.py"):
            self.assertIn(expected, workflow)
        for forbidden in ("pull_request.head", "secrets.", "pull-requests: write", "issues: write"):
            self.assertNotIn(forbidden, workflow)
        scripts = (ROOT / ".github/workflows/pr-scripts.yml").read_text()
        self.assertIn("'.github/workflows/migration-preflight.yml'", scripts)
        self.assertIn("-p 'test_migration_preflight.py'", scripts)


if __name__ == "__main__":
    unittest.main()
