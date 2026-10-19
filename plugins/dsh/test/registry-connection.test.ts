import { expect, test } from 'bun:test';
import { Context } from '@deepseek-ai/cordis';
import SkillHubService from '@astron-team/skillhub-dsh';

async function service() {
  const ctx = new Context();
  const fiber = ctx.plugin(SkillHubService, { registryUrl: 'https://configured.example' });
  await fiber;
  return { api: ctx.skillhub, close: () => fiber.dispose() };
}
const catalog = { data: { items: [], total: 0, page: 0, size: 12 } };

test.each(['paged', 'limited'] as const)('connection probe reports %s anonymously without changing the active source', async mode => {
  const requests: string[] = [];
  const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch(request) {
    const path = new URL(request.url).pathname;
    requests.push(path);
    expect(request.method).toBe('GET');
    expect(request.headers.get('authorization')).toBeNull();
    expect(request.headers.get('cookie')).toBeNull();
    if (path === '/api/web/skills') return mode === 'paged' ? Response.json(catalog) : new Response('', { status: 404 });
    return Response.json({ data: { items: [], total: 0, limit: 20 } });
  } });
  const f = await service();
  try {
    const before = f.api.getContext();
    const result = await f.api.testRegistryConnection(server.url.href, new AbortController().signal);
    expect(result).toMatchObject({ reachable: true, mode });
    expect(result.elapsedMs).toBeGreaterThanOrEqual(0);
    expect(Object.keys(result).sort()).toEqual(['elapsedMs', 'mode', 'reachable']);
    expect(requests).toEqual(mode === 'paged' ? ['/api/web/skills'] : ['/api/web/skills', '/api/cli/v1/skills/search']);
    expect(f.api.getContext()).toEqual(before);
    expect(f.api.getLocalRuntimeStatus()).toBeNull();
  } finally { await f.close(); await server.stop(true); }
});

test('invalid, credential-bearing and non-http draft URLs are rejected without changing configuration', async () => {
  const f = await service();
  try {
    const before = f.api.getContext();
    for (const url of ['', 'not a URL', 'file:///private', 'https://user:secret@example.com', 'https://example.com?key=secret', 'https://example.com#secret']) {
      const result = await f.api.testRegistryConnection(url, new AbortController().signal).catch(error => error);
      expect(result).toMatchObject({ code: 'skillhub/registry-error', details: { reason: 'INVALID_SOURCE', retryable: false } });
      expect(result.message).not.toContain('secret');
    }
    expect(f.api.getContext()).toEqual(before);
  } finally { await f.close(); }
});

test.each([[403, 'ANONYMOUS_DENIED'], [429, 'RATE_LIMITED'], [503, 'REGISTRY_UNAVAILABLE'], [200, 'INVALID_RESPONSE']] as const)(
  'connection probe safely reports status %s without returning response contents', async (status, reason) => {
    let requests = 0;
    const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch() { requests++; return new Response('private upstream content', { status }); } });
    const f = await service();
    try {
      const result = await f.api.testRegistryConnection(server.url.href, new AbortController().signal).catch(error => error);
      expect(result).toMatchObject({ code: 'skillhub/registry-error', details: { reason } });
      expect(JSON.stringify(result)).not.toContain('private upstream content');
      expect(requests).toBe(1);
    } finally { await f.close(); await server.stop(true); }
  });

test('connection probe cancellation stops an in-flight read', async () => {
  const reached = Promise.withResolvers<void>();
  const release = Promise.withResolvers<Response>();
  const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch() { reached.resolve(); return release.promise; } });
  const f = await service();
  const controller = new AbortController();
  try {
    const result = f.api.testRegistryConnection(server.url.href, controller.signal).catch(error => error);
    await reached.promise;
    controller.abort();
    expect(await result).toMatchObject({ code: 'skillhub/registry-error', details: { reason: 'CANCELLED', retryable: false } });
  } finally { release.resolve(new Response('')); await f.close(); await server.stop(true); }
});

test('the ten-second total deadline includes the legacy fallback request', async () => {
  const paths: string[] = [];
  const release = Promise.withResolvers<Response>();
  // Keep the fixture socket alive beyond the probe deadline; an idle disconnect can retry GET.
  const server = Bun.serve({ hostname: '127.0.0.1', port: 0, idleTimeout: 30, async fetch(request) {
    const path = new URL(request.url).pathname;
    paths.push(path);
    if (path === '/api/web/skills') {
      await new Promise(resolve => setTimeout(resolve, 1000));
      return new Response('', { status: 404 });
    }
    return release.promise;
  } });
  const f = await service();
  try {
    const started = performance.now();
    const result = await f.api.testRegistryConnection(server.url.href, new AbortController().signal).catch(error => error);
    expect(result).toMatchObject({ code: 'skillhub/registry-error', details: { reason: 'NETWORK_ERROR', retryable: true } });
    const elapsed = performance.now() - started;
    expect(elapsed).toBeGreaterThanOrEqual(9_900);
    expect(elapsed).toBeLessThan(10_900);
    expect(paths).toEqual(['/api/web/skills', '/api/cli/v1/skills/search']);
  } finally { release.resolve(new Response('')); await f.close(); await server.stop(true); }
}, 15_000);
