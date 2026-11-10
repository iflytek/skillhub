import { expect, test } from 'bun:test';
import { renderToStaticMarkup } from 'react-dom/server';
import { DetailMeta, sourceHost } from '../src/client/detail-presentation.js';
import { FavoriteButton } from '../src/client/favorite-button.js';
import { CatalogSkeleton } from '../src/client/loading-layout.js';
import { MarketCardActions } from '../src/client/market-card-actions.js';
import type { SkillDetail } from '../src/host/types.js';

const detail: SkillDetail = { detailToken: 'detail', title: 'Writer', namespace: 'team', slug: 'writer', version: '1.0.0', versionId: 1,
  summary: '', starCount: 0, ratingAvg: 4.5, webUrl: 'https://market.example/skills/team/writer',
  documentation: { state: 'ready', content: '' }, tabs: { versions: 'unknown', reviews: 'unknown', securityAudit: 'anonymous-denied' } };

test('detail metrics keep accessible meanings without verbose visual labels, and website remains API-bound', () => {
  const html = renderToStaticMarkup(<DetailMeta detail={detail} websiteEnabled />);
  expect(html).toContain('aria-label="市场收藏数 0"');
  expect(html).toContain('aria-label="评分 4.5 / 5"');
  expect(html).not.toContain('市场收藏 ');
  expect(html).not.toContain('下载量');
  expect(html).toContain('href="https://market.example/skills/team/writer"');
  expect(html).toContain('>官网<');
  expect(renderToStaticMarkup(<DetailMeta detail={{ ...detail, webUrl: null }} websiteEnabled />)).not.toContain('<a');
  expect(renderToStaticMarkup(<DetailMeta detail={detail} websiteEnabled={false} />)).not.toContain('<a');
});

test('source host labels omit credentials and handle unreadable addresses', () => {
  expect(sourceHost('broken')).toBe('技能来源');
  expect(sourceHost('https://user:secret@custom.example/path?token=value')).toBe('custom.example');
});

test('detail favorite has a visible label while preserving busy and disabled boundaries', () => {
  const html = renderToStaticMarkup(<FavoriteButton showLabel item={{ namespace: 'team', slug: 'writer' }} pressed disabled={false} busy onToggle={() => {}} />);
  expect(html).toContain('aria-label="取消收藏 writer @team/writer"');
  expect(html).toContain('已收藏'); expect(html).toContain('aria-busy="true"'); expect(html).not.toMatch(/<button[^>]*\sdisabled=/);
});

test('discovery status is honest local inventory text, not another write action', () => {
  const item = { itemKey: 'item', namespace: 'team', slug: 'writer', version: '1', scope: 'project' as const, state: 'present' as const, installationState: 'enabled' as const };
  for (const [records, verified, label] of [[[item], true, '已安装'], [[{ ...item, installationState: 'disabled' as const }], true, '已停用'], [[item], false, '安装待核验']] as const) {
    const html = renderToStaticMarkup(<MarketCardActions presentation="status" records={[...records]} sourceRevision="source" verified={verified} onInstall={() => { throw new Error('Unexpected install'); }} />);
    expect(html).toContain(label); expect(html).not.toContain('<button');
  }
});


test('catalog loading does not repeat the navigation already owned by the workbench header', () => {
  const html = renderToStaticMarkup(<CatalogSkeleton tab="browse" draft="" />);
  expect(html).not.toContain('role="tab"');
  expect(html).not.toContain('role="tablist"');
  expect(html).toContain('aria-label="正在加载发现技能"');
  expect(html).toContain('aria-label="技能排序"');
});
