import { expect, test } from 'bun:test';
import { randomUUID } from 'node:crypto';
import { InstallationRequests } from '../src/client/installation-requests.js';
import type { HostInstallRequest, NativeOperation } from '../src/host/types.js';

const input = { detailToken: 'detail', targetToken: 'target', hostInstanceId: randomUUID(), initiatedFrom: 'conversation' as const };
const operation: NativeOperation = { operationId: randomUUID(), revision: 0, kind: 'install', namespace: 'team', slug: 'writer', version: '1',
  targetLabel: 'Project', phase: 'accepted', files: 'pending', discovery: 'unconfirmed', recovery: 'not-needed', warningCount: 0,
  context: { sourceIdentity: 'https://registry.example', sourceLabel: 'Registry', sourceRevision: 'source', hostLabel: 'Host',
    hostInstanceId: input.hostInstanceId, initiatedFrom: 'conversation', createdAt: '2026-10-14T00:00:00Z', updatedAt: '2026-10-14T00:00:00Z' } };

test('concurrent surfaces share one submission and an accepted operation survives subscriber remount', async () => {
  const store = new InstallationRequests();
  const response = Promise.withResolvers<NativeOperation>();
  let sends = 0;
  const send = async () => { sends++; return response.promise; };
  const first = store.submit('identity', input, send);
  expect(store.submit('identity', input, send)).toBe(first);
  expect(store.get('identity')?.state).toBe('submitting');
  response.resolve(operation);
  await first;
  const unsubscribe = store.subscribe(() => {});
  unsubscribe();
  await store.submit('identity', { ...input, detailToken: 'refreshed' }, send);
  expect(sends).toBe(1);
  expect(store.get('identity')?.operation).toEqual(operation);
  store.dispose();
});

test('explicit reinstall supersedes only the expected receipt with a fresh durable key and deduplicates clicks', async () => {
  const storage = storageFixture();
  const store = new InstallationRequests(() => storage);
  const sent: HostInstallRequest[] = [];
  await store.submit('identity', input, async request => { sent.push(request); return operation; });
  const next = { ...operation, operationId: randomUUID() };
  const response = Promise.withResolvers<NativeOperation>();
  const send = async (request: HostInstallRequest) => { sent.push(request); return response.promise; };
  const first = store.submit('identity', { ...input, detailToken: 'fresh-detail' }, send, operation.operationId);
  expect(store.submit('identity', input, send, operation.operationId)).toBe(first);
  response.resolve(next); await first;
  expect(sent).toHaveLength(2);
  expect(sent[1]!.requestKey).not.toBe(sent[0]!.requestKey);
  expect(sent[1]).toMatchObject({ mode: 'new', detailToken: 'fresh-detail' });
  const reloaded = new InstallationRequests(() => storage);
  expect(reloaded.get('identity')?.operationId).toBe(next.operationId);
  await expect(store.submit('identity', input, send, operation.operationId)).rejects.toThrow('已变化');
  expect(sent).toHaveLength(2);
});

test('failed reinstall response retries the new key and never resurrects the original receipt', async () => {
  const store = new InstallationRequests();
  await store.submit('identity', input, async () => operation);
  let failed: HostInstallRequest | undefined;
  await store.submit('identity', input, async request => { failed = request; throw new Error('lost'); }, operation.operationId);
  expect(store.get('identity')?.state).toBe('unresolved');
  await expect(store.submit('identity', input, async () => operation, operation.operationId)).rejects.toThrow('已变化');
  await store.submit('identity', input, async request => {
    expect(request).toEqual({ ...failed!, mode: 'retry' });
    return { ...operation, operationId: randomUUID() };
  });
});

test('response loss retries the exact original arguments and key even when new references are offered', async () => {
  const store = new InstallationRequests();
  const sent: HostInstallRequest[] = [];
  await store.submit('identity', input, async request => { sent.push(request); throw new Error('Response lost'); });
  expect(store.get('identity')?.state).toBe('unresolved');
  await store.submit('identity', { ...input, detailToken: 'new', targetToken: 'new', initiatedFrom: 'plaza' }, async request => {
    sent.push(request); return operation;
  });
  expect(sent[0]?.mode).toBe('new');
  expect(sent[1]).toEqual({ ...sent[0]!, mode: 'retry' });
  expect(store.get('identity')?.state).toBe('accepted');
  store.dispose();
});

test('a missing retry record stays unresolved and disposal prevents late responses from restoring state', async () => {
  const store = new InstallationRequests();
  await store.submit('identity', input, async () => null);
  const key = store.get('identity')!.request.requestKey;
  await store.submit('identity', input, async request => { expect(request.mode).toBe('retry'); expect(request.requestKey).toBe(key); return null; });
  expect(store.get('identity')?.state).toBe('unresolved');
  const response = Promise.withResolvers<NativeOperation>();
  const pending = store.submit('other', input, async () => response.promise);
  store.dispose();
  response.resolve(operation);
  await pending;
  expect(store.get('other')).toBeUndefined();
  await expect(store.submit('new', input, async () => operation)).rejects.toThrow('unavailable');
});

function storageFixture() {
  const data = new Map<string, string>();
  return { data, getItem: (key: string) => data.get(key) ?? null, setItem: (key: string, value: string) => { data.set(key, value); } };
}

test('reload restores only an operation reference, never a cached success, without another submission', async () => {
  const storage = storageFixture();
  const first = new InstallationRequests(() => storage);
  await first.submit('identity', input, async () => operation);
  first.dispose();
  const reloaded = new InstallationRequests(() => storage);
  expect(reloaded.get('identity')).toMatchObject({ state: 'accepted', operation: null, operationId: operation.operationId });
  await reloaded.submit('identity', input, async () => { throw new Error('Must query operation instead'); });
  expect([...storage.data.values()][0]).not.toContain('files');
});

test('response loss survives reload with the exact original key and references', async () => {
  const storage = storageFixture();
  const first = new InstallationRequests(() => storage);
  let original: HostInstallRequest | undefined;
  await first.submit('identity', input, async request => { original = request; throw new Error('lost'); });
  first.dispose();
  const next = new InstallationRequests(() => storage);
  expect(next.get('identity')?.state).toBe('unresolved');
  await next.submit('identity', { ...input, detailToken: 'new-reference' }, async request => {
    expect(request).toEqual({ ...original!, mode: 'retry' });
    return operation;
  });
});

test('unavailable, full or corrupt browser storage blocks sends instead of losing request identity', async () => {
  const corrupt = storageFixture();
  corrupt.setItem('skillhub:installation:v1:identity', '{bad json');
  const full = storageFixture();
  full.setItem = () => { throw new Error('quota'); };
  for (const storage of [() => { throw new Error('denied'); }, () => full, () => corrupt]) {
    const store = new InstallationRequests(storage);
    let sent = false;
    await expect(store.submit('identity', input, async () => { sent = true; return operation; })).rejects.toThrow();
    expect(sent).toBe(false);
  }
});

test('failure to save a received operation still preserves the pre-send request for reload', async () => {
  const storage = storageFixture();
  const write = storage.setItem;
  let writes = 0;
  storage.setItem = (key, value) => { if (++writes > 1) throw new Error('quota'); write(key, value); };
  const first = new InstallationRequests(() => storage);
  await first.submit('identity', input, async () => operation);
  expect(first.get('identity')?.state).toBe('accepted');
  const next = new InstallationRequests(() => storage);
  expect(next.get('identity')?.state).toBe('unresolved');
  expect(next.get('identity')?.request.requestKey).toBe(first.get('identity')!.request.requestKey);
});
