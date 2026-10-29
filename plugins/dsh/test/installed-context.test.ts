import { expect, test } from 'bun:test';
import { installedContext } from '../src/client/installed-context.js';
test('workspace, scope and retention changes invalidate monotonically including A to B to A', () => {
  const scope = {};
  let snapshot = { scope, cwd: 'A', active: true };
  let listener = () => {};
  const context = installedContext(() => snapshot, next => { listener = next; return () => { listener = () => {}; }; });
  const seen: number[] = [];
  const stop = context.subscribe(() => seen.push(context.getSnapshot()));
  expect(context.getSnapshot()).toBe(0);
  snapshot = { ...snapshot, cwd: 'B' }; listener();
  snapshot = { ...snapshot, cwd: 'A' }; listener();
  snapshot = { ...snapshot, scope: {} }; listener();
  snapshot = { ...snapshot, active: false }; listener();
  expect(seen).toEqual([1, 2, 3, 4]);
  expect(context.getSnapshot()).toBe(4);
  stop();
});
