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
