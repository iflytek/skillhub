import { afterEach, expect, test } from 'bun:test';
import { realpath, access, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
// Node-only CI entry; keep its helpers importable without starting a Host or downloading dependencies.
// @ts-expect-error The development-only Node entry has no TypeScript declarations.
import { hostInstallManifest, isolatedEnvironment, profileInstallManifest, retainedCordisFamily, runBounded, selectTarball } from '../scripts/smoke-native-host.mjs';

const roots: string[] = [];
async function root() {
  const path = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-smoke-test-')));
  roots.push(path);
  return path;
}
async function removeFixture(path: string) {
  const deadline = Date.now() + 2000;
  for (;;) {
    try { await rm(path, { recursive: true, force: true }); return; }
    catch (error) {
      const code = (error as NodeJS.ErrnoException).code;
      if ((code !== 'EBUSY' && code !== 'EPERM') || Date.now() >= deadline) throw error;
      // A killed Windows Job can release its cwd handles shortly after the wrapper emits exit.
      await new Promise(resolve => setTimeout(resolve, 25));
    }
  }
}
afterEach(async () => { await Promise.all(roots.splice(0).map(removeFixture)); });

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

test('bounded process runner keeps raw output private and cancels a running process tree', async () => {
  const directory = await root();
  const script = join(directory, 'child.mjs');
  const log = join(directory, 'private.log');
  const ready = join(directory, 'grandchild-ready');
  const orphanMarker = join(directory, 'orphan-survived');
  const grandchild = join(directory, 'grandchild.mjs');
  await writeFile(grandchild, `import { writeFileSync } from 'node:fs';
    writeFileSync(${JSON.stringify(ready)}, 'ready');
    setTimeout(() => writeFileSync(${JSON.stringify(orphanMarker)}, 'unexpected'), 3000);`);
  await writeFile(script, `import { spawn } from 'node:child_process';
    console.log('private-output');
    spawn(process.execPath, [${JSON.stringify(grandchild)}], { stdio: 'ignore' });
    setInterval(() => {}, 1000);`);
  const controller = new AbortController();
  const result = runBounded([script], { cwd: directory, env: process.env, log,
    timeoutMs: 15000, signal: controller.signal }).then(() => undefined, (error: unknown) => error);
  try {
    const deadline = Date.now() + 10000;
    let started = false;
    while (Date.now() < deadline) {
      try { await access(ready); started = true; break; }
      catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error; }
      await new Promise(resolve => setTimeout(resolve, 25));
    }
    expect(started).toBe(true);
    controller.abort();
    const error = await result;
    if (!(error instanceof Error)) throw new Error('Expected process cancellation to reject');
    expect(error.message).toContain('deadline');
    expect(await readFile(log, 'utf8')).toContain('private-output');
    await new Promise(resolve => setTimeout(resolve, 3500));
    await expect(access(orphanMarker)).rejects.toThrow();
  } finally { controller.abort(); await result; }
}, 30000);

test('failed tree termination cannot make the runner wait indefinitely for child exit', async () => {
  const directory = await root();
  const script = join(directory, 'unresponsive.mjs');
  await writeFile(script, 'setInterval(() => {}, 1000);');
  let exited: Promise<void> | undefined;
  const started = Date.now();
  await expect(runBounded([script], { cwd: directory, env: process.env,
    log: join(directory, 'cleanup.log'), timeoutMs: 100, cleanupTimeoutMs: 100,
    terminateProcess: (child: import('node:child_process').ChildProcess) => {
      exited = new Promise(resolve => child.once('exit', () => resolve()));
    },
  })).rejects.toThrow('cleanup deadline');
  expect(Date.now() - started).toBeLessThan(3000);
  // The deadline rejects before the forced kill emits exit; fixture cleanup also bounds delayed handle release.
  await exited;
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

test('fresh smoke uses ordinary resolution and retained smoke pins the entire validated Cordis family', () => {
  expect(hostInstallManifest('fresh')).toEqual({ private: true, type: 'module' });
  expect(hostInstallManifest('retained')).toEqual({ private: true, type: 'module', overrides: retainedCordisFamily });
  expect(retainedCordisFamily).toEqual({
    '@deepseek-ai/cordis': '4.0.4', '@deepseek-ai/cordis-plugin-loader': '1.0.5',
    '@deepseek-ai/cordis-plugin-include': '1.0.9', '@deepseek-ai/cordis-plugin-timer': '1.1.6',
  });
  expect(() => hostInstallManifest('unknown')).toThrow('Unknown Cordis family');
});

test('retained profile pins pnpm separately while fresh profile preserves ordinary configuration', () => {
  const profile = { private: true, dependencies: { fixture: '1.0.0' },
    dsh: { profile: { bundles: ['@deepseek-ai/dsh-base'] } },
    pnpm: { supportedArchitectures: { os: ['current'] }, overrides: { unrelated: '2.0.0' } } };
  expect(profileInstallManifest(profile, 'fresh')).toBe(profile);
  expect(profileInstallManifest(profile, 'retained')).toEqual({ ...profile, pnpm: {
    ...profile.pnpm, overrides: { unrelated: '2.0.0', ...retainedCordisFamily },
  } });
  expect(profile.pnpm.overrides).toEqual({ unrelated: '2.0.0' });
  expect(profileInstallManifest({ private: true }, 'retained')).toEqual({
    private: true, pnpm: { overrides: retainedCordisFamily },
  });
  expect(() => profileInstallManifest(profile, 'unknown')).toThrow('Unknown Cordis family');
});
