// Launch isolated real Host + two HTTP Registry fixtures. --keep leaves a manual preview.
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { openSync, closeSync } from 'node:fs';
import { access, mkdir, readFile, realpath, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, isAbsolute, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';
import { setTimeout as delay } from 'node:timers/promises';
import { createServer } from 'node:net';
import { isolatedEnvironment } from '../../scripts/smoke-native-host.mjs';
const { values } = parseArgs({ options: { root: { type: 'string' }, keep: { type: 'boolean' }, resume: { type: 'string' } } });
assert(values.root, 'Pass --root for an isolated prepared Host');
const root = await realpath(resolve(values.root));
const rel = relative(resolve(tmpdir()), root); assert(rel && !rel.startsWith('..') && !isAbsolute(rel));
for (const dir of ['home', 'native-install-home', 'agents']) assert((await realpath(join(root, dir))).startsWith(root + '/'));
const fixtureRoot = dirname(fileURLToPath(import.meta.url));
const bin = join(root, 'node_modules/@deepseek-ai/dsh/lib/bin.js'); await access(bin);
const resume = values.resume ? resolve(values.resume) : undefined;
if (resume) assert(resume.startsWith(root + '/'), 'Resume report must belong to this isolated root');
const previous = resume ? JSON.parse(await readFile(resume, 'utf8')) : undefined;
if (previous) assert.equal(previous.passed, true);
const run = join(root, 'registry-switching-' + Date.now()); await mkdir(run, { mode: 0o700 });
for (const port of [43219, 43220]) await new Promise((yes, no) => {
  const socket = createServer(); socket.once('error', no); socket.listen(port, '127.0.0.1', () => socket.close(yes));
});
const stateA = join(run, 'state-a.txt'); const stateB = join(run, 'state-b.txt');
await writeFile(stateA, 'current'); await writeFile(stateB, 'current');
const logA = join(run, 'requests-a.jsonl'); const logB = join(run, 'requests-b.jsonl');
const observer = join(run, 'observer.mjs'); await writeFile(observer, await readFile(join(fixtureRoot, 'registry-switching-fixture.mjs')));
const patch = join(run, 'observer.yml');
const output = join(run, 'result.json');
await writeFile(patch, '- id: astron-skillhub\n  config:\n    registryUrl: http://127.0.0.1:43219\n- insert:\n    - id: registry-switching-observer\n      name: ' + JSON.stringify(observer) + '\n      config: ' + JSON.stringify({ root, stateA, stateB, logA, logB, output }) + '\n');
const env = isolatedEnvironment(root); const children = [];
function start(args, name, extra = {}) {
  const fd = openSync(join(run, name), 'wx', 0o600);
  try { const child = spawn(process.execPath, args, { cwd: root, env: { ...env, ...extra }, stdio: ['ignore', fd, fd] }); children.push(child); return child; }
  finally { closeSync(fd); }
}
let stopped = false;
async function stop() { if (stopped) return; stopped = true; for (const child of children.toReversed()) if (child.exitCode === null) { child.kill('SIGTERM'); await Promise.race([new Promise(done => child.once('exit', done)), delay(10000)]); if (child.exitCode === null) child.kill('SIGKILL'); } }
process.once('SIGTERM', () => void stop()); process.once('SIGINT', () => void stop());
try {
  for (const [port, state, log] of [[43219, stateA, logA], [43220, stateB, logB]]) start([join(fixtureRoot, 'catalog-fixture.mjs')], `registry-${port}.log`, { DSH_FIXTURE_PORT: String(port), DSH_FIXTURE_NAMESPACE: 'registry-demo', DSH_FIXTURE_UPDATE_STATE: state, DSH_FIXTURE_REQUEST_LOG: log, DSH_FIXTURE_RESOLVE_DELAY_MS: port === 43219 ? '1000' : '0', DSH_FIXTURE_DOWNLOAD_DELAY_MS: port === 43220 ? '1000' : '0' });
  const ready = Date.now() + 10000;
  for (const port of [43219, 43220]) {
    while (!(await fetch(`http://127.0.0.1:${port}/api/v1/search`, { signal: AbortSignal.timeout(1000) }).then(r => r.ok, () => false))) { assert(Date.now() < ready, 'Registry startup timeout'); await delay(100); }
  }
  start([bin, '--profile', 'skillhub-m0', ...(resume ? [] : ['--patch', patch]), '--host', '127.0.0.1', '--port', '0', '--no-open'], 'host.log', { DSH_PROBE_SKIP_DISCOVERY: '1' });
  const deadline = Date.now() + 180000; let result;
  while (Date.now() < deadline) {
    assert(children.every(child => child.exitCode === null), 'Fixture/Host exited; see private logs');
    result = previous ?? await readFile(output, 'utf8').then(JSON.parse, () => undefined);
    const log = await readFile(join(run, 'host.log'), 'utf8').catch(() => '');
    if (result && /dsh web: http:\/\/127\.0\.0\.1:\d+\/\?token=/.test(log)) break; await delay(200);
  }
  assert(result, 'Observer timeout');
  if (resume) await writeFile(output, JSON.stringify({ ...result, resumedWithoutObserver: true }, null, 2));
  const log = await readFile(join(run, 'host.log'), 'utf8');
  const entry = /dsh web: (http:\/\/127\.0\.0\.1:\d+\/\?token=\S+)/.exec(log)?.[1];
  assert(entry, 'Host URL did not become ready before deadline');
  if (entry) await writeFile(join(run, 'private-entry-url.txt'), entry, { mode: 0o600 });
  console.log(JSON.stringify({ ...result, run, entryFile: entry ? join(run, 'private-entry-url.txt') : null }));
  assert.equal(result.passed, true, 'Observer assertions failed');
  if (values.keep) await new Promise(done => { process.once('SIGTERM', done); process.once('SIGINT', done); });
} finally { await stop(); }
