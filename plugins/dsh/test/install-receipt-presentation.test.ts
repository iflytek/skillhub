import { isInventoryScopeVerified } from '../src/client/installed-inventory.js';
import { expect, test } from 'bun:test';
import { receiptInventoryIssue, receiptPresentation } from '../src/client/install-receipt-presentation.js';
import type { InstalledSkillList } from '../src/host/types.js';

const receipt = { namespace: 'demo', slug: 'helper', version: '1.2.0', targetLabel: '当前项目' };
const item: InstalledSkillList['items'][number] = { itemKey: 'project-record', namespace: 'demo', slug: 'helper',
  version: '1.2.0', scope: 'project', state: 'present' };
const inventory: InstalledSkillList = { items: [item], complete: true, issues: [], unavailable: [] };

test('receipt requires the original scope, skill and exact version to match current inventory', () => {
  expect(receiptInventoryIssue(receipt, inventory, false)).toBeNull();
  for (const items of [[], [{ ...item, scope: 'user' as const }], [{ ...item, namespace: 'other' }],
    [{ ...item, version: '1.3.0' }], [{ ...item, state: 'missing' as const }]]) {
    expect(receiptInventoryIssue(receipt, { ...inventory, items }, false)).not.toBeNull();
  }
});
test('unreadable inventory and unknown historic scope cannot be presented as a verified install', () => {
  expect(receiptInventoryIssue(receipt, inventory, true)).not.toBeNull();
  expect(receiptInventoryIssue(receipt, { ...inventory, complete: false }, false)).not.toBeNull();
  expect(receiptInventoryIssue(receipt, { ...inventory, unavailable: ['project'] }, false)).not.toBeNull();
  expect(receiptInventoryIssue({ ...receipt, targetLabel: 'old scope' }, inventory, false)).not.toBeNull();
  expect(receiptInventoryIssue(receipt, undefined, false)).toBeNull();
});


const completed = { phase: 'completed', warningCount: 0, recovery: 'not-needed' } as const;
test('compact receipt distinguishes verified inventory from historical completion and warnings', () => {
  expect(receiptPresentation(completed, false, false, null)).toMatchObject({ label: '安装完成', tone: 'success' });
  for (const issue of ['当前状态无法核验。', '已无安装记录。', '当前版本已变化。']) {
    const result = receiptPresentation(completed, false, false, issue);
    expect(result).toMatchObject({ label: '历史安装完成', tone: 'warning', message: issue });
  }
  expect(receiptPresentation(completed, false, true, null)).toMatchObject({ label: '当前待核验', tone: 'pending' });
  expect(receiptPresentation({ ...completed, warningCount: 1 }, false, false, null).message).toContain('告警');
  expect(receiptPresentation(completed, true, false, null)).toMatchObject({ label: '状态待核验', tone: 'warning' });
});
test('failed and uncertain receipts retain visible recovery guidance instead of a success claim', () => {
  for (const phase of ['failed', 'unknown', 'recovery-required'] as const) {
    const result = receiptPresentation({ ...completed, phase, recovery: 'incomplete' }, false, false, null);
    expect(result.tone).toBe('warning');
    expect(result.message).toContain('核验');
    expect(result.message).toContain('勿重复安装');
  }
  expect(receiptPresentation({ ...completed, phase: 'failed', recovery: 'restored' }, false, false, null).message).toContain('原文件已恢复');
  expect(receiptPresentation(null, false, false, null).label).toBe('状态待核验');
  expect(receiptPresentation(undefined, false, false, null).label).toBe('核验中');
});

test('receipt verifies its healthy original scope despite another scope being unavailable', () => {
  expect(receiptInventoryIssue(receipt, { ...inventory, unavailable: ['user'] }, false)).toBeNull();
  expect(receiptInventoryIssue({ ...receipt, targetLabel: '当前用户' }, {
    ...inventory, items: [{ ...item, scope: 'user' }], unavailable: ['project'],
  }, false)).toBeNull();
  expect(receiptInventoryIssue(receipt, { ...inventory, complete: false, unavailable: ['user'] }, false)).not.toBeNull();
});

test('install scope verification allows only the independently healthy scope and fails closed on corrupt inventory', () => {
  for (const unavailable of ['project', 'user'] as const) {
    const partial = { ...inventory, unavailable: [unavailable] };
    const healthy = unavailable === 'project' ? 'user' : 'project';
    expect(isInventoryScopeVerified(partial, healthy)).toBe(true);
    expect(isInventoryScopeVerified(partial, unavailable)).toBe(false);
    expect(isInventoryScopeVerified({ ...partial, complete: false }, healthy)).toBe(false);
  }
  expect(isInventoryScopeVerified(undefined, 'user')).toBe(false);
});
