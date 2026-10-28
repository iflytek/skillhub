import { describe, expect, spyOn, test } from 'bun:test';
import { Context } from '@deepseek-ai/cordis';
import SkillHubService from '@astron-team/skillhub-dsh';
import { Config } from '../src/host/config.ts';
import type { TabMode } from '../src/host/types.ts';

describe('native catalog service', () => {
  test.each([undefined, {}])('missing Registry uses the official default without fetching (%j)', async configuration => {
    const fetchSpy = spyOn(globalThis, 'fetch').mockImplementation(Object.assign(
      () => { throw new Error('Unexpected network request.'); }, { preconnect: fetch.preconnect }));
    const ctx = new Context();
    const fiber = ctx.plugin(SkillHubService, configuration);
    try {
      await fiber;
      expect(Config(configuration).get()).toMatchObject({ registryUrl: 'https://skill.xfyun.cn' });
      expect(ctx.skillhub.getContext()).toMatchObject({
        configured: true, registryUrl: 'https://skill.xfyun.cn',
        detailTabs: { versions: 'auto', securityAudit: 'auto', reviews: 'auto' },
      });
      expect(fetchSpy).not.toHaveBeenCalled();
    } finally { await fiber.dispose(); fetchSpy.mockRestore(); }
  });

  test('explicit custom Registry survives schema resolution and Host initialization without fetching', async () => {
    const fetchSpy = spyOn(globalThis, 'fetch').mockImplementation(Object.assign(
      () => { throw new Error('Unexpected network request.'); }, { preconnect: fetch.preconnect }));
    const registryUrl = 'https://registry.example/team';
    const ctx = new Context();
    const fiber = ctx.plugin(SkillHubService, { registryUrl });
    try {
      await fiber;
      expect(Config({ registryUrl }).get().registryUrl).toBe(registryUrl);
      expect(ctx.skillhub.getContext()).toMatchObject({ configured: true, registryUrl });
      expect(fetchSpy).not.toHaveBeenCalled();
    } finally { await fiber.dispose(); fetchSpy.mockRestore(); }
  });

  test.each([undefined, null, 'https://web.example/read/writer?version=1.2.0#examples'])('detail pins requests and preserves optional API page URL: %s', async webUrl => {
    const paths: string[] = [];
    const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch(request) {
      const url = new URL(request.url);
      if (url.pathname === '/api/v1/skills/team/writer') return Response.json({ data: { id: 42, namespace: 'team', slug: 'writer', displayName: 'Writer', summary: '', webUrl } });
      if (url.pathname.endsWith('/versions') || url.pathname.endsWith('/reviews')) return Response.json({ data: { items: [], total: 0, page: 0, size: 1 } });
      paths.push(url.pathname + url.search);
      expect(request.headers.get('authorization')).toBeNull();
      return url.pathname.endsWith('/resolve') ? Response.json({ data: {
        namespace: 'team', slug: 'writer', version: '1.2.0', versionId: 12,
        fingerprint: 'abc', downloadUrl: 'https://storage.example/private-download',
      } }) : new Response('# Writer\n\nUse resources/example.txt.');
    } });
    const ctx = new Context();
    const fiber = ctx.plugin(SkillHubService, { registryUrl: server.url.href });
    await fiber;
    try {
      const detail = await ctx.skillhub.getSkillDetail(ctx.skillhub.getContext().sourceRevision,
        'team', 'writer', '1.2.0', new AbortController().signal);
      expect(detail).toMatchObject({ namespace: 'team', slug: 'writer', version: '1.2.0', versionId: 12,
        documentation: { state: 'ready', content: '# Writer\n\nUse resources/example.txt.' } });
      expect(detail.webUrl).toBe(webUrl);
      expect(paths).toEqual(['/api/cli/v1/skills/team/writer/resolve?version=1.2.0',
        '/api/v1/skills/team/writer/versions/1.2.0/file?path=SKILL.md']);
    } finally { await fiber.dispose(); await server.stop(true); }
  });

  test('missing documentation preserves the resolved identity with an explicit module error', async () => {
    const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch(request) {
      if (new URL(request.url).pathname === '/api/v1/skills/team/writer') return Response.json({ data: { id: 42, namespace: 'team', slug: 'writer', displayName: 'Writer', summary: '' } });
      return new URL(request.url).pathname.endsWith('/resolve') ? Response.json({ data: {
        namespace: 'team', slug: 'writer', version: '1.0.0', versionId: 1,
        fingerprint: 'abc', downloadUrl: '/download',
      } }) : new Response('upstream diagnostic', { status: 404 });
    } });
    const ctx = new Context();
    const fiber = ctx.plugin(SkillHubService, { registryUrl: server.url.href });
    await fiber;
    try {
      expect(await ctx.skillhub.getSkillDetail(ctx.skillhub.getContext().sourceRevision,
        'team', 'writer', '1.0.0', new AbortController().signal)).toMatchObject({
        version: '1.0.0', documentation: { state: 'error', reason: 'NOT_FOUND', retryable: false },
      });
    } finally { await fiber.dispose(); await server.stop(true); }
  });

  test.each([200, 503])('source changes discard in-flight search responses with status %s', async status => {
    const reached = Promise.withResolvers<void>();
    const release = Promise.withResolvers<void>();
    const server = Bun.serve({ hostname: '127.0.0.1', port: 0, async fetch() {
      reached.resolve();
      await release.promise;
      return Response.json({ data: { items: [], total: 0, limit: 20 } }, { status });
    } });
    const ctx = new Context();
    let current = { registryUrl: server.url.href, detailTabs: {
      versions: 'auto' as TabMode, securityAudit: 'auto' as TabMode, reviews: 'auto' as TabMode,
    } };
    const fiber = ctx.plugin(ctx => { new SkillHubService(ctx, { get: () => current }); });
    await fiber;
    try {
      const outcome = ctx.skillhub.listSkills(ctx.skillhub.getContext().sourceRevision, '', new AbortController().signal)
        .then(() => null, (error: unknown) => error);
      await reached.promise;
      current = { ...current, registryUrl: '' };
      release.resolve();
      expect(await outcome).toMatchObject({ code: 'skillhub/context-stale' });
    } finally { release.resolve(); await fiber.dispose(); await server.stop(true); }
  });

  test.each(['catalog', 'labels', 'filtered'] as const)('%s preserves source revision protection for late responses', async method => {
    const reached = Promise.withResolvers<void>();
    const release = Promise.withResolvers<void>();
    const server = Bun.serve({ hostname: '127.0.0.1', port: 0, async fetch(request) {
      const url = new URL(request.url);
      expect(url.pathname).toBe(method === 'labels' ? '/api/web/labels' : '/api/web/skills');
      if (method === 'filtered') expect(url.searchParams.get('label')).toBe('writing');
      reached.resolve(); await release.promise;
      return Response.json({ data: method === 'labels' ? [] : { items: [], total: 0, page: 0, size: 12 } });
    } });
    const ctx = new Context();
    let source = server.url.href;
    const fiber = ctx.plugin(ctx => { new SkillHubService(ctx, { get: () => ({ registryUrl: source,
      detailTabs: { versions: 'auto', securityAudit: 'auto', reviews: 'auto' } }) }); });
    await fiber;
    try {
      const revision = ctx.skillhub.getContext().sourceRevision;
      const signal = new AbortController().signal;
      const pending = method === 'labels' ? ctx.skillhub.listLabels(revision, signal)
        : method === 'filtered' ? ctx.skillhub.listCatalogByLabel(revision, '', 0, 'newest', 'writing', signal)
        : ctx.skillhub.listCatalog(revision, '', 0, 'newest', signal);
      const result = pending.then(() => null, (error: unknown) => error);
      await reached.promise; source = ''; release.resolve();
      expect(await result).toMatchObject({ code: 'skillhub/context-stale' });
    } finally { release.resolve(); await fiber.dispose(); await server.stop(true); }
  });

  test('live configuration changes refresh the source but tab changes preserve its revision', async () => {
    let current = { registryUrl: '', detailTabs: {
      versions: 'auto' as TabMode, securityAudit: 'auto' as TabMode, reviews: 'auto' as TabMode,
    } };
    const ctx = new Context();
    const fiber = ctx.plugin(ctx => { new SkillHubService(ctx, { get: () => current }); });
    await fiber;
    try {
      const initial = ctx.skillhub.getContext();
      current = { ...current, registryUrl: 'https://registry.example/base/' };
      const connected = ctx.skillhub.getContext();
      expect(connected.registryUrl).toBe('https://registry.example/base');
      expect(connected.sourceRevision).not.toBe(initial.sourceRevision);
      current = { ...current, detailTabs: { ...current.detailTabs, reviews: 'hide' } };
      expect(ctx.skillhub.getContext()).toMatchObject({ sourceRevision: connected.sourceRevision, detailTabs: { reviews: 'hide' } });
      current = { ...current, registryUrl: 'file:///tmp/private' };
      expect(() => ctx.skillhub.getContext()).toThrow('Invalid SkillHub source address.');
      current = { ...current, registryUrl: '' };
      expect(ctx.skillhub.getContext().configured).toBe(false);
    } finally { await fiber.dispose(); }
  });

  test.each(['', '   '])('explicit blank source %j disconnects and refuses search', async registryUrl => {
    const ctx = new Context();
    const fiber = ctx.plugin(SkillHubService, { registryUrl });
    await fiber;
    try {
      const context = ctx.skillhub.getContext();
      expect(context.configured).toBe(false);
      expect(context.detailTabs).toEqual({ versions: 'auto', securityAudit: 'auto', reviews: 'auto' });
      await expect(ctx.skillhub.listSkills(context.sourceRevision, '', new AbortController().signal))
        .rejects.toMatchObject({ code: 'skillhub/source-not-configured' });
    } finally { await fiber.dispose(); }
  });

  test('uses configured anonymous source and invalidates context across reloads', async () => {
    let requests = 0;
    const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch(request) {
      requests++;
      expect(request.headers.get('authorization')).toBeNull();
      return Response.json({ data: {
        items: [{ namespace: 'team', slug: 'writer', latestVersion: '1.0.0', summary: null }],
        total: 30, limit: 20,
      } });
    } });
    const ctx = new Context();
    let fiber = ctx.plugin(SkillHubService, { registryUrl: server.url.href });
    await fiber;
    try {
      const before = ctx.skillhub.getContext();
      expect(before.registryUrl).toBe(server.url.href.replace(/\/$/, ''));
      const result = await ctx.skillhub.listSkills(before.sourceRevision, 'writer', new AbortController().signal);
      expect(result).toMatchObject({ returned: 1, total: 30, limit: 20 });
      expect(result.items[0]?.summary).toBe('');
      await fiber.dispose();
      fiber = ctx.plugin(SkillHubService, { registryUrl: server.url.href });
      await fiber;
      expect(ctx.skillhub.getContext().sourceRevision).not.toBe(before.sourceRevision);
      await expect(ctx.skillhub.listSkills(before.sourceRevision, '', new AbortController().signal))
        .rejects.toMatchObject({ code: 'skillhub/context-stale' });
      expect(requests).toBe(1);
    } finally {
      await fiber.dispose();
      await server.stop(true);
    }
  });

  test('preserves anonymous denial as a structured Remote error without response contents', async () => {
    const server = Bun.serve({ hostname: '127.0.0.1', port: 0,
      fetch: () => new Response('private upstream details', { status: 403 }),
    });
    const ctx = new Context();
    const fiber = ctx.plugin(SkillHubService, { registryUrl: server.url.href });
    await fiber;
    try {
      await expect(ctx.skillhub.listSkills(ctx.skillhub.getContext().sourceRevision, '', new AbortController().signal))
        .rejects.toMatchObject({
          code: 'skillhub/registry-error', message: 'SkillHub request failed.',
          details: { reason: 'ANONYMOUS_DENIED', retryable: false, status: 403 },
        });
    } finally {
      await fiber.dispose();
      await server.stop(true);
    }
  });

  test('disposing the Host cancels in-flight requests and marks their context stale', async () => {
    const reached = Promise.withResolvers<void>();
    const release = Promise.withResolvers<void>();
    const server = Bun.serve({ hostname: '127.0.0.1', port: 0, async fetch() {
      reached.resolve();
      await release.promise;
      return Response.json({ data: { items: [], total: 0, limit: 20 } });
    } });
    const ctx = new Context();
    const fiber = ctx.plugin(SkillHubService, { registryUrl: server.url.href });
    await fiber;
    try {
      const pending = ctx.skillhub.listSkills(ctx.skillhub.getContext().sourceRevision, '', new AbortController().signal);
      const outcome = pending.then(() => null, (error: unknown) => error);
      await reached.promise;
      await fiber.dispose();
      expect(await outcome).toMatchObject({ code: 'skillhub/context-stale' });
    } finally {
      release.resolve();
      await fiber.dispose();
      await server.stop(true);
    }
  });
});

test('Host preserves Retry-After metadata and prevents different searches from bypassing the same source wait', async () => {
  let calls = 0;
  const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch() {
    calls++;
    return new Response('private diagnostics', { status: 429, headers: { 'Retry-After': '60' } });
  } });
  const ctx = new Context();
  const fiber = ctx.plugin(SkillHubService, { registryUrl: server.url.href });
  await fiber;
  try {
    const revision = ctx.skillhub.getContext().sourceRevision;
    const expected = { code: 'skillhub/registry-error', details: { reason: 'RATE_LIMITED', status: 429, retryable: true } };
    for (const query of ['page', 'another-query']) {
      const error = await ctx.skillhub.listSkills(revision, query, new AbortController().signal).then(() => null, (error: unknown) => error);
      expect(error).toMatchObject(expected);
      expect(error).toHaveProperty('details.retryAfterSeconds');
    }
    expect(calls).toBe(1);
  } finally { await fiber.dispose(); await server.stop(true); }
});
