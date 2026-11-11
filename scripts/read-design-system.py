#!/usr/bin/env python3
"""Read selected design-library themes, complete component contracts or specs."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import shutil
from datetime import datetime, timezone

from design_tokens import css_modes, differences, project, resolved, source_hash
from context_pages import DEFAULT_LIMIT, page

SCRIPT_DIR = Path(__file__).resolve().parent
ROOT = SCRIPT_DIR.parent.parent if SCRIPT_DIR.parent.name == '.trellis' else SCRIPT_DIR.parent
DEFAULT_LIBRARY = ROOT / 'design-system/skillhub'
PRIVATE_LIBRARY = ROOT / '.design_library/skillhub'
REGISTRY = Path.home() / '.local/share/skillhub/design-library.json'


def library_files(library):
    files = {}
    for path in sorted(library.rglob('*')):
        if path.is_symlink():
            raise ValueError('Design snapshot contains a symlink; preserve and review it')
        if path.is_file():
            files[str(path.relative_to(library))] = source_hash(path.read_bytes())
    return files


def resolve_library(explicit=None):
    if explicit is not None:
        return explicit.resolve()
    if DEFAULT_LIBRARY.exists():
        return DEFAULT_LIBRARY.resolve()
    if PRIVATE_LIBRARY.exists():
        return PRIVATE_LIBRARY.resolve()
    if REGISTRY.exists():
        entry = load_json(REGISTRY)
        library = Path(entry['path']).resolve()
        allowed = (REGISTRY.parent / 'design-libraries').resolve()
        if library.parent != allowed or library.name != entry.get('fingerprint'):
            raise ValueError('Registered snapshot is outside the managed private cache')
        if not library.is_dir() or library_files(library) != entry['files']:
            raise ValueError('Registered design snapshot is unavailable or changed; restore the reviewed source snapshot')
        return library
    return DEFAULT_LIBRARY.resolve()


def register_library(library):
    projection(library)
    files = library_files(library)
    fingerprint = source_hash(json.dumps(files, sort_keys=True).encode())
    target = REGISTRY.parent / 'design-libraries' / fingerprint
    if target.is_symlink():
        raise ValueError('Refusing symlink snapshot directory')
    if target.exists():
        if library_files(target) != files:
            raise ValueError('Existing registered snapshot has changed')
    else:
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(library, target)
    if REGISTRY.is_symlink():
        raise ValueError('Refusing symlink design registry')
    REGISTRY.write_text(json.dumps({'path': str(target), 'files': files, 'fingerprint': fingerprint}, indent=2) + '\n')
    return {'registered': True, 'fingerprint': fingerprint, 'path': str(target),
            'scope': 'Private local snapshot; never copied into Git or another worktree'}


def load_json(path):
    def unique_keys(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f'{path.name}: duplicate JSON key {key}')
            result[key] = value
        return result
    return json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=unique_keys)


def source(library, name):
    path = (library / name).resolve()
    if not path.is_relative_to(library):
        raise ValueError(f'path escapes design library: {name}')
    data = path.read_bytes()
    return {'file': name, 'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data)}


def component_index(library):
    index = load_json(library / 'components/index.json')
    components = index.get('components')
    if not isinstance(components, list) or not components:
        raise ValueError('components/index.json: components must be a nonempty array')
    result = {}
    for item in components:
        if not isinstance(item, dict) or not isinstance(item.get('slug'), str):
            raise ValueError('component entry must have a string slug')
        slug = item['slug']
        if slug in result:
            raise ValueError(f'duplicate component slug: {slug}')
        contract = item.get('contractData')
        if not isinstance(contract, dict) or contract.get('slug') != slug:
            raise ValueError(f'{slug}: missing or mismatched contractData')
        result[slug] = item
    return result


def document_name(name):
    if not re.fullmatch(r'[a-z0-9]+(?:-[a-z0-9]+)*(?:\.md)?', name):
        raise ValueError(f'invalid spec name: {name}')
    return name if name.endswith('.md') else name + '.md'


def projection(library, refresh=False):
    source(library, 'colors_and_type.css')
    source(library, 'tokens.full.json')
    data = (library / 'colors_and_type.css').read_bytes()
    expected = project(data.decode('utf-8'))
    current = load_json(library / 'tokens.full.json')
    actual = {key: value for key, value in current.items() if key != '_meta'}
    errors = differences(actual, expected)
    if current.get('_meta', {}).get('sourceSha256') != source_hash(data):
        errors.append('tokens._meta.sourceSha256: stale or missing CSS source hash')
    if refresh:
        target = library / 'tokens.full.json'
        if target.is_symlink():
            raise ValueError('Refusing to overwrite symlink projection')
        if errors:
            metadata = {**current.get('_meta', {}), 'source': 'colors_and_type.css',
                        'sourceSha256': source_hash(data),
                        'generatedAt': datetime.now(timezone.utc).date().isoformat(),
                        'regenerate': 'python3 scripts/read-design-system.py refresh'}
            temporary = library / 'tokens.full.json.tmp'
            if temporary.exists() or temporary.is_symlink():
                raise ValueError('Projection temporary file already exists; preserve and inspect it')
            temporary.write_text(json.dumps({'_meta': metadata, **expected}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
            temporary.replace(target)
        return {'updated': bool(errors), 'changes': errors, 'sourceSha256': source_hash(data)}
    if errors:
        raise ValueError('Theme projection differs from CSS; refresh the reviewed library projection:\n' + '\n'.join(errors[:16]))
    return current


def read(library, args):
    if not library.is_dir():
        raise ValueError('Design library not loaded. Place the selected library at '
                         'design-system/skillhub/ or supply --library PATH. '
                         'No design constraints were loaded.')
    if args.command == 'refresh':
        return projection(library, refresh=True)
    if args.command == 'register':
        return register_library(library)
    consumption = load_json(library / 'library-consumption.json')
    identity = {'version': consumption.get('version'),
                'workingRevision': consumption.get('workingRevision')}
    names = ['library-consumption.json']
    if args.command == 'theme':
        tokens = projection(library)
        sections = args.sections or [key for key in tokens if key != '_meta']
        unknown = set(sections) - set(tokens)
        if unknown:
            raise ValueError('unknown theme section: ' + ', '.join(sorted(unknown)))
        names += ['colors_and_type.css', 'tokens.full.json', 'specs/themes.md']
        result = {'tokens': {key: tokens[key] for key in sections},
                  'rules': 'Read specs/themes.md for usage; CSS is the token source. '
                           'JSON projections do not prove product implementation.'}
    elif args.command == 'component':
        index = component_index(library)
        projection(library)
        light, dark = css_modes((library / 'colors_and_type.css').read_text(encoding='utf-8'))
        selected = {}
        for slug in args.slugs:
            if slug not in index:
                raise ValueError(f'unknown component: {slug}; available: ' + ', '.join(index))
            item = index[slug]
            names.append(item['preview'])
            contract = item['contractData']
            variables = set(contract.get('tokensConsumed', [])) | set(re.findall(r'var\((--[\w-]+)', json.dumps(contract)))
            values = {name: {'light': resolved(name, light), 'dark': resolved(name, dark)}
                      for name in sorted(variables)}
            selected[slug] = {'preview': item['preview'], 'contractData': contract, 'resolvedTokens': values}
        names += ['components/index.json', 'colors_and_type.css', 'components.runtime.css']
        result = {'components': selected,
                  'rules': 'contractData is complete, not a summary. Read the actual React '
                           'component and callers before using its API.'}
    elif args.command == 'spec':
        documents = {}
        for name in args.names:
            relative = 'specs/' + document_name(name)
            names.append(relative)
            source(library, relative)  # Check containment before reading.
            documents[relative] = (library / relative).read_text(encoding='utf-8')
        result = {'specs': documents}
    else:
        index = component_index(library)
        tokens = projection(library)
        for section in ['color', 'typography', 'spacing', 'radius', 'size', 'shadow']:
            if not isinstance(tokens.get(section), dict) or not tokens[section]:
                raise ValueError(f'tokens.full.json: missing or empty {section}')
        for group, entries in tokens['color'].items():
            for token, values in entries.items():
                if not isinstance(values, dict) or not values.get('light') or not values.get('dark'):
                    raise ValueError(f'{group}/{token}: missing light/dark values')
        names += ['tokens.full.json', 'colors_and_type.css', 'components/index.json',
                  'specs/themes.md', 'specs/task-composition.md',
                  'specs/component-state-matrix.md', 'specs/accessibility.md']
        for item in index.values():
            source(library, item['preview'])
        for path in library.rglob('*.json'):
            load_json(path)
        result = {'ok': True, 'components': len(index),
                  'specs': sorted(path.name for path in (library / 'specs').glob('*.md')),
                  'scope': 'Library JSON, light/dark token pairs and selected source paths. '
                           'Not Trae compatibility, Figma, visual or product acceptance.'}
    return {'library': identity,
            'sources': [source(library, name) for name in dict.fromkeys(names)], **result}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--library', type=Path, help='Explicit library; otherwise repository core, project-local, then registered private snapshot')
    parser.add_argument('--offset', type=int, default=0, help='Character offset in the stable full JSON payload')
    parser.add_argument('--limit', type=int, default=DEFAULT_LIMIT, help='Page size, default 1800, maximum 2400 characters; UTF-8 budget may reduce it')
    parser.add_argument('--expect-sha256', help='Required payload identity for continuation pages')
    commands = parser.add_subparsers(dest='command', required=True)
    theme = commands.add_parser('theme', help='Read complete theme sections, including light/dark values')
    theme.add_argument('sections', nargs='*', help='For example: color typography size radius')
    component = commands.add_parser('component', help='Read complete contracts by slug')
    component.add_argument('slugs', nargs='+')
    spec = commands.add_parser('spec', help='Read selected specs by filename')
    spec.add_argument('names', nargs='+')
    commands.add_parser('check', help='Validate library structure and CSS projection without writing files')
    commands.add_parser('register', help='Store a reviewed private snapshot for this user; no public distribution')
    commands.add_parser('refresh', help='Regenerate tokens.full.json from reviewed CSS; writes only that projection')
    args = parser.parse_args()
    try:
        if args.offset < 0 or args.limit is not None and args.limit < 1:
            raise ValueError('offset must be nonnegative and limit must be positive')
        if args.command in ['refresh', 'register', 'check'] and (args.limit != DEFAULT_LIMIT or args.offset or args.expect_sha256):
            raise ValueError('check/refresh/register cannot be chunked')
        if args.command in ['refresh', 'register'] and args.library is None and not DEFAULT_LIBRARY.is_dir():
            raise ValueError('refresh/register requires a project-local or explicit source library')
        output = read(resolve_library(args.library), args)
    except (OSError, ValueError, KeyError, TypeError, AttributeError) as error:
        print(f'Design system: {error}', file=sys.stderr)
        return 1
    payload = json.dumps(output, ensure_ascii=False, indent=2)
    if args.command in ['theme', 'component', 'spec']:
        try:
            payload = json.dumps(page(payload, args.offset, args.limit, args.expect_sha256), ensure_ascii=False)
        except ValueError as error:
            print(f'Design system: {error}', file=sys.stderr)
            return 1
    print(payload)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
