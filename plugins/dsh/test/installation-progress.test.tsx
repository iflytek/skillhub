import { expect, test } from 'bun:test';
import { renderToStaticMarkup } from 'react-dom/server';
import type { NativeOperation } from '../src/host/types.js';
import type { InstallationAttempt } from '../src/client/installation-requests.js';
import { installationProgressState, InstallationProgressSummary } from '../src/client/installation-progress.js';
import { shouldRefreshInstallation } from '../src/client/use-installation-operation.js';

const operation: NativeOperation = { operationId: 'operation', revision: 0, kind: 'install', namespace: 'team', slug: 'writer', version: '1',
  targetLabel: 'Project', phase: 'accepted', files: 'pending', discovery: 'unconfirmed', recovery: 'not-needed', warningCount: 0,
  context: { sourceIdentity: 'https://registry.example', sourceLabel: 'Registry', sourceRevision: 'source', hostLabel: 'Host',
    hostInstanceId: 'host', initiatedFrom: 'conversation', createdAt: '2026-10-14T00:00:00Z', updatedAt: '2026-10-14T00:00:00Z' } };
const attempt: InstallationAttempt = { state: 'accepted', operation, request: { detailToken: 'detail', targetToken: 'target',
  hostInstanceId: 'host', initiatedFrom: 'conversation', requestKey: 'key', mode: 'new' } };

test('progress follows actual phases and never fabricates intermediate percentages', () => {
  for (const phase of ['accepted', 'staging', 'revalidating', 'committing', 'recovering'] as const) {
    const value = { ...operation, phase };
    expect(installationProgressState(attempt, value, false).state).toBe('running');
    const html = renderToStaticMarkup(<InstallationProgressSummary attempt={attempt} operation={value} readFailed={false} />);
    expect(html).toContain('role="progressbar"');
    expect(html).not.toContain('aria-valuenow');
  }
  expect(installationProgressState({ ...attempt, state: 'submitting' }, undefined, false).label).toBe('正在提交安装');
  expect(installationProgressState({ ...attempt, state: 'submitting', operation: null }, null, false).state).toBe('running');
});

test('compact completion keeps essential discovery warning without exposing technical metadata', () => {
  const value = { ...operation, phase: 'completed' as const, discovery: 'confirmed' as const };
  const html = renderToStaticMarkup(<InstallationProgressSummary attempt={attempt} operation={value} readFailed={false} />);
  expect(html).toContain('安装完成');
  expect(html).toContain('技能已就绪');
  expect(html).toContain('aria-valuenow="100"');
  for (const text of ['registry.example', '2026-10-14', '原操作', '刷新发现状态', '操作 ID']) expect(html).not.toContain(text);
  expect(installationProgressState(attempt, { ...value, discovery: 'unconfirmed' }, false).message).toContain('尚待确认');
  expect(installationProgressState(attempt, { ...value, warningCount: 1 }, false).label).toContain('告警');
});

test('errors and uncertain results stop animation instead of claiming success', () => {
  for (const phase of ['failed', 'unknown', 'recovery-required'] as const) expect(installationProgressState(attempt, { ...operation, phase }, false).state).toBe('stopped');
  expect(installationProgressState({ ...attempt, state: 'unresolved' }, undefined, false).state).toBe('stopped');
  expect(installationProgressState(attempt, operation, true).state).toBe('stopped');
  expect(installationProgressState(attempt, null, false).state).toBe('stopped');
});

test('only terminal phase transitions refresh inventory, repeated receipt reads do not', () => {
  const completed = { ...operation, phase: 'completed' as const };
  expect(shouldRefreshInstallation(undefined, completed)).toBe(true);
  expect(shouldRefreshInstallation(operation, completed)).toBe(true);
  expect(shouldRefreshInstallation(completed, { ...completed, revision: 2 })).toBe(false);
  expect(shouldRefreshInstallation(completed, { ...operation, phase: 'recovery-required' })).toBe(true);
  expect(shouldRefreshInstallation(operation, operation)).toBe(false);
  expect(shouldRefreshInstallation(operation, null)).toBe(false);
});
