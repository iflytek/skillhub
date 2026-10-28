import { expect, test } from 'bun:test';
import { renderToStaticMarkup } from 'react-dom/server';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { InstalledSkills, type InstalledServices } from '../src/client/installed-skills.js';
import { installedNavigationFor } from '../src/client/installed-navigation.js';
import { UpgradeRequests, RemovalRequests } from '../src/client/upgrade-requests.js';
import { selectSkillItem } from '../src/client/skill-primary-actions.js';
import type { TypertRemoteNamespaceMap } from '@deepseek-ai/dsh-typert-protocol';

const item = { itemKey: 'user', namespace: 'demo', slug: 'writer', scope: 'user' as const, version: '1', state: 'present' as const, installationState: 'disabled' as const };
test('installed defaults to one skill card with no position filter or management controls', () => {
  const client = new QueryClient();
  client.setQueryData(['skillhub-installed', 'session', 'revision'], { items: [item, { ...item, itemKey: 'project', scope: 'project', version: '2', installationState: 'enabled' }], complete: true, unavailable: [], issues: [] });
  const unexpected = async () => { throw new Error('render cannot request'); };
  const storage = () => ({ getItem: () => null, setItem() {}, removeItem() {}, key: () => null, length: 0 });
  const upgrades = new UpgradeRequests(storage), removals = new RemovalRequests(storage);
  const services = { sessionId: 'session', upgradeRequests: upgrades, removalRequests: removals, list: unexpected,
    skillUse: { useSkill: unexpected }, management: { selectSkillUse: unexpected } } as unknown as InstalledServices;
  try {
    const html = renderToStaticMarkup(<QueryClientProvider client={client}><InstalledSkills services={services} sourceRevision="revision" source="https://registry.example"
      remote={{} as TypertRemoteNamespaceMap['skillhub']} modes={{ versions: 'auto', reviews: 'auto', securityAudit: 'auto' }} scrollContainer={{ current: null }} navigation={installedNavigationFor({}, 'session', 'revision')} /></QueryClientProvider>);
    expect(html.match(/astron-sh-installed-card"/g)).toHaveLength(1);
    expect(html).toContain('当前显示 1 个技能'); expect(html).toContain('使用技能'); expect(html).toContain('管理安装');
    for (const text of ['筛选安装范围', 'role="combobox"', '>停用</button>', '>启用</button>', '>卸载</button>', '检查更新']) expect(html).not.toContain(text);
  } finally { client.clear(); upgrades.dispose(); removals.dispose(); }
});
test('late cancelled selector responses cannot supply a key', async () => {
  const abort = new AbortController();
  await expect(selectSkillItem(async () => { abort.abort(); return { ok: true, value: { status: 'ready', itemKey: 'stale-key' } }; }, 'revision', 'demo', 'writer', abort.signal)).rejects.toThrow();
});

test('an open installation detail survives removal of its last record without selecting a sibling', () => {
  const client = new QueryClient();
  client.setQueryData(['skillhub-installed', 'session', 'revision'], { items: [], complete: true, unavailable: [], issues: [] });
  client.setQueryData(['skillhub-installed-detail', 'session', 'revision', item.itemKey], { item, content: { state: 'local-only', reason: 'NOT_FOUND' } });
  const unexpected = async () => { throw new Error('render cannot request'); };
  const services = { sessionId: 'session', list: unexpected, management: { installation: { sessionId: 'session' }, selectSkillUse: unexpected }, skillUse: { useSkill: unexpected } } as unknown as InstalledServices;
  const navigation = installedNavigationFor({}, 'session', 'revision'); navigation.selected = item; navigation.intent = 'remove';
  try {
    const html = renderToStaticMarkup(<QueryClientProvider client={client}><InstalledSkills services={services} sourceRevision="revision" source="https://registry.example"
      remote={{} as TypertRemoteNamespaceMap['skillhub']} modes={{ versions: 'auto', reviews: 'auto', securityAudit: 'auto' }} scrollContainer={{ current: null }} navigation={navigation} /></QueryClientProvider>);
    expect(html).toContain('aria-label="安装管理"'); expect(html).not.toContain('使用已安装版本</button>'); expect(html).not.toContain('role="dialog"');
  } finally { client.clear(); }
});
