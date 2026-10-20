import { describe, expect, test } from 'bun:test';
import { renderToStaticMarkup } from 'react-dom/server';
import { InstallationStatus } from '../src/client/installation-status.js';
import type { SearchInstallationStatus } from '../src/host/types.js';

const snapshot: SearchInstallationStatus = {
  checkedAt: '2026-10-19T00:00:00Z', complete: true, unavailable: [], records: [],
};
const render = (installation?: SearchInstallationStatus, extra: { snapshot?: boolean; pending?: boolean } = {}) =>
  renderToStaticMarkup(<InstallationStatus installation={installation} {...extra} />);

describe('source-aware installation badges', () => {
  test('legacy history and loading never claim a skill is not installed', () => {
    expect(render(undefined, { snapshot: true })).toContain('安装状态未记录');
    expect(render(undefined, { pending: true })).toContain('核对安装状态…');
    expect(render()).toContain('安装状态未知');
    expect(render()).not.toContain('>未安装<');
  });
  test('shows both exact local versions and scopes without claiming DSH activation', () => {
    const html = render({ ...snapshot, records: [
      { scope: 'user', version: '1.0.0', state: 'present' },
      { scope: 'project', version: '2.0.0', state: 'present' },
    ] });
    expect(html).toContain('v1.0.0 · 所有项目 / v2.0.0 · 仅此项目');
    expect(html).toContain('data-tone="success"');
    expect(html).toContain('文件存在不代表 DSH 已启用或技能已执行');
    expect(html).toContain('role="status"');
  });
  test('no matching record is only a current-source observation', () => {
    const html = render(snapshot);
    expect(html).toContain('无本来源记录');
    expect(html).toContain('不代表其他来源或手动安装的同名技能不存在');
  });
  test.each(['missing', 'metadata-invalid', 'mismatch', 'unreadable'] as const)('distinguishes abnormal %s from installed', state => {
    const html = render({ ...snapshot, records: [{ scope: 'project', version: '1.0.0', state }] });
    expect(html).toContain('安装记录异常');
    expect(html).toContain('data-tone="warning"');
    expect(html).not.toContain('已安装');
  });
  test('partial reads cannot establish absence and preserve verified positive records', () => {
    expect(render({ ...snapshot, unavailable: ['project'] })).toContain('安装状态未知');
    expect(render({ ...snapshot, complete: false })).toContain('安装状态未知');
    const html = render({ ...snapshot, unavailable: ['project'], records: [{ scope: 'user', version: '1.0.0', state: 'present' }] });
    expect(html).toContain('已安装 v1.0.0 · 所有项目 · 部分未知');
  });
  test('mixed records retain the installed location and warn about the abnormal one', () => {
    const html = render({ ...snapshot, records: [
      { scope: 'user', version: '1.0.0', state: 'present' },
      { scope: 'project', version: '2.0.0', state: 'mismatch' },
    ] });
    expect(html).toContain('已安装 v1.0.0 · 所有项目 · 部分异常');
    expect(html).toContain('仅此项目 · v2.0.0 · 安装记录与本地文件不一致');
  });
  test('history explicitly labels its snapshot time and does not imply current state', () => {
    const html = render(snapshot, { snapshot: true });
    expect(html).toContain('查询时状态');
    expect(html).toContain('不代表当前仍然安装');
  });
});
