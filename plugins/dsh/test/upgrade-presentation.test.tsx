import { expect, test } from 'bun:test';
import { renderToStaticMarkup } from 'react-dom/server';
import { UpgradeConfirmationSummary } from '../src/client/upgrade-control.js';
import { selectedVersionUpdateMessage } from '../src/client/install-control.js';
import type { UpgradePlanView } from '../src/host/types.js';
const item = { namespace: 'global', slug: 'meeting-notes', version: '1.0.0', scope: 'user' };
const plan: UpgradePlanView = { hostInstanceId: 'host', status: 'ready', reason: 'newer-version', currentVersion: '1.0.0', candidateVersion: '1.1.0', planId: 'plan', expiresAt: '2026-10-20T00:00:00Z', targets: [{ scope: 'user', path: '/private/user/skills' }, { scope: 'project', path: '/private/project/skills' }] };
test('confirmation exposes exact version transition and every affected scope without raw paths', () => {
  const html = renderToStaticMarkup(<UpgradeConfirmationSummary item={item} plan={plan} targetVersion="1.1.0" />);
  expect(html).toContain('v1.0.0'); expect(html).toContain('v1.1.0');
  expect(html).toContain('所有项目（当前用户）、仅此项目');
  expect(html).not.toContain('/private/'); expect(html).not.toContain('2026-10-20');
});
test('selected non-latest version remains explicit while preflight is loading', () => {
  const html = renderToStaticMarkup(<UpgradeConfirmationSummary item={item} plan={undefined} targetVersion="1.1.0" />);
  expect(html).toContain('v1.1.0'); expect(html).not.toContain('最新版');
});
test('older and unorderable versions explain that no upgrade can be submitted', () => {
  const update = { state: 'blocked' as const, reason: 'remote-older', currentVersion: '1.2.0', candidateVersion: '1.0.0', checkedAt: '' };
  expect(selectedVersionUpdateMessage(update)).toContain('不支持降级');
  expect(selectedVersionUpdateMessage({ ...update, reason: 'unknown-order' })).toContain('无法可靠比较版本');
  expect(selectedVersionUpdateMessage({ ...update, state: 'current' })).toContain('无需升级');
});
