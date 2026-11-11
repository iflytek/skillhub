#!/usr/bin/env python3
"""Read one project document per page or audit a source list against a Codex transcript.

No task, hook, trust or source writes. Audit observes delivery, not model comprehension.
"""
import argparse
import importlib.util
import json
from pathlib import Path
import re
import sys
from types import SimpleNamespace

from context_pages import DEFAULT_LIMIT, coverage, page, transcript_outputs

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent if HERE.parent.name == '.trellis' else HERE.parent


def document(relative):
    name = Path(relative)
    path = (ROOT / name).resolve()
    if name.is_absolute() or '..' in name.parts or not path.is_relative_to(ROOT):
        raise ValueError('Document must be a relative path inside this checkout')
    if path.suffix not in ('.md', '.json', '.jsonl', '.yaml', '.toml'):
        raise ValueError('Read a textual project document, not an asset or environment file')
    return path.read_text(encoding='utf-8')


def design_payload(query):
    if query.get('command') not in ('theme', 'component', 'spec'):
        raise ValueError('A design source must select theme, component or spec')
    names = query.get('names')
    if not isinstance(names, list) or not names or not all(isinstance(n, str) for n in names):
        raise ValueError('A design source requires a nonempty names array')
    spec = importlib.util.spec_from_file_location('design_reader', HERE / 'read-design-system.py')
    reader = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(reader)
    args = SimpleNamespace(command=query['command'], sections=names, slugs=names, names=names)
    value = reader.read(reader.resolve_library(), args)
    return json.dumps(value, ensure_ascii=False, indent=2)


def required_documents(relative, text):
    """Explicit local links and design queries, not the whole reference graph."""
    match = re.search(r'^## 必需上下文\s*\n(.*?)(?=^## |\Z)', text, re.M | re.S)
    if match is None:
        return []
    result = []
    for target in re.findall(r'\]\(([^)]+)\)', match[1]):
        if target.startswith(('http:', 'https:')) or '#' in target:
            raise ValueError('Required context must link a complete local document')
        path = (ROOT / relative).parent.joinpath(target).resolve()
        if not path.is_relative_to(ROOT):
            raise ValueError('Required context escapes this checkout')
        result.append({'file': str(path.relative_to(ROOT)), 'reason': f'Required by {relative}'})
    for block in re.findall(r'```json\s*\n(.*?)```', match[1], re.S):
        queries = json.loads(block)
        if not isinstance(queries, list) or not all(
                isinstance(item, dict) and set(item) == {'design'} for item in queries):
            raise ValueError('Required design context must be an array of design queries')
        result.extend(queries)
    return result


def audit(manifest, transcript, after_line=0):
    if isinstance(manifest, list):
        sources = manifest
    else:
        if not manifest.is_file():
            raise ValueError('Source list is missing')
        if manifest.suffix == '.jsonl':
            sources = [json.loads(line) for line in manifest.read_text(encoding='utf-8').splitlines() if line.strip()]
        else:
            sources = json.loads(manifest.read_text(encoding='utf-8'))
    if not isinstance(sources, list) or not sources:
        raise ValueError('Source list must be a nonempty array (or existing JSONL context list)')
    epoch, outputs = transcript_outputs(transcript, after_line)
    results = []
    queue = list(sources)
    seen = set()
    while queue:
        source = queue.pop(0)
        key = json.dumps(source.get('file', source.get('design')), sort_keys=True) if isinstance(source, dict) else None
        if key is not None and key in seen:
            continue
        seen.add(key)
        label = source.get('file', source.get('design')) if isinstance(source, dict) else None
        try:
            if not isinstance(source, dict) or ('file' in source) == ('design' in source):
                raise ValueError('Each source requires exactly one file or design query')
            payload = document(source['file']) if 'file' in source else design_payload(source['design'])
            if 'file' in source:
                queue.extend(required_documents(source['file'], payload))
            results.append({'source': label, **coverage(payload, outputs)})
        except (OSError, ValueError, KeyError, TypeError, AttributeError) as error:
            results.append({'source': label, 'complete': False, 'error': str(error)})
    return {'ok': all(item['complete'] for item in results), 'afterLine': epoch, 'sources': results,
            'boundary': 'Observed current bodies in persisted tool outputs after the latest compaction/cutoff. '
                        'Not proof of comprehension, compliance, host edit enforcement or future retention. '
                        'The source list is task-scoped and may omit applicable rules; review its selection.'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    read = commands.add_parser('file', help='Read a single document; follow returned continuation')
    read.add_argument('path')
    read.add_argument('--offset', type=int, default=0)
    read.add_argument('--limit', type=int, default=DEFAULT_LIMIT)
    read.add_argument('--expect-sha256')
    check = commands.add_parser('check', help='Observe delivery for an existing task source list')
    check.add_argument('manifest', type=Path, nargs='?')
    check.add_argument('--source', action='append', default=[], help='Inline file source for read-only tasks')
    check.add_argument('--design', action='append', default=[], help='Inline design query, e.g. "component button"')
    check.add_argument('--transcript', type=Path, help='Default: current CODEX_THREAD_ID log; never guesses another session')
    check.add_argument('--after-line', type=int, default=0, help='Additional phase/new-turn cutoff; latest compaction always applies')
    args = parser.parse_args()
    try:
        if args.command == 'file':
            body = document(args.path)
            result = page(body, args.offset, args.limit, args.expect_sha256)
        else:
            if args.after_line < 0:
                raise ValueError('after-line must be nonnegative')
            if args.manifest is not None and (args.source or args.design):
                raise ValueError('Choose a source list or inline sources, not both')
            selected = args.manifest
            if selected is None:
                selected = [{'file': name} for name in args.source]
                for query in args.design:
                    parts = query.split()
                    if len(parts) < 2:
                        raise ValueError('Design query requires a command and names')
                    selected.append({'design': {'command': parts[0], 'names': parts[1:]}})
            transcript = args.transcript
            if transcript is None:
                import os
                thread = os.environ.get('CODEX_THREAD_ID', '')
                if not re.fullmatch(r'[0-9a-f-]{36}', thread):
                    raise ValueError('Current session unavailable; supply --transcript explicitly')
                matches = list((Path.home() / '.codex/sessions').rglob('*' + thread + '.jsonl'))
                if len(matches) != 1:
                    raise ValueError('Current session log unavailable or ambiguous')
                transcript = matches[0]
            result = audit(selected, transcript, args.after_line)
        print(json.dumps(result, ensure_ascii=False))
        return 0 if result.get('ok', True) else 1
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(f'Context: {error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
