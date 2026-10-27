import { expect, test } from 'bun:test';
import { renderToStaticMarkup } from 'react-dom/server';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { InstallControl, preferredInstallationScope, type InstallationServices } from '../src/client/install-control.js';
import { InstallationRequests } from '../src/client/installation-requests.js';
import type { InstalledSkillList, SkillDetail } from '../src/host/types.js';

const detail: SkillDetail = { namespace: 'global', slug: 'writer', title: '写作助手', summary: '', version: '1.2.0', detailToken: 'detail', versionId: 1, tabs: { versions: 'hidden', securityAudit: 'hidden', reviews: 'hidden' }, documentation: { state: 'ready', content: '# 写作助手' } };
const record: InstalledSkillList['items'][number] = { itemKey: 'item', namespace: 'global', slug: 'writer', version: '1.2.0', scope: 'user', state: 'present' };
const inventory = (items: InstalledSkillList['items']): InstalledSkillList => ({ items, complete: true, issues: [], unavailable: [] });
test('existing scope is preferred and another skill never changes the selection', () => {
  expect(preferredInstallationScope(inventory([{ ...record, scope: 'user' }]), detail, ['project', 'user'])).toBe('user');
  expect(preferredInstallationScope(inventory([{ ...record, scope: 'project' }, record]), detail, ['project', 'user'])).toBe('project');
  expect(preferredInstallationScope(inventory([{ ...record, slug: 'other', scope: 'user' }]), detail, ['project', 'user'])).toBe('user');
  expect(preferredInstallationScope(inventory([]), detail, ['user'])).toBe('user');
  expect(preferredInstallationScope(inventory([]), detail, ['project'])).toBeUndefined();
  // Installed scopes remain viewable even if they are not currently writable.
  expect(preferredInstallationScope(inventory([{ ...record, scope: 'user' }]), detail, ['project'])).toBe('user');
});

function render(items: InstalledSkillList['items']) {
  const client = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity } } });
  const requests = new InstallationRequests();
  client.setQueryData(['skillhub-install-targets', 'session', 'detail'], { hostInstanceId: 'host', targets: [{ scope: 'project', targetToken: 'target' }, { scope: 'user', targetToken: 'user' }], unavailable: [] });
  client.setQueryData(['skillhub-installed', 'session', 'source'], inventory(items));
  client.setQueryData(['skillhub-compatibility'], { dependencyBaseline: 'matched', localWritesAllowed: true });
  const unexpected = async () => { throw new Error('SSR must not call transport'); };
  const services: InstallationServices = { sessionId: 'session', skillUse: { useSkill: unexpected }, requests, targets: unexpected, installed: unexpected, start: unexpected, operation: unexpected, compatibility: unexpected, manageInstalled: () => {} };
  try { return renderToStaticMarkup(<QueryClientProvider client={client}><InstallControl detail={detail} sourceRevision="source" services={services} initiatedFrom="plaza" /></QueryClientProvider>); }
  finally { client.clear(); requests.dispose(); }
}

test('current inventory renders a removal action without a duplicate install action', () => {
  const html = render([record]);
  expect(html).toContain('已安装 v1.2.0'); expect(html).toContain('>卸载</button>');
  expect(html).not.toContain('>安装到此项目</button>'); expect(html).not.toContain('安装完成');
  expect(html).toContain('>使用此技能</button>');
  expect(html).not.toContain('当前范围未安装'); expect(html).not.toContain('DSH Host 上该用户');
});

test('empty verified inventory renders an explicit scoped install action', () => {
  const html = render([]);
  expect(html).toContain('>安装到所有项目</button>');
  expect(html).toContain('<summary>高级选项</summary>');
  expect(html).not.toContain('安装完成'); expect(html).not.toContain('重新安装到');
  expect(html).not.toContain('>卸载</button>');
});

test('use entry belongs to the installed version and never a different viewed version or invalid record', () => {
  for (const item of [{ ...record, version: '1.1.0' }, { ...record, state: 'missing' as const }, { ...record, state: 'mismatch' as const }]) {
    expect(render([item])).not.toContain('>使用此技能</button>');
  }
});
