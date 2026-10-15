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
