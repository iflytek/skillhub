import { expect, test } from 'bun:test';
import { renderToStaticMarkup } from 'react-dom/server';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { parseManagementResult, type ManagementViewResult, type ManagementReceipt } from '../src/client/management-result.js';
import { availabilityNotice, ManagementContent, InstallationManagement, ManagementLocations, ManagementConfirmation, refreshManagementLocation, groupManagementItems, managementTargetLabel, verifyManagementReference, verifiedManagementUse, type ManagementViewServices } from '../src/client/management-tool-view.js';
import { InstallationRequests } from '../src/client/installation-requests.js';

const base = { schemaVersion: 1 as const, toolCallId: 'call-1', sourceRevision: 'revision', sourceLabel: 'https://private-source.example' };
const item = { itemKey: 'internal-key', namespace: 'global', slug: 'notes', version: '1.0.0', scope: 'user' as const, state: 'present' as const };
const installed = { ...base, kind: 'skillhub-installed' as const, items: [item], complete: true, unavailable: [], issues: [] };
const pending: ManagementReceipt = { ...base, kind: 'skillhub-management', action: 'upgrade', item, status: 'awaiting-confirmation', candidateVersion: '2.0.0', reason: '', confirmationRef: 'secret-confirmation-ref', operation: null };
function render(result: ManagementViewResult, operation?: object, locations = false) {
  const client = new QueryClient();
  if (operation && result.kind === 'skillhub-management' && result.operation) client.setQueryData(['skillhub-operation', 'session', result.operation.context.hostInstanceId, result.operation.operationId], operation);
  const requests = new InstallationRequests();
  const unexpected = async () => { throw new Error('Rendering cannot execute requests'); };
  const services: ManagementViewServices = { selectSkillUse: unexpected, client, refreshInstalled: unexpected, check: unexpected, begin: unexpected, confirm: unexpected, assertCurrent() {},
    installation: { manageInstalled() {}, skillUse: { useSkill: unexpected }, sessionId: 'session', requests, targets: unexpected, installed: unexpected, start: unexpected, operation: unexpected, compatibility: unexpected } };
  try { return renderToStaticMarkup(<QueryClientProvider client={client}>{locations && result.kind === 'skillhub-installed' ? <ManagementLocations items={result.items} revision={result.sourceRevision} services={services} /> : <ManagementContent result={result} services={services} />}</QueryClientProvider>); }
  finally { requests.dispose(); client.clear(); }
}
test('metadata belongs to this call and invalid records cannot offer management controls', () => {
  expect(parseManagementResult(installed, 'call-1')).toEqual(installed);
  expect(parseManagementResult(installed, 'different-call')).toBeUndefined();
  expect(parseManagementResult({ ...installed, items: [{ ...item, scope: '/tmp/arbitrary' }] }, 'call-1')).toBeUndefined();
  expect(parseManagementResult({ ...pending, status: 'completed' }, 'call-1')).toBeUndefined();
  expect(parseManagementResult({ ...pending, confirmationRef: null }, 'call-1')).toBeUndefined();
  expect(parseManagementResult({ ...pending, status: 'accepted' }, 'call-1')).toBeUndefined();
});
test('list is current-source scoped, limited, and keeps distinct actual installation scopes', () => {
  const html = render({ ...installed, items: Array.from({ length: 8 }, (_, i) => ({ ...item, slug: `notes-${i}`, itemKey: `key-${i}`, scope: i === 0 ? 'project' : 'user' })) });
  expect(html).toContain('当前来源'); expect(html).not.toContain('当前项目'); expect(html).not.toContain('当前用户');
  expect(html).toContain('notes-5'); expect(html).not.toContain('notes-6'); expect(html).toContain('查看更多');
  expect(html).toContain('管理安装'); expect(html).toContain('使用技能');
  expect(html).not.toContain('aria-label="技能操作"');
  expect(html).not.toContain('重新查询'); expect(html).not.toContain('刷新安装'); expect(html).not.toContain('查询时的安装记录');
  expect(html).not.toContain(base.sourceLabel); expect(html).not.toContain('key-0');
});
test('incomplete empty inventory is not rendered as a verified empty list', () => {
  const html = render({ ...installed, items: [], complete: false, unavailable: ['project'] });
  expect(html).toContain('列表可能不完整'); expect(html).toContain('重新查询'); expect(html).not.toContain('当前来源暂无安装记录');
});
test('update confirmation has version/scope and both human actions but no internal reference or raw paths', () => {
  const html = render(pending);
  expect(html).toContain('v1.0.0 → v2.0.0'); expect(html).toContain('当前用户');
  expect(html).toContain('确认更新'); expect(html).toContain('取消');
  expect(html).not.toContain(pending.confirmationRef!); expect(html).not.toContain(item.itemKey); expect(html).not.toContain(base.sourceLabel);
});
test('local changes and cancelled result never show a submit button', () => {
  const blocked = render({ ...pending, status: 'blocked', confirmationRef: null, reason: 'local-changes' });
  expect(blocked).toContain('存在本地修改'); expect(blocked).not.toContain('确认更新'); expect(blocked).not.toContain('重新查询');
  const cancelled = render({ ...pending, status: 'cancelled', confirmationRef: null });
  expect(cancelled).toContain('已取消，未执行更新'); expect(cancelled).not.toContain('确认更新');
});
test('failed update lookup cannot render as latest and missing installation offers record cleanup', () => {
  const html = render({ ...base, kind: 'skillhub-update-check', item, check: { state: 'unavailable', currentVersion: '1.0.0', candidateVersion: null, reason: 'network', checkedAt: new Date().toISOString() } });
  expect(html).toContain('更新检查未完成'); expect(html).not.toContain('已是最新版本');
  const missing = render({ ...installed, items: [{ ...item, state: 'missing' }] }, undefined, true);
  expect(missing).toContain('清理记录'); expect(missing).not.toContain('>卸载</button>');
});
test('author-controlled names stay escaped plain text', () => {
  const html = render({ ...installed, items: [{ ...item, slug: '<script>alert(1)</script>' }] });
  expect(html).toContain('&lt;script&gt;'); expect(html).not.toContain('<script>');
});

test('direct update of the installed version explains that no update is needed', () => {
  const html = render({ ...pending, status: 'blocked', confirmationRef: null, candidateVersion: item.version, reason: 'same-content' });
  expect(html).toContain('已安装此版本，无需更新');
  expect(html).not.toContain('确认更新');
  expect(html).not.toContain('当前条件不满足');
});


test('update-check has one relevant action, not a duplicate installed list', () => {
  const html = render({ ...base, kind: 'skillhub-update-check', item, check: { state: 'upgradable', currentVersion: '1.0.0', candidateVersion: '2.0.0', reason: '', checkedAt: '2026-10-23' } });
  expect(html).toContain('v1.0.0 → v2.0.0'); expect(html).toContain('>更新</button>');
  expect(html).not.toContain('使用此技能'); expect(html).not.toContain('>检查更新</button>'); expect(html).not.toContain('>卸载</button>');
  expect(html).toContain('@global/notes'); expect(html).toContain('当前用户');
});
test('operation result is compact, keeps warning and scope, and distinguishes processing', () => {
  const op = { operationId: '11111111-1111-4111-8111-111111111111', context: { hostInstanceId: '22222222-2222-4222-8222-222222222222' }, kind: 'upgrade', namespace: 'global', slug: 'notes', version: '2.0.0', targetLabel: '当前用户', phase: 'staging', warningCount: 0 };
  const result = { schemaVersion: 1 as const, kind: 'skillhub-operation' as const, toolCallId: 'call-1', operation: op };
  expect(render(result)).toContain('更新处理中'); expect(render(result)).not.toContain('检查更新');
  const warning = render({ ...result, operation: { ...op, phase: 'completed', warningCount: 1, discovery: 'failed', recovery: 'unknown' } });
  expect(warning).toContain('有告警需核验'); expect(warning).toContain('恢复状态待核验'); expect(warning).not.toContain('DSH 发现尚待确认');
  expect(warning).toContain('@global/notes'); expect(warning).toContain('当前用户');
  expect(parseManagementResult(result, 'call-1')).toEqual(result);
});
test('reference validation never silently substitutes another version or scope', async () => {
  const services = { assertCurrent() {}, installation: { installed: async () => ({ ok: true as const, value: { ...installed, items: [{ ...item, itemKey: 'new-key', version: '2.0.0' }] } }) } } as unknown as ManagementViewServices;
  await expect(verifyManagementReference(services, 'revision', item.itemKey, new AbortController().signal)).rejects.toMatchObject({ code: 'skillhub/context-stale' });
  services.installation.installed = async () => ({ ok: true, value: { ...installed, items: [], complete: false, unavailable: ['user'] } });
  await expect(verifyManagementReference(services, 'revision', item.itemKey, new AbortController().signal)).rejects.toMatchObject({ code: 'skillhub/installation-unavailable' });
  services.installation.installed = async () => ({ ok: true, value: installed });
  await verifyManagementReference(services, 'revision', item.itemKey, new AbortController().signal);
});
test('Use preflight blocks stale records but preserves task-selection cancellation and errors', async () => {
  const cancellation = new Error('selection cancelled'); let invalid = '', uses = 0;
  const services = { assertCurrent() {}, installation: { installed: async () => ({ ok: true as const, value: installed }), skillUse: { useSkill: async () => { uses++; throw cancellation; } } } } as unknown as ManagementViewServices;
  const use = verifiedManagementUse(services, 'revision', item.itemKey, message => { invalid = message; })!;
  await expect(use.useSkill('revision', item.itemKey, new AbortController().signal)).rejects.toBe(cancellation);
  expect(invalid).toBe(''); expect(uses).toBe(1);
  services.installation.installed = async () => ({ ok: true, value: { ...installed, items: [] } });
  await expect(use.useSkill('revision', item.itemKey, new AbortController().signal)).rejects.toMatchObject({ code: 'skillhub/context-stale' });
  expect(invalid).toContain('安装记录已变化'); expect(uses).toBe(1);
});

test('completed receipt stays concise while incomplete discovery and warnings remain visible', () => {
  const result: ManagementReceipt = { ...pending, status: 'accepted', confirmationRef: null, operation: { operationId: '11111111-1111-4111-8111-111111111111', context: { hostInstanceId: '22222222-2222-4222-8222-222222222222' } } };
  const clean = { phase: 'completed', warningCount: 0, discovery: 'confirmed', recovery: 'not-needed' };
  const html = render(result, clean);
  expect(html).toContain('更新已完成。'); expect(html).not.toContain('查询操作状态'); expect(html).not.toContain('检查更新'); expect(html).toContain('v1.0.0 → v2.0.0');
  const uncertain = render(result, { ...clean, warningCount: 1, discovery: 'unconfirmed' });
  expect(uncertain).toContain('有告警需核验'); expect(uncertain).not.toContain('DSH 发现尚待确认'); expect(uncertain).toContain('查询操作状态');
});

test('read retry is reserved for unavailable or stale results, not healthy or completed cards', () => {
  const stale = render({ ...pending, status: 'blocked', confirmationRef: null, reason: 'record-changed' });
  expect(stale).toContain('重新查询'); expect(stale).not.toContain('确认更新');
  const unavailable = render({ ...base, kind: 'skillhub-update-check', item, check: { state: 'blocked', currentVersion: '1.0.0', candidateVersion: null, reason: 'local-record-unverified', checkedAt: '2026-10-23' } });
  expect(unavailable).toContain('重新查询'); expect(unavailable).not.toContain('>更新</button>');
  const current = render({ ...base, kind: 'skillhub-update-check', item, check: { state: 'current', currentVersion: '1.0.0', candidateVersion: null, reason: '', checkedAt: '2026-10-23' } });
  expect(current).toContain('已是最新版本'); expect(current).not.toContain('<button');
  const cancelled = render({ ...pending, status: 'cancelled', confirmationRef: null });
  expect(cancelled).not.toContain('重新查询'); expect(cancelled).not.toContain('操作详情');
});


test('grouping preserves exact targets and separates namespace identities', () => {
  const project = { ...item, itemKey: 'project-key', scope: 'project' as const, version: '2.0.0' };
  const secondUser = { ...item, itemKey: 'second-user-key', version: '3.0.0' };
  const foreignName = { ...item, namespace: 'another', itemKey: 'another-key' };
  const groups = groupManagementItems([item, project, secondUser, foreignName]);
  expect(groups).toEqual([[item, project, secondUser], [foreignName]]);
  expect(managementTargetLabel(groups[0]!, item)).toBe('当前用户 1');
  expect(managementTargetLabel(groups[0]!, secondUser)).toBe('当前用户 2');
  expect(managementTargetLabel(groups[0]!, project)).toBe('当前项目');
  const html = render({ ...installed, items: [item, project, secondUser, foreignName] });
  expect(html).toContain('2 个技能 · 4 处安装');
  expect(html).toContain('data-single="false"');
  const oneSkill = render({ ...installed, items: [item, project, secondUser] });
  expect(oneSkill).toContain('data-single="true"');
  expect((oneSkill.match(/class="astron-sh-management-group"/gu) ?? []).length).toBe(1);
  expect((html.match(/class="astron-sh-management-group"/gu) ?? []).length).toBe(2);
  const managed = render({ ...installed, items: [item, project, secondUser] }, undefined, true);
  expect(managed).toContain('所有项目 1'); expect(managed).toContain('所有项目 2');
  expect(managed).toContain('v2.0.0'); expect(managed).toContain('v3.0.0');
  expect(managed).not.toContain('role="tab"');
  for (const key of ['internal-key', 'project-key', 'second-user-key', 'another-key']) expect(html).not.toContain(key);
});
test('single installation has no tab and unhealthy targets do not offer destructive actions', () => {
  expect(render(installed)).not.toContain('role="tab"'); expect(render(installed)).not.toContain('role="tabpanel"');
  for (const state of ['metadata-invalid', 'mismatch', 'unreadable'] as const) {
    const html = render({ ...installed, items: [{ ...item, state }] });
    expect(html).toContain('管理安装'); expect(html).not.toContain('更多操作');
    expect(html).not.toContain('使用此技能'); expect(html).not.toContain('>卸载</button>');
  }
  const partial = render({ ...installed, complete: false, unavailable: ['project'] });
  expect(partial).toContain('当前项目状态未知'); expect(partial).not.toContain('未安装');
});
test('normal controls use one disclosure, no permanent query maintenance button', () => {
  const html = render(installed);
  expect(html).toContain('管理安装'); expect(html).toContain('使用技能'); expect(html).not.toContain('aria-label="技能操作"');
  expect(html).not.toContain('重新查询'); expect(html).not.toContain('操作详情');
});
test('unknown operation offers only original-operation lookup and processing receipt has no resubmit', () => {
  const op = { operationId: '11111111-1111-4111-8111-111111111111', context: { hostInstanceId: '22222222-2222-4222-8222-222222222222' }, kind: 'upgrade', namespace: 'global', slug: 'notes', version: '2.0.0', targetLabel: '当前项目', phase: 'unknown', warningCount: 0 };
  const html = render({ schemaVersion: 1, kind: 'skillhub-operation', toolCallId: 'call-1', operation: op });
  expect(html).toContain('查询原操作'); expect(html).not.toContain('确认更新'); expect(html).not.toContain('重新查询');
  const receipt: ManagementReceipt = { ...pending, status: 'accepted', confirmationRef: null, operation: op };
  const progressing = render(receipt, { phase: 'staging', warningCount: 0, discovery: 'unconfirmed', recovery: 'not-needed' });
  expect(progressing).toContain('正在更新'); expect(progressing).not.toContain('<button');
});

test('mixed single and dual installations share identity, position and action slots without fake single tabs', () => {
  const html = render({ ...installed, items: [
    { ...item, slug: 'single', scope: 'user', itemKey: 'single' },
    { ...item, slug: 'dual', scope: 'project', itemKey: 'dual-project' },
    { ...item, slug: 'dual', scope: 'user', itemKey: 'dual-user' },
  ] });
  expect(html.match(/class="astron-sh-management-group"/g)).toHaveLength(2);
  expect(html.match(/>管理安装<\/button>/g)).toHaveLength(2);
  expect(html).not.toContain('role="tab"'); expect(html).not.toContain('当前用户');
});

test('two-column catalog initially shows six cards and offers more for larger totals', () => {
  for (const count of [6, 12, 13]) {
    const html = render({ ...installed, items: Array.from({ length: count }, (_, i) => ({ ...item, slug: `batch-${i}`, itemKey: `batch-${i}` })) });
    expect(html.match(/class="astron-sh-management-group"/g)).toHaveLength(6);
    expect(html.includes('查看更多')).toBe(count > 6);
    expect(html).toContain('batch-5'); expect(html).not.toContain('batch-6');
  }
});


test('availability transcript is compact and does not offer ownership or mutation actions', () => {
  const value = { schemaVersion: 1 as const, kind: 'skillhub-availability' as const, toolCallId: 'call-1', diagnosis: {
    checkedAt: 'now', name: 'writer', association: 'not-associated' as const, integrity: 'unknown' as const,
    discovery: 'winner' as const, reason: 'observed' as const, invocation: { userInvocable: false, modelInvocable: true },
  } };
  const parsed = parseManagementResult({ ...value, diagnosis: { ...value.diagnosis, path: '/secret/path' } }, 'call-1');
  expect(parsed).toEqual(value);
  const html = render(value);
  expect(html).toContain('writer'); expect(html).toContain('仅反映本次检查结果'); expect(html).not.toContain('仍会重新核验');
  expect(html).toContain('来源未关联'); expect(html).toContain('用户调用禁用'); expect(html).toContain('模型调用允许');
  expect(html).not.toContain('<button'); expect(html).not.toContain('未安装');
  expect(render({ ...value, diagnosis: { ...value.diagnosis, invocation: undefined, discovery: 'unknown', reason: 'snapshot-incomplete' } })).toContain('宿主技能列表不完整');
});


test('standalone missing definition identifies the selected installation without a fabricated invocation name', () => {
  const html = render({ schemaVersion: 1, kind: 'skillhub-availability', toolCallId: 'call-1', diagnosis: {
    checkedAt: 'now', association: 'managed-reference', scope: 'user', integrity: 'missing', discovery: 'unknown', reason: 'files-missing',
    installation: { namespace: 'team', slug: 'selected-package', version: '1.2.0', scope: 'user' },
  } });
  expect(html).toContain('@team/selected-package'); expect(html).toContain('v1.2.0');
  expect(html).toContain('当前用户'); expect(html).toContain('文件缺失'); expect(html).not.toContain('<button');
});

test('disabled installation renders enable instead of use and remains an installed location', () => {
  const disabled = { ...item, installationState: 'disabled' as const, locationKey: 'location-a' };
  const result = { ...installed, items: [disabled] };
  expect(parseManagementResult(result, 'call-1')).toEqual(result);
  const html = render(result);
  expect(html).toContain('已停用'); expect(html).toContain('管理安装'); expect(html).not.toContain('>启用</button>');
  const advanced = render(result, undefined, true); expect(advanced).toContain('>启用</button>'); expect(advanced).not.toContain('使用此位置');
  expect(html).not.toContain('使用此技能'); expect(html).not.toContain('安装文件缺失');
  expect(html).not.toContain('location-a'); expect(html).not.toContain('更多操作');
});
test('toggle confirmations preserve exact scope and explain historical context limits', () => {
  for (const action of ['enable', 'disable'] as const) {
    const value = { ...pending, action, candidateVersion: null };
    expect(parseManagementResult(value, 'call-1')).toEqual(value);
    const html = render(value);
    expect(html).toContain(action === 'enable' ? '确认启用' : '确认停用');
    expect(html).toContain('当前用户'); expect(html).toContain('取消');
    expect(html).toContain(action === 'enable' ? '保留原有调用权限' : '不撤回已加载内容');
    expect(html).not.toContain('确认卸载');
  }
  const shared = render({ ...pending, action: 'disable', status: 'blocked', confirmationRef: null, reason: 'shared-target' });
  expect(shared).toContain('多个来源共同管理'); expect(shared).not.toContain('确认停用');
});
test('disabled reference cannot use even if a historical key was incorrectly retained', async () => {
  let uses = 0;
  const services = { assertCurrent() {}, installation: {
    installed: async () => ({ ok: true as const, value: { ...installed, items: [{ ...item, installationState: 'disabled' }] } }),
    skillUse: { useSkill: async () => { uses++; } },
  } } as unknown as ManagementViewServices;
  const use = verifiedManagementUse(services, 'revision', item.itemKey, () => {})!;
  await expect(use.useSkill('revision', item.itemKey, new AbortController().signal)).rejects.toMatchObject({ code: 'skillhub/context-stale' });
  expect(uses).toBe(0);
});
test('disabled availability distinguishes installation state from author invocation policy', () => {
  const value = { schemaVersion: 1 as const, kind: 'skillhub-availability' as const, toolCallId: 'call-1', diagnosis: {
    checkedAt: 'now', installationState: 'disabled' as const, association: 'managed-reference' as const,
    integrity: 'verified' as const, discovery: 'unknown' as const, reason: 'installation-disabled' as const,
    invocation: { userInvocable: true, modelInvocable: false },
  } };
  expect(parseManagementResult(value, 'call-1')).toEqual(value);
  const html = render(value);
  expect(html).toContain('该安装已停用'); expect(html).toContain('用户调用允许'); expect(html).toContain('模型调用禁用');
});

test.each([
  ['shadowed', 'same-name-winner', '所选安装被同名技能覆盖'],
  ['not-discovered', 'not-discovered', '当前会话未发现此技能'],
  ['winner', 'installation-disabled', '当前会话仍发现此安装'],
  ['unknown', 'snapshot-incomplete', '宿主技能列表不完整'],
] as const)('standalone disabled diagnosis preserves state with %s discovery', (discovery, reason, observation) => {
  const html = render({ schemaVersion: 1, kind: 'skillhub-availability', toolCallId: 'call-1', diagnosis: {
    checkedAt: 'now', installationState: 'disabled', association: 'managed-reference', integrity: 'verified', discovery, reason,
  } });
  expect(html).toContain('该安装已停用');
  expect(html).toContain(observation);
  expect(html).not.toContain('使用技能');
  expect(html).not.toContain('用户调用允许');
});

test('post-success refresh requires stable exact location and same source, never scope-only rebinding', async () => {
  const target = { ...item, locationKey: 'location-user-a' };
  const next = { ...target, itemKey: 'after-toggle', installationState: 'disabled' as const };
  const sibling = { ...target, locationKey: 'location-user-b', itemKey: 'sibling' };
  const services = { assertCurrent() {}, refreshInstalled: async () => ({ sourceRevision: 'revision', list: { ...installed, items: [sibling, next] } }) } as unknown as ManagementViewServices;
  expect((await refreshManagementLocation(services, 'revision', target)).items).toEqual([sibling, next]);
  await expect(refreshManagementLocation(services, 'changed-source', target)).rejects.toMatchObject({ code: 'skillhub/context-stale' });
  await expect(refreshManagementLocation(services, 'revision', item)).rejects.toMatchObject({ code: 'skillhub/context-stale' });
  services.refreshInstalled = async () => ({ sourceRevision: 'revision', list: { ...installed, items: [sibling] } });
  await expect(refreshManagementLocation(services, 'revision', target)).rejects.toMatchObject({ code: 'skillhub/context-stale' });
});

test('normal toggle operation is concise without asserting discovery success', () => {
  const operation = { operationId: '11111111-1111-4111-8111-111111111111', context: { hostInstanceId: '22222222-2222-4222-8222-222222222222' }, kind: 'disable', slug: 'notes', version: '1.0.0', targetLabel: '当前用户', phase: 'completed', warningCount: 0, discovery: 'unconfirmed' as const };
  const html = render({ schemaVersion: 1, kind: 'skillhub-operation', toolCallId: 'call-1', operation });
  expect(html).toContain('停用已完成'); expect(html).not.toContain('DSH 发现尚待确认'); expect(html).not.toContain('已就绪'); expect(html).not.toContain('该位置已停用'); expect(html).not.toContain('查询原操作');
});

test('fallback toggle confirmation has one scope and impact explanation with a separated footer', () => {
  const client = new QueryClient();
  const services = { installation: {} } as ManagementViewServices;
  const result = { ...pending, action: 'disable' as const, candidateVersion: null };
  try {
    const html = renderToStaticMarkup(<QueryClientProvider client={client}><ManagementConfirmation initial={result} services={services} presentation="dialog" title="笔记助手" /></QueryClientProvider>);
    expect(html).toContain('笔记助手'); expect(html).toContain('@global/notes · v1.0.0');
    expect(html).toContain('所有项目 · 当前用户'); expect(html).toContain('不撤回已加载内容');
    expect(html).toContain('其他同名安装可能仍可用'); expect(html).toContain('astron-sh-dialog-actions');
    expect(html).not.toContain('确认停用这份安装？'); expect(html).not.toContain('影响当前用户的多个项目');
  } finally { client.clear(); }
});

test('management location list renders an explicit empty state after the final installation is removed', () => {
  const html = render({ ...installed, items: [] }, undefined, true);
  expect(html).toContain('该技能暂无安装记录'); expect(html).not.toContain('<button');
});

test('detail installation management is inline and survives an empty refreshed inventory', () => {
  const client = new QueryClient();
  const requests = new InstallationRequests();
  const unexpected = async () => { throw new Error('Rendering cannot execute requests'); };
  const services: ManagementViewServices = { selectSkillUse: unexpected, client, refreshInstalled: unexpected, check: unexpected, begin: unexpected, confirm: unexpected, assertCurrent() {},
    installation: { sessionId: 'session', requests, targets: unexpected, installed: unexpected, start: unexpected, operation: unexpected, compatibility: unexpected } };
  const key = ['skillhub-installed', 'session', 'revision', 'management-locations', item.namespace, item.slug];
  const renderPanel = () => renderToStaticMarkup(<QueryClientProvider client={client}><InstallationManagement identity={item} revision="revision" services={services} /></QueryClientProvider>);
  try {
    client.setQueryData(key, { ...installed, items: [{ ...item, installationState: 'disabled' }, { ...item, itemKey: 'project-key', scope: 'project', version: '2', installationState: 'enabled' }] });
    const html = renderPanel();
    expect(html).toContain('aria-label="安装管理"'); expect(html).toContain('所有项目 v1.0.0'); expect(html).toContain('仅此项目 v2');
    expect(html).toContain('>启用</button>'); expect(html).not.toContain('>停用</button>');
    expect(html.match(/aria-label="更多操作"/g)).toHaveLength(2);
    expect(html).not.toContain('使用此位置'); expect(html).not.toContain('检查可用性'); expect(html).not.toContain('>卸载</button>');
    expect(html).not.toContain('role="dialog"'); expect(html).not.toContain('role="combobox"'); expect(html).not.toContain('>管理安装</button>');
    client.setQueryData(key, { ...installed, items: [] });
    const empty = renderPanel(); expect(empty).toContain('该技能暂无安装记录'); expect(empty).not.toContain('>停用</button>'); expect(empty).not.toContain('>启用</button>');
  } finally { requests.dispose(); client.clear(); }
});

test('availability notices stay concise without converting discovery into invocation permission', () => {
  const observed = { checkedAt: 'now', association: 'matched' as const, integrity: 'verified' as const, discovery: 'winner' as const, reason: 'observed' as const };
  expect(availabilityNotice({ ...observed, association: 'not-associated', integrity: 'unknown' }).success).toBe(false);
  expect(availabilityNotice({ ...observed, association: 'not-associated', integrity: 'unknown', invocation: { userInvocable: false, modelInvocable: true } }).message).toContain('用户调用禁用');
  expect(availabilityNotice({ ...observed, association: 'not-associated', integrity: 'unknown', installationState: 'disabled' }).message).toContain('缓存');
  expect(availabilityNotice({ ...observed, association: 'not-associated', integrity: 'unknown', reason: 'same-name-winner' }).message).toBe('此安装暂不可用，当前会话使用了另一份同名技能。');
});
