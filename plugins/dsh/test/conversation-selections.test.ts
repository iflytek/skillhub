import { expect, test } from 'bun:test';
import { ConversationSelections } from '../src/host/conversation-selections.js';
const scope = { sessionId: 'session', cwd: '/project', sourceRevision: 'source' };
const snapshot = { namespace: 'team', slug: 'writer', version: '1.0.0', versionId: 1, fingerprint: 'original' };
function setup() {
  const store = new ConversationSelections();
  const search = store.add({ ...scope, callId: 'search-call' }, snapshot);
  return { store, search };
}
test('only a displayed fixed selection promotes the original search without re-resolving latest', () => {
  const { store, search } = setup();
  const fixed = store.stage(search, scope, 'detail', snapshot);
  expect(fixed).not.toBe(search);
  expect(() => store.resolve(search, scope)).toThrow();
  expect(() => store.resolve(fixed, scope)).toThrow();
  store.acknowledge(fixed, scope, 'detail');
  expect(store.resolve(search, scope)).toEqual({ selectionRef: fixed, detailToken: 'detail', snapshot });
  const result = store.resolve(fixed, scope);
  result.snapshot.fingerprint = 'mutated';
  expect(store.resolve(search, scope).snapshot.fingerprint).toBe('original');
});
test('a newer pending render rejects an older acknowledgment and forged detail tokens', () => {
  const { store, search } = setup();
  const first = store.stage(search, scope, 'first', snapshot);
  const second = store.stage(search, scope, 'second', snapshot);
  expect(() => store.acknowledge(first, scope, 'first')).toThrow();
  expect(() => store.acknowledge(second, scope, 'forged')).toThrow();
  expect(store.acknowledge(search, scope, 'second')).toBe(true);
  expect(store.resolve(search, scope).selectionRef).toBe(second);
});
test('different viewed content makes the original search ambiguous, explicit fixed references remain precise', () => {
  const { store, search } = setup();
  const first = store.stage(search, scope, 'first', snapshot);
  store.acknowledge(first, scope, 'first');
  const second = store.stage(search, scope, 'second', { ...snapshot, fingerprint: 'changed' });
  store.acknowledge(second, scope, 'second');
  expect(() => store.resolve(search, scope)).toThrow();
  expect(store.resolve(first, scope).snapshot.fingerprint).toBe('original');
  expect(store.resolve(second, scope).snapshot.fingerprint).toBe('changed');
});
test('session, project and source drift reject both search and fixed references', () => {
  const { store, search } = setup();
  const fixed = store.stage(search, scope, 'detail', snapshot);
  store.acknowledge(fixed, scope, 'detail');
  for (const changed of [{ ...scope, sessionId: 'other' }, { ...scope, cwd: '/other' }, { ...scope, sourceRevision: 'other' }]) {
    expect(() => store.resolve(search, changed)).toThrow();
    expect(() => store.resolve(fixed, changed)).toThrow();
  }
  expect(() => store.stage(search, scope, 'wrong', { ...snapshot, version: '2.0.0' })).toThrow();
  store.clear();
  expect(() => store.resolve(fixed, scope)).toThrow();
});
test('eviction never revives an orphan fixed reference', () => {
  const { store, search } = setup();
  const fixed = store.stage(search, scope, 'detail', snapshot);
  store.acknowledge(fixed, scope, 'detail');
  for (let i = 0; i < 399; i++) store.add({ ...scope, callId: String(i) }, snapshot);
  expect(() => store.resolve(fixed, scope)).toThrow();
});

test('direct installation verifies once without acknowledging a displayed detail', async () => {
  const { store, search } = setup();
  let calls = 0;
  const pending = Promise.withResolvers<{ detailToken: string; snapshot: typeof snapshot }>();
  const verify = async (candidate: { version: string }) => {
    calls++;
    expect(candidate.version).toBe('1.0.0');
    return pending.promise;
  };
  const first = store.resolveForInstall(search, scope, verify);
  const second = store.resolveForInstall(search, scope, verify);
  pending.resolve({ detailToken: 'verified', snapshot });
  expect(await first).toEqual(await second);
  expect(calls).toBe(1);
  expect(() => store.resolve(search, scope)).toThrow();
  const result = await store.resolveForInstall(search, scope, verify);
  result.snapshot.fingerprint = 'tampered';
  expect((await store.resolveForInstall(search, scope, verify)).snapshot.fingerprint).toBe('original');
  expect(calls).toBe(1);
});

test('failed verification is retryable and cannot promote a pending UI selection', async () => {
  const { store, search } = setup();
  const fixed = store.stage(search, scope, 'pending-ui', snapshot);
  const verify = async () => ({ detailToken: 'verified', snapshot });
  await expect(store.resolveForInstall(fixed, scope, verify)).rejects.toThrow();
  await expect(store.resolveForInstall(search, scope, async () => { throw new Error('unavailable'); })).rejects.toThrow('unavailable');
  expect(() => store.resolve(search, scope)).toThrow();
  await expect(store.resolveForInstall(search, scope, async () => ({ detailToken: 'wrong', snapshot: { ...snapshot, version: '2.0.0' } }))).rejects.toThrow();
  expect((await store.resolveForInstall(search, scope, verify)).detailToken).toBe('verified');
  expect(() => store.resolve(search, scope)).toThrow();
});

test('direct installation retains viewed snapshots and rejects ambiguity and stale scope', async () => {
  const { store, search } = setup();
  const verify = async () => { throw new Error('must not fetch'); };
  const fixed = store.stage(search, scope, 'viewed', snapshot);
  store.acknowledge(fixed, scope, 'viewed');
  expect((await store.resolveForInstall(search, scope, verify)).snapshot).toEqual(snapshot);
  const second = store.stage(search, scope, 'changed', { ...snapshot, fingerprint: 'changed' });
  store.acknowledge(second, scope, 'changed');
  await expect(store.resolveForInstall(search, scope, verify)).rejects.toThrow();
  expect((await store.resolveForInstall(fixed, scope, verify)).snapshot).toEqual(snapshot);
  for (const changed of [{ ...scope, cwd: '/other' }, { ...scope, sessionId: 'other' }, { ...scope, sourceRevision: 'other' }]) {
    await expect(store.resolveForInstall(search, changed, verify)).rejects.toThrow();
  }
});

test('verification completion cannot revive cleared references or override a newly viewed snapshot', async () => {
  for (const change of ['clear', 'view', 'ambiguous']) {
    const { store, search } = setup();
    const pending = Promise.withResolvers<{ detailToken: string; snapshot: typeof snapshot }>();
    const outcome = store.resolveForInstall(search, scope, () => pending.promise);
    if (change === 'clear') store.clear();
    else {
      const fixed = store.stage(search, scope, 'viewed', { ...snapshot, fingerprint: 'displayed' });
      store.acknowledge(fixed, scope, 'viewed');
      if (change === 'ambiguous') {
        const second = store.stage(search, scope, 'second', snapshot);
        store.acknowledge(second, scope, 'second');
      }
    }
    pending.resolve({ detailToken: 'verified', snapshot });
    if (change === 'view') expect((await outcome).snapshot.fingerprint).toBe('displayed');
    else await expect(outcome).rejects.toThrow();
  }
});
