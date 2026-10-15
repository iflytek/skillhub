import { expect, test } from 'bun:test';
import { setTimeout as delay } from 'node:timers/promises';
import { retryAfterDeadline } from '../src/host/retry-after.js';
import { RegistryAdapter } from '../src/host/registry-adapter.js';

const now = Date.UTC(2026, 9, 15, 0, 0, 0);
test('Retry-After parses seconds and HTTP dates without treating malformed delays as dates', () => {
  expect(retryAfterDeadline('120', now)).toBe(now + 120_000);
  expect(retryAfterDeadline('0', now)).toBe(now);
  expect(retryAfterDeadline(' 2 ', now)).toBe(now + 2000);
  expect(retryAfterDeadline('Thu, 15 Oct 2026 00:02:00 GMT', now)).toBe(now + 120_000);
  expect(retryAfterDeadline('Wed, 14 Oct 2026 00:00:00 GMT', now)).toBe(now);
  for (const input of [null, '', '-1', '1.5', '1e3', 'NaN', '999999999999999999999999999', 'not a date']) {
    expect(retryAfterDeadline(input, now)).toBeUndefined();
  }
});

test('a real 429 prevents follow-up requests across endpoints until manual retry after expiry', async () => {
  let calls = 0;
  const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch() {
    calls++;
    return calls === 1 ? new Response('private diagnostics', { status: 429, headers: { 'Retry-After': '1' } })
      : Response.json({ data: { items: [], total: 0, limit: 20 } });
  } });
  try {
    const registry = new RegistryAdapter(server.url.href);
    await expect(registry.search()).rejects.toMatchObject({ code: 'RATE_LIMITED', status: 429, retryAfterSeconds: 1 });
    await expect(registry.documentation('team', 'writer', '1.0.0')).rejects.toMatchObject({ code: 'RATE_LIMITED', retryAfterSeconds: 1 });
    await expect(registry.search('retry')).rejects.toMatchObject({ code: 'RATE_LIMITED' });
    expect(calls).toBe(1);
    await expect(registry.search('', AbortSignal.abort())).rejects.toMatchObject({ code: 'CANCELLED' });
    await delay(1100);
    expect(calls).toBe(1); // No delayed task automatically resubmits.
    expect(await registry.search()).toEqual({ items: [], total: 0, limit: 20 });
    expect(calls).toBe(2);
  } finally { await server.stop(true); }
});
