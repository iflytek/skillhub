import { expect, test } from 'bun:test';
import { MutationObserver, QueryClient, QueryObserver, isCancelledError } from '@tanstack/react-query';
import type { FavoriteList, FavoriteSkill } from '../src/host/types.js';
import { favoriteIdentity, favoriteSnapshot, favoritesKey, filterFavorites, resolvedFavoriteItem } from '../src/client/favorites.js';
import { favoriteMutationOptions } from '../src/client/use-favorites.js';

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(done => { resolve = done; });
  return { promise, resolve };
}
const first: FavoriteSkill = { namespace: 'team', slug: 'writer', displayName: '文档助手', summary: 'Write notes' };
const second: FavoriteSkill = { namespace: 'another', slug: 'writer', displayName: 'Another Writer' };

test('favorite snapshots are bounded public identity and text, never a version, token or market count', () => {
  const item = { ...first, displayName: '长'.repeat(300), summary: '文'.repeat(1100), latestVersion: '1.2.0', detailToken: 'private', starCount: 42 };
  const snapshot = favoriteSnapshot(item);
  expect(snapshot).toEqual({ namespace: 'team', slug: 'writer', displayName: '长'.repeat(256), summary: '文'.repeat(1024) });
  expect(item.starCount).toBe(42);
  expect(favoriteIdentity(first)).not.toBe(favoriteIdentity(second));
  expect(favoriteIdentity({ namespace: 'a/b', slug: 'c' })).not.toBe(favoriteIdentity({ namespace: 'a', slug: 'b/c' }));
});

test('favorite search covers the complete local list independently of catalog pages', () => {
  const items = Array.from({ length: 30 }, (_, index) => ({ namespace: 'demo', slug: `skill-${index}`, displayName: index === 29 ? '跨页收藏' : `Skill ${index}` }));
  expect(filterFavorites(items, '跨页')).toEqual([items[29]!]);
  expect(filterFavorites([first, second], 'ＡＮＯＴＨＥＲ')).toEqual([second]);
  expect(filterFavorites([first], 'write notes')).toEqual([first]);
  expect(filterFavorites(items, '   ')).toBe(items);
});

test('only a fresh matching version resolution can create an installable catalog selection', () => {
  expect(resolvedFavoriteItem(first, { namespace: 'team', slug: 'writer', version: '2.0.0' })).toMatchObject({ latestVersion: '2.0.0', summary: 'Write notes' });
  for (const version of [{ namespace: 'other', slug: 'writer', version: '2' }, { namespace: 'team', slug: 'other', version: '2' }, { namespace: 'team', slug: 'writer', version: '  ' }]) {
    expect(() => resolvedFavoriteItem(first, version)).toThrow('技能版本暂时无法读取');
  }
});

test('saving cancels an older list read before committing the confirmed snapshot into its own source cache', async () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const stale = deferred<FavoriteList>(); const saved: FavoriteList = { items: [first] };
  const reading = client.fetchQuery({ queryKey: favoritesKey('source-a'), queryFn: () => stale.promise }).catch(error => error);
  const mutation = new MutationObserver(client, favoriteMutationOptions({ setFavorite: async () => ({ ok: true, value: saved }) }, client, 'source-a'));
  try {
    client.setQueryData(favoritesKey('source-b'), { items: [second] });
    await mutation.mutate({ sourceRevision: 'source-a', item: first, favorite: true, signal: new AbortController().signal });
    stale.resolve({ items: [] });
    expect(isCancelledError(await reading)).toBe(true);
    expect(client.getQueryData<FavoriteList>(favoritesKey('source-a'))).toEqual(saved);
    expect(client.getQueryData<FavoriteList>(favoritesKey('source-b'))).toEqual({ items: [second] });
  } finally { client.clear(); }
});

test('same-source saves serialize across observers and a failed write preserves the previous cache', async () => {
  const client = new QueryClient(); const entered = deferred<void>(); const release = deferred<void>();
  let calls = 0;
  const remote = { setFavorite: async () => {
    calls++;
    if (calls === 1) { entered.resolve(); await release.promise; return { ok: true as const, value: { items: [first] } }; }
    return { ok: true as const, value: { items: [first, second] } };
  } };
  const options = favoriteMutationOptions(remote, client, 'source');
  const a = new MutationObserver(client, options), b = new MutationObserver(client, options);
  try {
    const firstSave = a.mutate({ sourceRevision: 'source', item: first, favorite: true, signal: new AbortController().signal });
    await entered.promise;
    const secondSave = b.mutate({ sourceRevision: 'source', item: second, favorite: true, signal: new AbortController().signal });
    await Promise.resolve(); expect(calls).toBe(1);
    release.resolve(); await Promise.all([firstSave, secondSave]);
    expect(client.getQueryData<FavoriteList>(favoritesKey('source'))).toEqual({ items: [first, second] });
    const failing = new MutationObserver(client, favoriteMutationOptions({ setFavorite: async () => { throw new Error('unavailable'); } }, client, 'source'));
    await expect(failing.mutate({ sourceRevision: 'source', item: first, favorite: false, signal: new AbortController().signal })).rejects.toThrow('unavailable');
    expect(client.getQueryData<FavoriteList>(favoritesKey('source'))).toEqual({ items: [first, second] });
  } finally { client.clear(); }
});

test('a queued cancelled save never transports or changes another source', async () => {
  const client = new QueryClient(); let calls = 0;
  const entered = deferred<void>(), release = deferred<void>();
  const controller = new AbortController();
  const options = favoriteMutationOptions({ setFavorite: async () => {
    calls++; entered.resolve(); await release.promise;
    return { ok: true, value: { items: [first] } };
  } }, client, 'old-source');
  const firstMutation = new MutationObserver(client, options), queued = new MutationObserver(client, options);
  try {
    client.setQueryData(favoritesKey('new-source'), { items: [second] });
    const pending = firstMutation.mutate({ sourceRevision: 'old-source', item: first, favorite: true, signal: new AbortController().signal });
    await entered.promise;
    const cancelled = queued.mutate({ sourceRevision: 'old-source', item: second, favorite: true, signal: controller.signal }).then(() => null, (error: unknown) => error);
    controller.abort(); release.resolve(); await pending;
    expect(await cancelled).toMatchObject({ name: 'AbortError' });
    expect(calls).toBe(1); expect(client.getQueryData<FavoriteList>(favoritesKey('old-source'))).toEqual({ items: [first] });
    expect(client.getQueryData<FavoriteList>(favoritesKey('new-source'))).toEqual({ items: [second] });
  } finally { client.clear(); }
});

test('a confirmed write updates active readers without another read, while a lost response reconciles from disk', async () => {
  const client = new QueryClient();
  let reads = 0, writes = 0, loseResponse = false;
  let stored: FavoriteList = { items: [] };
  const reader = new QueryObserver(client, {
    queryKey: favoritesKey('source-a'), initialData: stored, staleTime: Infinity,
    queryFn: async () => { reads++; return stored; },
  });
  const unsubscribe = reader.subscribe(() => {});
  const mutation = new MutationObserver(client, favoriteMutationOptions({ setFavorite: async (_revision, item, favorite) => {
    writes++; stored = { items: favorite ? [item] : [] };
    if (loseResponse) throw new Error('response lost after write');
    return { ok: true, value: stored };
  } }, client, 'source-a'));
  try {
    client.setQueryData(favoritesKey('source-b'), { items: [second] });
    const input = { sourceRevision: 'source-a', item: first, favorite: true, signal: new AbortController().signal };
    await mutation.mutate(input);
    expect(writes).toBe(1); expect(reads).toBe(0);
    expect(reader.getCurrentResult().data).toEqual({ items: [first] });
    for (const text of ['', '文档', 'writer', 'not-found']) filterFavorites(reader.getCurrentResult().data!.items, text);
    expect(reads).toBe(0); expect(writes).toBe(1);
    loseResponse = true;
    await expect(mutation.mutate({ ...input, favorite: false })).rejects.toThrow('response lost after write');
    expect(writes).toBe(2); expect(reads).toBe(1);
    expect(reader.getCurrentResult().data).toEqual({ items: [] });
    expect(client.getQueryData<FavoriteList>(favoritesKey('source-b'))).toEqual({ items: [second] });
  } finally { unsubscribe(); client.clear(); }
});

test('a read started during a pending write cannot replace the confirmed list with its older snapshot', async () => {
  const client = new QueryClient();
  const entered = deferred<void>(), release = deferred<void>(), stale = deferred<FavoriteList>();
  const mutation = new MutationObserver(client, favoriteMutationOptions({ setFavorite: async () => {
    entered.resolve(); await release.promise;
    return { ok: true, value: { items: [first] } };
  } }, client, 'source-a'));
  let reads = 0, readAborted = false;
  try {
    client.setQueryData(favoritesKey('source-a'), { items: [] });
    client.setQueryData(favoritesKey('source-b'), { items: [second] });
    const saving = mutation.mutate({ sourceRevision: 'source-a', item: first, favorite: true, signal: new AbortController().signal });
    await entered.promise;
    // A focus/reconnect read may begin after onMutate cancelled earlier reads.
    const reading = client.fetchQuery({ queryKey: favoritesKey('source-a'), queryFn: ({ signal }) => {
      reads++; signal.addEventListener('abort', () => { readAborted = true; }, { once: true });
      return stale.promise;
    } }).catch(error => error);
    release.resolve(); await saving;
    expect(client.getQueryData<FavoriteList>(favoritesKey('source-a'))).toEqual({ items: [first] });
    stale.resolve({ items: [] });
    await reading;
    expect(readAborted).toBe(true);
    expect(client.getQueryData<FavoriteList>(favoritesKey('source-a'))).toEqual({ items: [first] });
    expect(client.getQueryData<FavoriteList>(favoritesKey('source-b'))).toEqual({ items: [second] });
    expect(reads).toBe(1);
  } finally { release.resolve(); stale.resolve({ items: [] }); client.clear(); }
});
