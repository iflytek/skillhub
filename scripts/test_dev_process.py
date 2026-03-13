import subprocess
import tempfile
import time
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parent.parent
SCRIPT_PATH = REPO_ROOT / "scripts" / "dev_process.py"


class DevProcessTest(unittest.TestCase):
    def run_dev_process(self, *args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["python3", str(SCRIPT_PATH), *args],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
            check=False,
        )

    def test_status_and_stop_follow_process_group_after_leader_exits(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            pid_file = Path(temp_dir) / "web.pid"
            log_file = Path(temp_dir) / "web.log"

            start_result = self.run_dev_process(
                "start",
                "--pid-file",
                str(pid_file),
                "--log-file",
                str(log_file),
                "--cwd",
                str(REPO_ROOT),
                "--",
                "python3",
                "-c",
                (
                    "import subprocess, time; "
                    "subprocess.Popen(['/bin/sh', '-lc', 'sleep 30']); "
                    "time.sleep(1)"
                ),
            )
            self.assertEqual(start_result.returncode, 0, start_result.stderr)
            self.assertTrue(pid_file.exists())

            time.sleep(2)

            status_result = self.run_dev_process("status", "--pid-file", str(pid_file))
            self.assertEqual(status_result.returncode, 0, status_result.stderr)
            self.assertEqual(status_result.stdout.strip(), pid_file.read_text().strip())

            stop_result = self.run_dev_process("stop", "--pid-file", str(pid_file), "--timeout", "1")
            self.assertEqual(stop_result.returncode, 0, stop_result.stderr)
            self.assertFalse(pid_file.exists())

            final_status = self.run_dev_process("status", "--pid-file", str(pid_file))
            self.assertNotEqual(final_status.returncode, 0)


if __name__ == "__main__":
    unittest.main()
