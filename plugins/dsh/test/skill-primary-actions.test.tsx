import { expect, test } from 'bun:test';
import { renderToStaticMarkup } from 'react-dom/server';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { installedCardBadges, installedCardVersions, installedUpdateKeys, InstalledSkills, type InstalledServices } from '../src/client/installed-skills.js';
import { installedNavigationFor } from '../src/client/installed-navigation.js';
import { UpgradeRequests, RemovalRequests } from '../src/client/upgrade-requests.js';
import { selectSkillItem, SkillPrimaryActions } from '../src/client/skill-primary-actions.js';
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
    expect(html).toContain('多个版本 · v1 / v2'); expect(html).toContain('暂无摘要');
    expect(html).toContain('检查更新'); expect(html).not.toContain('0 处检查失败');
    expect(html).toContain('aria-label="writer 更多操作"'); expect(html).toContain('aria-haspopup="menu"');
    expect(html).toContain('1 个技能'); expect(html).toContain('使用技能'); expect(html).not.toContain('>管理安装</button>'); expect(html).not.toContain('>详情</button>'); expect(html).toContain('aria-label="查看 writer @demo/writer 详情"');
    for (const text of ['筛选安装范围', 'role="combobox"', '>停用</button>', '>启用</button>', '>卸载</button>']) expect(html).not.toContain(text);
    client.setQueryData(['skillhub-installed', 'session', 'revision'], { items: [{ ...item, displayName: '写作助手', summary: '整理草稿并校对文档。' }], complete: true, unavailable: [], issues: [] });
    const disabled = renderToStaticMarkup(<QueryClientProvider client={client}><InstalledSkills services={services} sourceRevision="revision" source="https://registry.example"
      remote={{} as TypertRemoteNamespaceMap['skillhub']} modes={{ versions: 'auto', reviews: 'auto', securityAudit: 'auto' }} scrollContainer={{ current: null }} navigation={{ ...installedNavigationFor({}, 'session', 'revision'), keyword: '写作' }} /></QueryClientProvider>);
    expect(disabled).toContain('>已停用</span>'); expect(disabled).not.toContain('>使用技能</button>'); expect(disabled).not.toContain('>管理安装</button>');
    expect(disabled).toContain('aria-label="查看 写作助手 @demo/writer 详情"'); expect(disabled).toContain('整理草稿并校对文档。'); expect(disabled).not.toContain('暂无摘要'); expect(disabled).toContain('astron-sh-badge-neutral');
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

test('installed card status stays concise without hiding failed or blocked checks', () => {
  const enabled = { ...item, installationState: 'enabled' as const };
  const update = { state: 'upgradable' as const, currentVersion: '1', candidateVersion: '2', reason: '', checkedAt: '2026-10-29' };
  expect(installedCardBadges([enabled], {}, true)).toEqual([]);
  expect(installedCardBadges([item], { user: update }, true).map(badge => badge.label)).toEqual(['已停用', '可更新至 2']);
  expect(installedCardBadges([enabled], {}, false).map(badge => badge.label)).toEqual(['安装待核验']);
  const sibling = { ...enabled, itemKey: 'project' };
  expect(installedCardBadges([enabled, sibling], { user: update, project: { ...update, state: 'blocked' } }, true).map(badge => badge.label)).toEqual(['更新需处理']);
  expect(installedCardBadges([item, { ...item, itemKey: 'project' }], { user: update, project: { state: 'failed' } }, true).map(badge => badge.label)).toEqual(['已停用', '检查失败']);
  expect(installedCardBadges([enabled, sibling], { user: update }, true).map(badge => badge.label)).toEqual(['可更新']);
});

test('installed versions describe actual copies rather than the remote update candidate', () => {
  expect(installedCardVersions([item]).label).toBe('v1');
  expect(installedCardVersions([item, { ...item, scope: 'project' }]).label).toBe('v1');
  const mixed = installedCardVersions([item, { ...item, scope: 'project', version: '2' }]);
  expect(mixed.label).toBe('多个版本 · v1 / v2');
  expect(mixed.title).toBe('所有项目 · v1；仅此项目 · v2');
});


test('card update check limits the existing read-only scan to current exact installation references', () => {
  const project = { ...item, itemKey: 'project', scope: 'project' as const };
  const other = { ...item, itemKey: 'other', slug: 'other' };
  expect(installedUpdateKeys([item, project, other], {}, false, [item, project])).toEqual(['user', 'project']);
  expect(installedUpdateKeys([project, other], {}, false, [item])).toEqual([]);
  expect(installedUpdateKeys([item, project, other], { project: { state: 'failed' } }, true)).toEqual(['project']);
  expect(installedUpdateKeys([item, project, other], {}, false)).toEqual(['user', 'project', 'other']);
});


test('unreadable entry projection never offers use or invents an enabled summary', () => {
  const html = renderToStaticMarkup(<SkillPrimaryActions items={[{ ...item, state: 'unreadable', installationState: undefined }]}
    revision="revision" verified={false} select={async () => { throw new Error('must not select'); }} onManage={() => {}} />);
  expect(html).not.toContain('使用技能');
  expect(html).toContain('安装状态待核验');
  expect(html).toContain('管理安装');
});
