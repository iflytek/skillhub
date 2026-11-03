import { createRoot } from 'react-dom/client';
import { useState } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { InstalledSkills, type InstalledServices } from '../../src/client/installed-skills.js';
import { installedNavigationFor } from '../../src/client/installed-navigation.js';
import { UpgradeRequests, RemovalRequests } from '../../src/client/upgrade-requests.js';
import { installedContext } from '../../src/client/installed-context.js';
import type { InstalledSkillList, InstalledUpdateCheck } from '../../src/host/types.js';
import type { TypertRemoteNamespaceMap } from '@deepseek-ai/dsh-typert-protocol';
const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
let session = 'A', source = 'source-A', cwd = 'A';
let items: InstalledSkillList['items'] = ['one', 'two', 'three', 'four'].map(slug => ({ itemKey: slug, slug, namespace: 'demo', scope: 'user', state: 'present', version: '1', installationState: 'enabled' }));
let held = false, staleRound = false;
const begins: string[] = [];
const pending: (() => void)[] = [], listeners = new Set<() => void>(), calls: string[] = [];
const context = installedContext(() => ({ scope: null, cwd, active: true }), listener => { listeners.add(listener); return () => { listeners.delete(listener); }; });
const storage = () => ({ getItem: () => null, setItem() {}, removeItem() {}, key: () => null, length: 0 });
const services = { context, get sessionId() { return session; },
  get management() {
    const captured = cwd;
    return { client, assertCurrent: () => { if (captured !== cwd) throw new Error('stale workspace'); },
      installation: { sessionId: session, installed: async () => ({ ok: true, value: { items, complete: true, issues: [], unavailable: [] } }) },
      begin: async () => { if (captured !== cwd) throw new Error('stale workspace'); begins.push(captured); return { ok: true, value: { status: 'cancelled' } }; },
    };
  }, upgradeRequests: new UpgradeRequests(storage), removalRequests: new RemovalRequests(storage),
  list: async () => ({ ok: true, value: { items, complete: true, issues: [], unavailable: [] } }),
  detail: async (_revision: string, itemKey: string) => ({ ok: true, value: { item: items.find(item => item.itemKey === itemKey), content: { state: 'local-only', reason: 'NOT_FOUND' } } }),
  checkUpdate: async () => ({ ok: true, value: { state: 'upgradable', currentVersion: '1', candidateVersion: '2', reason: '', checkedAt: '' } satisfies InstalledUpdateCheck }),
  beginInstalledUpdateScan: async () => ({ ok: true, value: { scanId: crypto.randomUUID(), itemKeys: items.map(item => item.itemKey) } }),
  endInstalledUpdateScan: async () => ({ ok: true, value: undefined }),
  checkInstalledUpdateScan: async (_revision: string, _scanId: string, key: string) => {
    calls.push(key);
    if (staleRound && key !== items[0]?.itemKey) {
      await new Promise<void>(resolve => pending.push(resolve));
      if (key === items[1]?.itemKey) return { ok: false, error: { code: 'skillhub/context-stale' } };
    }
    if (held) await new Promise<void>(resolve => pending.push(resolve));
    return { ok: true, value: { state: 'upgradable', currentVersion: '1', candidateVersion: '2', reason: '', checkedAt: '' } satisfies InstalledUpdateCheck };
  },
} as unknown as InstalledServices;
let rerender = () => {};
function App() {
  const [, render] = useState(0); rerender = () => render(value => value + 1);
  return <QueryClientProvider client={client}><InstalledSkills services={services} sourceRevision={source} source="https://registry.example"
    remote={{} as TypertRemoteNamespaceMap['skillhub']} modes={{ versions: 'hide', reviews: 'hide', securityAudit: 'hide' }} scrollContainer={{ current: null }} navigation={installedNavigationFor({}, session, source)} /></QueryClientProvider>;
}
Object.assign(window, { fixture: {
  staleRound: async () => {
    staleRound = true; held = false; calls.length = 0;
    items = [...items, ...['five', 'six', 'seven', 'eight'].map(slug => ({ ...items[0]!, slug, itemKey: slug }))];
    await client.invalidateQueries({ queryKey: ['skillhub-installed'] });
  },
  hold: () => { held = true; calls.length = 0; },
  release: () => { held = false; pending.splice(0).forEach(resolve => resolve()); },
  calls: () => [...calls],
  begins: () => [...begins],
  workspace: (value: string) => { cwd = value; listeners.forEach(listener => listener()); },
  source: (value: string) => { source = value; rerender(); },
  session: (value: string) => { session = value; rerender(); },
  inventory: () => { items = items.map(item => item.slug === 'one' ? { ...item, itemKey: 'one-new', version: '2' } : item); return client.invalidateQueries({ queryKey: ['skillhub-installed'] }); },
} });
createRoot(document.getElementById('root')!).render(<App />);
