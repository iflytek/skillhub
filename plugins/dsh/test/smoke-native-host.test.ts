import { afterEach, expect, test } from 'bun:test';
import { access, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
// Node-only CI entry; keep its helpers importable without starting a Host or downloading dependencies.
// @ts-expect-error The development-only Node entry has no TypeScript declarations.
import { isolatedEnvironment, runBounded, selectTarball } from '../scripts/smoke-native-host.mjs';

const roots: string[] = [];
async function root() {
  const path = await mkdtemp(join(tmpdir(), 'skillhub-smoke-test-'));
  roots.push(path);
  return path;
}
afterEach(async () => { await Promise.all(roots.splice(0).map(path => rm(path, { recursive: true, force: true }))); });

test('isolates user configuration and strips model credentials and Node preload hooks', () => {
  const directory = join(tmpdir(), 'skillhub-environment');
  const env = isolatedEnvironment(directory, { Path: 'system-bin', DSH_HOME: '/personal',
    DEEPSEEK_API_KEY: 'must-not-pass', OPENAI_API_KEY: 'must-not-pass', NODE_OPTIONS: '--require personal',
    npm_config_userconfig: '/personal/npmrc', SystemRoot: 'C:\\Windows', HTTPS_PROXY: 'http://proxy.invalid' });
  expect(env.DSH_HOME).toBe(join(directory, 'home'));
  expect(env.DSH_AGENTS_HOME).toBe(join(directory, 'agents'));
  expect(env.HOME).toBe(join(directory, 'native-install-home'));
  expect(env.USERPROFILE).toBe(env.HOME);
  expect(env.npm_config_userconfig).toBe(join(directory, 'empty.npmrc'));
  expect(env.NODE_OPTIONS).toBeUndefined();
  expect(env.DEEPSEEK_API_KEY).toBeUndefined();
  expect(env.OPENAI_API_KEY).toBeUndefined();
  expect(env.Path).toBeUndefined();
  expect(env.PATH).toContain('system-bin');
  expect(env.SystemRoot).toBe('C:\\Windows');
});

test('accepts exactly one candidate and rejects ambiguous directories', async () => {
  const directory = await root();
  await expect(selectTarball(directory)).rejects.toThrow('exactly one');
  const artifact = join(directory, 'candidate.tgz');
  await writeFile(artifact, 'fixture');
  expect(await selectTarball(directory)).toBe(artifact);
  await writeFile(join(directory, 'other.tgz'), 'fixture');
  await expect(selectTarball(directory)).rejects.toThrow('exactly one');
  await writeFile(join(directory, 'plain.txt'), 'fixture');
  await expect(selectTarball(join(directory, 'plain.txt'))).rejects.toThrow('.tgz');
});

test('bounded process runner keeps raw output private and terminates an unresponsive child', async () => {
  const directory = await root();
  const script = join(directory, 'child.mjs');
  const log = join(directory, 'private.log');
  const orphanMarker = join(directory, 'orphan-survived');
  const grandchild = join(directory, 'grandchild.mjs');
  await writeFile(grandchild, `import { writeFileSync } from 'node:fs';
    setTimeout(() => writeFileSync(${JSON.stringify(orphanMarker)}, 'unexpected'), 3000);`);
  await writeFile(script, `import { spawn } from 'node:child_process';
    spawn(process.execPath, [${JSON.stringify(grandchild)}], { stdio: 'ignore' });
    console.log('private-output'); setInterval(() => {}, 1000);`);
  await expect(runBounded([script], { cwd: directory, env: process.env, log, timeoutMs: 2000 }))
    .rejects.toThrow('deadline');
  expect(await readFile(log, 'utf8')).toContain('private-output');
  await new Promise(resolve => setTimeout(resolve, 2000));
  await expect(access(orphanMarker)).rejects.toThrow();
}, 10000);

test('failed tree termination cannot make the runner wait indefinitely for child exit', async () => {
  const directory = await root();
  const script = join(directory, 'unresponsive.mjs');
  await writeFile(script, 'setInterval(() => {}, 1000);');
  const started = Date.now();
  await expect(runBounded([script], { cwd: directory, env: process.env,
    log: join(directory, 'cleanup.log'), timeoutMs: 100, cleanupTimeoutMs: 100,
    terminateProcess: () => undefined,
  })).rejects.toThrow('cleanup deadline');
  expect(Date.now() - started).toBeLessThan(3000);
}, 5000);

test('successful parent exit still cleans remaining descendants', async () => {
  const directory = await root();
  const marker = join(directory, 'orphan-survived');
  const grandchild = join(directory, 'orphan.mjs');
  const parent = join(directory, 'parent.mjs');
  await writeFile(grandchild, `import { writeFileSync } from 'node:fs';
    setTimeout(() => writeFileSync(${JSON.stringify(marker)}, 'unexpected'), 1000);`);
  await writeFile(parent, `import { spawn } from 'node:child_process';
    spawn(process.execPath, [${JSON.stringify(grandchild)}], { stdio: 'ignore' }).unref();`);
  await runBounded([parent], { cwd: directory, env: process.env,
    log: join(directory, 'parent.log'), timeoutMs: 10000 });
  await new Promise(resolve => setTimeout(resolve, 1500));
  await expect(access(marker)).rejects.toThrow();
}, 10000);

test('passes Unicode and shell-looking arguments literally without a shell', async () => {
  const directory = await root();
  const script = join(directory, 'arguments.mjs');
  const log = join(directory, 'arguments.log');
  const args = ['with space', 'quoted"value', 'C:\\space dir\\', '$(echo injected)', '; & []中文'];
  await writeFile(script, 'console.log(JSON.stringify(process.argv.slice(2)));');
  await runBounded([script, ...args], { cwd: directory, env: process.env, log, timeoutMs: 10000 });
  expect(JSON.parse((await readFile(log, 'utf8')).trim())).toEqual(args);
}, 15000);
