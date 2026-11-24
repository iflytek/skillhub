import { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AccountWorkspace, type AccountPageName } from '../../src/client/account-workspace.js';
import type { AccountRemote } from '../../src/client/account-common.js';
import { NativeControlStyles } from '../../src/client/native-control-styles.js';

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
function Fixture() {
  const [toolbar, setToolbar] = useState<HTMLDivElement | null>(null);
  const [primary, setPrimary] = useState<HTMLDivElement | null>(null);
  const [screen, navigate] = useState<AccountPageName>(null);
  return <section className="skillhub-controls astron-sh-catalog">
    <NativeControlStyles /><header><div ref={setToolbar} /><div ref={setPrimary} /></header>
    <AccountWorkspace remote={remote} revision="fixture" source="https://registry.example.test" toolbar={toolbar} primary={primary} screen={screen} navigate={navigate} open={() => {}} />
  </section>;
}
createRoot(document.getElementById('root')!).render(<QueryClientProvider client={client}><Fixture /></QueryClientProvider>);
