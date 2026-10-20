import { describe, expect, test } from 'bun:test';
import { stripMarkdownFrontmatter } from '../src/client/markdown-content.js';
import { RegistryAdapter } from '../src/host/registry-adapter.js';

function transport(handler: (url: URL) => Response | Promise<Response>): typeof fetch {
  return Object.assign(async (input: string | URL | Request) => handler(new URL(String(input))), { preconnect: fetch.preconnect });
}
const version = (value: string, date: string | null, downloadAvailable = true) => ({ version: value, publishedAt: date, changelog: null, downloadAvailable });

describe('skill Markdown reader', () => {
  test.each([
    ['---\nname: sample\ndescription: |\n  long text\n---\n# Body', '# Body'],
    ['\uFEFF---\r\nname: sample\r\n---\r\n# Body', '# Body'],
    ['+++\nname = "sample"\n+++\nBody', 'Body'],
    ['---\nname: sample\n---', ''],
    ['---\nparagraph\n---\nBody', '---\nparagraph\n---\nBody'],
    ['---\nname: sample', '---\nname: sample'],
    ['Body\n---\nname: sample\n---', 'Body\n---\nname: sample\n---'],
    ['```yaml\n---\nname: sample\n---\n```', '```yaml\n---\nname: sample\n---\n```'],
  ])('strips only complete leading metadata fences', (input, expected) => {
    expect(stripMarkdownFrontmatter(input)).toBe(expected);
  });
});

describe('browsable version catalog', () => {
  test('requests filtered server pagination once and preserves its exact global total/order', async () => {
    let requests = 0;
    const raw = [version('20261017.164831', '2026-10-17'), version('1.0.2', '2026-10-12')];
    const client = new RegistryAdapter('https://registry.example', transport(url => {
      requests++;
      expect(url.pathname).toBe('/api/v1/skills/team/writer/versions');
      expect(url.searchParams.get('downloadableOnly')).toBe('true');
      expect(url.searchParams.get('page')).toBe('1');
      expect(url.searchParams.get('size')).toBe('2');
      return Response.json({ data: { items: raw, total: 25, page: 1, size: 2 } });
    }));
    expect(await client.browseVersions('team', 'writer', 1, 2)).toEqual({ items: raw, total: 25, page: 1, size: 2 });
    expect(requests).toBe(1);
  });
  test('keeps existing capability query unchanged', async () => {
    const client = new RegistryAdapter('https://registry.example', transport(url => {
      expect(url.searchParams.has('downloadableOnly')).toBe(false);
      return Response.json({ data: { items: [], total: 0, page: 0, size: 1 } });
    }));
    expect((await client.versions('team', 'writer', 0, 1)).total).toBe(0);
  });
  test.each([429, 503])('never converts transient %s to an empty version list', async status => {
    const client = new RegistryAdapter('https://registry.example', transport(() => new Response(null, { status })));
    await expect(client.browseVersions('team', 'writer', 0, 20)).rejects.toMatchObject({ status });
  });
  test('rejects mismatched pagination rather than inventing a filtered total', async () => {
    const client = new RegistryAdapter('https://registry.example', transport(() => Response.json({ data: { items: [], total: 0, page: 2, size: 20 } })));
    await expect(client.browseVersions('team', 'writer', 0, 20)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' });
  });
});
