import { expect, test } from 'bun:test';
import { mkdtemp, mkdir, readFile, rename, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
// @ts-expect-error Development-only observer fixture has no TypeScript declarations.
import { withRenamedAvailabilityPath } from './integration/skill-availability-fixture.mjs';
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
