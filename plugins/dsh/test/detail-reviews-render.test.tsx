import { expect, test } from 'bun:test';
import { renderToStaticMarkup } from 'react-dom/server';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { TypertRemoteNamespaceMap } from '@deepseek-ai/dsh-typert-protocol';
import type { ReviewPage, SkillDetail } from '../src/host/types.js';
import { DetailTabs } from '../src/client/detail-tabs.js';
const detail: SkillDetail = { detailToken: 'token', versionId: 1, namespace: 'team', slug: 'writer', version: '1.0.0', title: 'Writer', summary: '', tabs: { versions: 'unknown', reviews: 'available', securityAudit: 'anonymous-denied' }, documentation: { state: 'ready', content: '# Writer' } };
const identity = 'source/team/writer/1.0.0';
const remote = {} as TypertRemoteNamespaceMap['skillhub'];
function renderReviews({ data, error, page = 0, mode = 'auto' }: { data?: ReviewPage; error?: string; page?: number; mode?: 'auto' | 'hide' | 'show' } = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retryOnMount: false } } });
  client.setQueryData(['skillhub-detail-ui', identity], { selected: 'reviews', pages: { versions: 0, reviews: page } });
  const queryKey = ['skillhub-module', identity, 'reviews', page];
  if (data) client.setQueryData(queryKey, data);
  if (error) client.getQueryCache().build(client, { queryKey }).setState({ status: 'error', error: new Error(error), fetchStatus: 'idle' });
  const html = renderToStaticMarkup(<QueryClientProvider client={client}><DetailTabs detail={detail} cacheIdentity={identity} modes={{ versions: 'hide', reviews: mode, securityAudit: 'show' }} remote={remote}><p>正文概览</p></DetailTabs></QueryClientProvider>);
  client.clear(); return html;
}
const review = { id: 1, displayName: '真实用户', score: 4, reviewText: '<script>unsafe</script> 使用反馈', createdAt: '2026-11-03T08:00:00Z' };
test('reviews render actual feedback, escaped text and read-only scope', () => {
  const html = renderReviews({ data: { items: [review], total: 23, page: 0, size: 20 } });
  expect(html).toContain('真实用户'); expect(html).toContain('使用反馈'); expect(html).toContain('&lt;script&gt;'); expect(html).not.toContain('<script>');
  expect(html).toContain('评分 4 / 5'); expect(html).toContain('共 23 条评论'); expect(html).toContain('只读展示'); expect(html).toContain('评论针对整个技能，不限定当前版本');
  expect(html).toMatch(/<button[^>]*disabled=""[^>]*>上一页<\/button>/); expect(html).not.toMatch(/<button[^>]*disabled=""[^>]*>下一页<\/button>/); expect(html).not.toContain('正文概览');
});
test('empty reviews are explicit without inventing a zero rating', () => {
  const html = renderReviews({ data: { items: [], total: 0, page: 0, size: 20 } });
  expect(html).toContain('暂无评论信息。'); expect(html).toContain('评分人数未提供'); expect(html).toContain('此技能的使用反馈将显示在这里。'); expect(html).not.toContain('审核通过');
});
test('review loading and failures stay visible and retryable', () => {
  expect(renderReviews()).toContain('role="status">正在读取评论…');
  const html = renderReviews({ error: '当前来源不允许匿名读取此项信息。' });
  expect(html).toContain('role="alert"'); expect(html).toContain('不允许匿名读取'); expect(html).toContain('重试'); expect(html).not.toContain('暂无评论信息');
});
test('last page disables next and preserves missing author/content fallbacks', () => {
  const html = renderReviews({ page: 1, data: { items: [{ ...review, displayName: '', reviewText: '' }], total: 23, page: 1, size: 20 } });
  expect(html).toContain('未填写评论内容'); expect(html).toContain('>用户<'); expect(html).toContain('第 2 页'); expect(html).toMatch(/<button[^>]*disabled=""[^>]*>下一页<\/button>/); expect(html).not.toMatch(/<button[^>]*disabled=""[^>]*>上一页<\/button>/);
});
test('hidden review policy overrides remembered selection and keeps independent audit entry', () => {
  const html = renderReviews({ mode: 'hide' });
  expect(html).toContain('data-detail-view="details"'); expect(html).toContain('正文概览'); expect(html).not.toContain('正在读取评论'); expect(html).not.toContain('评论分页'); expect(html).toContain('安全审核');
});
