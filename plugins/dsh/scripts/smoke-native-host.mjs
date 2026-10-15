import { spawn } from 'node:child_process';
import { createHash, randomUUID } from 'node:crypto';
import { closeSync, openSync } from 'node:fs';
import { access, mkdir, mkdtemp, readFile, readdir, realpath, stat, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { delimiter, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';

const DSH_VERSION = '0.2.0-rc.2';
const PNPM_VERSION = '10.33.0';

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
  const { values } = parseArgs({ options: { artifact: { type: 'string' }, report: { type: 'string' }, help: { type: 'boolean' } } });
  if (values.help) {
    console.log('node scripts/smoke-native-host.mjs --artifact <candidate.tgz|directory> --report <summary.json>');
    return;
  }
  if (!values.artifact || !values.report) throw new Error('Specify --artifact and --report; see --help.');
  const [major, minor] = process.versions.node.split('.').map(Number);
  if (major !== 24 || minor < 2) throw new Error('Use the fixed Node >=24.2.0 <25 baseline.');
  const artifact = await selectTarball(values.artifact);
  const npm = await findNpm();
  const reportFile = resolve(values.report);
  await mkdir(dirname(reportFile), { recursive: true });
  const root = await mkdtemp(join(tmpdir(), 'skillhub-native-smoke-'));
  const env = isolatedEnvironment(root);
  for (const directory of ['home', 'native-install-home', 'agents', 'workspace', 'logs']) await mkdir(join(root, directory), { mode: 0o700 });
  for (const name of ['empty.npmrc', 'empty-global.npmrc']) await writeFile(join(root, name), '', { mode: 0o600 });
  await writeFile(join(root, 'package.json'), JSON.stringify({ private: true, type: 'module' }));
  const abort = new AbortController();
  const stop = () => abort.abort();
  process.once('SIGINT', stop);
  process.once('SIGTERM', stop);
  const summary = { passed: false, platform: process.platform, node: process.versions.node,
    dsh: DSH_VERSION, pnpm: PNPM_VERSION,
    artifactSha256: createHash('sha256').update(await readFile(artifact)).digest('hex'),
    modelCalled: false, probes: [], stage: 'install-host' };
  const run = (name, args, timeoutMs = 300000) => runBounded(args, { cwd: join(root, 'workspace'), env,
    log: join(root, 'logs', name + '.log'), timeoutMs, signal: abort.signal });
  try {
    await run('install-host', [npm, 'install', '--prefix', root, '--ignore-scripts', '--no-audit', '--no-fund',
      '--registry=https://registry.npmjs.org', `@deepseek-ai/dsh@${DSH_VERSION}`, `pnpm@${PNPM_VERSION}`], 600000);
    const dsh = join(root, 'node_modules/@deepseek-ai/dsh/lib/bin.js');
    await access(dsh);
    summary.stage = 'initialize-web';
    await run(summary.stage, [dsh, '--profile', 'skillhub-m0', '--from-default-profile', 'web', '--dump-config']);
    summary.stage = 'install-candidate';
    await run(summary.stage, [dsh, 'plugin', '--profile', 'skillhub-m0', 'add', artifact, '--ignore-scripts']);
    const manifest = JSON.parse(await readFile(join(root, 'home/profiles/skillhub-m0/node_modules/@astron-team/skillhub-dsh/package.json'), 'utf8'));
    if (manifest.name !== '@astron-team/skillhub-dsh') throw new Error('Unexpected installed candidate identity.');
    const probeScript = fileURLToPath(new URL('./probe-host.mjs', import.meta.url));
    for (const probe of ['runtime-baseline', 'plugin-lifecycle']) {
      summary.stage = probe;
      await run(probe, [probeScript, '--root', root, '--probe', probe], 150000);
      const lines = (await readFile(join(root, 'logs', probe + '.log'), 'utf8')).trim().split('\n');
      const result = JSON.parse(lines.at(-1));
      if (result.passed !== true || result.probe !== probe) throw new Error('Probe did not provide a successful result.');
      summary.probes.push({ name: probe, passed: true });
    }
    summary.stage = 'complete';
    summary.passed = true;
  } catch (error) {
    summary.failure = ['SPAWN_FAILED', 'DEADLINE_OR_CANCELLED', 'PROCESS_FAILED', 'CLEANUP_DEADLINE'].includes(error.code)
      ? error.code : 'INVALID_RESULT';
    if (['runtime-baseline', 'plugin-lifecycle'].includes(summary.stage)) {
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
