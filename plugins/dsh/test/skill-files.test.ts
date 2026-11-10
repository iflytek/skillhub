import { describe, expect, test } from 'bun:test';
import { Context } from '@deepseek-ai/cordis';
import SkillHubService from '@astron-team/skillhub-dsh';
import { RegistryAdapter } from '../src/host/registry-adapter.ts';
import { MAX_SKILL_PREVIEW_BYTES } from '../src/host/skill-files.ts';
import type { SkillHubConfig } from '../src/host/types.ts';

function transport(handler: (url: URL, init?: RequestInit) => Response | Promise<Response>): typeof fetch {
  return Object.assign(async (input: string | URL | Request, init?: RequestInit) => handler(new URL(String(input)), init),
    { preconnect: fetch.preconnect });
}
const entry = (filePath = 'SKILL.md', fileSize = 24, contentType: string | null = 'text/plain') => ({ filePath, fileSize, contentType });
const signal = () => new AbortController().signal;

describe('fixed-version file transport', () => {
  test('lists a bounded directory and requests only selected encoded text without credentials', async () => {
    const path = 'notes/会议 & results.md';
    const requests: string[] = [];
    const adapter = new RegistryAdapter('https://registry.example/sub', transport((url, init) => {
      requests.push(url.pathname + url.search);
      expect(init?.credentials).toBe('omit');
      expect(init?.redirect).toBe('error');
      return url.pathname.endsWith('/files') ? Response.json({ data: [entry(path)] }) : new Response('# Exact version');
    }));
    const list = await adapter.files('team', 'writer', '1.3.0', signal());
    expect(list.state).toBe('ready');
    expect(requests).toHaveLength(1);
    if (list.state !== 'ready') throw new Error('Expected directory');
    expect(await adapter.file('team', 'writer', '1.3.0', list.items[0]!, signal())).toEqual({
      state: 'text', path, content: '# Exact version',
    });
    const selected = new URL('https://registry.example' + requests[1]);
    expect(selected.pathname).toBe('/sub/api/v1/skills/team/writer/versions/1.3.0/file');
    expect(selected.searchParams.get('path')).toBe(path);
  });

  test.each([404, 405])('unsupported HTTP %s differs from an empty directory', async status => {
    const adapter = new RegistryAdapter('https://registry.example', transport(() => new Response('', { status })));
    expect(await adapter.files('team', 'writer', '1.3.0')).toEqual({ state: 'unsupported', items: [] });
    const empty = new RegistryAdapter('https://registry.example', transport(() => Response.json({ data: [] })));
    expect(await empty.files('team', 'writer', '1.3.0')).toEqual({ state: 'ready', items: [] });
  });

  test.each([401, 403])('denied HTTP %s does not become unsupported or empty', async status => {
    const adapter = new RegistryAdapter('https://registry.example', transport(() => new Response('', { status })));
    await expect(adapter.files('team', 'writer', '1.3.0')).rejects.toMatchObject({ code: 'ANONYMOUS_DENIED', status });
  });

  test.each(['../secret', '/etc/passwd', 'C:\\secret', 'folder//file', 'folder/./file', 'x\0y'])('rejects unsafe listed name %s', async path => {
    const adapter = new RegistryAdapter('https://registry.example', transport(() => Response.json({ data: [entry(path)] })));
    await expect(adapter.files('team', 'writer', '1.3.0')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' });
  });

  test('rejects duplicate or excessive directory entries', async () => {
    for (const files of [[entry(), entry()], Array.from({ length: 501 }, (_, i) => entry(`${i}.md`))]) {
      const adapter = new RegistryAdapter('https://registry.example', transport(() => Response.json({ data: files })));
      await expect(adapter.files('team', 'writer', '1.3.0')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' });
    }
  });

  test('binary and advertised oversized files do not fetch bodies', async () => {
    let requests = 0;
    const adapter = new RegistryAdapter('https://registry.example', transport(() => { requests++; throw new Error('Unexpected read'); }));
    expect(await adapter.file('team', 'writer', '1.3.0', { path: 'image.png', size: 64, contentType: 'image/png' })).toMatchObject({ state: 'binary' });
    expect(await adapter.file('team', 'writer', '1.3.0', { path: 'big.md', size: MAX_SKILL_PREVIEW_BYTES + 1, contentType: 'text/plain' })).toMatchObject({ state: 'too-large' });
    expect(requests).toBe(0);
  });

  test('streaming byte limit wins over a false small size and cancels the reader', async () => {
    let cancelled = false;
    const adapter = new RegistryAdapter('https://registry.example', transport(() => new Response(new ReadableStream({
      start(controller) { controller.enqueue(new Uint8Array(MAX_SKILL_PREVIEW_BYTES + 1).fill(65)); },
      cancel() { cancelled = true; },
    }))));
    expect(await adapter.file('team', 'writer', '1.3.0', { path: 'text.md', size: 1, contentType: null })).toMatchObject({ state: 'too-large' });
    expect(cancelled).toBe(true);
  });

  test('distinguishes invalid UTF-8, NUL binary and a genuinely empty text file', async () => {
    for (const [body, state] of [[new Uint8Array([255]), 'unreadable'], [new Uint8Array([0]), 'binary'], [new Uint8Array(), 'text']] as const) {
      const adapter = new RegistryAdapter('https://registry.example', transport(() => new Response(body)));
      const result = await adapter.file('team', 'writer', '1.3.0', { path: 'text.md', size: body.length, contentType: 'text/plain' });
      expect(result.state).toBe(state);
      if (result.state === 'text') expect(result.content).toBe('');
    }
  });
});

describe('Host file references', () => {
  test('detail loading is lazy, unlisted names fail before I/O, and source changes invalidate access', async () => {
    const paths: string[] = [];
    const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch(request) {
      const url = new URL(request.url); paths.push(url.pathname + url.search);
      if (url.pathname.endsWith('/resolve')) return Response.json({ data: { namespace: 'team', slug: 'writer', version: '1.3.0', versionId: 7, fingerprint: 'f', downloadUrl: '/archive' } });
      if (url.pathname.endsWith('/files')) return Response.json({ data: [entry('notes/会议.md')] });
      if (url.pathname.endsWith('/file')) return new Response('# Preview');
      return Response.json({ data: { id: 42, namespace: 'team', slug: 'writer', displayName: 'Writer', summary: '' } });
    } });
    const ctx = new Context();
    let config: SkillHubConfig = { registryUrl: server.url.href, detailTabs: { versions: 'hide', reviews: 'hide', securityAudit: 'hide' } };
    const fiber = ctx.plugin(ctx => { new SkillHubService(ctx, { get: () => config }); }); await fiber;
    try {
      const detail = await ctx.skillhub.getSkillDetail(ctx.skillhub.getContext().sourceRevision, 'team', 'writer', '1.3.0', signal());
      expect(paths.some(path => path.endsWith('/files'))).toBe(false);
      const before = paths.length;
      await expect(ctx.skillhub.readSkillFile(detail.detailToken, 'notes/会议.md', signal())).rejects.toMatchObject({ code: 'skillhub/registry-error' });
      expect(paths).toHaveLength(before);
      await ctx.skillhub.listSkillFiles(detail.detailToken, signal());
      const afterList = paths.length;
      await expect(ctx.skillhub.readSkillFile(detail.detailToken, '../secret', signal())).rejects.toMatchObject({ code: 'skillhub/registry-error' });
      expect(paths).toHaveLength(afterList);
      expect(await ctx.skillhub.readSkillFile(detail.detailToken, 'notes/会议.md', signal())).toEqual({ state: 'text', path: 'notes/会议.md', content: '# Preview' });
      config = { ...config, registryUrl: '' };
      const beforeStale = paths.length;
      await expect(ctx.skillhub.readSkillFile(detail.detailToken, 'notes/会议.md', signal())).rejects.toMatchObject({ code: 'skillhub/context-stale' });
      expect(paths).toHaveLength(beforeStale);
    } finally { await fiber.dispose(); await server.stop(true); }
  });
});


test.each(['list', 'body'])('source changes during an in-flight %s read reject the old result', async phase => {
  let armed = false; let started!: () => void; let release!: () => void;
  const reading = new Promise<void>(resolve => { started = resolve; });
  const resumed = new Promise<void>(resolve => { release = resolve; });
  const server = Bun.serve({ hostname: '127.0.0.1', port: 0, async fetch(request) {
    const path = new URL(request.url).pathname;
    if (path.endsWith('/resolve')) return Response.json({ data: { namespace: 'team', slug: 'writer', version: '1.3.0', versionId: 7, fingerprint: 'f', downloadUrl: '/archive' } });
    if (armed && path.endsWith(phase === 'list' ? '/files' : '/file')) { started(); await resumed; }
    if (path.endsWith('/files')) return Response.json({ data: [entry()] });
    if (path.endsWith('/file')) return new Response('# Preview');
    return Response.json({ data: { id: 42, namespace: 'team', slug: 'writer', displayName: 'Writer', summary: '' } });
  } });
  let config: SkillHubConfig = { registryUrl: server.url.href, detailTabs: { versions: 'hide', reviews: 'hide', securityAudit: 'hide' } };
  const ctx = new Context(); const fiber = ctx.plugin(ctx => { new SkillHubService(ctx, { get: () => config }); }); await fiber;
  try {
    const detail = await ctx.skillhub.getSkillDetail(ctx.skillhub.getContext().sourceRevision, 'team', 'writer', '1.3.0', signal());
    if (phase === 'body') await ctx.skillhub.listSkillFiles(detail.detailToken, signal());
    armed = true;
    const pending = phase === 'list' ? ctx.skillhub.listSkillFiles(detail.detailToken, signal()) : ctx.skillhub.readSkillFile(detail.detailToken, 'SKILL.md', signal());
    const outcome = pending.then(() => undefined, error => error);
    await reading; config = { ...config, registryUrl: '' }; release();
    expect(await outcome).toMatchObject({ code: 'skillhub/context-stale' });
  } finally { release(); await fiber.dispose(); await server.stop(true); }
});

test('mutating a returned file list cannot authorize an unlisted path', async () => {
  let bodies = 0;
  const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch(request) {
    const path = new URL(request.url).pathname;
    if (path.endsWith('/resolve')) return Response.json({ data: { namespace: 'team', slug: 'writer', version: '1.3.0', versionId: 7, fingerprint: 'f', downloadUrl: '/archive' } });
    if (path.endsWith('/files')) return Response.json({ data: [entry()] });
    if (path.endsWith('/file')) { bodies++; return new Response('# Preview'); }
    return Response.json({ data: { id: 42, namespace: 'team', slug: 'writer', displayName: 'Writer', summary: '' } });
  } });
  const ctx = new Context(); const fiber = ctx.plugin(ctx => { new SkillHubService(ctx, { get: () => ({ registryUrl: server.url.href, detailTabs: { versions: 'hide', reviews: 'hide', securityAudit: 'hide' } }) }); }); await fiber;
  try {
    const detail = await ctx.skillhub.getSkillDetail(ctx.skillhub.getContext().sourceRevision, 'team', 'writer', '1.3.0', signal());
    const list = await ctx.skillhub.listSkillFiles(detail.detailToken, signal());
    bodies = 0;
    if (list.state !== 'ready') throw new Error('Expected directory');
    list.items[0]!.path = 'secret.md'; list.items.push({ path: 'injected.md', size: 1, contentType: 'text/plain' });
    for (const path of ['secret.md', 'injected.md']) await expect(ctx.skillhub.readSkillFile(detail.detailToken, path, signal())).rejects.toMatchObject({ code: 'skillhub/registry-error' });
    expect(bodies).toBe(0);
    expect(await ctx.skillhub.readSkillFile(detail.detailToken, 'SKILL.md', signal())).toMatchObject({ state: 'text' });
    expect(bodies).toBe(1);
  } finally { await fiber.dispose(); await server.stop(true); }
});
