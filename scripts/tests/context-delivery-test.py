#!/usr/bin/env python3
"""Negative delivery cases use real JSONL shapes, without models or private libraries."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
from context_pages import MAX_OUTPUT_BYTES, coverage, digest, page, transcript_outputs

spec = importlib.util.spec_from_file_location('context_reader', SCRIPTS / 'read-context.py')
reader = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reader)


class DeliveryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.text = '# Rule\n' + '中文\\"规则与例外\n' * 1000

    def pages(self):
        result = []; offset = 0
        while True:
            item = page(self.text, offset, expected=digest(self.text))
            result.append(item)
            offset = item['chunk']['nextOffset']
            if offset is None:
                return result

    def transcript(self, values):
        path = self.root / 'session.jsonl'
        path.write_text(''.join(json.dumps(value, ensure_ascii=False) + '\n' for value in values))
        return path

    def output(self, value):
        return {'type': 'response_item', 'payload': {'type': 'custom_tool_call_output', 'output': value}}

    def test_pages_reconstruct_with_unicode_and_escaping_within_byte_budget(self):
        items = self.pages()
        self.assertEqual(self.text, ''.join(item['text'] for item in items))
        for item in items:
            self.assertLessEqual(len(json.dumps(item, ensure_ascii=False).encode()), MAX_OUTPUT_BYTES)
        outputs = [(i, json.dumps(v, ensure_ascii=False)) for i, v in enumerate(items)]
        self.assertTrue(coverage(self.text, outputs)['complete'])

    def test_oversize_invalid_offset_or_unpinned_continuation_rejected(self):
        for offset, limit, expected in [(0, 18000, None), (-1, 2, None), (1, 2, None),
                                         (0, 0, None), (0, 10, 'stale')]:
            with self.subTest(offset=offset, limit=limit), self.assertRaises(ValueError):
                page(self.text, offset, limit, expected)

    def test_missing_middle_last_only_and_truncated_page_cannot_pass(self):
        pages = self.pages()
        for selected in [pages[:1] + pages[2:], pages[-1:], []]:
            self.assertFalse(coverage(self.text, [(i, json.dumps(p)) for i, p in enumerate(selected)])['complete'])
        self.assertFalse(coverage(self.text, [(1, json.dumps(pages[0])[:-20])])['complete'])

    def test_tampered_text_and_wrong_page_digest_cannot_pass(self):
        for field in ['text', 'pageSha256']:
            pages = self.pages()
            pages[0][field] = 'changed'
            self.assertFalse(coverage(self.text, [(i, json.dumps(p)) for i, p in enumerate(pages)])['complete'])

    def test_source_change_invalidates_old_delivery(self):
        outputs = [(i, json.dumps(p)) for i, p in enumerate(self.pages())]
        self.assertFalse(coverage(self.text + 'new rule', outputs)['complete'])

    def test_actual_nested_tool_outputs_not_user_or_assistant_claims(self):
        values = [self.output([{'type': 'input_text', 'text': 'Script completed\n'},
                              {'type': 'input_text', 'text': json.dumps({'output': json.dumps(p)})}])
                  for p in self.pages()]
        path = self.transcript(values)
        _, outputs = transcript_outputs(path)
        self.assertTrue(coverage(self.text, outputs)['complete'])
        for role in ['user', 'assistant']:
            path = self.transcript([{'type': 'response_item', 'payload': {'type': 'message',
                                     'role': role, 'content': [{'text': self.text}]}}])
            self.assertFalse(coverage(self.text, transcript_outputs(path)[1])['complete'])

    def test_compaction_and_explicit_cutoff_require_new_delivery(self):
        path = self.transcript([self.output(self.text), {'type': 'compacted', 'payload': {}},
                                self.output('summary only')])
        epoch, outputs = transcript_outputs(path)
        self.assertEqual(2, epoch)
        self.assertFalse(coverage(self.text, outputs)['complete'])
        path = self.transcript([self.output(self.text), self.output('summary only')])
        self.assertFalse(coverage(self.text, transcript_outputs(path, 1)[1])['complete'])

    def test_empty_text_requires_an_observed_empty_page(self):
        self.assertFalse(coverage('', [])['complete'])
        self.assertTrue(coverage('', [(1, json.dumps(page('')))])['complete'])

    def test_manifest_checks_every_source_current_content_and_missing_files(self):
        (self.root / 'one.md').write_text('first rule')
        (self.root / 'two.md').write_text('second rule')
        manifest = self.root / 'sources.json'
        manifest.write_text(json.dumps([{'file': 'one.md'}, {'file': 'two.md'}]))
        path = self.transcript([self.output('first rule')])
        with patch.object(reader, 'ROOT', self.root):
            self.assertFalse(reader.audit(manifest, path)['ok'])
            path = self.transcript([self.output('first rule'), self.output('second rule')])
            self.assertTrue(reader.audit(manifest, path)['ok'])
            (self.root / 'two.md').write_text('changed rule')
            self.assertFalse(reader.audit(manifest, path)['ok'])
            (self.root / 'two.md').unlink()
            self.assertFalse(reader.audit(manifest, path)['ok'])

    def test_empty_source_list_and_outside_checkout_fail(self):
        manifest = self.root / 'sources.json'
        manifest.write_text('[]')
        with self.assertRaises(ValueError):
            reader.audit(manifest, self.root / 'unused.jsonl')
        with patch.object(reader, 'ROOT', self.root), self.assertRaises(ValueError):
            reader.document('../outside.md')

    def test_required_context_expands_missing_peer_and_cycles_are_deduplicated(self):
        first = '# First\n## 必需上下文\n- [Peer](two.md)\n## Optional\n[Ignored](absent.md)\n'
        second = '# Second\n## 必需上下文\n- [First](one.md)\n'
        (self.root / 'one.md').write_text(first)
        (self.root / 'two.md').write_text(second)
        manifest = self.root / 'sources.json'
        manifest.write_text(json.dumps([{'file': 'one.md'}]))
        with patch.object(reader, 'ROOT', self.root):
            result = reader.audit(manifest, self.transcript([self.output(first)]))
            self.assertFalse(result['ok'])
            self.assertEqual(2, len(result['sources']))
            result = reader.audit(manifest, self.transcript([self.output(first), self.output(second)]))
            self.assertTrue(result['ok'])
            self.assertEqual(2, len(result['sources']))

    def test_required_context_refuses_external_fragment_and_escape(self):
        with patch.object(reader, 'ROOT', self.root):
            for target in ['https://example.test/rule.md', 'other.md#part', '../outside.md']:
                with self.subTest(target=target), self.assertRaises(ValueError):
                    reader.required_documents('one.md', f'## 必需上下文\n- [Rule]({target})\n')

    def test_required_design_query_is_audited_even_when_not_in_callers_list(self):
        body = '# Rule\n## 必需上下文\n```json\n[{"design":{"command":"spec","names":["accessibility"]}}]\n```\n'
        (self.root / 'one.md').write_text(body)
        with patch.object(reader, 'ROOT', self.root), patch.object(reader, 'design_payload', return_value='design body'):
            result = reader.audit([{'file': 'one.md'}], self.transcript([self.output(body)]))
            self.assertFalse(result['ok'])
            self.assertEqual(2, len(result['sources']))
            result = reader.audit([{'file': 'one.md'}], self.transcript([self.output(body), self.output('design body')]))
            self.assertTrue(result['ok'])

    def test_malformed_required_query_does_not_skip_validation(self):
        (self.root / 'one.md').write_text('## 必需上下文\n```json\n{"invalid":true}\n```\n')
        with patch.object(reader, 'ROOT', self.root):
            result = reader.audit([{'file': 'one.md'}], self.transcript([]))
            self.assertFalse(result['ok'])


if __name__ == '__main__':
    unittest.main()
