import { expect, test } from 'bun:test';
import { InstallationTargets } from '../src/host/installation-targets.js';

const context = { sessionId: 'session', cwd: '/project', sourceRevision: 'source', detailToken: 'detail' };
const target = { scope: 'project' as const, root: '/project/.dsh/skills', dshHome: '/home/test/.dsh' };

test('public target descriptors contain no paths and cannot mutate the stored binding', async () => {
  const store = new InstallationTargets();
  const input = { ...target };
  const descriptor = store.issue(context, input);
  expect(Object.keys(descriptor).sort()).toEqual(['scope', 'targetToken']);
  input.root = '/elsewhere';
  const resolved = await store.resolve(descriptor.targetToken, context, async () => target);
  expect(resolved).toEqual(target);
  resolved.root = '/mutated';
  expect(await store.resolve(descriptor.targetToken, context, async () => target)).toEqual(target);
});

test('references reject other sessions, sources, projects and details before resolving paths', async () => {
  const store = new InstallationTargets();
  const { targetToken } = store.issue(context, target);
  let calls = 0;
  for (const field of ['sessionId', 'cwd', 'sourceRevision', 'detailToken']) {
    await expect(store.resolve(targetToken, { ...context, [field]: 'changed' }, async () => {
      calls++; return target;
    })).rejects.toThrow();
  }
  expect(calls).toBe(0);
  await expect(store.resolve('forged', context, async () => target)).rejects.toThrow();
});

test('canonical target, home and policy changes invalidate previously issued references', async () => {
  const store = new InstallationTargets();
  const { targetToken } = store.issue(context, target);
  for (const changed of [{ ...target, root: '/redirected' }, { ...target, dshHome: '/another-home' }, { ...target, scope: 'user' as const }]) {
    await expect(store.resolve(targetToken, context, async () => changed)).rejects.toThrow();
  }
  await expect(store.resolve(targetToken, context, async () => { throw new Error('policy denied'); })).rejects.toThrow('policy denied');
});

test('reload invalidation during asynchronous resolution cannot revive a target', async () => {
  const store = new InstallationTargets();
  const { targetToken } = store.issue(context, target);
  await expect(store.resolve(targetToken, context, async () => {
    store.clear(); return target;
  })).rejects.toThrow();
});

test('bounded references evict the oldest target', async () => {
  const store = new InstallationTargets();
  const { targetToken } = store.issue(context, target);
  for (let i = 0; i < 400; i++) store.issue(context, target);
  await expect(store.resolve(targetToken, context, async () => target)).rejects.toThrow();
});
