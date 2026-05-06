import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

spec = importlib.util.spec_from_file_location("ralph_module", SCRIPT_DIR / "ralph.py")
ralph = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(ralph)


VALID_PRD = """{
  "project": "SkillHub",
  "branchName": "test-branch",
  "userStories": [
    {
      "id": "US-001",
      "title": "Story",
      "description": "desc",
      "acceptanceCriteria": [],
      "priority": 1,
      "passes": false,
      "notes": "",
      "retryCount": 0,
      "blocked": false
    }
  ]
}
"""


class RalphPrdGuardTest(unittest.TestCase):
    def test_snapshot_creates_backup_for_valid_prd(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            prd_path = Path(tmpdir) / "prd.json"
            backup_path = Path(tmpdir) / ".prd.json.last-valid.bak"
            prd_path.write_text(VALID_PRD, encoding="utf-8")

            with patch.object(ralph, "PRD_FILE", prd_path), patch.object(ralph, "PRD_BACKUP_FILE", backup_path):
                snapshot = ralph._snapshot_current_prd()

            self.assertEqual(snapshot, VALID_PRD)
            self.assertEqual(backup_path.read_text(encoding="utf-8"), VALID_PRD)

    def test_snapshot_auto_repairs_invalid_prd_from_backup(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            prd_path = Path(tmpdir) / "prd.json"
            backup_path = Path(tmpdir) / ".prd.json.last-valid.bak"
            prd_path.write_text('{"project":"broken"\n"userStories":[]}', encoding="utf-8")
            backup_path.write_text(VALID_PRD, encoding="utf-8")

            with patch.object(ralph, "PRD_FILE", prd_path), patch.object(ralph, "PRD_BACKUP_FILE", backup_path):
                snapshot = ralph._snapshot_current_prd()

            self.assertEqual(snapshot, VALID_PRD)
            self.assertEqual(prd_path.read_text(encoding="utf-8"), VALID_PRD)

    def test_integrity_check_restores_invalid_output(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            prd_path = Path(tmpdir) / "prd.json"
            backup_path = Path(tmpdir) / ".prd.json.last-valid.bak"
            prd_path.write_text('{"project":"broken"\n"userStories":[]}', encoding="utf-8")

            with patch.object(ralph, "PRD_FILE", prd_path), patch.object(ralph, "PRD_BACKUP_FILE", backup_path):
                restored = ralph._ensure_prd_integrity_after_agent("开发 Agent", VALID_PRD)

            self.assertFalse(restored)
            self.assertEqual(prd_path.read_text(encoding="utf-8"), VALID_PRD)
            self.assertEqual(ralph._load_prd(prd_path)["project"], "SkillHub")


if __name__ == "__main__":
    unittest.main()
