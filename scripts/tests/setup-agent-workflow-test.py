"""Portable setup/update regression; native CLI checks skip if not installed."""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("agent_setup", ROOT / "scripts/setup-agent-workflow.py")
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)


class ManagedSetupTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        policy = self.root / setup.POLICY
        policy.parent.mkdir(parents=True)
        policy.write_text("Fixture policy\n", encoding="utf-8")
        self.relative = ".agents/skills/openspec-apply-change/SKILL.md"
        self.generated = {self.relative: b"managed apply\n"}
        self.originals = {self.relative: b"upstream apply\n"}

    def apply(self):
        return setup.synchronize(self.root, self.generated, self.originals, "apply")

    def test_preview_has_no_install_side_effect(self):
        result = setup.synchronize(self.root, self.generated, self.originals, "preview")
        self.assertEqual([self.relative], result["changes"])
        self.assertFalse((self.root / ".agents").exists())
        self.assertFalse((self.root / setup.MANIFEST).exists())

    def test_install_is_idempotent_and_checkable(self):
        self.apply()
        self.assertEqual([], self.apply()["changes"])
        self.assertEqual([], setup.synchronize(self.root, self.generated, self.originals, "check")["changes"])

    def test_missing_entry_fails_check(self):
        self.apply()
        (self.root / self.relative).unlink()
        with self.assertRaisesRegex(ValueError, "Missing or changed"):
            setup.synchronize(self.root, self.generated, self.originals, "check")

    def test_local_edit_is_preserved_and_fails_check(self):
        self.apply()
        target = self.root / self.relative
        target.write_text("My custom changes\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Missing or changed"):
            setup.synchronize(self.root, self.generated, self.originals, "check")
        with self.assertRaisesRegex(ValueError, "Local edits conflict"):
            self.apply()
        self.assertEqual("My custom changes\n", target.read_text())

    def test_conflict_prevents_other_entries_from_being_written(self):
        other = ".agents/skills/openspec-verify-change/SKILL.md"
        target = self.root / other
        target.parent.mkdir(parents=True)
        target.write_text("Custom verifier\n", encoding="utf-8")
        self.generated[other] = b"managed verifier\n"
        self.originals[other] = b"upstream verifier\n"
        with self.assertRaisesRegex(ValueError, "Local edits conflict"):
            self.apply()
        self.assertFalse((self.root / self.relative).exists())
        self.assertFalse((self.root / setup.MANIFEST).exists())

    def test_known_upstream_entry_can_receive_project_reading_adapter(self):
        target = self.root / self.relative
        target.parent.mkdir(parents=True)
        target.write_bytes(self.originals[self.relative])
        report = self.apply()
        self.assertEqual(self.generated[self.relative], target.read_bytes())
        self.assertEqual(self.originals[self.relative], (self.root / report["backup"] / self.relative).read_bytes())

    def test_managed_refresh_keeps_backup(self):
        self.apply()
        self.generated[self.relative] = b"updated managed apply\n"
        report = self.apply()
        self.assertEqual(b"managed apply\n", (self.root / report["backup"] / self.relative).read_bytes())

    def test_corrupt_manifest_does_not_write_entries(self):
        manifest = self.root / setup.MANIFEST
        manifest.parent.mkdir(parents=True)
        manifest.write_text("[]", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Invalid integration manifest"):
            self.apply()
        self.assertFalse((self.root / ".agents").exists())

    def test_no_symlink_or_traversal_writes(self):
        outside = self.root / "outside"
        outside.mkdir()
        (self.root / ".agents").symlink_to(outside, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "Refusing symlink"):
            self.apply()
        with self.assertRaisesRegex(ValueError, "escapes project"):
            setup.safe_path(self.root, "../outside")

    def test_missing_shared_policy_refuses_install(self):
        (self.root / setup.POLICY).unlink()
        with self.assertRaisesRegex(ValueError, "Missing shared policy"):
            self.apply()


@unittest.skipUnless(shutil.which("openspec"), "Native OpenSpec CLI is not installed")
class NativeIntegrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        version = subprocess.check_output(["openspec", "--version"], text=True).strip()
        if version != setup.OPENSPEC_VERSION:
            raise unittest.SkipTest(f"Native integration requires OpenSpec {setup.OPENSPEC_VERSION}")

    def test_native_generation_includes_verify_without_changing_global_profile(self):
        global_home = Path(os.environ.get("XDG_CONFIG_HOME", str(Path.home() / ".config")))
        config = global_home / "openspec/config.json"
        before = config.read_bytes() if config.exists() else None
        generated, originals = setup.generate_skills()
        after = config.read_bytes() if config.exists() else None
        self.assertEqual(before, after)
        self.assertEqual(8, len(generated))
        self.assertEqual(b"codex\n", generated[".agents/skills/.openspec-target"])
        for name, stage in setup.STAGES.items():
            key = f".agents/skills/{name}/SKILL.md"
            self.assertIn(setup.MARKER.encode(), generated[key])
            self.assertIn(setup.POLICY.encode(), generated[key])
            self.assertTrue(generated[key].startswith(b"---\n"))
            self.assertNotEqual(originals[key], generated[key])
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            policy = root / setup.POLICY
            policy.parent.mkdir(parents=True)
            policy.write_text("Project policy\n", encoding="utf-8")
            (root / "openspec").mkdir()
            (root / "openspec/config.yaml").write_text("schema: spec-driven\n", encoding="utf-8")
            setup.synchronize(root, generated, originals, "apply")
            setup.synchronize(root, generated, originals, "check")
            # Run an actual upstream refresh under an isolated core profile.
            config_home = root / "isolated-config"
            (config_home / "openspec").mkdir(parents=True)
            (config_home / "openspec/config.json").write_text(
                json.dumps({"profile": "core", "delivery": "skills"}), encoding="utf-8")
            env = dict(os.environ, XDG_CONFIG_HOME=str(config_home),
                       OPENSPEC_TELEMETRY="0", OPENSPEC_NO_UPDATE_CHECK="1")
            subprocess.run(["openspec", "update", "--force", str(root)],
                           capture_output=True, text=True, check=True, env=env, timeout=30)
            with self.assertRaisesRegex(ValueError, "Missing or changed"):
                setup.synchronize(root, generated, originals, "check")
            failed_check = subprocess.run(
                [sys.executable, str(ROOT / "scripts/setup-agent-workflow.py"),
                 "--root", str(root), "--check"], capture_output=True, text=True, timeout=30)
            self.assertEqual(1, failed_check.returncode)
            self.assertIn("Missing or changed", failed_check.stderr)
            setup.synchronize(root, generated, originals, "apply")
            setup.synchronize(root, generated, originals, "check")

    def test_real_apply_returns_project_rules_but_does_not_expand_paths(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            change = root / "openspec/changes/example"
            change.mkdir(parents=True)
            shutil.copyfile(ROOT / "openspec/config.yaml", root / "openspec/config.yaml")
            (change / "tasks.md").write_text("## 1. Task\n- [ ] 1.1 Implement and test\n", encoding="utf-8")
            policy = root / setup.POLICY
            policy.parent.mkdir(parents=True)
            policy.write_text("BODY_SENTINEL_NOT_AUTOMATICALLY_EXPANDED\n", encoding="utf-8")
            result = subprocess.run(
                ["openspec", "instructions", "apply", "--change", "example", "--json"],
                cwd=root, capture_output=True, text=True, check=True, timeout=20,
            )
            output = json.loads(result.stdout)
            self.assertEqual("ready", output["state"])
            self.assertIn(setup.POLICY, output["context"])
            self.assertTrue(output["operationGuidance"])
            self.assertNotIn("BODY_SENTINEL_NOT_AUTOMATICALLY_EXPANDED", result.stdout)
            self.assertEqual(1, output["progress"]["remaining"])

    def test_invalid_context_is_not_mistaken_for_delivery(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            change = root / "openspec/changes/example"
            change.mkdir(parents=True)
            (root / "openspec/config.yaml").write_text(
                "schema: spec-driven\ncontext: [invalid, type]\n", encoding="utf-8")
            (change / "tasks.md").write_text(
                "## 1. Task\n- [ ] 1.1 Implement and test\n", encoding="utf-8")
            result = subprocess.run(
                ["openspec", "instructions", "apply", "--change", "example", "--json"],
                cwd=root, capture_output=True, text=True, check=True, timeout=20,
            )
            # OpenSpec tolerates a bad optional field. Exit 0 alone is insufficient.
            self.assertNotIn("context", json.loads(result.stdout))
            self.assertIn("context", result.stderr.lower())


if __name__ == "__main__":
    unittest.main()
