import { expect, test } from 'bun:test';
import { renderToStaticMarkup } from 'react-dom/server';
import { RemovalConfirmationSummary } from '../src/client/removal-control.js';

const item = { namespace: 'global', slug: 'meeting-notes', scope: 'user' };
test('removal summary keeps identity, version, chosen scope and the scope-specific impact', () => {
  const html = renderToStaticMarkup(<RemovalConfirmationSummary item={item} version="1.2.0" stale={false} />);
  expect(html).toContain('@global/meeting-notes · v1.2.0');
  expect(html).toContain('所有项目（当前用户）');
  expect(html).toContain('项目内的独立安装保留');
  for (const text of ['来源：', '目标主机', '确认有效至', '操作 ID', '/tmp/', 'http://', '正在运行的任务']) expect(html).not.toContain(text);
});
test('project removal does not imply removing the user-level or other project installation', () => {
  const html = renderToStaticMarkup(<RemovalConfirmationSummary item={{ ...item, scope: 'project' }} version="1.2.0" stale={false} />);
  expect(html).toContain('仅此项目');
  expect(html).toContain('用户级及其他项目的安装保留');
  expect(html).not.toContain('移除当前用户共享的技能');
});
test('missing targets are explicitly record cleanup without file deletion', () => {
  const html = renderToStaticMarkup(<RemovalConfirmationSummary item={item} version="1.2.0" stale />);
  expect(html).toContain('仅移除失效记录，不删除文件');
});
