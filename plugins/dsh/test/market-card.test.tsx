import { describe, expect, test } from 'bun:test';
import { renderToStaticMarkup } from 'react-dom/server';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SkillCard } from '../src/client/skill-card.js';
import { MarketCardActions } from '../src/client/market-card-actions.js';
import { InstallationStatus } from '../src/client/installation-status.js';
import { catalogLabel } from '../src/client/catalog-label.js';

const item = { namespace: 'demo', slug: 'document', displayName: '文档助手' };
describe('market presentation and action boundaries', () => {
  test('whole-card details trigger and installation action are sibling buttons', () => {
    const html = renderToStaticMarkup(<SkillCard item={item} version="1.0" onOpen={() => {}}
      actions={<button>安装</button>} />);
    expect(html).toContain('aria-label="查看 文档助手 @demo/document 1.0 详情"');
    expect(html).not.toContain('详情<');
    const detailStart = html.indexOf('class="astron-sh-card-open"');
    const detailEnd = html.indexOf('</button>', detailStart);
    expect(html.indexOf('>安装</button>')).toBeGreaterThan(detailEnd);
    expect(html).not.toContain('astron-sh-card-open"><button');
  });
  test('unknown inventory disables direct install action', () => {
    const client = new QueryClient();
    const render = (verified: boolean) => renderToStaticMarkup(<QueryClientProvider client={client}>
      <MarketCardActions services={{} as import('../src/client/installed-skills.js').InstalledServices} records={[]} sourceRevision="r1" verified={verified} onInstall={() => {}} />
    </QueryClientProvider>);
    expect(render(false)).toContain('disabled=""');
    expect(render(true)).not.toContain('disabled=""');
  });
  test('hides only complete empty source records, preserving unsafe status', () => {
    const base = { checkedAt: '2026-10-20T00:00:00Z', complete: true, unavailable: [], records: [] };
    expect(renderToStaticMarkup(<InstallationStatus hideEmpty installation={base} />)).toBe('');
    expect(renderToStaticMarkup(<InstallationStatus hideEmpty installation={{ ...base, complete: false }} />)).toContain('安装状态未知');
    expect(renderToStaticMarkup(<InstallationStatus hideEmpty pending installation={base} />)).toContain('核对安装状态');
    expect(renderToStaticMarkup(<InstallationStatus hideEmpty installation={{ ...base, records: [{ scope: 'user', version: '1', state: 'mismatch' }] }} />)).toContain('安装记录异常');
  });
  test('Chinese presentation does not change custom names or filter slugs', () => {
    for (const [slug, displayName, expected] of [
      ['official', 'Official', '官方'], ['ai-competition', 'AI Competition', '人工智能竞赛'],
      ['aicomp-2026', 'aicomp-2026', '2026 人工智能竞赛'], ['coding', '开发工具', '开发工具'],
      ['private-label', 'Custom Workspace', 'Custom Workspace'], ['private-label', '', 'private-label'],
    ]) expect(catalogLabel({ slug: slug!, displayName: displayName! })).toBe(expected!);
  });
});

test.each(['single', 'mixed', 'disabled', 'abnormal'] as const)('market %s exposes use/management, never position controls', state => {
  const client = new QueryClient();
  const unexpected = async () => { throw new Error('Rendering must not transport'); };
  const services = { skillUse: { useSkill: unexpected }, management: { selectSkillUse: unexpected } } as unknown as import('../src/client/installed-skills.js').InstalledServices;
  const record = { itemKey: 'precise-record', namespace: 'demo', slug: 'document', version: '1', scope: 'project' as const, state: state === 'abnormal' ? 'mismatch' as const : 'present' as const, installationState: state === 'disabled' ? 'disabled' as const : 'enabled' as const };
  const records = state === 'mixed' ? [record, { ...record, itemKey: 'user', scope: 'user' as const, installationState: 'disabled' as const }] : [record];
  try {
    const html = renderToStaticMarkup(<QueryClientProvider client={client}><MarketCardActions records={records} sourceRevision="r" verified services={services} onManage={() => {}} onInstall={() => {}} /></QueryClientProvider>);
    expect(html).toContain('管理安装');
    expect(html.includes('使用技能')).toBe(state === 'single' || state === 'mixed');
    for (const text of ['role="combobox"', '更多操作', '>启用</button>', '>停用</button>', '>卸载</button>', '检查更新']) expect(html).not.toContain(text);
    expect(html.match(/astron-sh-button-default/g)?.length).toBe(1);
  } finally { client.clear(); }
});

test('disabled and mixed installation summaries describe locations without implying callable skills', () => {
  const base = { checkedAt: 'now', complete: true, unavailable: [] };
  const disabled = { scope: 'project' as const, state: 'present' as const, version: '1', installationState: 'disabled' as const };
  const single = renderToStaticMarkup(<InstallationStatus installation={{ ...base, records: [disabled] }} />);
  expect(single).toContain('data-tone="neutral"'); expect(single).toContain('>已停用 · v1 · 仅此项目</span>');
  const mixed = renderToStaticMarkup(<InstallationStatus installation={{ ...base, records: [{ ...disabled, scope: 'user', installationState: 'enabled' }, disabled] }} />);
  expect(mixed).toContain('所有项目 · v1 · 已启用 / 仅此项目 · v1 · 已停用');
  expect(mixed).toContain('data-multiple="true"'); expect(mixed.match(/class="astron-sh-location-summary"/g)?.length).toBe(2);
  expect(mixed).toContain('文件存在不代表 DSH 已启用或技能已执行'); expect(mixed).not.toContain('可调用');
});
