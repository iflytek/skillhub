"""Exercise workflow shell commands with real Node module resolution, offline."""

import http.server
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import threading
import unittest


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = (ROOT / ".github/workflows/deepwiki.yml").read_text()


def run_block(name):
    step = WORKFLOW.split(f"      - name: {name}\n", 1)[1].split("\n      - ", 1)[0]
    return "\n".join(
        line[10:] for line in step.split("        run: |\n", 1)[1].splitlines()
        if line.startswith("          ")
    )


class DeepWikiWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="deepwiki-workflow-test-")
        self.addCleanup(self.tmp.cleanup)
        self.base = Path(self.tmp.name)
        self.workspace = self.base / "workspace"
        self.crawler = self.base / "actions/crawler"
        self.workspace.mkdir()
        (self.crawler / "scripts").mkdir(parents=True)
        self.env = dict(os.environ, CRAWLER_DIR=str(self.crawler),
                        REPOSITORY="iflytek/skillhub", NODE_OPTIONS="--no-global-search-paths",
                        GITHUB_OUTPUT=str(self.base / "output"))
        self.env.pop("NODE_PATH", None)
        self.env.update(dict(re.findall(
            r"^      (MAX_CONCURRENCY|REQUEST_TIMEOUT|RETRY_ATTEMPTS): '([^']+)'$",
            WORKFLOW, re.MULTILINE)))
        self.env["USER_AGENT"] = "DeepWiki-Crawler/1.0"
        for name in ("check", "crawl"):
            (self.crawler / f"scripts/{name}-deepwiki.js").write_text(
                "const fetch = require('node-fetch');\n"
                "require('cheerio');\n"
                "if (process.env.FAIL_STAGE === '" + name + "') process.exit(17);\n"
                "if (process.argv[2] !== 'iflytek/skillhub') process.exit(18);\n"
                "fetch(process.env.TEST_URL).then(text => console.log('" + name
                + " ' + text)).catch(() => process.exit(19));\n"
            )

    def shell(self, script):
        return subprocess.run(["bash", "-c", script], cwd=self.workspace,
                              env=self.env, text=True, capture_output=True, timeout=10)

    def install_fixture(self, destination):
        modules = destination / "node_modules"
        for module in ("node-fetch", "cheerio"):
            (modules / module).mkdir(parents=True)
        (modules / "cheerio/index.js").write_text("module.exports = {};\n")
        (modules / "node-fetch/index.js").write_text(
            "module.exports = url => new Promise((resolve, reject) => {\n"
            "require('http').get(url, res => { let body = '';\n"
            "res.on('data', part => body += part);\n"
            "res.on('end', () => resolve(body)); }).on('error', reject); });\n"
        )

    def test_original_split_directory_failure_then_fixed_resolution(self):
        self.install_fixture(self.workspace)
        result = self.shell('node "$CRAWLER_DIR/scripts/check-deepwiki.js" "$REPOSITORY"')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Cannot find module 'node-fetch'", result.stderr)
        self.install_fixture(self.crawler)

        class Handler(http.server.BaseHTTPRequestHandler):
            def do_GET(self):
                self.send_response(200)
                self.end_headers()
                self.wfile.write(b"mock-http-ok")

            def log_message(self, *_):
                pass

        server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        self.env["TEST_URL"] = f"http://127.0.0.1:{server.server_port}/"
        result = self.shell(run_block("Check repository and crawl DeepWiki"))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.splitlines(), ["check mock-http-ok", "crawl mock-http-ok"])
        self.env["FAIL_STAGE"] = "crawl"
        result = self.shell(run_block("Check repository and crawl DeepWiki"))
        self.assertEqual(result.returncode, 17)
        self.assertEqual(result.stdout.splitlines(), ["check mock-http-ok"])

    def test_failures_propagate_and_bad_repository_is_rejected(self):
        self.install_fixture(self.crawler)
        self.env["FAIL_STAGE"] = "check"
        result = self.shell(run_block("Check repository and crawl DeepWiki"))
        self.assertEqual(result.returncode, 17)
        self.assertNotIn("crawl", result.stdout)
        self.env["REPOSITORY"] = "iflytek/skillhub; echo unsafe"
        result = self.shell(run_block("Check repository and crawl DeepWiki"))
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("unsafe", result.stdout)

    def test_install_directory_versions_and_failure(self):
        bin_dir = self.base / "bin"
        bin_dir.mkdir()
        npm = bin_dir / "npm"
        npm.write_text("#!/usr/bin/env python3\nimport json, os, sys\n"
                       "print(json.dumps(sys.argv[1:]))\n"
                       "sys.exit(int(os.environ.get('INSTALL_EXIT', '0')))\n")
        npm.chmod(0o700)
        self.env["PATH"] = str(bin_dir) + os.pathsep + self.env["PATH"]
        command = run_block("Install crawler runtime dependencies")
        result = self.shell(command)
        self.assertEqual(result.returncode, 0, result.stderr)
        args = json.loads(result.stdout)
        self.assertEqual(args[:3], ["install", "--prefix", str(self.crawler)])
        for option in ("--ignore-scripts", "--package-lock=false", "--save-exact",
                       "node-fetch@2.7.0", "cheerio@1.0.0"):
            self.assertIn(option, args)
        self.env["INSTALL_EXIT"] = "23"
        self.assertEqual(self.shell(command).returncode, 23)

    def test_workflow_contract(self):
        for expected in ("types: [published]", "workflow_dispatch:", "node-version: '24'",
                         "timeout-minutes: 10", "persist-credentials: false",
                         "ref: b9fc8378235f298d736d12c5f4651d6ea0820c61",
                         "contents: read", "REPOSITORY: ${{ github.repository }}"):
            self.assertIn(expected, WORKFLOW)
        self.assertEqual(self.env["MAX_CONCURRENCY"], "3")
        self.assertEqual(self.env["REQUEST_TIMEOUT"], "30000")
        self.assertEqual(self.env["RETRY_ATTEMPTS"], "3")
        pr = (ROOT / ".github/workflows/pr-scripts.yml").read_text()
        self.assertIn("'.github/workflows/deepwiki.yml'", pr)
        self.assertIn("-p 'test_deepwiki_workflow.py'", pr)
