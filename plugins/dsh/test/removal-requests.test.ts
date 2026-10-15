import { expect, test } from 'bun:test';
import { randomUUID } from 'node:crypto';
import { RemovalRequests, UpgradeRequests, upgradeRequestKey } from '../src/client/upgrade-requests.js';
import type { HostExecutePlanRequest, NativeOperation } from '../src/host/types.js';

function fixture(kind: 'uninstall' | 'remove-stale-record') {
  const data = new Map<string, string>();
  const storage = { get length() { return data.size; }, key: (index: number) => [...data.keys()][index] ?? null,
    getItem: (key: string) => data.get(key) ?? null, setItem: (key: string, value: string) => { data.set(key, value); },
    removeItem: (key: string) => { data.delete(key); } };
  const operation: NativeOperation = { operationId: randomUUID(), revision: 0, kind, namespace: 'demo', slug: 'writer', version: '1',
    targetLabel: 'Project', phase: 'accepted', files: 'pending', discovery: 'unconfirmed', recovery: 'not-needed', warningCount: 0,
    context: { sourceIdentity: 'https://registry.example', sourceLabel: 'Registry', sourceRevision: 'source', hostLabel: 'Host',
      hostInstanceId: randomUUID(), initiatedFrom: 'plaza', createdAt: '2026-10-14T00:00:00Z', updatedAt: '2026-10-14T00:00:00Z' } };
  const key = upgradeRequestKey('session', operation.context.sourceIdentity, { namespace: 'demo', slug: 'writer', scope: 'project' });
  return { data, storage, operation, key };
}

test.each(['uninstall', 'remove-stale-record'] as const)('%s remains discoverable without an installed row after response loss', async kind => {
  const f = fixture(kind);
  const host = f.operation.context.hostInstanceId;
  const sent: HostExecutePlanRequest[] = [];
  const first = new RemovalRequests(() => f.storage);
  await first.submit(f.key, randomUUID(), host, async request => { sent.push(request); throw new Error('Response lost'); });
  first.dispose();
  const next = new RemovalRequests(() => f.storage);
  expect(next.list('session', f.operation.context.sourceIdentity)).toEqual([{ key: f.key, namespace: 'demo', slug: 'writer', scope: 'project' }]);
  expect(next.list('foreign', f.operation.context.sourceIdentity)).toEqual([]);
  expect(next.list('session', 'https://other.example')).toEqual([]);
  const gate = Promise.withResolvers<NativeOperation>();
  const send = async (request: HostExecutePlanRequest) => { sent.push(request); return gate.promise; };
  const pending = next.submit(f.key, randomUUID(), randomUUID(), send);
  expect(next.submit(f.key, randomUUID(), host, send)).toBe(pending);
  gate.resolve(f.operation); await pending;
  expect(sent).toHaveLength(2);
  expect(sent[1]).toEqual(sent[0]);
  const reloaded = new RemovalRequests(() => f.storage);
  expect(reloaded.get(f.key)).toMatchObject({ state: 'accepted', hostInstanceId: host, operationId: f.operation.operationId });
  expect(() => reloaded.closeResult(f.key, { ...f.operation, phase: 'recovery-required' })).toThrow();
  const upgrade = new UpgradeRequests(() => f.storage);
  expect(upgrade.get(f.key)).toBeUndefined();
  await upgrade.submit(f.key, randomUUID(), host, async () => ({ ...f.operation, kind: 'upgrade' }));
  reloaded.closeResult(f.key, { ...f.operation, phase: 'completed' });
  expect(reloaded.list('session', f.operation.context.sourceIdentity)).toEqual([]);
  expect(upgrade.get(f.key)?.state).toBe('accepted');
  next.dispose(); reloaded.dispose(); upgrade.dispose();
});

test('removal rejects another operation kind and refuses writes after corrupt storage discovery', async () => {
  const f = fixture('uninstall');
  const store = new RemovalRequests(() => f.storage);
  await store.submit(f.key, randomUUID(), f.operation.context.hostInstanceId, async () => ({ ...f.operation, kind: 'upgrade' }));
  expect(store.get(f.key)?.state).toBe('unresolved');
  f.data.set('skillhub:removal:v1:not-json', '{}');
  expect(() => store.list('session', f.operation.context.sourceIdentity)).toThrow('无法读取');
  let sent = false;
  await expect(store.submit(f.key, randomUUID(), f.operation.context.hostInstanceId, async () => { sent = true; return null; })).rejects.toThrow('无法读取');
  expect(sent).toBe(false);
  store.dispose();
});
