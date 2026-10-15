import { describe, expect, test } from 'bun:test';
import { RegistryAdapter } from '../src/host/registry-adapter.ts';

function transport(handler: (url: string, init?: RequestInit) => Response | Promise<Response>): typeof fetch {
  return Object.assign(async (input: string | URL | Request, init?: RequestInit) => {
    return handler(String(input), init);
  }, { preconnect: fetch.preconnect });
}
const resolved = {
  namespace: 'team', slug: 'writer', version: '1.0.0', versionId: 7,
  fingerprint: 'sha256:abc', downloadUrl: '/download.zip',
};

describe('anonymous Registry adapter', () => {
  test.each(['headers', 'body'] as const)('times out stalled %s and permits a later retry', async phase => {
    let stalled = true;
    const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch() {
      if (!stalled) return Response.json({ data: { items: [], total: 0, limit: 20 } });
      if (phase === 'headers') return new Promise<Response>(() => {});
      return new Response(new ReadableStream({ start(controller) {
        controller.enqueue(new TextEncoder().encode('{"data":'));
      } }));
    } });
    try {
      const client = new RegistryAdapter(server.url.href, fetch, 100);
      await expect(client.search()).rejects.toMatchObject({ code: 'NETWORK_ERROR', retryable: true });
      stalled = false;
      expect(await client.search()).toEqual({ items: [], total: 0, limit: 20 });
    } finally { await server.stop(true); }
  });

  test('caller cancellation interrupts an in-flight body before the deadline', async () => {
    const caller = new AbortController();
    const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch() {
      return new Response(new ReadableStream({ start(controller) {
        controller.enqueue(new TextEncoder().encode('# incomplete'));
      } }));
    } });
    const timer = setTimeout(() => caller.abort(), 50);
    try {
      await expect(new RegistryAdapter(server.url.href, fetch, 1_000)
        .documentation('team', 'writer', '1', caller.signal)).rejects.toMatchObject({ code: 'CANCELLED', retryable: false });
    } finally { clearTimeout(timer); await server.stop(true); }
  });

  test('review pages drop moderation and account fields and normalize absent public text', async () => {
    const client = new RegistryAdapter('https://registry.example', transport(url => {
      expect(url).toEndWith('/skills/42/reviews?page=0&size=20');
      return Response.json({ data: { items: [{ id: 8, score: 5, createdAt: '2026-10-13T00:00:00Z',
        userId: 'private', moderationReason: 'private' }], total: 1, page: 0, size: 20 } });
    }));
    expect((await client.reviews(42, 0, 20)).items).toEqual([
      { id: 8, score: 5, createdAt: '2026-10-13T00:00:00Z', displayName: '', reviewText: '' },
    ]);
  });

  test('rejects invalid pagination before requesting and mismatched returned pages', async () => {
    let requests = 0;
    const client = new RegistryAdapter('https://registry.example', transport(() => {
      requests++;
      return Response.json({ data: { items: [], total: 0, page: 0, size: 20 } });
    }));
    for (const page of [-1, 0.5, Number.NaN]) {
      await expect(client.versions('team', 'writer', page, 20)).rejects.toMatchObject({ code: 'INVALID_INPUT' });
    }
    expect(requests).toBe(0);
    await expect(client.versions('team', 'writer', 1, 20)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' });
  });

  test('preserves reverse-proxy base path, encodes search, and sends no credentials', async () => {
    const client = new RegistryAdapter('https://registry.example/base/', transport((url, init) => {
      expect(url).toBe('https://registry.example/base/api/cli/v1/skills/search?q=a%26b&limit=20');
      expect(init?.credentials).toBe('omit');
      expect(init?.redirect).toBe('error');
      expect(init?.headers).toEqual({ Accept: 'application/json' });
      return Response.json({ data: { items: [], total: 0, limit: 20 } });
    }));
    expect(await client.search('a&b')).toEqual({ items: [], total: 0, limit: 20 });
  });

  test.each(['file:///tmp', 'https://user:secret@example.com', 'https://example.com?q=x', 'https://example.com/#token'])('rejects invalid source %s', source => {
    expect(() => new RegistryAdapter(source)).toThrow('INVALID_SOURCE');
  });

  test('encodes coordinate segments and pins the requested version', async () => {
    const client = new RegistryAdapter('https://registry.example', transport(url => {
      expect(url).toContain('/team%2Fx/writer%3F/resolve?version=1.0.0%2Bbuild');
      return Response.json({ data: { ...resolved, namespace: 'team/x', slug: 'writer?', version: '1.0.0+build' } });
    }));
    expect((await client.resolve('team/x', 'writer?', '1.0.0+build')).version).toBe('1.0.0+build');
  });

  test('rejects version drift and malformed success responses', async () => {
    for (const data of [resolved, { items: [], total: '0', limit: 20 }, null]) {
      const client = new RegistryAdapter('https://registry.example', transport(() => Response.json({ data })));
      await expect(client.resolve('team', 'writer', '2.0.0')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' });
    }
  });

  test('rejects dot-segment coordinates before any network request', async () => {
    let calls = 0;
    const client = new RegistryAdapter('https://registry.example', transport(() => { calls++; return new Response(); }));
    await expect(client.resolve('..', 'writer')).rejects.toMatchObject({ code: 'INVALID_INPUT' });
    expect(calls).toBe(0);
  });

  test.each([
    [401, 'ANONYMOUS_DENIED', false], [403, 'ANONYMOUS_DENIED', false],
    [404, 'NOT_FOUND', false], [429, 'RATE_LIMITED', true], [503, 'REGISTRY_UNAVAILABLE', true],
  ] as const)('preserves HTTP %d semantics', async (status, code, retryable) => {
    const client = new RegistryAdapter('https://registry.example', transport(() => new Response('private error details', { status })));
    await expect(client.search()).rejects.toMatchObject({ code, retryable, status, message: code });
  });

  test('reads raw UTF-8 documentation rather than a JSON envelope', async () => {
    const client = new RegistryAdapter('https://registry.example', transport(url => {
      expect(url).toEndWith('/versions/1.0.0/file?path=SKILL.md');
      return new Response('# 中文技能');
    }));
    expect(await client.documentation('team', 'writer', '1.0.0')).toBe('# 中文技能');
  });

  test('bounds response bytes even without Content-Length', async () => {
    const client = new RegistryAdapter('https://registry.example', transport(() => new Response('x'.repeat(1024 * 1024 + 1))));
    await expect(client.documentation('team', 'writer', '1')).rejects.toMatchObject({ code: 'RESPONSE_TOO_LARGE' });
  });

  test('cancellation is distinct from a retryable network failure', async () => {
    const client = new RegistryAdapter('https://registry.example', transport(() => { throw new Error('sensitive transport details'); }));
    await expect(client.search()).rejects.toMatchObject({ code: 'NETWORK_ERROR', retryable: true });
    await expect(client.search('', AbortSignal.abort())).rejects.toMatchObject({ code: 'CANCELLED', retryable: false });
  });

  test('real loopback HTTP request remains anonymous and refuses redirects', async () => {
    const server = Bun.serve({
      hostname: '127.0.0.1', port: 0,
      fetch(request) {
        if (request.headers.has('authorization') || request.headers.has('cookie')) {
          return new Response(null, { status: 403 });
        }
        if (new URL(request.url).searchParams.get('q') === 'redirect') {
          return Response.redirect(new URL('/login', request.url));
        }
        return Response.json({ data: {
          items: [{ namespace: 'team', slug: 'writer', latestVersion: '1.0.0', summary: null }],
          total: 1, limit: 20,
        } });
      },
    });
    try {
      const client = new RegistryAdapter(server.url.href);
      expect((await client.search()).items[0]?.summary).toBeNull();
      await expect(client.search('redirect')).rejects.toMatchObject({ code: 'NETWORK_ERROR' });
    } finally {
      await server.stop(true);
    }
  });
});
