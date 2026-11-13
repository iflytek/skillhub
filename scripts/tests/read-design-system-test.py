#!/usr/bin/env python3
"""Exercise the read-only design-library CLI with independent fixture inputs."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import importlib.util
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / 'read-design-system.py'
spec = importlib.util.spec_from_file_location('design_tokens', SCRIPT.with_name('design_tokens.py'))
projection = importlib.util.module_from_spec(spec)
spec.loader.exec_module(projection)
CSS = '''
:root {
 --sh-bg-page: #ffffff;
 --sh-primary-base: #212121;
 --sh-interaction-selected-bg: var(--sh-primary-base);
 --sh-font-sans: 'Fixture', sans-serif;
 --sh-text-sm: 13px; --sh-text-sm-lh: 18px;
 --sh-weight-medium: 500; --sh-tracking-heading: -0.02em;
 --sh-space-1: 4px; --sh-radius-xs: 6px;
 --sh-size-control-md: 32px; --sh-size-control-xs: 24px;
 --sh-control-md-height: 32px;
 --sh-duration-fast: 150ms; --sh-ease-standard: ease-out;
 --sh-z-dialog: 50; --sh-overlay-scrim-alpha: 0.40;
 --shadow-card: 0 1px 2px rgb(0 0 0 / 0.1), 0 2px 4px rgb(0 0 0 / 0.2);
}
.dark { --sh-bg-page: #101010; --sh-primary-base: #eeeeee; }
'''


class DesignReaderTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.library = Path(self.temp.name) / 'library'
        self.library.mkdir()
        self.contract = {'slug': 'button', 'unknowns': ['fixture limitation'],
                         'customNestedField': {'preserve': ['every', 'field']}}
        self.index = {'components': [{'slug': 'button', 'preview': 'preview/component-button.html',
                                     'contractData': self.contract}]}
        self.tokens = {'_meta': {'sourceSha256': hashlib.sha256(CSS.encode()).hexdigest()}, **projection.project(CSS)}
        self.write('library-consumption.json', {'version': 'fixture'})
        self.write('components/index.json', self.index)
        self.write('tokens.full.json', self.tokens)
        for name in ['colors_and_type.css', 'components.runtime.css', 'preview/component-button.html',
                     'specs/themes.md', 'specs/task-composition.md',
                     'specs/component-state-matrix.md', 'specs/accessibility.md']:
            path = self.library / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(CSS if name == 'colors_and_type.css' else 'fixture content\n', encoding='utf-8')

    def write(self, name, data):
        path = self.library / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data), encoding='utf-8')

    def raw_cli(self, *args):
        return subprocess.run([sys.executable, str(SCRIPT), '--library', str(self.library), *args],
                              text=True, capture_output=True)

    def run_cli(self, *args):
        result = self.raw_cli(*args)
        if result.returncode or '--limit' in args or args[0] in ('check', 'refresh', 'register'):
            return result
        value = json.loads(result.stdout)
        parts = [value['text']]
        while value['chunk']['nextOffset'] is not None:
            value = json.loads(self.raw_cli('--offset', str(value['chunk']['nextOffset']),
                               '--expect-sha256', value['chunk']['payloadSha256'], *args).stdout)
            parts.append(value['text'])
        result.stdout = ''.join(parts) + '\n'
        return result

    def test_complete_contract_and_source_identity_without_writes(self):
        before = {str(p): hashlib.sha256(p.read_bytes()).hexdigest()
                  for p in self.library.rglob('*') if p.is_file()}
        result = self.run_cli('component', 'button')
        self.assertEqual(result.returncode, 0, result.stderr)
        output = json.loads(result.stdout)
        self.assertEqual(output['components']['button']['contractData'], self.contract)
        for entry in output['sources']:
            self.assertEqual(entry['sha256'], before[str(self.library / entry['file'])])
        after = {str(p): hashlib.sha256(p.read_bytes()).hexdigest()
                 for p in self.library.rglob('*') if p.is_file()}
        self.assertEqual(before, after)

    def test_theme_keeps_both_modes_and_requested_sections(self):
        result = self.run_cli('theme', 'color')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout)['tokens'], {'color': self.tokens['color']})

    def test_selected_spec_preserves_full_body(self):
        text = '# Theme\n\n规则与例外\n'
        (self.library / 'specs/themes.md').write_text(text, encoding='utf-8')
        result = self.run_cli('spec', 'themes')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout)['specs'], {'specs/themes.md': text})

    def test_check_accepts_complete_fixture_and_rejects_missing_dark(self):
        result = self.run_cli('check')
        self.assertEqual(result.returncode, 0, result.stderr)
        del self.tokens['color']['bg']['sh-bg-page']['dark']
        self.write('tokens.full.json', self.tokens)
        result = self.run_cli('check')
        self.assertEqual(result.returncode, 1)
        self.assertIn('dark', result.stderr)
        self.assertEqual(result.stdout, '')

    def test_unknown_component_and_theme_section_do_not_emit_partial_success(self):
        for args in [('component', 'button', 'missing'), ('theme', 'missing')]:
            with self.subTest(args=args):
                result = self.run_cli(*args)
                self.assertEqual(result.returncode, 1)
                self.assertIn('unknown', result.stderr)
                self.assertEqual(result.stdout, '')

    def test_missing_library_reports_no_constraints_loaded(self):
        self.library = self.library / 'missing'
        result = self.run_cli('check')
        self.assertEqual(result.returncode, 1)
        self.assertIn('No design constraints were loaded', result.stderr)

    def test_duplicate_or_mismatched_contract_is_rejected(self):
        self.index['components'].append(self.index['components'][0])
        self.write('components/index.json', self.index)
        result = self.run_cli('component', 'button')
        self.assertEqual(result.returncode, 1)
        self.assertIn('duplicate component slug', result.stderr)
        self.index['components'] = [{'slug': 'button', 'contractData': {'slug': 'different'}}]
        self.write('components/index.json', self.index)
        result = self.run_cli('check')
        self.assertEqual(result.returncode, 1)
        self.assertIn('mismatched contractData', result.stderr)

    def test_invalid_json_and_paths_are_rejected(self):
        (self.library / 'tokens.full.json').write_text('{invalid', encoding='utf-8')
        result = self.run_cli('theme')
        self.assertEqual(result.returncode, 1)
        result = self.run_cli('spec', '../outside')
        self.assertEqual(result.returncode, 1)
        self.assertIn('invalid spec name', result.stderr)


    def test_drift_is_rejected_for_check_theme_and_component(self):
        self.tokens['size']['control-md'] = '999px'
        self.write('tokens.full.json', self.tokens)
        for args in [('check',), ('theme', 'size'), ('component', 'button')]:
            result = self.run_cli(*args)
            self.assertEqual(1, result.returncode)
            self.assertEqual('', result.stdout)
            self.assertIn('999px', result.stderr)

    def test_refresh_rebuilds_missing_groups_and_is_idempotent(self):
        self.tokens['size']['control-md'] = '28px'
        del self.tokens['control']
        self.write('tokens.full.json', self.tokens)
        result = self.run_cli('refresh')
        self.assertEqual(0, result.returncode, result.stderr)
        data = json.loads((self.library / 'tokens.full.json').read_text())
        self.assertEqual('32px', data['size']['control-md'])
        self.assertEqual('24px', data['size']['control-xs'])
        self.assertEqual('32px', data['control']['md']['height'])
        self.assertEqual('#eeeeee', data['color']['interaction']['sh-interaction-selected-bg']['dark'])
        self.assertIn(', 0 2px', data['shadow']['card']['light'])
        before = (self.library / 'tokens.full.json').read_bytes()
        self.assertFalse(json.loads(self.run_cli('refresh').stdout)['updated'])
        self.assertEqual(before, (self.library / 'tokens.full.json').read_bytes())

    def test_source_changes_missing_tokens_and_cycles_are_rejected(self):
        for css in [CSS.replace('32px', '33px'), CSS.replace('--sh-primary-base: #212121;', ''),
                    CSS.replace('--sh-primary-base: #212121;', '--sh-primary-base: var(--sh-primary-base);')]:
            (self.library / 'colors_and_type.css').write_text(css)
            result = self.run_cli('check')
            self.assertEqual(1, result.returncode)
            self.assertEqual('', result.stdout)

    def test_complete_contract_includes_resolved_source_values(self):
        self.contract['tokensConsumed'] = ['--sh-size-control-md', '--sh-interaction-selected-bg']
        self.index['components'][0]['contractData'] = self.contract
        self.write('components/index.json', self.index)
        data = json.loads(self.run_cli('component', 'button').stdout)['components']['button']
        self.assertEqual({'light': '32px', 'dark': '32px'}, data['resolvedTokens']['--sh-size-control-md'])
        self.assertEqual('#eeeeee', data['resolvedTokens']['--sh-interaction-selected-bg']['dark'])

    def test_chunks_reconstruct_the_same_full_payload(self):
        expected = self.run_cli('component', 'button').stdout.rstrip('\n')
        pieces = []; offset = 0; hashes = set()
        while True:
            result = self.run_cli('--limit', '300', '--offset', str(offset), '--expect-sha256', hashlib.sha256(expected.encode()).hexdigest(), 'component', 'button')
            self.assertEqual(0, result.returncode, result.stderr)
            item = json.loads(result.stdout); pieces.append(item['text']); hashes.add(item['chunk']['payloadSha256'])
            offset = item['chunk']['nextOffset']
            if offset is None: break
        self.assertEqual(expected, ''.join(pieces))
        self.assertEqual({hashlib.sha256(expected.encode()).hexdigest()}, hashes)


    def test_default_is_bounded_and_oversize_or_unpinned_continuation_fails(self):
        result = self.raw_cli('component', 'button')
        data = json.loads(result.stdout)
        self.assertIn('pageSha256', data)
        self.assertLessEqual(len(result.stdout.encode()), 6501)
        for args in [('--limit', '18000', 'component', 'button'),
                     ('--offset', '1', 'component', 'button'),
                     ('--expect-sha256', 'wrong', 'component', 'button')]:
            result = self.raw_cli(*args)
            self.assertEqual(1, result.returncode)
            self.assertEqual('', result.stdout)

    def reader_module(self):
        sys.path.insert(0, str(SCRIPT.parent))
        try:
            spec = importlib.util.spec_from_file_location('design_reader_test', SCRIPT)
            module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
            return module
        finally:
            sys.path.pop(0)

    def test_registered_snapshot_survives_source_removal_and_detects_tampering(self):
        reader = self.reader_module()
        registry = Path(self.temp.name) / 'private/design-library.json'
        with patch.object(reader, 'REGISTRY', registry), patch.object(reader, 'DEFAULT_LIBRARY', Path(self.temp.name) / 'new-worktree/.design_library/skillhub'):
            result = reader.register_library(self.library)
            snapshot = Path(result['path'])
            self.assertEqual(snapshot, reader.resolve_library())
            self.assertNotEqual(self.library, snapshot)
            (self.library / 'tokens.full.json').unlink()
            self.assertEqual(snapshot, reader.resolve_library())
            (snapshot / 'specs/themes.md').write_text('unreviewed mutation')
            with self.assertRaisesRegex(ValueError, 'changed'):
                reader.resolve_library()

    def test_explicit_missing_library_does_not_silently_use_registered_source(self):
        reader = self.reader_module()
        registry = Path(self.temp.name) / 'private/design-library.json'
        with patch.object(reader, 'REGISTRY', registry):
            reader.register_library(self.library)
            explicit = Path(self.temp.name) / 'missing-library'
            self.assertEqual(explicit.resolve(), reader.resolve_library(explicit))

    def test_missing_projection_field_is_rejected_until_refreshed(self):
        del self.tokens['control']['md']['height']
        self.write('tokens.full.json', self.tokens)
        result = self.run_cli('check')
        self.assertEqual(1, result.returncode)
        self.assertIn('control.md.height: missing', result.stderr)

    def test_conditional_css_token_blocks_are_not_misread_as_global(self):
        (self.library / 'colors_and_type.css').write_text('@media (min-width: 100px) {' + CSS + '}')
        result = self.run_cli('refresh')
        self.assertEqual(1, result.returncode)
        self.assertIn('Conditional', result.stderr)


if __name__ == '__main__':
    unittest.main()
