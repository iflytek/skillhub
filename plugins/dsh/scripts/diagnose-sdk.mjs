import { spawn } from 'node:child_process';
import { closeSync, openSync } from 'node:fs';
import { mkdir, mkdtemp, open, readFile, realpath, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';
import { hostInstallManifest, isolatedEnvironment, summarizeNpmPreparation } from './smoke-native-host.mjs';

const codes = ['ERESOLVE', 'ETARGET', 'E404', 'E401', 'E403', 'E429', 'E500', 'E502', 'E503', 'E504', 'EINTEGRITY', 'EBADENGINE', 'EUSAGE', 'EINVALIDPACKAGENAME', 'EINVALIDTAGNAME', 'EUNSUPPORTEDPROTOCOL', 'EOVERRIDE', 'ECONNRESET', 'ECONNREFUSED', 'ETIMEDOUT', 'ENOTFOUND', 'EAI_AGAIN', 'ERR_SOCKET_TIMEOUT', 'CERT_HAS_EXPIRED', 'UNABLE_TO_VERIFY_LEAF_SIGNATURE', 'SELF_SIGNED_CERT_IN_CHAIN', 'EACCES', 'EPERM', 'ENOENT', 'ENOSPC', 'EEXIST', 'ENOTEMPTY', 'EMFILE', 'ENOMEM'];
const logLimit = 1024 * 1024;
// Explicit public dependency identities only: an arbitrary syntactically valid package name can contain secrets.
const publicPackages = new Set([
  'react', 'react-dom', 'zod', 'js-yaml', 'commander', 'chokidar', 'pnpm', 'typescript', 'semver',
  ...['cordis', 'cordis-plugin-loader', 'cordis-plugin-include', 'cordis-plugin-timer', 'schemastery',
    'dsh', 'dsh-base', 'dsh-app-boot', 'dsh-agent', 'dsh-tools', 'dsh-skill', 'dsh-skill-filesystem',
    'dsh-fs', 'dsh-fs-local', 'dsh-home-paths', 'dsh-sandbox', 'dsh-sandbox-policy', 'dsh-session',
    'dsh-session-query', 'dsh-user-questions', 'dsh-typert-protocol', 'dsh-plugin-manager',
    'dsh-sdk-app', 'dsh-sdk-minimal', 'dsh-web-app', 'dsh-api-gateway', 'dsh-client-ui-plugin-manager',
    'dsh-client-ui-layout', 'dsh-client-ui-conversation', 'dsh-client-ui-session', 'dsh-client-ui-settings',
    'dsh-client-ui-tool', 'dsh-client-ui-sidebar', 'dsh-client-ui-workspace', 'dsh-client-locale',
    'dsh-api-session-controller', 'dsh-api-workspace-controller'].map(name => '@deepseek-ai/' + name),
]);
const exactVersion = /^(?:0|[1-9][0-9]{0,5})\.(?:0|[1-9][0-9]{0,5})\.(?:0|[1-9][0-9]{0,5})(?:-(?:rc|alpha|beta)\.[0-9]{1,6})?$/;
function safeRange(value) {
  if (!value || value.length > 120) return false;
  return value.split(' || ').every(part => part.split(' ').every(term => {
    const version = term.replace(/^(?:>=|<=|>|<|=|\^|~)/, '');
    return exactVersion.test(version) || /^(?:\*|[0-9]{1,6}(?:\.(?:[0-9]{1,6}|x|\*)){0,2})$/.test(version);
  }));
}
function dependencyObservation(line) {
  const simple = /^npm (?:error|ERR!) (Found: |While resolving: |No matching version found for )(@[^\s@]+\/[^\s@]+|[^\s@]+)@([^\s]+?)(?:\.)?\r?$/.exec(line);
  if (simple && publicPackages.has(simple[2]) && (exactVersion.test(simple[3])
    || simple[1] === 'No matching version found for ' && safeRange(simple[3]))) {
    return { kind: simple[1] === 'Found: ' ? 'found' : simple[1] === 'While resolving: ' ? 'resolving' : 'missing-version',
      package: simple[2], ...(exactVersion.test(simple[3]) ? { version: simple[3] } : { range: simple[3] }) };
  }
  const peer = /^npm (?:error|ERR!) peer(?:Optional)? (@[^\s@]+\/[^\s@]+|[^\s@]+)@"([^"\r\n]+)" from (@[^\s@]+\/[^\s@]+|[^\s@]+)@([^\s]+)\r?$/.exec(line);
  if (peer && publicPackages.has(peer[1]) && safeRange(peer[2]) && publicPackages.has(peer[3]) && exactVersion.test(peer[4])) {
    return { kind: 'peer', package: peer[1], range: peer[2], fromPackage: peer[3], fromVersion: peer[4] };
  }
}

export function safeNpmDiagnostics(log, truncated = false) {
  let text = log.slice(-logLimit);
  const cut = truncated || log.length > logLimit;
  if (cut) text = text.slice(text.indexOf('\n') + 1);
  const found = new Set();
  const httpStatuses = {};
  const dependencies = [];
  const seen = new Set();
  for (const line of text.split('\n')) {
    const dependency = dependencyObservation(line);
    if (dependency && dependencies.length < 20 && !seen.has(JSON.stringify(dependency))) {
      seen.add(JSON.stringify(dependency)); dependencies.push(dependency);
    }
    const code = /^npm (?:error|ERR!) code ([A-Z][A-Z0-9_]*)\r?$/.exec(line)?.[1];
    if (codes.includes(code)) found.add(code);
    const status = /^npm http fetch (?:GET|POST|PUT|DELETE|HEAD) ([1-5][0-9]{2}) \S+ [0-9]+ms(?: .*)?\r?$/.exec(line)?.[1];
    if (status) httpStatuses[status] = (httpStatuses[status] ?? 0) + 1;
  }
  return { npmCodes: codes.filter(code => found.has(code)), httpStatuses, dependencies, preparation: summarizeNpmPreparation(text, cut) };
}

/** Linux-only diagnostic process group; raw output is private and never inherited by Actions logs. */
export async function runPrivate(args, { cwd, env, log, timeoutMs, cleanupMs = 5000 }) {
  const fd = openSync(log, 'wx', 0o600);
  let child;
  try { child = spawn(process.execPath, args, { cwd, env, detached: true, stdio: ['ignore', fd, fd] }); }
  finally { closeSync(fd); }
  const started = Date.now();
  let timedOut = false;
  let cleanupTimer;
  const kill = () => { if (child.pid) { try { process.kill(-child.pid, 'SIGKILL'); } catch { /* Exit/cleanup outcome remains explicit. */ } } };
  return new Promise(resolveResult => {
    let settled = false;
    const finish = result => {
      if (settled) return;
      settled = true; clearTimeout(timer); clearTimeout(cleanupTimer); kill();
      resolveResult({ ...result, timedOut, elapsedMs: Date.now() - started });
    };
    const timer = setTimeout(() => {
      timedOut = true; kill();
      cleanupTimer = setTimeout(() => finish({ exitCode: null, signal: null, state: 'cleanup-deadline' }), cleanupMs);
    }, timeoutMs);
    child.once('error', () => finish({ exitCode: null, signal: null, state: 'spawn-failed' }));
    child.once('close', (code, signal) => finish({ exitCode: Number.isInteger(code) ? code : null,
      signal: ['SIGKILL', 'SIGTERM', 'SIGINT', 'SIGABRT', 'SIGSEGV'].includes(signal) ? signal : null, state: 'closed' }));
  });
}

async function readTail(path) {
  const file = await open(path, 'r');
  try {
    const { size } = await file.stat();
    const buffer = Buffer.alloc(Math.min(size, logLimit));
    const { bytesRead } = await file.read(buffer, 0, buffer.length, Math.max(0, size - buffer.length));
    return safeNpmDiagnostics(buffer.subarray(0, bytesRead).toString('utf8'), size > logLimit);
  } finally { await file.close(); }
}

async function main() {
  const { values } = parseArgs({ options: { 'dsh-version': { type: 'string' } } });
  const dsh = values['dsh-version'];
  const expectedNode = { '0.2.2-rc.1': '24.9.0', '0.2.1-rc.5': '24.22.2' }[dsh];
  if (process.platform !== 'linux' || !expectedNode || process.versions.node !== expectedNode || !process.env.RUNNER_TEMP) throw new Error('Invalid diagnostic runtime');
  const root = await mkdtemp(join(await realpath(process.env.RUNNER_TEMP), 'dsh-sdk-diagnostic-'));
  const env = isolatedEnvironment(root);
  for (const path of ['home', 'native-install-home', 'agents', 'workspace', 'logs']) await mkdir(join(root, path), { mode: 0o700 });
  for (const name of ['empty.npmrc', 'empty-global.npmrc']) await writeFile(join(root, name), '', { mode: 0o600 });
  await writeFile(join(root, 'package.json'), JSON.stringify(hostInstallManifest('fresh')), { mode: 0o600 });
  const npm = await realpath(join(dirname(process.execPath), '../lib/node_modules/npm/bin/npm-cli.js'));
  const versionLog = join(root, 'logs/npm-version.log');
  const versionRun = await runPrivate([npm, '--version'], { cwd: join(root, 'workspace'), env, log: versionLog, timeoutMs: 10000 });
  const version = (await readFile(versionLog, 'utf8')).trim();
  if (versionRun.exitCode !== 0 || !/^\d+\.\d+\.\d+$/.test(version)) throw new Error('Invalid npm version');
  const log = join(root, 'logs/install-host.log');
  const outcome = await runPrivate([npm, 'install', '--prefer-dedupe', '--timing', '--loglevel=http', '--prefix', root,
    '--ignore-scripts', '--no-audit', '--no-fund', '--registry=https://registry.npmjs.org', `@deepseek-ai/dsh@${dsh}`, 'pnpm@10.33.0'],
  { cwd: join(root, 'workspace'), env, log, timeoutMs: 600000 });
  const summary = { schemaVersion: 1, purpose: 'sdk-install-only', node: process.versions.node, npm: version,
    dsh, pnpm: '10.33.0', ...outcome, ...await readTail(log) };
  await writeFile(resolve('sdk-diagnostic-summary.json'), JSON.stringify(summary, null, 2) + '\n', { flag: 'wx', mode: 0o600 });
  console.log(JSON.stringify(summary));
  if (outcome.exitCode !== 0 || outcome.state !== 'closed' || outcome.timedOut) process.exitCode = 1;
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(() => { console.error('SDK diagnostic setup or report failed; raw details remain private.'); process.exitCode = 1; });
}
