import { describe, expect, test } from 'bun:test';
import { RegistryAdapter } from '../src/host/registry-adapter.ts';

function transport(handler: (url: URL, init?: RequestInit) => Response): typeof fetch {
  return Object.assign(async (input: string | URL | Request, init?: RequestInit) => handler(new URL(String(input)), init),
    { preconnect: fetch.preconnect });
}

describe('anonymous catalog labels', () => {
  test('reads public labels without credentials and excludes unrelated fields', async () => {
    const registry = new RegistryAdapter('https://registry.example/base', transport((url, init) => {
      expect(url.pathname).toBe('/base/api/web/labels');
      expect(init?.credentials).toBe('omit');
      return Response.json({ data: [{ slug: 'writing', type: 'RECOMMENDED', displayName: ' 写作 ', internalId: 42 },
        { slug: 'verified', type: 'PRIVILEGED', displayName: '已验证' }] });
    }));
    expect(await registry.labels()).toEqual({ available: true, items: [
      { slug: 'writing', type: 'RECOMMENDED', displayName: '写作' },
      { slug: 'verified', type: 'PRIVILEGED', displayName: '已验证' },
    ] });
  });

  test.each([404, 405])('absent label route %s disables labels only', async status => {
    const registry = new RegistryAdapter('https://registry.example', transport(url => url.pathname.endsWith('/labels')
      ? new Response('', { status }) : Response.json({ data: { items: [], total: 0, page: 0, size: 12 } })));
    expect(await registry.labels()).toEqual({ available: false, items: [] });
    expect(await registry.catalog('', 0, 'newest')).toMatchObject({ mode: 'paged', items: [] });
  });

  test.each([403, 429, 500])('does not misrepresent label failure %s as unsupported', async status => {
    const registry = new RegistryAdapter('https://registry.example', transport(() => new Response('', { status })));
    await expect(registry.labels()).rejects.toMatchObject({ status });
  });

  test('empty labels are supported; invalid slugs cannot enter the selector', async () => {
    for (const data of [[], [{ slug: 'bad--slug', type: 'RECOMMENDED', displayName: 'Bad' }]]) {
      const registry = new RegistryAdapter('https://registry.example', transport(() => Response.json({ data })));
      if (data.length) await expect(registry.labels()).rejects.toMatchObject({ code: 'INVALID_RESPONSE' });
      else expect(await registry.labels()).toEqual({ available: true, items: [] });
    }
  });

  test('passes single label with query, sort and pagination; empty label means all', async () => {
    const labels: (string | null)[] = [];
    const registry = new RegistryAdapter('https://registry.example', transport(url => {
      expect(url.searchParams.get('q')).toBe('a&label=other');
      expect(url.searchParams.get('sort')).toBe('rating');
      expect(url.searchParams.get('page')).toBe('1');
      expect(url.searchParams.get('size')).toBe('12');
      labels.push(url.searchParams.get('label'));
      return Response.json({ data: { items: [], total: 0, page: 1, size: 12 } });
    }));
    await registry.catalog('a&label=other', 1, 'rating', undefined, 'developer-tools');
    await registry.catalog('a&label=other', 1, 'rating', undefined, '');
    expect(labels).toEqual(['developer-tools', null]);
  });

  test('invalid labels fail before transport', async () => {
    let calls = 0;
    const registry = new RegistryAdapter('https://registry.example', transport(() => { calls++; return new Response(''); }));
    for (const label of ['UPPER', '-first', 'last-', 'two--hyphens', 'a&label=b', '../a', ' ', 'x'.repeat(65)]) {
      await expect(registry.catalog('', 0, 'newest', undefined, label)).rejects.toMatchObject({ code: 'INVALID_INPUT' });
    }
    expect(calls).toBe(0);
  });

  test.each([404, 405])('filtered catalog never silently falls back on %s', async status => {
    const paths: string[] = [];
    const registry = new RegistryAdapter('https://registry.example', transport(url => {
      paths.push(url.pathname); return new Response('', { status });
    }));
    await expect(registry.catalog('', 0, 'newest', undefined, 'writing')).rejects.toMatchObject({ status });
    expect(paths).toEqual(['/api/web/skills']);
  });
});
