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
      <MarketCardActions records={[]} sourceRevision="r1" verified={verified} onInstall={() => {}} onManage={() => {}} />
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
