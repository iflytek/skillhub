import { expect, test } from 'bun:test';
import { renderToStaticMarkup } from 'react-dom/server';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { TypertRemoteNamespaceMap } from '@deepseek-ai/dsh-typert-protocol';
import type { SkillFileEntry, SkillFilePreview } from '../src/host/types.js';
import { fileBrowserStateKey, type SkillFileBrowserState } from '../src/client/file-tree.js';
import { SkillFiles } from '../src/client/skill-files.js';

const remote = {} as TypertRemoteNamespaceMap['skillhub'];
const entry: SkillFileEntry = { path: 'SKILL.md', size: 10, contentType: 'text/markdown' };
function render(items: SkillFileEntry[], preview: SkillFilePreview, rootName = 'review-skill') {
  const client = new QueryClient();
  client.setQueryData(['skillhub-files', 'fixed-version', 'detail'], { state: 'ready', items });
  client.setQueryData(['skillhub-file', 'fixed-version', 'detail', preview.path], preview);
  try {
    return renderToStaticMarkup(<QueryClientProvider client={client}><section className="skillhub-controls"><SkillFiles token="detail" identity="fixed-version" rootName={rootName} remote={remote} /></section></QueryClientProvider>);
  } finally { client.clear(); }
}

test('compact file rows retain full accessible paths and real sizes, with one aggregate header', () => {
  const long = { path: 'references/a-very-long-reference-file-name.md', size: 2048, contentType: 'text/plain' };
  const html = render([entry, long], { state: 'text', path: entry.path, content: '# Skill' });
  expect(html).toContain('aria-label="版本文件列表"');
  expect(html).toContain('aria-pressed="true" aria-label="SKILL.md 10 B"');
  expect(html).toContain(`aria-label="${long.path} 2.0 KB"`);
  expect(html).toContain(`title="${long.path} · 2.0 KB"`);
  expect(html).toContain('2 个文件 · 1 个目录');
  expect(html).toContain('2.0 KB');
  expect(html).toContain('aria-label="review-skill 根目录" aria-expanded="true"');
  expect(html).toContain('aria-label="目录 references"');
  expect(html).not.toContain('astron-sh-files-total');
  expect(html).toContain('tabindex="0" role="region" aria-label="只读文件正文"');
  expect(html).not.toContain('role="tree"');
  expect(html).not.toContain('role="treeitem"');
});

test('long readonly text remains complete and HTML is escaped rather than executed', () => {
  const text = '<script>unsafe()</script>\n' + Array.from({ length: 200 }, (_, index) => `line ${index}`).join('\n');
  const html = render([entry], { state: 'text', path: entry.path, content: text });
  expect(html).toContain('&lt;script&gt;unsafe()&lt;/script&gt;');
  expect(html).not.toContain('<script>unsafe');
  expect(html).toContain('line 199');
  expect(html).toContain('只读'); expect(html).toContain('复制');
  expect(html).toContain('UTF-8 · 只读');
  const gutter = html.slice(html.indexOf('<pre class="astron-sh-file-line-numbers"'));
  expect(gutter.slice(0, gutter.indexOf('</pre>'))).toContain('aria-hidden="true">1\n2\n3');
});

test.each(['binary', 'unreadable', 'too-large'] as const)('non-text state %s keeps its message and disables copying', state => {
  const html = render([entry], state === 'too-large' ? { state, path: entry.path, limitBytes: 262144 } : { state, path: entry.path });
  expect(html).not.toContain('<pre');
  expect(html).not.toContain('UTF-8 · 只读');
  expect(html).not.toContain('Markdown');
  expect(html).toContain('role="status"');
  expect(html).toMatch(/<button[^>]*disabled=""[^>]*>/);
  expect(html).toContain(state === 'binary' ? '二进制文件，仅展示文件信息。' : state === 'unreadable' ? '此文件无法作为 UTF-8 文本预览。' : '文件超过 256.0 KB 的预览上限。');
});


test('initially selected nested files reveal their ancestry without expanding unrelated deep folders', () => {
  const selected = { ...entry, path: 'custom/deep/notes.md' };
  const html = render([selected, { ...entry, path: 'other/unrelated/notes.md' }], { state: 'text', path: selected.path, content: 'hello' });
  expect(html).toContain('aria-label="目录 custom" title="custom/" aria-expanded="true"');
  expect(html).toContain('aria-label="目录 custom/deep" title="custom/deep/" aria-expanded="true"');
  expect(html).toContain('aria-label="目录 other" title="other/" aria-expanded="true"');
  expect(html).toContain('aria-label="目录 other/unrelated" title="other/unrelated/" aria-expanded="false"');
  expect(html).toContain('aria-label="custom/deep/notes.md 10 B"');
  expect(html).not.toContain('aria-label="other/unrelated/notes.md 10 B"');
  expect(html).toContain('custom / deep / notes.md');
});


test('returning to Files restores its selected path and folded directories without sharing another token state', () => {
  const client = new QueryClient();
  const nested = { ...entry, path: 'custom/deep/notes.md' };
  const items = [entry, nested];
  for (const token of ['detail-1', 'detail-2']) {
    client.setQueryData(['skillhub-files', 'fixed-version', token], { state: 'ready', items });
    client.setQueryData(['skillhub-file', 'fixed-version', token, entry.path], { state: 'text', path: entry.path, content: 'root document' });
    client.setQueryData(['skillhub-file', 'fixed-version', token, nested.path], { state: 'text', path: nested.path, content: 'selected nested document' });
  }
  const saved: SkillFileBrowserState = { selected: nested.path, expanded: [''] };
  client.setQueryData(fileBrowserStateKey('fixed-version', 'detail-1'), saved);
  const renderToken = (token: string) => renderToStaticMarkup(<QueryClientProvider client={client}><SkillFiles token={token} identity="fixed-version" rootName="package" remote={remote} /></QueryClientProvider>);
  try {
    const restored = renderToken('detail-1');
    expect(restored).toContain('selected nested document');
    expect(restored).not.toContain('root document');
    expect(restored).toContain('aria-label="目录 custom" title="custom/" aria-expanded="false"');
    expect(restored).not.toContain('aria-label="custom/deep/notes.md 10 B"');
    const isolated = renderToken('detail-2');
    expect(isolated).toContain('root document');
    expect(isolated).not.toContain('selected nested document');
    expect(isolated).toContain('aria-label="目录 custom" title="custom/" aria-expanded="true"');
    expect(client.getQueryData<SkillFileBrowserState>(fileBrowserStateKey('fixed-version', 'detail-1'))).toEqual(saved);
    expect(client.getQueryData(fileBrowserStateKey('other-source', 'detail-1'))).toBeUndefined();
  } finally { client.clear(); }
});
