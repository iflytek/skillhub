import { expect, test } from 'bun:test';
import { checkInstalledUpdates, updateFailed } from '../src/client/update-overview.js';
import type { InstalledUpdateCheck } from '../src/host/types.js';
const current: InstalledUpdateCheck = { state: 'current', currentVersion: '1', candidateVersion: null, reason: '', checkedAt: '' };
test('bounded queue delivers progressive mixed results and continues after failure', async () => {
  let active = 0; let peak = 0;
  const seen: string[] = [];
  await checkInstalledUpdates(['a', 'b', 'c', 'd'], async key => {
    active++; peak = Math.max(peak, active);
    await Promise.resolve(); active--;
    if (key === 'b') throw new Error('network');
    return current;
  }, new AbortController().signal, (key, result) => { seen.push(key); expect(updateFailed(result)).toBe(key === 'b'); });
  expect(peak).toBe(3); expect(seen.sort()).toEqual(['a', 'b', 'c', 'd']);
});
test('cancel settles ignored abort transport, stops queue and discards late replies', async () => {
  const controller = new AbortController(); let calls = 0; let received = 0;
  const pending: ((value: InstalledUpdateCheck) => void)[] = [];
  const work = checkInstalledUpdates(['a', 'b', 'c', 'd'], (_key, signal) => {
    calls++; expect(signal.aborted).toBe(false);
    return new Promise(resolve => pending.push(resolve));
  }, controller.signal, () => received++);
  controller.abort(); await work;
  pending.forEach(resolve => resolve(current)); await Promise.resolve();
  expect(calls).toBe(3); expect(received).toBe(0);
});
test('timeouts settle as failures and abort each request', async () => {
  let aborted = false;
  const results: string[] = [];
  await checkInstalledUpdates(['a'], (_key, signal) => {
    signal.addEventListener('abort', () => { aborted = true; });
    return new Promise(() => {});
  }, new AbortController().signal, (_key, result) => results.push(result.state), 5);
  expect(aborted).toBe(true); expect(results).toEqual(['failed']);
  expect(updateFailed({ ...current, state: 'unavailable' })).toBe(true);
  expect(updateFailed({ ...current, state: 'blocked' })).toBe(false);
});
