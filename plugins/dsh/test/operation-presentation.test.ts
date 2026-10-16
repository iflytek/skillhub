import { expect, test } from 'bun:test';
import { canCollapseHistory } from '../src/client/operation-presentation.js';
import type { NativeOperation } from '../src/host/types.js';

test('history never hides unconfirmed, failed, recovering or warned operations', () => {
  expect(canCollapseHistory(undefined, false)).toBe(false);
  expect(canCollapseHistory(null, false)).toBe(false);
  const complete = { phase: 'completed' as const, warningCount: 0 };
  expect(canCollapseHistory(complete, false)).toBe(true);
  expect(canCollapseHistory(complete, true)).toBe(false);
  expect(canCollapseHistory({ ...complete, warningCount: 1 }, false)).toBe(false);
  for (const phase of ['failed', 'recovery-required', 'unknown'] as NativeOperation['phase'][]) {
    expect(canCollapseHistory({ ...complete, phase }, false)).toBe(false);
  }
});
