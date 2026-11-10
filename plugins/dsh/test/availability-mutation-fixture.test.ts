import { expect, test } from 'bun:test';
import { mkdtemp, mkdir, readFile, rename, rm, writeFile } from 'node:fs/promises';
import { setTimeout as delay } from 'node:timers/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
// @ts-expect-error Development-only observer fixture has no TypeScript declarations.
import { waitForAvailabilityWinner, withRenamedAvailabilityPath } from './integration/skill-availability-fixture.mjs';
// @ts-expect-error Development-only Node script has no TypeScript declarations.
import { safeAvailabilityCheckpoint } from '../scripts/smoke-native-host.mjs';

function checkpoint(): { scenario: string; checkpoint: string; assertion: string; restoreOsCode?: string } { return { scenario: 'directory-missing', checkpoint: 'mutation', assertion: 'none' }; }

test('failed initial rename never restores an absent backup or replaces the original error', async () => {
  const state = checkpoint();
  const original = Object.assign(new Error('/private/locked/path'), { code: 'EPERM' });
  const calls: string[][] = [];
  let inspected = false;
  await expect(withRenamedAvailabilityPath(state, '/source', '/backup', async () => { inspected = true; }, async (...paths: string[]) => {
    calls.push(paths); throw original;
  })).rejects.toBe(original);
  expect(calls).toEqual([['/source', '/backup']]);
  expect(inspected).toBe(false);
  expect(state.checkpoint).toBe('mutation-apply');
});

test('failed restoration preserves the primary inspection error and its checkpoint', async () => {
  const state = checkpoint();
  const original = Object.assign(new Error('/private/assertion'), { code: 'ERR_ASSERTION' });
  let calls = 0;
  await expect(withRenamedAvailabilityPath(state, '/source', '/backup', async () => {
    Object.assign(state, { checkpoint: 'assertion', assertion: 'integrity' });
    throw original;
  }, async () => { if (++calls === 2) throw Object.assign(new Error('/private/restore'), { code: 'EBUSY' }); })).rejects.toBe(original);
  expect(calls).toBe(2);
  expect(state).toEqual({ scenario: 'directory-missing', checkpoint: 'assertion', assertion: 'integrity', restoreOsCode: 'EBUSY' });
});

test('restoration failure after successful inspection identifies the restore phase', async () => {
  const state = checkpoint();
  const original = Object.assign(new Error('/private/restore'), { code: 'EACCES' });
  let calls = 0;
  await expect(withRenamedAvailabilityPath(state, '/source', '/backup', async () => {}, async () => {
    if (++calls === 2) throw original;
  })).rejects.toBe(original);
  expect(state.checkpoint).toBe('mutation-restore');
});

test('real directory rename and restore preserve fixture files without skipping inspection', async () => {
  const root = await mkdtemp(join(tmpdir(), 'skillhub-availability-mutation-'));
  const source = join(root, 'skill');
  const backup = join(root, 'backup');
  await mkdir(source);
  await writeFile(join(source, 'SKILL.md'), 'original');
  try {
    let inspected = false;
    await withRenamedAvailabilityPath(checkpoint(), source, backup, async () => {
      inspected = true;
      expect(await readFile(join(backup, 'SKILL.md'), 'utf8')).toBe('original');
      await expect(readFile(join(source, 'SKILL.md'))).rejects.toHaveProperty('code', 'ENOENT');
    }, rename);
    expect(inspected).toBe(true);
    expect(await readFile(join(source, 'SKILL.md'), 'utf8')).toBe('original');
  } finally { await rm(root, { recursive: true, force: true }); }
});

test('mutation summary keeps only fixed OS codes and excludes paths or arbitrary error strings', () => {
  const state = { ...checkpoint(), checkpoint: 'mutation-apply', osCode: 'EPERM', restoreOsCode: 'ENOENT', message: '/private/token' };
  expect(safeAvailabilityCheckpoint(state)).toEqual({ scenario: 'directory-missing', checkpoint: 'mutation-apply', assertion: 'none', osCode: 'EPERM', restoreOsCode: 'ENOENT' });
  for (const value of ['/private/token', 'secret', 'constructor', {}, undefined]) {
    const safe = safeAvailabilityCheckpoint({ ...state, osCode: value, restoreOsCode: value });
    expect(safe.osCode).toBeUndefined();
    expect(safe.restoreOsCode).toBeUndefined();
    expect(JSON.stringify(safe)).not.toMatch(/private|token|secret/);
  }
});


const discoveredWinner = (directory: string) => ({ snapshot: { complete: true, skills: [{ name: 'document-helper' }] },
  winner: { name: 'document-helper', path: join(directory, 'SKILL.md'), resourceBase: { kind: 'directory', path: directory } } });

test('availability fixture waits through stale same-name user winner and incomplete snapshots for the exact project', async () => {
  const project = '/project/.dsh/skills/document-helper';
  const current = discoveredWinner(project);
  const sequence = [discoveredWinner('/home/skills/document-helper'), { ...current, snapshot: { ...current.snapshot, complete: false } }, current];
  let reads = 0;
  await waitForAvailabilityWinner(async () => sequence[reads++], project, new AbortController().signal, { timeoutMs: 1000, pollMs: 1 });
  expect(reads).toBe(3);
});

test('availability fixture never accepts a wrong winner and fails at its bounded deadline', async () => {
  let reads = 0;
  await expect(waitForAvailabilityWinner(async () => { reads++; return discoveredWinner('/other'); }, '/selected',
    new AbortController().signal, { timeoutMs: 20, pollMs: 1 })).rejects.toThrow('before the deadline');
  expect(reads).toBeGreaterThan(0);
});

test('availability discovery wait preserves service errors and cancellation without retrying', async () => {
  const failure = new Error('Service unavailable');
  let reads = 0;
  await expect(waitForAvailabilityWinner(async () => { reads++; throw failure; }, '/selected',
    new AbortController().signal, { timeoutMs: 100, pollMs: 1 })).rejects.toBe(failure);
  expect(reads).toBe(1);
  const controller = new AbortController();
  await expect(waitForAvailabilityWinner(async () => { controller.abort(failure); return discoveredWinner('/selected'); },
    '/selected', controller.signal)).rejects.toBe(failure);
});


test('availability fixture observes a delayed watcher refresh instead of asserting immediately after commit', async () => {
  const started = Date.now();
  let reads = 0;
  await waitForAvailabilityWinner(async () => {
    reads++;
    return discoveredWinner(Date.now() - started >= 200 ? '/selected' : '/previous');
  }, '/selected', new AbortController().signal, { timeoutMs: 2000, pollMs: 20 });
  expect(Date.now() - started).toBeGreaterThanOrEqual(200);
  expect(reads).toBeGreaterThan(1);
});

test('availability discovery rejects mismatched name, path or resource identity despite a complete catalog', async () => {
  const valid = discoveredWinner('/selected');
  for (const winner of [{ ...valid.winner, name: 'another' }, { ...valid.winner, path: '/other/SKILL.md' },
    { ...valid.winner, resourceBase: { kind: 'directory', path: '/other' } }]) {
    await expect(waitForAvailabilityWinner(async () => ({ ...valid, winner }), '/selected',
      new AbortController().signal, { timeoutMs: 10, pollMs: 1 })).rejects.toHaveProperty('code', 'DISCOVERY_TIMEOUT');
  }
});


test('availability hard deadline rejects a late valid read and aborts its bounded signal', async () => {
  let observed: AbortSignal | undefined;
  await expect(waitForAvailabilityWinner(async (signal: AbortSignal) => {
    observed = signal;
    await delay(50);
    return discoveredWinner('/selected');
  }, '/selected', new AbortController().signal, { timeoutMs: 10 })).rejects.toHaveProperty('code', 'DISCOVERY_TIMEOUT');
  expect(observed?.aborted).toBe(true);
  await delay(60); // Let the ignored late result settle; Bun also detects unhandled rejections.
});

test('availability hard deadline settles even when a reader never settles', async () => {
  let observed: AbortSignal | undefined;
  await expect(waitForAvailabilityWinner((signal: AbortSignal) => {
    observed = signal;
    return new Promise(() => {});
  }, '/selected', new AbortController().signal, { timeoutMs: 10 })).rejects.toHaveProperty('code', 'DISCOVERY_TIMEOUT');
  expect(observed?.aborted).toBe(true);
});

test('availability external cancellation wins before deadline and preserves its exact reason during a pending read', async () => {
  const controller = new AbortController();
  const reason = new Error('External cancellation');
  let observed: AbortSignal | undefined;
  const work = waitForAvailabilityWinner((signal: AbortSignal) => {
    observed = signal;
    controller.abort(reason);
    return new Promise(() => {});
  }, '/selected', controller.signal, { timeoutMs: 100 });
  await expect(work).rejects.toBe(reason);
  expect(observed?.reason).toBe(reason);
});

test('availability deadline remains the cause if cancellation and late rejection follow it', async () => {
  const controller = new AbortController();
  let rejectRead!: (reason: Error) => void;
  const work = waitForAvailabilityWinner(() => new Promise((_resolve, reject) => { rejectRead = reject; }),
    '/selected', controller.signal, { timeoutMs: 10 });
  await expect(work).rejects.toHaveProperty('code', 'DISCOVERY_TIMEOUT');
  controller.abort(new Error('Late cancellation'));
  rejectRead(new Error('Late reader failure'));
  await delay(0);
});

test('availability success clears the deadline and detaches external cancellation', async () => {
  const controller = new AbortController();
  let observed: AbortSignal | undefined;
  await waitForAvailabilityWinner(async (signal: AbortSignal) => { observed = signal; return discoveredWinner('/selected'); },
    '/selected', controller.signal, { timeoutMs: 20 });
  controller.abort(new Error('After success'));
  await delay(30);
  expect(observed?.aborted).toBe(false);
});

test('availability rejects a valid read that crosses the deadline before timers can run', async () => {
  await expect(waitForAvailabilityWinner(async () => {
    const until = Date.now() + 20;
    while (Date.now() < until) { /* Simulate a reader monopolizing the event loop. */ }
    return discoveredWinner('/selected');
  }, '/selected', new AbortController().signal, { timeoutMs: 5 })).rejects.toHaveProperty('code', 'DISCOVERY_TIMEOUT');
});
