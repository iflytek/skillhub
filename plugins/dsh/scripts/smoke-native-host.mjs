import { spawn } from 'node:child_process';
import { createHash, randomUUID } from 'node:crypto';
import { closeSync, openSync } from 'node:fs';
import { access, mkdir, mkdtemp, readFile, readdir, realpath, stat, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { delimiter, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';

export const supportedDshVersions = ['0.2.0-rc.2', '0.2.1-rc.2'];
export function selectDshVersion(version, family = 'fresh') {
  if (!supportedDshVersions.includes(version)) throw new Error('Unsupported DSH release.');
  if (version === '0.2.1-rc.2' && family === 'retained') throw new Error('DSH 0.2.1 requires the current Cordis family.');
  return version;
}
export function scenarioProbes(scenario = 'smoke') {
  if (scenario === 'smoke') return ['runtime-baseline', 'plugin-lifecycle', 'skill-availability'];
  if (scenario === 'skill-toggle' || scenario === 'skill-toggle-watch-off') return ['runtime-baseline', scenario];
  throw new Error('Unsupported Host scenario');
}

const PNPM_VERSION = '10.33.0';

export const retainedCordisFamily = {
  '@deepseek-ai/cordis': '4.0.4',
  '@deepseek-ai/cordis-plugin-loader': '1.0.5',
  '@deepseek-ai/cordis-plugin-include': '1.0.9',
  '@deepseek-ai/cordis-plugin-timer': '1.1.6',
};

export function hostInstallManifest(family) {
  if (!['fresh', 'retained'].includes(family)) throw new Error('Unknown Cordis family.');
  return { private: true, type: 'module', ...(family === 'retained' ? { overrides: retainedCordisFamily } : {}) };
}

export function profileInstallManifest(manifest, family) {
  hostInstallManifest(family); // Reject unknown modes before writing any fixture configuration.
  return family === 'retained' ? { ...manifest, pnpm: { ...manifest.pnpm,
    overrides: { ...manifest.pnpm?.overrides, ...retainedCordisFamily } } } : manifest;
}

// No shell or .cmd invocation: npm and the probe run with this exact Node binary.
// On Windows taskkill attempts tree termination while the parent still exists;
// the independent cleanup deadline also covers a failed/hung taskkill process.
function terminateTree(child) {
  if (!child.pid) return;
  if (process.platform === 'win32') {
    const fallback = () => { try { child.kill('SIGKILL'); } catch { /* The cleanup deadline still rejects. */ } };
    const killer = spawn(join(process.env.SystemRoot ?? 'C:\\Windows', 'System32/taskkill.exe'),
      ['/PID', String(child.pid), '/T', '/F'], { stdio: 'ignore', windowsHide: true, timeout: 5000 });
    killer.once('error', fallback);
    killer.once('exit', code => { if (code !== 0) fallback(); });
  } else {
    try { process.kill(-child.pid, 'SIGKILL'); }
    catch (error) { if (error.code !== 'ESRCH') child.kill('SIGKILL'); }
  }
}

export async function runBounded(args, { cwd, env, log, timeoutMs, signal,
  terminateProcess = terminateTree, cleanupTimeoutMs = 10000 }) {
  signal?.throwIfAborted();
  let executable = process.execPath;
  let launchArgs = args;
  if (process.platform === 'win32') {
    const configuration = join(dirname(log), `job-${randomUUID()}.json`);
    await writeFile(configuration, JSON.stringify({ executable, arguments: args, cwd }), { flag: 'wx', mode: 0o600 });
    executable = join(process.env.SystemRoot ?? 'C:\\Windows', 'System32/WindowsPowerShell/v1.0/powershell.exe');
    launchArgs = ['-NoProfile', '-NonInteractive', '-File', fileURLToPath(new URL('./windows-job.ps1', import.meta.url)),
      '-Configuration', configuration];
  }
  const fd = openSync(log, 'wx', 0o600);
  let child;
  try {
    child = spawn(executable, launchArgs, { cwd, env, stdio: ['ignore', fd, fd],
      detached: process.platform !== 'win32', windowsHide: true });
  } finally { closeSync(fd); }
  let stopped = false;
  let cleanupTimer;
  let rejectCleanup;
  const terminate = () => {
    if (stopped) return;
    stopped = true;
    cleanupTimer = setTimeout(() => {
      try { child.kill('SIGKILL'); } catch { /* Report failure without waiting indefinitely for exit. */ }
      rejectCleanup(Object.assign(new Error('Process cleanup deadline reached.'), { code: 'CLEANUP_DEADLINE' }));
    }, cleanupTimeoutMs);
    try { terminateProcess(child); }
    catch { try { child.kill('SIGKILL'); } catch { /* The cleanup deadline still rejects. */ } }
  };
  const timer = setTimeout(terminate, timeoutMs);
  signal?.addEventListener('abort', terminate, { once: true });
  try {
    await new Promise((resolveExit, reject) => {
      rejectCleanup = reject;
      child.once('error', () => reject(Object.assign(new Error('Process failed to start; inspect private logs.'), { code: 'SPAWN_FAILED' })));
      child.once('exit', code => code === 0 && !stopped ? resolveExit() : reject(Object.assign(new Error(
        stopped ? 'Process deadline or cancellation reached.' : 'Process failed; inspect private logs.'),
      { code: stopped ? 'DEADLINE_OR_CANCELLED' : 'PROCESS_FAILED' })));
      if (signal?.aborted) terminate();
    });
  } finally {
    clearTimeout(timer);
    clearTimeout(cleanupTimer);
    signal?.removeEventListener('abort', terminate);
    // POSIX groups remain addressable after their leader exits. The Windows
    // wrapper closes its Job only after all command descendants have terminated.
    if (process.platform !== 'win32') terminateTree(child);
  }
}

export async function selectTarball(input) {
  let target = await realpath(resolve(input));
  if ((await stat(target)).isDirectory()) {
    const candidates = (await readdir(target)).filter(name => name.endsWith('.tgz'));
    if (candidates.length !== 1) throw new Error('Artifact directory must contain exactly one .tgz.');
    target = await realpath(join(target, candidates[0]));
  }
  if (!target.endsWith('.tgz') || !(await stat(target)).isFile()) throw new Error('Candidate must be a .tgz file.');
  return target;
}

export function safeAvailabilityCheckpoint(value) {
  if (!value || typeof value !== 'object') return undefined;
  const scenarios = ['setup', 'user-install', 'user-winner', 'project-install', 'project-winner', 'user-shadowed', 'remote', 'by-name', 'content-modified', 'skill-missing', 'directory-missing', 'policy', 'absent', 'incomplete-provider', 'failed-provider'];
  const checkpoints = ['setup', 'install', 'install-poll', 'reference', 'before-digest', 'execute', 'assertion', 'after-digest', 'mutation'];
  const assertions = ['none', 'isError', 'kind', 'schemaVersion', 'association', 'scope', 'integrity', 'discovery', 'name', 'reason', 'invocation', 'installation', 'namespace', 'slug', 'version', 'fingerprint', 'render', 'privacy', 'unchanged', 'accepted', 'completed', 'reference'];
  if (!scenarios.includes(value.scenario) || !checkpoints.includes(value.checkpoint) || !assertions.includes(value.assertion)) return undefined;
  return { scenario: value.scenario, checkpoint: value.checkpoint, assertion: value.assertion };
}

export function safeToggleCheckpoint(value) {
  if (!value || typeof value !== 'object') return undefined;
  const scenarios = ['setup', 'install', 'initial-discovery', 'watch-off-disable', 'watch-off-diagnosis', 'watch-off-uninstall', 'cancel', 'disable', 'disabled-files', 'disabled-use', 'enable', 'disable-again', 'disabled-upgrade', 'disabled-uninstall', 'same-name-install', 'same-name-disable', 'same-name-fallback', 'same-name-diagnosis', 'cleanup'];
  const assertions = ['none', 'invoke', 'operation', 'discovery', 'equal', 'notEqual', 'deepEqual', 'rejects', 'truthy'];
  if (!scenarios.includes(value.scenario) || !assertions.includes(value.assertion)) return undefined;
  return { scenario: value.scenario, assertion: value.assertion };
}

export function safeFixtureLocation(value) {
  if (value?.fixture !== 'skill-toggle-fixture.mjs' || !Number.isInteger(value.line) || value.line < 1 || value.line > 10000) return undefined;
  return { fixture: value.fixture, line: value.line };
}

export function safeOperationObservation(value) {
  if (!value || typeof value !== 'object') return undefined;
  const fields = {
    phase: ['accepted', 'staging', 'revalidating', 'committing', 'recovering', 'completed', 'failed', 'recovery-required', 'unknown'],
    files: ['pending', 'installed', 'already-installed', 'upgraded', 'removed', 'stale-record-removed', 'enabled', 'disabled', 'failed', 'unknown'],
    discovery: ['unconfirmed', 'confirmed', 'failed'], recovery: ['not-needed', 'restored', 'incomplete', 'unknown'],
  };
  const safe = {};
  for (const [name, allowed] of Object.entries(fields)) if (allowed.includes(value[name])) safe[name] = value[name];
  if (typeof value.deadlineReached === 'boolean') safe.deadlineReached = value.deadlineReached;
  return Object.keys(safe).length ? safe : undefined;
}

const diagnosticCodes = ['EACCES', 'EPERM', 'ENOENT', 'EEXIST', 'ENOSPC', 'EBUSY', 'EXDEV', 'ENOTEMPTY', 'EIO', 'ELOCKED', 'ECONNRESET', 'ECONNREFUSED', 'ETIMEDOUT', 'ENOTFOUND', 'EAI_AGAIN', 'AUTHORIZATION', 'NETWORK', 'FILESYSTEM', 'INVALID_INPUT', 'VALIDATION', 'UNCLASSIFIED'];
export function safeHostFailureCodes(value) {
  return Array.isArray(value) ? diagnosticCodes.filter(code => value.includes(code)) : [];
}
export function knownHostFailureCodes(log) {
  // Observed codes are diagnostic hints, not attribution of the assertion's cause.
  return diagnosticCodes.filter(code => new RegExp('\\b' + code + '\\b').test(log));
}

export function safeProbeFailure(log, probe) {
  const stages = ['setup', 'validate-root', 'prepare-fixture', 'fixture-ports', 'registry-ready', 'host-result', 'host-assertion'];
  const codes = ['PROBE_FAILED', 'REGISTRY_EXITED', 'REGISTRY_TIMEOUT', 'CHILD_EXITED', 'HOST_ASSERTION_FAILED', 'HOST_RESULT_TIMEOUT'];
  for (const line of log.trim().split('\n').reverse()) {
    let result;
    try { result = JSON.parse(line); } catch { continue; }
    if (result?.passed === false && result.probe === probe && stages.includes(result.stage)
      && codes.includes(result.failureCode)) {
      const safe = { stage: result.stage, code: result.failureCode };
      if (result.diagnostics) {
        const observer = result.diagnostics.observer?.stage;
        const states = ['imported', 'applied', 'validation-failed', 'validated', 'running', 'completed', 'assertion-failed'];
        safe.observer = states.includes(observer) ? observer : 'not-observed';
        const validation = result.diagnostics.observer?.validation;
        if (['root-realpath', 'temporary-realpath', 'temporary-containment', 'home-identity'].includes(validation)) {
          safe.validation = validation;
        }
        const availability = safeAvailabilityCheckpoint(result.diagnostics.availability);
        if (probe === 'skill-availability' && availability) safe.availability = availability;
        if (['skill-toggle', 'skill-toggle-watch-off'].includes(probe)) {
          const toggle = safeToggleCheckpoint(result.diagnostics.toggle);
          const location = safeFixtureLocation(result.diagnostics.location);
          if (toggle) safe.toggle = toggle;
          if (location) safe.location = location;
        }
        if (['skill-availability', 'skill-toggle', 'skill-toggle-watch-off'].includes(probe)) {
          const operation = safeOperationObservation(result.diagnostics.operation);
          if (operation) safe.operation = operation;
          const codes = safeHostFailureCodes(result.diagnostics.observedHostCodes);
          if (codes.length) safe.observedHostCodes = codes;
        }
        safe.dependencies = {};
        for (const name of ['loader', 'pluginPackages', 'clientModules', 'skillhub']) {
          const present = result.diagnostics.dependencies?.[name];
          if (typeof present === 'boolean') safe.dependencies[name] = present;
        }
      }
      return safe;
    }
  }
  return undefined;
}

export function isolatedEnvironment(root, inherited = process.env) {
  // In particular, do not inherit model credentials, DSH overrides or Node preload hooks.
  const allowed = /^(PATH|SystemRoot|WINDIR|COMSPEC|PATHEXT|TEMP|TMP|TMPDIR|LANG|LC_ALL|HTTPS?_PROXY|ALL_PROXY|NO_PROXY|CI)$/i;
  const env = Object.fromEntries(Object.entries(inherited).filter(([key]) => allowed.test(key)));
  const path = Object.entries(env).find(([key]) => key.toUpperCase() === 'PATH')?.[1] ?? '';
  for (const key of Object.keys(env)) if (key.toUpperCase() === 'PATH') delete env[key];
  return { ...env, PATH: [join(root, 'node_modules/.bin'), dirname(process.execPath), path].join(delimiter),
    HOME: join(root, 'native-install-home'), USERPROFILE: join(root, 'native-install-home'),
    XDG_CONFIG_HOME: join(root, 'config'), XDG_CACHE_HOME: join(root, 'cache'),
    APPDATA: join(root, 'appdata'), LOCALAPPDATA: join(root, 'localappdata'),
    DSH_HOME: join(root, 'home'), DSH_AGENTS_HOME: join(root, 'agents'), DSH_TELEMETRY_DISABLED: '1',
    npm_config_cache: join(root, 'npm-cache'), npm_config_userconfig: join(root, 'empty.npmrc'),
    npm_config_globalconfig: join(root, 'empty-global.npmrc'), CI: 'true' };
}

async function findNpm() {
  const candidates = [process.env.npm_execpath,
    join(dirname(process.execPath), 'node_modules/npm/bin/npm-cli.js'),
    join(dirname(process.execPath), '../lib/node_modules/npm/bin/npm-cli.js')];
  for (const candidate of candidates) {
    if (!candidate || !candidate.endsWith('npm-cli.js')) continue;
    try { return await realpath(candidate); } catch { /* Try the next Node installation layout. */ }
  }
  throw new Error('Cannot locate npm-cli.js alongside Node. Run with a Node installation that includes npm.');
}

async function main() {
  const { values } = parseArgs({ options: { 'dsh-version': { type: 'string', default: '0.2.0-rc.2' }, artifact: { type: 'string' }, report: { type: 'string' }, help: { type: 'boolean' }, 'cordis-family': { type: 'string', default: 'fresh' }, scenario: { type: 'string', default: 'smoke' } } });
  if (values.help) {
    console.log('node scripts/smoke-native-host.mjs --artifact <candidate.tgz|directory> --report <summary.json> [--scenario smoke|skill-toggle|skill-toggle-watch-off] [--cordis-family fresh|retained] [--dsh-version 0.2.0-rc.2|0.2.1-rc.2]');
    return;
  }
  if (!values.artifact || !values.report) throw new Error('Specify --artifact and --report; see --help.');
  const [major, minor] = process.versions.node.split('.').map(Number);
  if (major !== 24 || minor < 2) throw new Error('Use the fixed Node >=24.2.0 <25 baseline.');
  const probes = scenarioProbes(values.scenario);
  const dshVersion = selectDshVersion(values['dsh-version'], values['cordis-family']);
  const installManifest = hostInstallManifest(values['cordis-family']);
  const artifact = await selectTarball(values.artifact);
  const npm = await findNpm();
  const reportFile = resolve(values.report);
  await mkdir(dirname(reportFile), { recursive: true });
  const root = await mkdtemp(join(tmpdir(), 'skillhub-native-smoke-'));
  const env = isolatedEnvironment(root);
  for (const directory of ['home', 'native-install-home', 'agents', 'workspace', 'logs']) await mkdir(join(root, directory), { mode: 0o700 });
  for (const name of ['empty.npmrc', 'empty-global.npmrc']) await writeFile(join(root, name), '', { mode: 0o600 });
  await writeFile(join(root, 'package.json'), JSON.stringify(installManifest));
  const abort = new AbortController();
  const stop = () => abort.abort();
  process.once('SIGINT', stop);
  process.once('SIGTERM', stop);
  const summary = { passed: false, platform: process.platform, node: process.versions.node,
    scenario: values.scenario, dsh: dshVersion, pnpm: PNPM_VERSION, cordisFamily: values['cordis-family'],
    artifactSha256: createHash('sha256').update(await readFile(artifact)).digest('hex'),
    modelCalled: false, probes: [], stage: 'install-host' };
  const run = (name, args, timeoutMs = 300000) => runBounded(args, { cwd: join(root, 'workspace'), env,
    log: join(root, 'logs', name + '.log'), timeoutMs, signal: abort.signal });
  try {
    await run('install-host', [npm, 'install', '--prefix', root, '--ignore-scripts', '--no-audit', '--no-fund',
      '--registry=https://registry.npmjs.org', `@deepseek-ai/dsh@${dshVersion}`, `pnpm@${PNPM_VERSION}`], 600000);
    const dsh = join(root, 'node_modules/@deepseek-ai/dsh/lib/bin.js');
    await access(dsh);
    summary.stage = 'initialize-web';
    await run(summary.stage, [dsh, '--profile', 'skillhub-m0', '--from-default-profile', 'web', '--dump-config']);
    summary.stage = 'install-candidate';
    // npm's root overrides do not propagate into the profile's separate pnpm project.
    // Keep the retained fixture coherent even if a future profile enables peer auto-install.
    if (values['cordis-family'] === 'retained') {
      const profileManifestPath = join(root, 'home/profiles/skillhub-m0/package.json');
      const profileManifest = JSON.parse(await readFile(profileManifestPath, 'utf8'));
      await writeFile(profileManifestPath, JSON.stringify(profileInstallManifest(profileManifest, 'retained'), null, 2) + '\n');
    }
    await run(summary.stage, [dsh, 'plugin', '--profile', 'skillhub-m0', 'add', artifact, '--ignore-scripts']);
    const manifest = JSON.parse(await readFile(join(root, 'home/profiles/skillhub-m0/node_modules/@astron-team/skillhub-dsh/package.json'), 'utf8'));
    if (manifest.name !== '@astron-team/skillhub-dsh') throw new Error('Unexpected installed candidate identity.');
    const probeScript = fileURLToPath(new URL('./probe-host.mjs', import.meta.url));
    for (const probe of probes) {
      summary.stage = probe;
      await run(probe, [probeScript, '--root', root, '--probe', probe], 150000);
      const lines = (await readFile(join(root, 'logs', probe + '.log'), 'utf8')).trim().split('\n');
      const result = JSON.parse(lines.at(-1));
      if (result.passed !== true || result.probe !== probe) throw new Error('Probe did not provide a successful result.');
      if (probe === 'runtime-baseline') {
        const detail = JSON.parse(await readFile(result.report, 'utf8'));
        const resolved = Object.fromEntries(detail.initial.host.map(pkg => [pkg.name, pkg.version]));
        summary.cordisPackages = Object.fromEntries(Object.keys(retainedCordisFamily).map(name => [name, resolved[name]]));
        if (values['cordis-family'] === 'retained' && Object.entries(retainedCordisFamily).some(([name, version]) => resolved[name] !== version)) {
          throw new Error('Retained Cordis family was not exercised.');
        }
      }
      summary.probes.push({ name: probe, passed: true });
    }
    summary.stage = 'complete';
    summary.passed = true;
  } catch (error) {
    summary.failure = ['SPAWN_FAILED', 'DEADLINE_OR_CANCELLED', 'PROCESS_FAILED', 'CLEANUP_DEADLINE'].includes(error.code)
      ? error.code : 'INVALID_RESULT';
    if (probes.includes(summary.stage)) {
      const log = await readFile(join(root, 'logs', summary.stage + '.log'), 'utf8').catch(() => '');
      summary.probeFailure = safeProbeFailure(log, summary.stage);
    }
    process.exitCode = 1;
  } finally {
    process.removeListener('SIGINT', stop);
    process.removeListener('SIGTERM', stop);
    // Only this fixed schema is suitable for upload. The temporary root contains private Host logs.
    await writeFile(reportFile, JSON.stringify(summary, null, 2) + '\n');
    console.log(JSON.stringify(summary));
    if (!summary.passed) console.error(`Private diagnostics retained locally: ${join(root, 'logs')}`);
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(() => { console.error('Invalid smoke setup; use --help and verify the local artifact and Node/npm installation.'); process.exitCode = 1; });
}
