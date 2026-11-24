import { useState, useSyncExternalStore } from 'react';
import { createRoot } from 'react-dom/client';
import { createPortal } from 'react-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AccountWorkspace, type AccountPageName } from '../../src/client/account-workspace.js';
import type { AccountRemote } from '../../src/client/account-common.js';
import { NativeControlStyles } from '../../src/client/native-control-styles.js';
import { AppearanceToggle } from '../../src/client/appearance-toggle.js';
import { ConfigPage } from '../../src/client/config-page.js';
import { Button } from '../../src/client/ui.js';
import type { ConfigForm, ConfigFormSnapshot } from '@deepseek-ai/dsh-client-ui-settings/client';
import type { SkillHubConfig } from '../../src/host/types.js';

// Isolated UI fixture: no real service, credentials, or filesystem writes.
let identity: { handle: string; displayName: string } | null = null;
const spaces = [{ id: 1, slug: 'design', displayName: '设计团队', type: 'TEAM', status: 'ACTIVE', currentUserRole: 'ADMIN' },
  ...Array.from({ length: 11 }, (_, i) => ({ id: i + 2, slug: `team-${i + 2}`, displayName: `团队 ${i + 2}`, type: 'TEAM', status: 'ACTIVE', currentUserRole: 'MEMBER' }))];
const api = {
  accountIdentity: () => identity,
  connectAccount: async (_revision: string, token: string) => {
    await new Promise(resolve => setTimeout(resolve, 250));
    if (token !== 'fixture-token') throw new Error('Token 无效，请重新输入。');
    identity = { handle: 'fixture-user', displayName: '演示用户' };
    return identity;
  },
  disconnectAccount: () => { identity = null; return true; },
  accountNamespaces: async () => { await new Promise(resolve => setTimeout(resolve, 200)); return spaces; },
  accountOwned: () => ({ items: [{ id: 1, slug: 'example', displayName: '示例技能', visibility: 'PRIVATE', status: 'ACTIVE' }], total: 1 }),
  accountMembers: () => ({ items: [{ userId: 'member', displayName: '团队成员', role: 'MEMBER' }], total: 1 }),
  accountCandidates: () => [{ userId: 'candidate', displayName: '候选用户', email: 'candidate@example.test' }],
  changeAccountMember: async () => { await new Promise(resolve => setTimeout(resolve, 250)); return true; },
  deleteAccountSkill: () => true,
  inspectUpload: () => ({ slug: 'example', existing: null }),
  publishUpload: () => ({ namespace: 'design', slug: 'example', version: '1.0.0', visibility: 'PRIVATE' }),
  uploadStatus: () => ({ status: '待服务端处理' }),
};
const remote = Object.fromEntries(Object.entries(api).map(([name, fn]) => [name, async (...args: unknown[]) => {
  try { return { ok: true, value: await Reflect.apply(fn, null, args) }; }
  catch { return { ok: false, error: { code: 'AUTH_REQUIRED', message: 'Token 无效，请重新输入。' } }; }
}])) as unknown as AccountRemote;
const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
let snapshot: ConfigFormSnapshot<SkillHubConfig> = { status: 'ready', revision: 1, writable: true, base: {}, user: {},
  value: { registryUrl: 'https://registry.example.test', savedRegistries: [{ name: '演示来源', url: 'https://registry.example.test' }, { name: '另一来源', url: 'https://other.example.test' }], detailTabs: { versions: 'auto', securityAudit: 'hide', reviews: 'auto' } } };
const listeners = new Set<() => void>();
const config: ConfigForm<SkillHubConfig> = {
  getSnapshot: () => snapshot,
  subscribe: listener => { listeners.add(listener); return () => { listeners.delete(listener); }; },
  mutate: async ops => {
    const url = ops.find(op => op.op === 'set' && op.path[0] === 'registryUrl');
    if (url?.op !== 'set' || typeof url.value !== 'string') return false;
    if (snapshot.value?.registryUrl !== url.value) identity = null;
    snapshot = { ...snapshot, revision: snapshot.revision! + 1, value: { ...snapshot.value!, registryUrl: url.value } };
    listeners.forEach(listener => listener()); return true;
  }, set: async () => false, unset: async () => false,
};
function Fixture() {
  const [toolbar, setToolbar] = useState<HTMLDivElement | null>(null);
  const [primary, setPrimary] = useState<HTMLDivElement | null>(null);
  const [screen, navigate] = useState<AccountPageName>(null);
  const [settings, showSettings] = useState(false);
  const source = useSyncExternalStore(config.subscribe, config.getSnapshot);
  if (settings) return <><Button onClick={() => showSettings(false)}>返回技能中心</Button><ConfigPage form={config} testConnection={async () => ({ ok: true, value: { reachable: true, mode: 'paged', elapsedMs: 1 } })} /></>;
  return <section className="skillhub-controls astron-sh-catalog">
    <NativeControlStyles /><header><AppearanceToggle /><div ref={setToolbar} /><div ref={setPrimary} /></header>
    <AccountWorkspace key={source.revision} remote={remote} revision={`fixture-${source.revision}`} source={source.value!.registryUrl} toolbar={toolbar} primary={primary} screen={screen} navigate={navigate} settings={() => showSettings(true)} open={() => {}} />
    {createPortal(<aside className="skillhub-controls astron-sh-management-notice" style={{ top: 'auto', bottom: 0, pointerEvents: 'none' }} aria-label="主题测试通知">界面测试通知</aside>, document.body)}
    {createPortal(<aside aria-label="宿主会话" style={{ position: 'fixed', bottom: 0, left: 0, pointerEvents: 'none', background: 'light-dark(#fff,#101010)' }}><div className="skillhub-controls"><p>会话内搜索状态</p></div></aside>, document.body)}
  </section>;
}
createRoot(document.getElementById('root')!).render(<QueryClientProvider client={client}><Fixture /></QueryClientProvider>);
