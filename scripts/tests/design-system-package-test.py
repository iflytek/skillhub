"""Verify the distributed design core without editor plugins or private resources."""
import importlib.util
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[2]
LIBRARY = ROOT / 'design-system/skillhub'


class PublishedDesignCoreTests(unittest.TestCase):
    def test_clean_export_uses_the_shipped_core_and_rejects_corruption(self):
        with tempfile.TemporaryDirectory() as temporary:
            export = Path(temporary)
            scripts = export / 'scripts'
            scripts.mkdir()
            for name in ['read-design-system.py', 'context_pages.py', 'design_tokens.py']:
                shutil.copy2(ROOT / 'scripts' / name, scripts / name)
            shutil.copytree(LIBRARY, export / 'design-system/skillhub')
            command = [sys.executable, str(scripts / 'read-design-system.py'), 'check']
            result = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(44, json.loads(result.stdout)['components'])
            # A broken shipped source must fail, even if this machine has a valid cache.
            projection = export / 'design-system/skillhub/tokens.full.json'
            data = json.loads(projection.read_text())
            data['control']['md']['height'] = '999px'
            projection.write_text(json.dumps(data))
            result = subprocess.run(command, capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('control.md.height', result.stderr)

    def reader(self):
        sys.path.insert(0, str(ROOT / 'scripts'))
        self.addCleanup(sys.path.pop, 0)
        spec = importlib.util.spec_from_file_location('published_design_reader', ROOT / 'scripts/read-design-system.py')
        reader = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(reader)
        return reader

    def test_repository_core_precedes_private_sources(self):
        reader = self.reader()
        with tempfile.TemporaryDirectory() as temporary:
            poison = Path(temporary) / 'registry.json'
            poison.write_text('not valid JSON')
            reader.REGISTRY = poison
            reader.PRIVATE_LIBRARY = Path(temporary) / 'unavailable-library'
            self.assertEqual(LIBRARY.resolve(), reader.resolve_library())
            selected = Path(temporary) / 'explicit-missing'
            self.assertEqual(selected.resolve(), reader.resolve_library(selected))

    def test_all_contracts_and_required_rules_are_readable(self):
        reader = self.reader()
        from types import SimpleNamespace
        components = json.loads((LIBRARY / 'components/index.json').read_text())['components']
        self.assertEqual(44, len(components))
        self.assertEqual(44, len({item['slug'] for item in components}))
        for item in components:
            with self.subTest(component=item['slug']):
                result = reader.read(LIBRARY, SimpleNamespace(command='component', slugs=[item['slug']]))
                self.assertEqual(item['contractData'], result['components'][item['slug']]['contractData'])
        for name in ['themes', 'task-composition', 'component-state-matrix', 'accessibility']:
            result = reader.read(LIBRARY, SimpleNamespace(command='spec', names=[name]))
            self.assertTrue(result['specs']['specs/' + name + '.md'].strip())

    def test_static_preview_dependencies_and_document_links_exist(self):
        for path in LIBRARY.rglob('*'):
            if not path.is_file() or path.suffix not in ['.html', '.md']:
                continue
            text = path.read_text()
            links = re.findall(r'\b(?:href|src)=["\']([^"\']+)["\']', text) if path.suffix == '.html' else re.findall(r'\]\(([^)]+)\)', text)
            for value in links:
                parsed = urlsplit(value)
                if parsed.scheme or parsed.netloc or not parsed.path:
                    continue
                target = (path.parent / unquote(parsed.path)).resolve()
                with self.subTest(file=str(path.relative_to(LIBRARY)), link=value):
                    self.assertTrue(target.is_relative_to(LIBRARY.resolve()), 'Link escapes the published core')
                    self.assertTrue(target.exists(), 'Missing distributed dependency')

    def test_license_and_distribution_scope(self):
        self.assertEqual('Apache-2.0', json.loads((LIBRARY / 'metadata.json').read_text())['license'])
        self.assertEqual((ROOT / 'LICENSE').read_bytes(), (LIBRARY / 'LICENSE').read_bytes())
        self.assertIn('Lucide', (LIBRARY / 'THIRD_PARTY_NOTICES.md').read_text())
        for name in ['prototypes', 'ui_kits', 'SKILL.md', 'CHANGELOG.md']:
            self.assertFalse((LIBRARY / name).exists())
        for path in LIBRARY.rglob('*'):
            self.assertFalse(path.is_symlink())
            if path.is_file():
                text = path.read_text()
                for private in ['figma-web-bridge', 'design-drafts/']:
                    self.assertFalse(private in text, f"Private reference in {path.relative_to(LIBRARY)}")
                self.assertIsNone(re.search(r"/home/[^/\s]+/|[A-Z]:\\Users\\[^\\\s]+", text), str(path.relative_to(LIBRARY)))


if __name__ == '__main__':
    unittest.main()
