import { describe, expect, test } from 'bun:test';
import { RegistryAdapter } from '../src/host/registry-adapter.ts';

function transport(handler: (url: URL, init?: RequestInit) => Response): typeof fetch {
  return Object.assign(async (input: string | URL | Request, init?: RequestInit) => handler(new URL(String(input)), init),
    { preconnect: fetch.preconnect });
}
const skill = { namespace: 'team', slug: 'writer', publishedVersion: { version: '1.2.0' },
  displayName: 'Writer', summary: 'Write documents', downloadCount: 0, starCount: 42, ratingAvg: 4.6, ratingCount: 12 };

describe('paged anonymous catalog', () => {
  test('maps published version and metrics, preserving base path without credentials', async () => {
    const registry = new RegistryAdapter('https://registry.example/base', transport((url, init) => {
      expect(url.pathname).toBe('/base/api/web/skills');
      expect(Object.fromEntries(url.searchParams)).toEqual({ q: 'a&b', page: '1', size: '12', sort: 'downloads' });
      expect(init?.credentials).toBe('omit');
      expect(init?.headers).toEqual({ Accept: 'application/json' });
      return Response.json({ data: { items: [{ ...skill, headlineVersion: { version: 'draft' }, ownerId: 'private' }],
        total: 25, page: 1, size: 12 } });
    }));
    const result = await registry.catalog('a&b', 1, 'downloads');
    expect(result).toMatchObject({ total: 25, page: 1, size: 12, hasMore: true, mode: 'paged',
      items: [{ namespace: 'team', slug: 'writer', latestVersion: '1.2.0', displayName: 'Writer', downloadCount: 0, starCount: 42, ratingAvg: 4.6 }] });
    expect(result.items[0]).not.toHaveProperty('ownerId');
    expect(result.items[0]).not.toHaveProperty('headlineVersion');
  });

  test('missing or invalid presentation metrics do not block old records or invent a zero rating', async () => {
    const registry = new RegistryAdapter('https://registry.example', transport(() => Response.json({ data: {
      items: [{ ...skill, downloadCount: -1, starCount: null, ratingAvg: 0, ratingCount: 0 },
        { namespace: 'team', slug: 'old', displayName: '  ', publishedVersion: { version: '1' } },
        { ...skill, slug: 'unpublished', publishedVersion: null }], total: 3, page: 0, size: 12,
    } })));
    const result = await registry.catalog('', 0, 'newest');
    expect(result.items).toHaveLength(2);
    expect(result.items[0]?.downloadCount).toBeUndefined();
    expect(result.items[0]?.starCount).toBeUndefined();
    expect(result.items[0]?.ratingAvg).toBeUndefined();
    expect(result.items[1]).toMatchObject({ slug: 'old', summary: '' });
    expect(result.items[1]?.displayName).toBeUndefined();
    expect(result.hasMore).toBe(false);
  });

  test.each([404, 405])('only absent first-page route (%s) falls back to bounded CLI search', async status => {
    const paths: string[] = [];
    const registry = new RegistryAdapter('https://registry.example', transport(url => {
      paths.push(url.pathname);
      return url.pathname === '/api/web/skills' ? new Response('', { status })
        : Response.json({ data: { items: [{ namespace: 'team', slug: 'old', latestVersion: '1', summary: null }], total: 30, limit: 20 } });
    }));
    expect(await registry.catalog('', 0, 'newest')).toEqual({ items: [{ namespace: 'team', slug: 'old', latestVersion: '1', summary: '' }],
      total: 30, page: 0, size: 20, mode: 'limited', hasMore: false });
    expect(paths).toEqual(['/api/web/skills', '/api/cli/v1/skills/search']);
    await expect(registry.catalog('', 1, 'newest')).rejects.toMatchObject({ status });
    expect(paths).toHaveLength(3);
  });

  test.each([401, 403, 429, 500])('does not hide HTTP %s with a legacy fallback', async status => {
    let calls = 0;
    const registry = new RegistryAdapter('https://registry.example', transport(() => {
      calls++; return new Response('', { status });
    }));
    await expect(registry.catalog('', 0, 'newest')).rejects.toMatchObject({ status });
    expect(calls).toBe(1);
  });

  test('rejects invalid page before transport and mismatched responses without downgrade', async () => {
    let calls = 0;
    const registry = new RegistryAdapter('https://registry.example', transport(() => {
      calls++; return Response.json({ data: { items: [], total: 0, page: 0, size: 12 } });
    }));
    for (const page of [-1, .5, Number.NaN]) await expect(registry.catalog('', page, 'newest')).rejects.toMatchObject({ code: 'INVALID_INPUT' });
    expect(calls).toBe(0);
    await expect(registry.catalog('', 1, 'newest')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' });
    expect(calls).toBe(1);
  });
});
