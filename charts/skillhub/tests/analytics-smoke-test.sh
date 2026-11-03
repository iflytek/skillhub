#!/usr/bin/env bash
# Exercise the installed-release smoke without a cluster or third-party packages.
set -euo pipefail
TEST_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
python3 - "${ANALYTICS_SMOKE_SCRIPT:-$TEST_DIR/analytics-smoke.sh}" <<'PY'
import copy
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

script = Path(sys.argv[1]).resolve()
website = '00000000-0000-4000-8000-000000000037'
# JSON is valid Helm YAML. The caller file deliberately omits settings supplied
# at installation through other files/--set, especially private credentials.
original = {
    'secrets': {'bootstrapAdminPassword': 'synthetic-private-bootstrap',
                'downloadAnonCookieSecret': 'synthetic-private-cookie-secret'},
    'postgresql': {'auth': {'password': 'synthetic-private-postgres'}},
    'redis': {'auth': {'password': 'synthetic-private-redis'}},
    'server': {'replicaCount': 1, 'image': {'tag': 'installed-server-tag'}},
    'web': {'basePath': '/registry/', 'analytics': {
        'upstream': 'http://original-stats.internal:3000', 'config': '{}'}},
    'existingSecret': '',
}
caller = {'web': {'analytics': {'upstream': 'http://original-stats.internal:3000'}}}

# These fakes model Helm value layering and process state, not chart rendering.
# They fail on unexpected operations so this test cannot reach a real cluster.
fake = r'''#!/usr/bin/env python3
import copy, json, os, sys
from pathlib import Path
root = Path(os.environ['FAKE_STATE'])
name = Path(sys.argv[0]).name
args = sys.argv[1:]
scenario = os.environ['FAKE_SCENARIO']

def check(condition, message):
    if not condition:
        sys.exit(message)

def record(value):
    with (root / 'calls').open('a') as f:
        f.write(value + '\n')

def merge(base, values):
    for key, value in values.items():
        if isinstance(value, dict) and isinstance(base.get(key), dict):
            merge(base[key], value)
        else:
            base[key] = copy.deepcopy(value)

if name == 'helm':
    check(args[:2] == ['upgrade', os.environ['FAKE_RELEASE']], 'unexpected Helm operation')
    check(args[args.index('-n') + 1] == os.environ['FAKE_NAMESPACE'], 'wrong release namespace')
    check('--wait' in args and args[args.index('--timeout') + 1] == '5m', 'upgrade must wait')
    original = json.loads((root / 'original.json').read_text())
    effective = copy.deepcopy(original) if '--reuse-values' in args else {}
    files = [Path(args[i + 1]) for i, arg in enumerate(args) if arg == '-f']
    check(Path(os.environ['FAKE_CALLER']) in files, 'caller values were omitted')
    for path in files:
        # Reading the known repository fixture models the old bug as well.
        text = path.read_text()
        if text.startswith('{'):
            values = json.loads(text)
        else:
            values = {}
            stack = [(-1, values)]
            for line in text.splitlines():
                if not line.strip() or line.lstrip().startswith('#'):
                    continue
                indent = len(line) - len(line.lstrip())
                key, value = line.strip().split(':', 1)
                while stack[-1][0] >= indent:
                    stack.pop()
                parent = stack[-1][1]
                value = value.strip()
                parent[key] = value.strip("'\"") if value else {}
                if not value:
                    stack.append((indent, parent[key]))
        merge(effective, values)
    expected = copy.deepcopy(original)
    expected['web']['analytics']['config'] = effective.get('web', {}).get('analytics', {}).get('config')
    if scenario == 'generated-profile':
        check(effective.get('secrets') == original['secrets'],
              'generated secret profile must retain empty values for lookup')
    check(effective == expected, 'upgrade changed unrelated release values or secrets')
    check(all(p.name != 'test-values.yaml' for p in files), 'repository fixture values injected')
    config = json.loads(effective['web']['analytics']['config'])
    check(config == {'enabled': True, 'adapter': 'umami', 'consent': 'granted',
                     'rules': {}, 'adapterConfig': {'websiteId': os.environ['FAKE_WEBSITE']}},
          'wrong analytics upgrade')
    (root / 'upgraded').touch()
    record('upgrade: analytics only; installed values and secrets retained; no test fixture')
elif name == 'kubectl':
    check(args[0] == '-n', 'namespace must be explicit')
    namespace, command = args[1], args[2:]
    if command[:2] == ['get', 'pods']:
        check(namespace == os.environ['FAKE_NAMESPACE'], 'wrong pod namespace')
        selector = command[command.index('-l') + 1]
        component = selector.rsplit('=', 1)[-1]
        check(component in ('server', 'web'), 'unexpected component')
        upgraded = (root / 'upgraded').exists()
        changed = upgraded and ((component == 'web' and scenario != 'web-stable') or
                                (component == 'server' and scenario == 'server-roll'))
        uid = component + ('-after' if changed else '-before')
        record('uid: ' + uid)
        print(json.dumps({'items': [{'metadata': {'uid': uid}, 'status': {
            'conditions': [{'type': 'Ready', 'status': 'True'}]}}]}))
    elif command[:3] == ['exec', 'deployment/umami-postgres', '--']:
        check(namespace == 'stats', 'event query must use independent stats namespace')
        check(os.environ['FAKE_WEBSITE'] in command[-1] and "event_name='skill.card.open'" in command[-1],
              'wrong event query')
        print(11 if (root / 'sent').exists() and scenario != 'no-event' else 10)
        record('event count')
    elif command[:2] == ['rollout', 'status']:
        check(command[2] == 'deployment/' + os.environ['FAKE_RELEASE'] + '-skillhub-web', 'wrong rollout')
        check((root / 'upgraded').exists(), 'rollout before upgrade')
        record('rollout: web')
    else:
        sys.exit('unexpected kubectl operation')
elif name == 'curl':
    check(args[-1].startswith('http://127.0.0.1:18082/') or '--data' in args, 'unexpected Web URL')
    if '-X' in args:
        check(args[args.index('-X') + 1] == 'POST', 'expected event POST')
        payload = json.loads(args[args.index('--data') + 1])['payload']
        check(payload['website'] == os.environ['FAKE_WEBSITE'] and payload['name'] == 'skill.card.open',
              'wrong event payload')
        (root / 'sent').touch()
        record('HTTP: event POST')
    else:
        check(args[-1].endswith('/_skillhub-analytics/script.js'), 'wrong tracker path')
        record('HTTP: tracker GET')
    print('200', end='')
else:
    sys.exit('unexpected fake command')
'''

with tempfile.TemporaryDirectory(prefix='analytics-smoke-test.') as tmp:
    root = Path(tmp)
    bin_dir = root / 'bin'
    bin_dir.mkdir()
    shim = bin_dir / 'fake'
    shim.write_text(fake)
    shim.chmod(0o755)
    for name in ('helm', 'kubectl', 'curl'):
        (bin_dir / name).symlink_to(shim)
    caller_file = root / 'installed-overrides.json'
    caller_file.write_text(json.dumps(caller))
    for scenario, failure in (
        ('generated-profile', None),
        ('success', None),
        ('server-roll', 'Server pod rolled unexpectedly'),
        ('web-stable', 'Web pod did not roll'),
        ('no-event', 'HTTP 200 but event not stored'),
    ):
        state = root / scenario
        state.mkdir()
        installed = copy.deepcopy(original)
        if scenario == 'generated-profile':
            # Empty application-secret values preserve the chart's existing-Secret
            # lookup path. This value-layer model does not perform that lookup.
            installed['secrets'] = {'allowAutoGenerated': True,
                                    'bootstrapAdminPassword': '',
                                    'downloadAnonCookieSecret': ''}
        (state / 'original.json').write_text(json.dumps(installed))
        env = dict(os.environ, PATH=str(bin_dir) + os.pathsep + os.environ['PATH'],
                   FAKE_STATE=str(state), FAKE_SCENARIO=scenario,
                   FAKE_WEBSITE=website, FAKE_CALLER=str(caller_file),
                   FAKE_RELEASE='original-release', FAKE_NAMESPACE='original-namespace',
                   ANALYTICS_SMOKE_WEB_URL='http://127.0.0.1:18082', TMPDIR=str(state))
        result = subprocess.run(['bash', str(script), website, str(caller_file),
                                 env['FAKE_RELEASE'], env['FAKE_NAMESPACE']],
                                env=env, text=True, capture_output=True)
        if failure is None:
            assert result.returncode == 0, result.stdout + result.stderr
            calls = (state / 'calls').read_text().splitlines()
            assert calls.count('event count') == 2, calls
            for call in ('uid: server-before', 'uid: web-before', 'uid: web-after', 'rollout: web'):
                assert call in calls, call
            assert calls.count('uid: server-before') == 2, calls
            assert 'server UID unchanged' in result.stdout, result.stdout
        else:
            assert result.returncode != 0 and failure in result.stderr, result.stdout + result.stderr
        assert not list(state.glob('skillhub-analytics-smoke.*')), 'temporary override was not cleaned'
        print('PASS: ' + scenario)
print('Analytics smoke regression tests passed (fake Helm/kubectl/curl; no deployment).')
PY
