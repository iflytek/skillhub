#!/usr/bin/env python3
"""Deployment contract checks using only synthetic inputs; never load local credential files."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
BASE = """SKILLHUB_PUBLIC_BASE_URL=https://skillhub.example.invalid
SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET=synthetic-validation-only-not-a-secret-value
POSTGRES_DB=skillhub
POSTGRES_USER=skillhub
POSTGRES_PASSWORD=synthetic-validation-only-not-a-secret
BOOTSTRAP_ADMIN_ENABLED=false
SESSION_COOKIE_SECURE=true
SKILLHUB_STORAGE_PROVIDER=local
"""


class BusinessLogsDeploymentTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="skillhub-1009-config-")
        self.addCleanup(self.directory.cleanup)
        self.fixture = Path(self.directory.name) / "synthetic.env"
        self.fixture.write_text(BASE)
        self.environment = dict(os.environ, COMPOSE_DISABLE_ENV_FILE="true")
        # Explicitly pin test settings rather than inheriting operator telemetry configuration.
        for key in list(self.environment):
            if key.startswith("SKILLHUB_BUSINESS_LOGS_"):
                del self.environment[key]

    def run_command(self, *command):
        return subprocess.run(command, cwd=ROOT, env=self.environment, capture_output=True, text=True, timeout=60)

    def validate(self, extra):
        self.fixture.write_text(BASE + extra)
        return self.run_command("sh", "scripts/validate-release-config.sh", str(self.fixture))

    def test_release_default_off_and_enabled_requires_endpoint(self):
        self.assertEqual(self.validate("").returncode, 0)
        result = self.validate("SKILLHUB_BUSINESS_LOGS_ENABLED=true\n")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("SKILLHUB_BUSINESS_LOGS_ENDPOINT is required", result.stderr)
        self.assertEqual(self.validate("SKILLHUB_BUSINESS_LOGS_ENABLED=true\nSKILLHUB_BUSINESS_LOGS_ENDPOINT=http://collector:4318/v1/logs\n").returncode, 0)

    def test_release_rejects_secret_bearing_url_without_printing_it(self):
        result = self.validate("SKILLHUB_BUSINESS_LOGS_ENABLED=true\nSKILLHUB_BUSINESS_LOGS_ENDPOINT=https://user:fake-secret@receiver.invalid/v1/logs?token=fake-query\n")
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("fake-secret", result.stdout + result.stderr)
        self.assertNotIn("fake-query", result.stdout + result.stderr)

    def test_compose_defaults_and_explicit_forwarding(self):
        command = ("docker", "compose", "--env-file", str(self.fixture), "-p", "skillhub-1009-config", "-f", "compose.release.yml", "config", "--format", "json")
        result = self.run_command(*command)
        self.assertEqual(result.returncode, 0, result.stderr)
        environment = json.loads(result.stdout)["services"]["server"]["environment"]
        self.assertEqual(environment["SKILLHUB_BUSINESS_LOGS_ENABLED"], "false")
        self.assertEqual(environment["SKILLHUB_BUSINESS_LOGS_ENDPOINT"], "")
        self.assertEqual(environment["SKILLHUB_BUSINESS_LOGS_MAX_QUEUE_SIZE"], "2048")
        self.fixture.write_text(BASE + "SKILLHUB_BUSINESS_LOGS_ENABLED=true\nSKILLHUB_BUSINESS_LOGS_ENDPOINT=http://collector:4318/v1/logs\nSKILLHUB_BUSINESS_LOGS_NAMESPACE_ALLOWLIST=7,12\nSKILLHUB_BUSINESS_LOGS_EVENT_ALLOWLIST=\n")
        result = self.run_command(*command)
        self.assertEqual(result.returncode, 0, result.stderr)
        environment = json.loads(result.stdout)["services"]["server"]["environment"]
        self.assertEqual(environment["SKILLHUB_BUSINESS_LOGS_ENABLED"], "true")
        self.assertEqual(environment["SKILLHUB_BUSINESS_LOGS_ENDPOINT"], "http://collector:4318/v1/logs")
        self.assertEqual(environment["SKILLHUB_BUSINESS_LOGS_NAMESPACE_ALLOWLIST"], "7,12")
        self.assertEqual(environment["SKILLHUB_BUSINESS_LOGS_EVENT_ALLOWLIST"], "")

    @unittest.skipUnless(shutil.which("helm"), "helm executable required")
    def test_helm_defaults_secret_reference_and_invalid_enabled_config(self):
        command = ("helm", "template", "skillhub-1009-config", "charts/skillhub", "-f", "charts/skillhub/tests/test-values.yaml", "--show-only", "templates/server-deployment.yaml")
        result = self.run_command(*command)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertRegex(result.stdout, 'name: SKILLHUB_BUSINESS_LOGS_ENABLED\\s+value: "false"')
        self.assertRegex(result.stdout, 'name: SKILLHUB_BUSINESS_LOGS_ENDPOINT\\s+value: ""')
        self.assertNotIn("name: SKILLHUB_BUSINESS_LOGS_HEADERS", result.stdout)
        result = self.run_command(*command, "--set", "server.businessLogs.enabled=true")
        self.assertNotEqual(result.returncode, 0)
        result = self.run_command(*command, "--set", "server.businessLogs.enabled=true", "--set", "server.businessLogs.endpoint=http://collector:4318/v1/logs", "--set", "server.businessLogs.headersSecretRef.name=analytics-credentials")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.count("name: SKILLHUB_BUSINESS_LOGS_HEADERS"), 1)
        self.assertRegex(result.stdout, 'name: SKILLHUB_BUSINESS_LOGS_HEADERS\\s+valueFrom:\\s+secretKeyRef:\\s+name: "analytics-credentials"\\s+key: "headers"\\s+optional: false')


if __name__ == "__main__":
    unittest.main(verbosity=2)
