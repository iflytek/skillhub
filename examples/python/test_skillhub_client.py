"""Offline regression tests: python -m unittest discover -s examples/python -p 'test_*.py'."""
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

import requests
from skillhub_client import SkillHubClient


class DownloadTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="skillhub 中文 ")
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.dest = self.root / "existing package.zip"
        self.session = MagicMock()
        self.response = self.session.get.return_value.__enter__.return_value
        self.client = SkillHubClient("https://example.invalid", session=self.session)

    def assert_no_temporary_files(self):
        self.assertEqual(list(self.root.glob(".skillhub-download-*")), [])

    def test_success_replaces_existing_file_after_stream_finishes(self):
        self.dest.write_bytes(b"original")

        def chunks(**kwargs):
            yield b"new"
            self.assertEqual(self.dest.read_bytes(), b"original")
            yield b""
            yield b" package"

        self.response.iter_content.side_effect = chunks
        result = self.client.download("global", "demo", "1.0.0", str(self.dest))
        self.assertEqual(result, str(self.dest))
        self.assertEqual(self.dest.read_bytes(), b"new package")
        self.assert_no_temporary_files()
        self.assertEqual(self.session.get.call_args.args[0],
                         "https://example.invalid/api/v1/skills/global/demo/versions/1.0.0/download")

    def test_interrupted_stream_preserves_existing_file(self):
        self.dest.write_bytes(b"original")

        def chunks(**kwargs):
            yield b"partial"
            raise requests.ConnectionError("interrupted")

        self.response.iter_content.side_effect = chunks
        with self.assertRaises(requests.ConnectionError):
            self.client.download("global", "demo", dest=str(self.dest))
        self.assertEqual(self.dest.read_bytes(), b"original")
        self.assert_no_temporary_files()

    def test_interrupted_stream_does_not_create_destination(self):
        self.response.iter_content.side_effect = requests.ConnectionError("interrupted")
        with self.assertRaises(requests.ConnectionError):
            self.client.download("global", "demo", dest=str(self.dest))
        self.assertFalse(self.dest.exists())
        self.assert_no_temporary_files()

    def test_http_error_preserves_existing_file(self):
        self.dest.write_bytes(b"original")
        self.response.raise_for_status.side_effect = requests.HTTPError("404")
        with self.assertRaises(requests.HTTPError):
            self.client.download("global", "demo", dest=str(self.dest))
        self.assertEqual(self.dest.read_bytes(), b"original")
        self.assert_no_temporary_files()

    def test_replace_failure_preserves_existing_file_and_cleans_stage(self):
        self.dest.write_bytes(b"original")
        self.response.iter_content.return_value = [b"new"]
        with patch("skillhub_client.os.replace", side_effect=PermissionError("locked")):
            with self.assertRaises(PermissionError):
                self.client.download("global", "demo", dest=str(self.dest))
        self.assertEqual(self.dest.read_bytes(), b"original")
        self.assert_no_temporary_files()

    def test_success_creates_new_file(self):
        self.response.iter_content.return_value = [b"zip bytes"]
        self.client.download("global", "demo", dest=str(self.dest))
        self.assertEqual(self.dest.read_bytes(), b"zip bytes")
        self.assert_no_temporary_files()


if __name__ == "__main__":
    unittest.main()
