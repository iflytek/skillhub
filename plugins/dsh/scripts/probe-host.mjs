import { spawn } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { mkdir, readFile, realpath, writeFile, access } from 'node:fs/promises';
import { openSync, closeSync } from 'node:fs';
import { createServer } from 'node:net';
import { tmpdir } from 'node:os';
import { dirname, isAbsolute, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { parseArgs } from 'node:util';

const probes = {
  'plugin-lifecycle': 'plugin-lifecycle-result.json',
  'plugin-coexistence': 'plugin-lifecycle-result.json',
  'history-restart': 'history-restart-result.json',
  'runtime-baseline': 'runtime-baseline-result.json',
  'runtime-unsupported': 'runtime-unsupported-result.json',
  'installed-state': 'installed-state-result.json',
  'upgrade-candidate': 'upgrade-candidate-result.json',
  'upgrade-drift': 'upgrade-drift-result.json',
  'plan-context': 'plan-context-result.json',
  'plan-source': 'plan-source-result.json',
};
let stage = 'setup';
let selectedProbe;
let failureCode = 'PROBE_FAILED';
function fail(code, message) { failureCode = code; throw new Error(message); }
async function main() {
const { values } = parseArgs({ options: { root: { type: 'string' }, probe: { type: 'string' }, help: { type: 'boolean' } } });
if (values.help) {
  console.log(`node scripts/probe-host.mjs --root <prepared temporary DSH root> --probe <${Object.keys(probes).join('|')}>`);
  process.exit(0);
}
if (!values.root || !Object.hasOwn(probes, values.probe ?? '')) throw new Error('Specify --root and a supported --probe; see --help.');
selectedProbe = values.probe;
stage = 'validate-root';
const root = await realpath(resolve(values.root));
const temporary = await realpath(tmpdir());
const rel = relative(temporary, root);
if (!rel || rel === '..' || rel.startsWith('..' + sep) || isAbsolute(rel)) throw new Error('Root must be an existing directory below the OS temporary directory.');
for (const directory of ['home', 'native-install-home', 'agents']) {
  const actual = await realpath(join(root, directory));
  if (!actual.startsWith(root + sep)) throw new Error('State directories must remain inside the temporary root.');
}
stage = 'prepare-fixture';
const pluginRoot = dirname(dirname(fileURLToPath(import.meta.url)));
const fixtureRoot = join(pluginRoot, 'test/integration');
const dshBin = join(root, 'node_modules/@deepseek-ai/dsh/lib/bin.js');
await access(dshBin);
await access(join(root, 'home/profiles/skillhub-m0/node_modules/@astron-team/skillhub-dsh/lib/client.js'));
const installedPatch = await readFile(join(root, 'home/profiles/skillhub-m0/node_modules/@astron-team/skillhub-dsh/cordis.patch.yml'), 'utf8');
const loaderIds = [...installedPatch.matchAll(/^\s*- id: (\S+)$/gm)].map(match => match[1]);
if (loaderIds.length !== 1 || !['astron-skillhub', 'skillhub'].includes(loaderIds[0])) {
  throw new Error('Expected the current or retained development package Loader ID');
}
const installedLoaderId = loaderIds[0];
if (values.probe === 'plugin-coexistence' && installedLoaderId === 'skillhub') {
  throw new Error('Retained Loader ID conflicts with the reference package; use the current package for coexistence');
}

stage = 'fixture-ports';
for (const port of [43219, 43220]) {
  await new Promise((resolvePort, reject) => {
    const server = createServer();
    server.once('error', () => reject(new Error(`Fixture port ${port} is already in use; no process was stopped.`)));
    server.listen(port, '127.0.0.1', () => server.close(error => error ? reject(error) : resolvePort()));
  });
}
const id = randomUUID();
const run = join(root, 'probe-' + id);
await mkdir(run, { mode: 0o700 });
const resultName = `probe-${id}-result.json`;
const resultFile = join(root, resultName);
const observer = join(run, 'observer.mjs');
const runtimeProbe = values.probe === 'runtime-baseline' || values.probe === 'runtime-unsupported';
const source = await readFile(join(fixtureRoot, runtimeProbe ? 'runtime-baseline-probe.mjs' : values.probe === 'plugin-coexistence' ? 'plugin-lifecycle-fixture.mjs' : `${values.probe}-fixture.mjs`), 'utf8');
await writeFile(observer, source.replaceAll(probes[values.probe], resultName), { flag: 'wx', mode: 0o600 });
const state = join(run, 'update-state.txt');
const downloads = join(run, 'download-state.txt');
await writeFile(state, 'current');
await writeFile(downloads, 'normal');
const patch = join(run, 'observer.yml');
const config = { root, ...(['plugin-lifecycle', 'plugin-coexistence'].includes(values.probe) ? { bundle: true } : {}),
  ...(values.probe === 'plugin-coexistence' ? { referencePackage: '@cocofhu/skillhub' } : {}) };
if (runtimeProbe) {
  const installedPackage = join(root, 'home/profiles/skillhub-m0/node_modules/@astron-team/skillhub-dsh');
  const manifest = JSON.parse(await readFile(join(installedPackage, 'package.json'), 'utf8'));
  Object.assign(config, { output: resultName, pluginEntry: join(installedPackage, 'lib/index.js'),
    hostPackages: ['@deepseek-ai/dsh', '@deepseek-ai/dsh-app-boot', ...Object.keys(manifest.peerDependencies)],
    clientPackages: manifest.dsh.client.inject,
    ...(values.probe === 'runtime-unsupported' ? { expectBlocked: true } : { reloadClient: '@deepseek-ai/dsh-client-ui-layout' }) });
}
const referencePatch = values.probe === 'plugin-coexistence'
  ? '- id: skillhub\n  config:\n    apiBase: http://127.0.0.1:43219\n    webBase: http://127.0.0.1:43219\n    timeoutMs: 1000\n' : '';
await writeFile(patch, `${referencePatch}- id: m0-observer\n  disabled: true\n- id: skillhub-local-runtime-probe\n  disabled: true\n- id: ${installedLoaderId}\n  config:\n    registryUrl: http://127.0.0.1:43219\n- insert:\n    - id: skillhub-probe-${id}\n      name: ${JSON.stringify(observer)}\n      config: ${JSON.stringify(config)}\n`);
const env = { ...process.env, HOME: join(root, 'native-install-home'), USERPROFILE: join(root, 'native-install-home'),
  DSH_HOME: join(root, 'home'), DSH_AGENTS_HOME: join(root, 'agents'), DSH_TELEMETRY_DISABLED: '1', DSH_PROBE_SKIP_DISCOVERY: '1',
  DSH_FIXTURE_NAMESPACE: `probe-${id}`, DSH_FIXTURE_CATALOG_SCROLL: '0', DSH_FIXTURE_UPDATE_STATE: state,
  DSH_FIXTURE_DOWNLOAD_STATE: downloads, DSH_FIXTURE_REQUEST_LOG: join(run, 'requests.jsonl') };
const children = [];
const abort = new AbortController();
const stop = () => abort.abort();
process.once('SIGINT', stop);
process.once('SIGTERM', stop);
function launch(args, log, overrides = {}) {
  const fd = openSync(join(run, log), 'wx', 0o600);
  try {
    const child = spawn(process.execPath, args, { cwd: root, env: { ...env, ...overrides }, stdio: ['ignore', fd, fd] });
    const state = { child, ended: false };
    state.done = new Promise(resolveExit => {
      child.once('error', () => { state.ended = true; resolveExit(); });
      child.once('exit', () => { state.ended = true; resolveExit(); });
    });
    children.push(state);
  } finally { closeSync(fd); }
}
try {
  stage = 'registry-ready';
  launch([join(fixtureRoot, 'catalog-fixture.mjs')], 'registry.log', { DSH_FIXTURE_PORT: '43219' });
  launch([join(fixtureRoot, 'catalog-fixture.mjs')], 'registry-second.log', { DSH_FIXTURE_PORT: '43220', DSH_FIXTURE_REQUEST_LOG: join(run, 'second-requests.jsonl') });
  // Await this fixture's successful HTTP response before starting the Host.
  const readyDeadline = Date.now() + 10000;
  while (!abort.signal.aborted) {
    abort.signal.throwIfAborted();
    if (children.some(child => child.ended)) fail('REGISTRY_EXITED', 'Registry process exited; inspect private run logs.');
    const ready = await fetch('http://127.0.0.1:43219/api/v1/search', { signal: AbortSignal.timeout(1000) }).then(r => r.ok, () => false);
    if (ready) break;
    if (Date.now() >= readyDeadline) fail('REGISTRY_TIMEOUT', 'Registry did not become ready.');
    await delay(100, undefined, { signal: abort.signal });
  }
  abort.signal.throwIfAborted();
  stage = 'host-result';
  launch([dshBin, '--profile', 'skillhub-m0', '--patch', patch, '--host', '127.0.0.1', '--port', '0', '--no-open'], 'host.log');
  const deadline = Date.now() + 90000;
  while (!abort.signal.aborted) {
    abort.signal.throwIfAborted();
    if (children.some(child => child.ended)) fail('CHILD_EXITED', 'Probe process exited before completion; inspect private run logs.');
    let report;
    try { report = JSON.parse(await readFile(resultFile, 'utf8')); }
    catch (error) { if (error.code !== 'ENOENT' && !(error instanceof SyntaxError)) throw error; }
    if (report) {
      if (report.passed !== true) {
        stage = 'host-assertion';
        fail('HOST_ASSERTION_FAILED', 'Host probe failed; inspect its result file.');
      }
      console.log(JSON.stringify({ passed: true, probe: values.probe, report: resultFile, runDirectory: run }));
      break;
    }
    if (Date.now() >= deadline) fail('HOST_RESULT_TIMEOUT', 'Probe exceeded its 90-second execution deadline.');
    await delay(200, undefined, { signal: abort.signal });
  }
  abort.signal.throwIfAborted();
} finally {
  for (const state of children.reverse()) {
    if (!state.ended) {
      state.child.kill('SIGTERM');
      const timeout = setTimeout(() => { if (!state.ended) state.child.kill('SIGKILL'); }, 10000);
      await state.done;
      clearTimeout(timeout);
    }
  }
  process.removeListener('SIGINT', stop);
  process.removeListener('SIGTERM', stop);
}

}
main().catch(() => {
  // Fixed fields only: never copy Host errors, paths or tokens into CI diagnostics.
  console.error(JSON.stringify({ passed: false, probe: selectedProbe, stage, failureCode }));
  process.exitCode = 1;
});
