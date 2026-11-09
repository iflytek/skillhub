#!/usr/bin/env python3
"""Offline runtime distribution and checksum boundaries; never start services."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
REF = "929f3f1b34a2ddd1739b8ba36715ae34e34173b3"
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
 if mode in ('html','tampered'):
  dest.write_text('<html>200 fallback</html>' if mode=='html' else 'tampered');raise SystemExit(0)
dest.write_bytes((pathlib.Path(os.environ['FIXTURE_ROOT'])/name).read_bytes())
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
        self.run_runtime("analytics-up", "--aliyun", "--ref", "fixture-ignored-ref")
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
            self.assertIn(f"https://gh-proxy.org/https://raw.githubusercontent.com/iflytek/skillhub/{REF}/{name}", urls)
        for name in ["compose.release.yml", ".env.release.example"]:
            self.assertIn("https://imageless.oss-cn-beijing.aliyuncs.com/" + name, urls)
        for call in self.calls():
            if call.get("url", "").startswith("https://gh-proxy.org/"):
                self.assertEqual(call["args"][0], "-q")
                for option in ["--proto", "--proto-redir", "--connect-timeout", "--max-time"]:
                    self.assertIn(option, call["args"])
                self.assertNotIn("-H", call["args"])

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
        for mode in ["404", "timeout", "html", "tampered"]:
            with self.subTest(mode=mode):
                directory = self.home / "analytics"
                directory.mkdir(parents=True, exist_ok=True)
                dest = directory / "compose.analytics-umami.yml"
                dest.write_bytes(b"original operator file\n")
                self.log.write_text("")
                self.run_runtime("analytics-up", "--aliyun", mode=mode, success=False)
                self.assertEqual(dest.read_bytes(), b"original operator file\n")
                self.assertEqual(list(directory.glob("*.download.*")), [])
                self.assertFalse(any(c.get("docker", [""])[0] != "compose" for c in self.calls() if "docker" in c))
                self.assertFalse(any("up" in c.get("docker", []) for c in self.calls()))

    def test_verified_cache_and_embedded_hash_drift(self):
        self.run_runtime("analytics-up", "--aliyun")
        cache = self.home / "analytics/compose.analytics-umami.yml"
        cache.chmod(0o600)
        self.log.write_text("")
        self.run_runtime("analytics-up", "--aliyun", mode="timeout")
        self.assertFalse(any(c.get("url", "").startswith("https://gh-proxy.org/") for c in self.calls()))
        self.assertEqual(cache.stat().st_mode & 0o777, 0o644)
        script = (ROOT / "scripts/runtime.sh").read_text()
        for name in FILES:
            digest = hashlib.sha256((ROOT / name).read_bytes()).hexdigest()
            self.assertIn(name + ") echo " + digest, script)
        self.assertIn("extension_ref=" + REF, script)

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
                self.run_runtime(command, "--aliyun", mode="tampered",
                                 extra={"FAIL_NAME": name}, success=False)
                self.assertEqual(dest.read_bytes(), b"keep previous configuration\n")
                self.assertEqual(list(self.home.rglob("*.download.*")), [])
                self.assertTrue(all(c["docker"] == ["compose", "version"]
                                    for c in self.calls() if "docker" in c))

    def test_portable_hash_and_missing_verifier(self):
        script = (ROOT / "scripts/runtime.sh").read_text()
        helpers = "extension_sha256()" + script.split("extension_sha256()", 1)[1].split("\nset_env_value()", 1)[0]
        probe = self.work / "probe.sh"
        probe.write_text("#!/bin/sh\nset -eu\n" + helpers + '\nfile_sha256 "$1"\n')
        isolated = self.work / "hash-bin"
        isolated.mkdir()
        (isolated / "shasum").symlink_to(shutil.which("shasum"))
        (isolated / "cut").symlink_to(shutil.which("cut"))
        source = ROOT / FILES[0]
        env = dict(self.env, PATH=str(isolated))
        result = subprocess.run(["/bin/sh", str(probe), str(source)], env=env,
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.strip(), hashlib.sha256(source.read_bytes()).hexdigest())
        (isolated / "shasum").unlink()
        result = subprocess.run(["/bin/sh", str(probe), str(source)], env=env,
                                capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("require sha256sum or shasum", result.stderr)
        probe.write_text("#!/bin/sh\nset -eu\n" + helpers +
                         '\nUSE_ALIYUN=true\nCUSTOM_RAW_BASE=\n'
                         'download_extension_file compose.analytics-umami.yml "$1"\n')
        dest = self.work / "preserved.yml"
        dest.write_text("original\n")
        result = subprocess.run(["/bin/sh", str(probe), str(dest)], env=env,
                                capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("require sha256sum or shasum", result.stderr)
        self.assertEqual(dest.read_text(), "original\n")
        self.assertFalse(self.log.exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
