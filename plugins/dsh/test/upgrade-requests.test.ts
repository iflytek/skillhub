import { expect, test } from 'bun:test';
import { randomUUID } from 'node:crypto';
import { UpgradeRequests, upgradeRequestKey } from '../src/client/upgrade-requests.js';
import type { HostExecutePlanRequest, NativeOperation } from '../src/host/types.js';

function fixture() {
  const host = randomUUID();
  const data = new Map<string, string>();
  const storage = { get length() { return data.size; }, key: (index: number) => [...data.keys()][index] ?? null, getItem: (key: string) => data.get(key) ?? null, setItem: (key: string, value: string) => { data.set(key, value); }, removeItem: (key: string) => { data.delete(key); } };
  const operation: NativeOperation = { operationId: randomUUID(), revision: 0, kind: 'upgrade', namespace: 'demo', slug: 'writer', version: '2',
    targetLabel: 'Project', phase: 'accepted', files: 'pending', discovery: 'unconfirmed', recovery: 'not-needed', warningCount: 0,
    context: { sourceIdentity: 'https://registry.example', sourceLabel: 'Registry', sourceRevision: 'source', hostLabel: 'Host',
      hostInstanceId: host, initiatedFrom: 'plaza', createdAt: '2026-10-14T00:00:00Z', updatedAt: '2026-10-14T00:00:00Z' } };
  return { host, data, storage, operation };
}

test('upgrade response loss preserves exact arguments across reload and duplicate clicks', async () => {
  const f = fixture();
  const first = new UpgradeRequests(() => f.storage);
  const sent: HostExecutePlanRequest[] = [];
  await first.submit('identity', randomUUID(), f.host, async request => { sent.push(request); throw new Error('lost response'); });
  expect(first.get('identity')?.state).toBe('unresolved');
  first.dispose();
  const next = new UpgradeRequests(() => f.storage);
  expect(next.get('identity')?.request).toEqual(sent[0]);
  const gate = Promise.withResolvers<NativeOperation>();
  const send = async (request: HostExecutePlanRequest) => { sent.push(request); return gate.promise; };
  const pending = next.submit('identity', randomUUID(), randomUUID(), send);
  expect(next.submit('identity', randomUUID(), f.host, send)).toBe(pending);
  gate.resolve(f.operation); await pending;
  expect(sent).toHaveLength(2);
  expect(sent[1]).toEqual(sent[0]);
  const reload = new UpgradeRequests(() => f.storage);
  expect(reload.get('identity')).toMatchObject({ state: 'accepted', hostInstanceId: f.host, operationId: f.operation.operationId });
  await reload.submit('identity', randomUUID(), f.host, async () => { throw new Error('must not send'); });
  next.dispose(); reload.dispose();
});

test('upgrade never sends when request persistence fails', async () => {
  let sends = 0;
  const store = new UpgradeRequests(() => ({ getItem: () => null, setItem: () => { throw new Error('quota'); }, removeItem: () => {} }));
  await expect(store.submit('identity', randomUUID(), randomUUID(), async () => { sends++; return null; })).rejects.toThrow('尚未提交');
  expect(sends).toBe(0); store.dispose();
});

test('foreign Host receipts remain unresolved and disposal ignores late responses', async () => {
  const f = fixture();
  const store = new UpgradeRequests(() => f.storage);
  await store.submit('identity', randomUUID(), randomUUID(), async () => f.operation);
  expect(store.get('identity')?.state).toBe('unresolved');
  const gate = Promise.withResolvers<NativeOperation>();
  const task = store.submit('another', randomUUID(), f.host, () => gate.promise);
  store.dispose(); gate.resolve(f.operation); await task;
  expect(store.get('another')).toBeUndefined();
});

test('version changes retain the original request until a verified terminal result is explicitly closed', async () => {
  const f = fixture();
  const store = new UpgradeRequests(() => f.storage);
  const oldItem = { namespace: 'demo', slug: 'writer', scope: 'project', version: '1' };
  const updatedItem = { ...oldItem, version: '2' };
  const key = upgradeRequestKey('session', 'source', oldItem);
  expect(upgradeRequestKey('session', 'source', updatedItem)).toBe(key);
  const plan = randomUUID();
  await store.submit(key, plan, f.host, async () => f.operation);
  expect(() => store.closeResult(key, f.operation)).toThrow('尚未完成核验');
  expect(() => store.closeResult(key, { ...f.operation, phase: 'recovery-required' })).toThrow();
  expect(() => store.closeResult(key, { ...f.operation, phase: 'completed', operationId: randomUUID() })).toThrow();
  const previousKey = store.get(key)!.request.requestKey;
  store.closeResult(key, { ...f.operation, phase: 'completed' });
  expect(store.get(key)).toBeUndefined();
  const next = new UpgradeRequests(() => f.storage);
  expect(next.get(key)).toBeUndefined();
  await next.submit(key, randomUUID(), f.host, async () => null);
  expect(next.get(key)!.request.requestKey).not.toBe(previousKey);
  store.dispose(); next.dispose();
});

test('upgrade history survives missing inventory and reload without changing the original request', async () => {
  const f = fixture();
  const key = upgradeRequestKey('session', f.operation.context.sourceIdentity, { namespace: 'demo', slug: 'writer', scope: 'project' });
  const first = new UpgradeRequests(() => f.storage);
  let original: HostExecutePlanRequest | undefined;
  await first.submit(key, randomUUID(), f.host, async request => { original = request; throw new Error('Response lost'); });
  first.dispose();
  const history = new UpgradeRequests(() => f.storage);
  expect(history.list('session', f.operation.context.sourceIdentity)).toEqual([{ key, namespace: 'demo', slug: 'writer', scope: 'project' }]);
  expect(history.list('foreign', f.operation.context.sourceIdentity)).toEqual([]);
  expect(history.list('session', 'https://other.example')).toEqual([]);
  await history.submit(key, randomUUID(), randomUUID(), async request => {
    if (!original) throw new Error('Missing original request');
    expect(request).toEqual(original);
    return { ...f.operation, phase: 'recovery-required' };
  });
  const reload = new UpgradeRequests(() => f.storage);
  expect(reload.list('session', f.operation.context.sourceIdentity)).toHaveLength(1);
  expect(reload.get(key)).toMatchObject({ state: 'accepted', operationId: f.operation.operationId });
  expect(() => reload.closeResult(key, { ...f.operation, phase: 'recovery-required' })).toThrow();
  reload.closeResult(key, { ...f.operation, phase: 'failed', recovery: 'restored' });
  expect(reload.list('session', f.operation.context.sourceIdentity)).toEqual([]);
  history.dispose(); reload.dispose();
});

test('invalid upgrade history blocks new writes instead of silently discarding the original request', async () => {
  const f = fixture();
  f.data.set('skillhub:upgrade:v1:not-json', '{}');
  const requests = new UpgradeRequests(() => f.storage);
  expect(() => requests.list('session', f.operation.context.sourceIdentity)).toThrow('无法读取');
  let sent = false;
  await expect(requests.submit('identity', randomUUID(), f.host, async () => { sent = true; return null; })).rejects.toThrow('无法读取');
  expect(sent).toBe(false);
  requests.dispose();
});
