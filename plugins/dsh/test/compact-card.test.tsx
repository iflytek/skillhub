import { describe, expect, test } from 'bun:test';
import { renderToStaticMarkup } from 'react-dom/server';
import { SkillCard, SkillMetrics, Icon } from '../src/client/skill-card.js';
import { Button } from '../src/client/ui.js';

const identity = { namespace: 'example', slug: 'document-helper', displayName: '文档整理助手' };
describe('compact card content and interaction parity', () => {
  test('unknown metrics disappear while real zero counts remain visible', () => {
    const unavailable = renderToStaticMarkup(<SkillCard item={{ ...identity, downloadCount: -1, ratingAvg: Number.NaN }} onOpen={() => {}} />);
    expect(unavailable).not.toContain('astron-sh-metrics');
    expect(unavailable).not.toContain('下载量');
    const zero = renderToStaticMarkup(<SkillMetrics item={{ ...identity, downloadCount: 0, starCount: 0 }} />);
    expect(zero).toContain('aria-label="下载量 0"');
    expect(zero).toContain('aria-label="市场收藏数 0"');
    expect(zero).not.toContain('aria-label="评分');
  });

  test('clipped identity and summary retain their full accessible text', () => {
    const displayName = '跨团队项目资料与风险事项整理助手'.repeat(4);
    const summary = '整理访谈记录、材料来源与行动清单。'.repeat(20);
    const html = renderToStaticMarkup(<SkillCard item={{ ...identity, displayName, summary }} version="1.2.3" onOpen={() => {}} descriptionId="skill-description" />);
    expect(html).toContain(`title="${displayName}"`);
    expect(html).toContain(`title="${summary}"`);
    expect(html).toContain('aria-describedby="skill-description"');
    expect(html).toContain('id="skill-description"');
    expect(html).toContain(`aria-label="查看 ${displayName} @example/document-helper 1.2.3 详情"`);
  });

  test('compact footer actions and favorites stay outside the card trigger', () => {
    const html = renderToStaticMarkup(<SkillCard item={identity} onOpen={() => {}} favoriteAction={<Button aria-label="收藏技能"><Icon name="bookmark" /></Button>}
      status={<span>仅此项目 · 已停用</span>} actions={<Button asChild size="sm"><a href="#details">查看详情</a></Button>} />);
    const triggerEnd = html.indexOf('</button>');
    expect(html.indexOf('aria-label="收藏技能"')).toBeGreaterThan(triggerEnd);
    expect(html.indexOf('href="#details"')).toBeGreaterThan(triggerEnd);
    expect(html).toContain('仅此项目 · 已停用');
    expect(html).toContain('class="astron-sh-button astron-sh-button-outline astron-sh-button-size-sm"');
    expect(html.match(/<button/g)?.length).toBe(2);
    expect(html).toContain('<svg');
    expect(html).not.toContain('vundefined');
  });
});
