import { expect, test } from 'bun:test';
import { renderToStaticMarkup } from 'react-dom/server';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { TypertRemoteNamespaceMap } from '@deepseek-ai/dsh-typert-protocol';
import type { SkillDetail } from '../src/host/types.js';
import { DetailContent } from '../src/client/detail-content.js';

const detail: SkillDetail = {
  detailToken: 'token', versionId: 1, namespace: 'team', slug: 'writer', version: '1.0.0', title: 'Writer', summary: 'Write documents',
  tabs: { versions: 'unknown', reviews: 'unknown', securityAudit: 'unknown' },
  documentation: { state: 'ready', content: '---\nname: private-frontmatter\ndescription: should-not-render\n---\n# 正文标题\n\n阅读正文。[unsafe](javascript:alert(1))\n\n<script>alert(1)</script>' },
};
const remote = {} as TypertRemoteNamespaceMap['skillhub'];
test('reading view hides pending capability banners and raw frontmatter while keeping sanitized body', () => {
  const client = new QueryClient();
  const html = renderToStaticMarkup(<QueryClientProvider client={client}><section className="skillhub-controls"><DetailContent
    detail={detail} modes={{ versions: 'auto', reviews: 'auto', securityAudit: 'auto' }} remote={remote} retry={() => {}}
    cacheIdentity="session/source/team/writer/1.0.0" /></section></QueryClientProvider>);
  expect(html).not.toContain('重新检查可选内容');
  expect(html).not.toContain('是否可读取尚未确认');
  expect(html).not.toContain('private-frontmatter');
  expect(html).not.toContain('should-not-render');
  expect(html).toContain('<h1>正文标题</h1>');
  expect(html).toContain('阅读正文。');
  expect(html).not.toContain('<script>');
  expect(html).not.toContain('href="javascript:');
  expect(html).toContain('astron-sh-markdown');
});

test('local package details hide market version and modules explicitly, without interpreting version text', () => {
  const client = new QueryClient();
  const html = renderToStaticMarkup(<QueryClientProvider client={client}><DetailContent localPackage detail={{ ...detail, version: 'local' }} modes={{ versions: 'show', reviews: 'show', securityAudit: 'show' }} remote={remote} retry={() => {}} /></QueryClientProvider>);
  expect(html).not.toContain('当前版本');
  expect(html).not.toContain('role="tab" data-state="inactive"');
  expect(html).toContain('正文标题');
  const market = renderToStaticMarkup(<QueryClientProvider client={new QueryClient()}><DetailContent detail={{ ...detail, version: 'local' }} modes={{ versions: 'hide', reviews: 'hide', securityAudit: 'hide' }} remote={remote} retry={() => {}} /></QueryClientProvider>);
  expect(market).toContain('当前版本 local');
});

test('cached file selection exposes only the file view and passes the actual package root name', () => {
  const identity = 'session/source/team/writer/files';
  const client = new QueryClient();
  client.setQueryData(['skillhub-detail-ui', identity], { selected: 'files', pages: { versions: 0, reviews: 0 } });
  client.setQueryData(['skillhub-files', identity, detail.detailToken], { state: 'ready', items: [{ path: 'SKILL.md', size: 8, contentType: 'text/markdown' }] });
  client.setQueryData(['skillhub-file', identity, detail.detailToken, 'SKILL.md'], { state: 'text', path: 'SKILL.md', content: '# Writer' });
  const html = renderToStaticMarkup(<QueryClientProvider client={client}><DetailContent detail={detail} modes={{ versions: 'hide', reviews: 'hide', securityAudit: 'hide' }} remote={remote} retry={() => {}} cacheIdentity={identity} /></QueryClientProvider>);
  expect(html).toContain('data-detail-view="files"');
  expect(html).toContain('aria-label="writer 根目录"');
  expect(html).toContain('文件预览 SKILL.md');
  expect(html).not.toContain('正文标题');
});

test('cached market file selection cannot reveal a file view for a local package', () => {
  const identity = 'session/source/local/files';
  const client = new QueryClient();
  client.setQueryData(['skillhub-detail-ui', identity], { selected: 'files', pages: { versions: 0, reviews: 0 } });
  const html = renderToStaticMarkup(<QueryClientProvider client={client}><DetailContent localPackage detail={detail} modes={{ versions: 'show', reviews: 'show', securityAudit: 'show' }} remote={remote} retry={() => {}} cacheIdentity={identity} /></QueryClientProvider>);
  expect(html).toContain('data-detail-view="details"');
  expect(html).toContain('正文标题');
  expect(html).not.toContain('版本文件列表');
  expect(html).not.toContain('role="tab" data-state="inactive"');
});
