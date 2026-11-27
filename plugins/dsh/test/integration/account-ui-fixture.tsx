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
import type { AccountMember, MemberMutation, Visibility } from '../../src/host/account-types.js';

// Isolated UI fixture: no real service, credentials, or filesystem writes.
let identity: { handle: string; displayName: string } | null = null;
let uploadsEmpty = false;
let finishUpload: ((published: boolean, legacy: boolean) => void) | undefined;
let uploadedPatch = 0;
let publishedPatch = 0;
let uploadStatusReads = 0;
const writes = { upload: 0, add: 0, role: 0, remove: 0, delete: 0 };
function startWrite(action: keyof typeof writes) {
  writes[action]++;
  if (!navigator.onLine) throw new TypeError('Fixture Registry offline');
}
let pauseInspection = false;
let finishInspection: (() => void) | undefined;
Object.assign(globalThis, {
  finishAccountUpload: (published = false, legacy = false) => finishUpload?.(published, legacy),
  accountUploadStatusReads: () => uploadStatusReads,
  publishAccountVersion: () => { publishedPatch = uploadedPatch; },
  accountWriteCounts: () => ({ ...writes }),
  pauseNextAccountInspection: () => { pauseInspection = true; },
  isAccountInspectionPaused: () => !!finishInspection,
  finishAccountInspection: () => { const finish = finishInspection; finishInspection = undefined; finish?.(); },
});
const designMembers: AccountMember[] = [
  { userId: 'usr_11111111-2222-4333-8444-555555555555', displayName: '空间所有者', role: 'OWNER' },
  { userId: 'fixture-user', displayName: '演示用户', role: 'ADMIN' },
  { userId: 'usr_66666666-7777-4888-9999-aaaaaaaaaaaa', displayName: '团队成员', role: 'MEMBER' },
];
const spaces = [{ id: 1, slug: 'design', displayName: '设计团队', type: 'TEAM', status: 'ACTIVE', currentUserRole: 'ADMIN' },
  ...Array.from({ length: 11 }, (_, i) => ({ id: i + 2, slug: `team-${i + 2}`, displayName: `团队 ${i + 2}`, type: 'TEAM', status: 'ACTIVE', currentUserRole: 'MEMBER' })),
  { id: 13, slug: 'e2e-dsh-api-archived-fixture', displayName: 'DSH ownership retest · 示例归档空间', type: 'TEAM', status: 'ARCHIVED', currentUserRole: 'MEMBER' },
  { id: 14, slug: 'global', displayName: '全局空间', type: 'GLOBAL', status: 'ACTIVE', currentUserRole: 'MEMBER' },
  { id: 15, slug: 'frozen-team', displayName: '冻结团队', type: 'TEAM', status: 'FROZEN', currentUserRole: 'ADMIN' },
  { id: 16, slug: 'catalog', displayName: '目录空间', type: 'CATALOG', status: 'ACTIVE', currentUserRole: 'ADMIN' }];
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
  accountOwned: (_revision: string, ns: string) => ns === 'global' || ns === 'design' && uploadsEmpty ? { items: [], total: 0 } : ({ items: [{ id: 1, slug: 'example', displayName: '示例技能', visibility: 'PRIVATE', status: 'ACTIVE' }], total: 1 }),
  accountTeam: () => ({ items: [{ namespace: 'design', slug: 'example', version: `1.0.${publishedPatch}`, versionId: publishedPatch + 1 }], nextCursor: null }),
  accountMembers: (_revision: string, ns: string) => ns === 'design' ? { items: [...designMembers], total: designMembers.length }
    : { items: [{ userId: 'usr_11111111-2222-4333-8444-555555555555', displayName: '空间所有者', role: 'OWNER' }, { userId: 'fixture-user', displayName: '演示用户', role: ns === 'frozen-team' ? 'ADMIN' : 'MEMBER' }], total: 2 },
  accountCandidates: () => [{ userId: 'candidate', displayName: '候选用户', email: 'candidate@example.test' }],
  changeAccountMember: async (_revision: string, ns: string, command: MemberMutation) => {
    startWrite(command.action);
    if (ns !== 'design') throw new Error('Read-only fixture namespace');
    await new Promise(resolve => setTimeout(resolve, 250));
    if (command.action === 'add') designMembers.push({ userId: command.userId, displayName: '候选用户', role: command.role });
    else {
      const index = designMembers.findIndex(member => member.userId === command.userId);
      if (index < 0 || designMembers[index]!.role === 'OWNER') throw new Error('Invalid fixture member');
      if (command.action === 'remove') designMembers.splice(index, 1);
      else designMembers[index] = { ...designMembers[index]!, role: command.role };
    }
    return true;
  },
  deleteAccountSkill: async () => {
    startWrite('delete');
    await new Promise(resolve => setTimeout(resolve, 250));
    uploadsEmpty = true;
    return true;
  },
  inspectUpload: async () => {
    if (pauseInspection) { pauseInspection = false; await new Promise<void>(resolve => { finishInspection = resolve; }); }
    return { slug: 'example', existing: null };
  },
  publishUpload: async (_revision: string, namespace: string, _archive: string, visibility: Visibility) => {
    startWrite('upload');
    const { published, legacy } = await new Promise<{ published: boolean; legacy: boolean }>(resolve => {
      finishUpload = (published, legacy) => resolve({ published, legacy });
    });
    uploadedPatch++;
    if (published) publishedPatch = uploadedPatch;
    return { namespace, slug: 'example', version: `1.0.${uploadedPatch}`, visibility, ...(legacy ? {} : { versionId: uploadedPatch + 1 }) };
  },
  uploadStatus: (_revision: string, versionId: number, version: string) => {
    uploadStatusReads++;
    if (versionId !== uploadedPatch + 1 || version !== `1.0.${uploadedPatch}`) throw new Error('Expected the upload receipt identity');
    return { id: versionId, version, status: publishedPatch === uploadedPatch ? 'PUBLISHED' : 'UPLOADED' };
  },
};
const remote = Object.fromEntries(Object.entries(api).map(([name, fn]) => [name, async (...args: unknown[]) => {
  try { return { ok: true, value: await Reflect.apply(fn, null, args) }; }
  catch (error) {
    if (error instanceof TypeError) return { ok: false, error: { code: 'skillhub/registry-error', details: { reason: 'NETWORK_ERROR' } } };
    return { ok: false, error: { code: 'AUTH_REQUIRED', message: 'Token 无效，请重新输入。' } }; }
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
