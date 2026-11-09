#!/usr/bin/env python3
"""Offline runtime distribution and failure boundaries; never start services."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
FILES = ["compose.release.analytics.yml", "compose.analytics-umami.yml",
         "compose.release.business-logs.yml", "compose.business-logs-collector.yml",
         "business-logs-collector.yaml"]


class ExtensionProxy(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="skillhub-proxy-test-")
        self.addCleanup(self.temp.cleanup)
        self.work = Path(self.temp.name)
        self.bin = self.work / "bin"
        self.bin.mkdir()
        self.home = self.work / "runtime"
        self.log = self.work / "calls.jsonl"
        self.fake("docker", """import json,os,sys
with open(os.environ['CALLS'],'a') as f:f.write(json.dumps({'docker':sys.argv[1:]})+'\\n')
if sys.argv[1:3]==['network','inspect']:raise SystemExit(1)
""")
        self.fake("curl", """import json,os,pathlib,sys
a=sys.argv[1:];url=next(x for x in a if '://' in x);dest=pathlib.Path(a[a.index('-o')+1])
with open(os.environ['CALLS'],'a') as f:f.write(json.dumps({'url':url,'args':a})+'\\n')
name=url.rsplit('/',1)[-1]
if url.startswith('https://gh-proxy.org/') and name==os.environ.get('FAIL_NAME',name):
 mode=os.environ.get('MODE','good')
 if mode in ('404','timeout'):raise SystemExit(22 if mode=='404' else 28)
 responses={'html':'<!DOCTYPE HTML><HTML>200 fallback</HTML>', 'empty':'',
            'whitespace':'  \\n\\t\\n', 'invalid':'not a configuration',
            'partial':'receivers: {}\\nexporters: {}\\n',
            'html-with-keys':'<html>error\\nservices: {}\\nreceivers: {}\\nexporters: {}\\nservice: {}\\n</html>'}
 if mode in responses:
  dest.write_text(responses[mode]);raise SystemExit(0)
data=(pathlib.Path(os.environ['FIXTURE_ROOT'])/name).read_bytes()
if url.startswith('https://gh-proxy.org/'):
 data+=('\\n# upstream revision '+os.environ.get('REVISION','original')+'\\n').encode()
dest.write_bytes(data)
""")
        self.env = dict(os.environ, PATH=str(self.bin) + os.pathsep + os.environ["PATH"],
                        CALLS=str(self.log), FIXTURE_ROOT=str(ROOT))
        for key in ["SKILLHUB_RAW_BASE", "SKILLHUB_HOME", "SKILLHUB_REF"]:
            self.env.pop(key, None)

    def fake(self, name, body):
        target = self.bin / name
        target.write_text("#!/usr/bin/env python3\n" + body)
        target.chmod(0o755)

    def run_runtime(self, command, *args, mode="good", extra=None, success=True):
        result = subprocess.run(["sh", str(ROOT / "scripts/runtime.sh"), command,
                                 "--home", str(self.home), *args],
                                env=dict(self.env, MODE=mode, **(extra or {})),
                                capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode == 0, success, result.stderr)
        return result

    def calls(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()]

    def test_all_optional_files_and_core_sources(self):
        self.env["SKILLHUB_REF"] = "fixture-ref"
        self.run_runtime("analytics-up", "--aliyun")
        self.run_runtime("analytics-enable", "--aliyun", "--website-id",
                         "4398de8e-40df-4483-9eea-ed213643525f")
        directory = self.home / "business-logs"
        directory.mkdir()
        (directory / ".env").write_text(
            "SKILLHUB_BUSINESS_LOGS_PROJECT=fixture-collector\n"
            "SKILLHUB_BUSINESS_LOGS_NETWORK=fixture-network\n"
            "SKILLHUB_BUSINESS_LOGS_DOWNSTREAM_ENDPOINT=https://receiver.example.test\n")
        self.run_runtime("business-logs-up", "--aliyun")
        urls = [c["url"] for c in self.calls() if "url" in c]
        for name in FILES:
            self.assertIn(f"https://gh-proxy.org/https://raw.githubusercontent.com/iflytek/skillhub/fixture-ref/{name}", urls)
        for name in ["compose.release.yml", ".env.release.example"]:
            self.assertIn("https://imageless.oss-cn-beijing.aliyuncs.com/" + name, urls)
        for call in self.calls():
            if call.get("url", "").startswith("https://gh-proxy.org/"):
                self.assertEqual(call["args"][0], "-q")
                for option in ["--proto", "--proto-redir", "--connect-timeout", "--max-time"]:
                    self.assertIn(option, call["args"])
                self.assertNotIn("-H", call["args"])

    def test_proxy_ref_defaults_environment_cli_and_image_version(self):
        for index, (args, extra, ref) in enumerate([
                ([], {}, "main"),
                (["--version", "v0.2.26"], {}, "main"),
                ([], {"SKILLHUB_REF": "fixture-env-ref"}, "fixture-env-ref"),
                (["--ref", "fixture-cli-ref", "--version", "v0.2.26"],
                 {"SKILLHUB_REF": "fixture-env-ref"}, "fixture-cli-ref")]):
            with self.subTest(ref=ref, args=args):
                self.home = self.work / f"ref-{index}"
                self.log.write_text("")
                self.run_runtime("analytics-up", "--aliyun", *args, extra=extra)
                self.assertIn(f"https://gh-proxy.org/https://raw.githubusercontent.com/iflytek/skillhub/{ref}/compose.analytics-umami.yml",
                              [c.get("url") for c in self.calls()])

    def test_default_install_never_fetches_optional_files(self):
        self.run_runtime("up", "--aliyun")
        self.assertFalse(any(c.get("url", "").startswith("https://gh-proxy.org/") for c in self.calls()))

    def test_github_ref_and_explicit_override_keep_existing_source(self):
        self.run_runtime("analytics-up", "--ref", "fixture-ref")
        self.assertIn("https://raw.githubusercontent.com/iflytek/skillhub/fixture-ref/compose.analytics-umami.yml",
                      [c.get("url") for c in self.calls()])
        self.log.write_text("")
        self.run_runtime("analytics-up", "--aliyun", extra={"SKILLHUB_RAW_BASE": "https://custom.example.test"})
        self.assertIn("https://custom.example.test/compose.analytics-umami.yml", [c.get("url") for c in self.calls()])
        self.assertFalse(any(c.get("url", "").startswith("https://gh-proxy.org/") for c in self.calls()))

    def test_failures_preserve_old_file_and_clean_temporary_download(self):
        for mode in ["404", "timeout", "html", "empty", "whitespace", "invalid", "html-with-keys"]:
            with self.subTest(mode=mode):
                directory = self.home / "analytics"
                directory.mkdir(parents=True, exist_ok=True)
                dest = directory / "compose.analytics-umami.yml"
                dest.write_bytes(b"original operator file\n")
                self.log.write_text("")
                self.run_runtime("analytics-up", "--aliyun", mode=mode, success=False)
                self.assertEqual(dest.read_bytes(), b"original operator file\n")
                self.assertEqual(list(directory.glob("*.download.*")), [])
                self.assert_no_docker_mutation()

    def test_updated_templates_are_redownloaded_without_runtime_changes(self):
        script_before = (ROOT / "scripts/runtime.sh").read_bytes()
        self.run_runtime("analytics-up", "--aliyun")
        dest = self.home / "analytics/compose.analytics-umami.yml"
        before = dest.read_bytes()
        dest.chmod(0o600)
        self.log.write_text("")
        self.run_runtime("analytics-up", "--aliyun", extra={"REVISION": "updated"})
        self.assertTrue(any(c.get("url", "").startswith("https://gh-proxy.org/") for c in self.calls()))
        self.assertNotEqual(dest.read_bytes(), before)
        self.assertIn(b"# upstream revision updated", dest.read_bytes())
        self.assertEqual(dest.stat().st_mode & 0o777, 0o644)
        self.assertEqual((ROOT / "scripts/runtime.sh").read_bytes(), script_before)
        self.log.write_text("")
        updated = dest.read_bytes()
        self.run_runtime("analytics-up", "--aliyun", mode="timeout", success=False)
        self.assertEqual(dest.read_bytes(), updated)
        self.assert_no_docker_mutation()

    def assert_no_docker_mutation(self):
        self.assertTrue(all(c["docker"] == ["compose", "version"]
                            for c in self.calls() if "docker" in c))

    def test_each_optional_failure_stops_before_docker_mutation(self):
        for index, name in enumerate(FILES):
            with self.subTest(name=name):
                self.home = self.work / f"runtime with spaces {index}"
                if name == "compose.release.analytics.yml":
                    command = "analytics-enable"
                    dest = self.home / name
                elif name == "compose.analytics-umami.yml":
                    command = "analytics-up"
                    dest = self.home / "analytics" / name
                else:
                    command = "business-logs-up"
                    dest = self.home / name if name.startswith("compose.release.") else self.home / "business-logs" / name
                dest.parent.mkdir(parents=True)
                dest.write_bytes(b"keep previous configuration\n")
                self.log.write_text("")
                self.run_runtime(command, "--aliyun", mode="partial",
                                 extra={"FAIL_NAME": name}, success=False)
                self.assertEqual(dest.read_bytes(), b"keep previous configuration\n")
                self.assertEqual(list(self.home.rglob("*.download.*")), [])
                self.assert_no_docker_mutation()


if __name__ == "__main__":
    unittest.main(verbosity=2)
