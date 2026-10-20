import { expect, test } from 'bun:test';
import { renderToStaticMarkup } from 'react-dom/server';
import { randomUUID } from 'node:crypto';
import { PendingInstallation } from '../src/client/install-tool-view.js';
import type { PendingInstallResult } from '../src/client/install-result.js';

const pending: PendingInstallResult = { schemaVersion: 1, kind: 'skillhub-install', status: 'awaiting-confirmation',
  toolCallId: 'call', selectionRef: randomUUID(), namespace: 'team', slug: 'writer', version: '1.0.0',
  sourceLabel: 'https://registry.example', sourceRevision: randomUUID(), createdAt: new Date().toISOString() };

test('pending installation renders scope meanings and cannot install from missing session UI services', () => {
  const html = renderToStaticMarkup(<PendingInstallation result={pending} />);
  expect(html).toContain('尚未安装');
  expect(html).toContain('选择范围并安装');
  expect(html).toContain('disabled');
  expect(html).toContain('当前项目：供这个项目中的 DSH 会话使用');
  expect(html).toContain('当前用户：可在其他项目中发现，读取仍受会话权限限制');
  expect(html).toContain('重新搜索');
  expect(html).not.toContain(pending.selectionRef);
  expect(html).not.toContain('安装成功');
  expect(html).not.toContain('<dialog');
});
