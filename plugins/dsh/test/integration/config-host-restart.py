"""Save plugin settings via UI, restart an isolated Host, verify in a fresh browser."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--root', required=True, type=Path)
parser.add_argument('--node', required=True, type=Path)
parser.add_argument('--chromium', required=True, type=Path)
args = parser.parse_args()
root = args.root.resolve()
assert root != Path(tempfile.gettempdir()).resolve() and root.is_relative_to(Path(tempfile.gettempdir()).resolve())
assert (root / 'home/profiles/skillhub-m0/package.json').is_file()
repo = Path(__file__).resolve().parents[4]
node = str(args.node.resolve())
assert Path(node).is_file() and args.chromium.resolve().is_file()
out = Path(tempfile.mkdtemp(prefix='config-restart-', dir=root))
children = []
handles = []


def launch(command, cwd, env, filename):
    handle = (out / filename).open('w')
    handles.append(handle)
    child = subprocess.Popen(command, cwd=cwd, env=env, stdout=handle, stderr=handle)
    children.append(child)
    return child


def start_host(phase):
    env = dict(os.environ, HOME=str(root / 'native-install-home'), USERPROFILE=str(root / 'native-install-home'),
               DSH_HOME=str(root / 'home'), DSH_AGENTS_HOME=str(root / 'agents'),
               DSH_TELEMETRY_DISABLED='1', DSH_PROBE_SKIP_DISCOVERY='1')
    host = launch([node, 'node_modules/@deepseek-ai/dsh/lib/bin.js', '--profile', 'skillhub-m0',
                   '--host', '127.0.0.1', '--port', '0', '--no-open'], root, env, f'host-{phase}.log')
    deadline = time.monotonic() + 60
    while True:
        assert host.poll() is None and registry.poll() is None, 'Host or Registry exited'
        match = re.search(r'dsh web: (http://127\.0\.0\.1:\d+/\?token=\S+)', (out / f'host-{phase}.log').read_text())
        if match:
            return host, match.group(1)
        assert time.monotonic() < deadline, 'Host did not become ready'
        time.sleep(.2)


try:
    registry = launch(['node', 'plugins/dsh/test/integration/catalog-fixture.mjs'], repo,
                      dict(os.environ, DSH_FIXTURE_REQUEST_LOG=str(out / 'requests.jsonl')), 'registry.log')
    pids = []
    for phase in ['save', 'verify']:
        host, entry = start_host(phase)
        pids.append(host.pid)
        env = dict(os.environ, DSH_PROBE_URL=entry, DSH_PROBE_OUTPUT=str(out), DSH_CONFIG_PHASE=phase,
                   DSH_FIXTURE_REQUEST_LOG=str(out / 'requests.jsonl'), DSH_PROBE_CHROMIUM=str(args.chromium.resolve()))
        browser = launch(['node', 'plugins/dsh/test/integration/config-persistence-probe.mjs'], repo, env, f'browser-{phase}.log')
        assert browser.wait(timeout=120) == 0, f'Browser {phase} failed; inspect {out}/browser-{phase}.log'
        host.terminate()
        host.wait(timeout=30)
    assert pids[0] != pids[1]
    result = {'passed': True, 'distinctHostProcesses': True, 'oldHostExitedBeforeRestart': True,
              'node': subprocess.check_output([node, '-p', 'process.versions.node'], text=True).strip(),
              'phases': [json.loads((out / f'config-{phase}-result.json').read_text()) for phase in ['save', 'verify']]}
    (out / 'config-restart-result.json').write_text(json.dumps(result, indent=2))
    print(json.dumps({'output': str(out), **result}))
finally:
    for child in reversed(children):
        if child.poll() is None:
            child.terminate()
            child.wait(timeout=30)
    for handle in handles:
        handle.close()
