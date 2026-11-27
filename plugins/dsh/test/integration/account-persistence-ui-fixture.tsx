import { createRoot } from 'react-dom/client';
import { QueryClient, focusManager } from '@tanstack/react-query';
import { CatalogPage, createCatalogNavigation } from '../../src/client/catalog-page.js';
import { UiAvailability } from '../../src/client/ui-availability.js';
import type { AccountRemote } from '../../src/client/account-common.js';

// Real CatalogPage/revision remounts, with simulated Host state and no credentials or network.
const first = 'https://first.example.test';
const second = 'https://second.example.test';
let source = first;
let epoch = 1;
let removalBlocked = true;
let restoreFailed = new URLSearchParams(location.search).has('restore');
const networkFailed = new URLSearchParams(location.search).has('network');
let restoreWasFailed = restoreFailed;
let identityCalls = 0;
const identity = { handle: 'fixture-user', displayName: '测试用户' };
const saved = new Map([[first, true], [second, true]]);
const connected = new Map(saved);
const failures = new Set<string>();
const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
const revision = () => `fixture-${epoch}`;
function fail(code: string, reason?: string): never { throw { code, ...(reason ? { details: { reason } } : {}) }; }
const api = {
  getContext: () => ({ sourceRevision: revision(), registryUrl: source, configured: true, accountClearFailed: failures.has(source),
    detailTabs: { versions: 'hide', reviews: 'hide', securityAudit: 'hide' } }),
  getCompatibility: () => ({ dependencyBaseline: 'verified', candidateDshVersion: '>=0.2.0-rc.2', localWritesAllowed: false }),
  listCatalog: () => ({ items: [], total: 0, page: 0, size: 12, hasMore: false, mode: 'paged' }),
  listLabels: () => ({ items: [], available: true }),
  listFavorites: () => ({ items: [] }),
  accountIdentity: (r: string) => {
    if (r !== revision()) fail('skillhub/context-stale');
    identityCalls++;
    if (!connected.get(source)) return null;
    if (restoreFailed) fail('skillhub/account-storage-error', 'RESTORE_FAILED');
    if (networkFailed) fail('skillhub/registry-error', 'NETWORK_ERROR');
    if (restoreWasFailed) { restoreWasFailed = false; epoch++; fail('skillhub/context-stale'); }
    return connected.get(source) ? identity : null;
  },
  connectAccount: () => fail('skillhub/account-storage-error', 'SAVE_FAILED'),
  disconnectAccount: (r: string) => {
    if (r !== revision()) fail('skillhub/context-stale');
    connected.set(source, false); epoch++;
    if (removalBlocked) { failures.add(source); fail('skillhub/account-storage-error', 'CLEAR_FAILED'); }
    saved.set(source, false); failures.delete(source); return true;
  },
};
const remote = Object.fromEntries(Object.entries(api).map(([name, fn]) => [name, async (...args: unknown[]) => {
  try { return { ok: true, value: await Reflect.apply(fn, null, args) }; }
  catch (error) { return { ok: false, error }; }
}])) as unknown as AccountRemote;
Object.assign(globalThis, {
  persistenceFixture: {
    snapshot: () => ({ identityCalls, firstSaved: saved.get(first), secondSaved: saved.get(second), revision: revision(),
      privateCache: client.getQueryCache().getAll().some(q => q.queryKey[0] === 'account-owned' && q.state.data !== undefined) }),
    seedPrivate: () => client.setQueryData(['account-owned', revision(), 'fixture', 0], { items: [{ id: 'private-fixture' }] }),
    switchSource: async (other: boolean) => { source = other ? second : first; epoch++; await client.invalidateQueries({ queryKey: ['skillhub-context'] }); },
    allowRemoval: () => { removalBlocked = false; },
    restoreAvailable: () => { restoreFailed = false; },
    focus: (value: boolean) => focusManager.setFocused(value),
    restart: async () => { connected.set(source, !!saved.get(source)); failures.clear(); epoch++; await client.invalidateQueries({ queryKey: ['skillhub-context'] }); },
  },
});
const availability = new UiAvailability();
for (const entry of ['main', 'sidebar', 'conversation', 'configuration'] as const) availability.enable(entry);
createRoot(document.getElementById('root')!).render(<CatalogPage remote={remote} navigation={createCatalogNavigation()} client={client}
  availability={availability} onReturn={() => {}} />);
