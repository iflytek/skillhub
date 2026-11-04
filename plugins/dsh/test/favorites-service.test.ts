import { describe, expect, test } from 'bun:test';
import { Context } from '@deepseek-ai/cordis';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { withFileLock } from '@deepseek-ai/dsh-atomic-write';
import SkillHubService from '@astron-team/skillhub-dsh';

const item = { namespace: 'team', slug: 'writer', summary: 'Write documents.' };
async function privateHome(run: (home: string) => Promise<void>) {
  const before = process.env.DSH_HOME;
  const home = await mkdtemp(join(tmpdir(), 'dsh-favorites-service-'));
  process.env.DSH_HOME = home;
  try { await run(home); }
  finally { if (before === undefined) delete process.env.DSH_HOME; else process.env.DSH_HOME = before; await rm(home, { recursive: true, force: true }); }
}

describe('native local favorites service', () => {
  test('preference reads/writes issue no Registry HTTP, isolate sources and expose stable storage errors', () => privateHome(async home => {
    const requests: string[] = [];
    const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch(request) { requests.push(request.url); return new Response('Unexpected request', { status: 500 }); } });
    const ctx = new Context(); let source = server.url.href;
    const fiber = ctx.plugin(ctx => { new SkillHubService(ctx, { get: () => ({ registryUrl: source, detailTabs: { versions: 'auto', securityAudit: 'auto', reviews: 'auto' } }) }); });
    await fiber;
    try {
      const signal = new AbortController().signal;
      const revisionA = ctx.skillhub.getContext().sourceRevision;
      expect(await ctx.skillhub.listFavorites(revisionA, signal)).toEqual({ items: [] });
      expect(await ctx.skillhub.setFavorite(revisionA, item, true, signal)).toEqual({ items: [item] });
      source = server.url.href + 'other'; const revisionB = ctx.skillhub.getContext().sourceRevision;
      expect(await ctx.skillhub.listFavorites(revisionB, signal)).toEqual({ items: [] });
      await ctx.skillhub.setFavorite(revisionB, item, true, signal);
      await ctx.skillhub.setFavorite(revisionB, item, false, signal);
      source = server.url.href; const renewedA = ctx.skillhub.getContext().sourceRevision;
      expect(await ctx.skillhub.listFavorites(renewedA, signal)).toEqual({ items: [item] });
      expect(requests).toEqual([]);
      const file = join(home, 'skillhub/favorites.json');
      const disk = JSON.parse(await readFile(file, 'utf8'));
      expect(disk.items).toEqual([{ registry: server.url.href.replace(/\/$/u, ''), ...item }]);
      await writeFile(file, '{damaged-private-path');
      await expect(ctx.skillhub.listFavorites(renewedA, signal)).rejects.toMatchObject({ code: 'skillhub/favorites-error', details: { reason: 'INVALID_STORE', retryable: false } });
      await expect(ctx.skillhub.setFavorite(renewedA, { ...item, slug: '' }, true, signal)).rejects.toMatchObject({ code: 'skillhub/favorites-error', details: { reason: 'INVALID_INPUT' } });
      expect(await readFile(file, 'utf8')).toBe('{damaged-private-path');
    } finally { await fiber.dispose(); await server.stop(true); }
  }));

  test('source switch while waiting for the lock refuses the pending preference write', () => privateHome(async home => {
    const ctx = new Context(); let source = 'https://a.example';
    const fiber = ctx.plugin(ctx => { new SkillHubService(ctx, { get: () => ({ registryUrl: source, detailTabs: { versions: 'auto', securityAudit: 'auto', reviews: 'auto' } }) }); });
    await fiber;
    const held = Promise.withResolvers<void>(), release = Promise.withResolvers<void>();
    try {
      const revision = ctx.skillhub.getContext().sourceRevision, signal = new AbortController().signal;
      await ctx.skillhub.setFavorite(revision, item, true, signal);
      const file = join(home, 'skillhub/favorites.json'), before = await readFile(file);
      const lock = withFileLock(file, async () => { held.resolve(); await release.promise; }); await held.promise;
      const outcome = ctx.skillhub.setFavorite(revision, { ...item, slug: 'next' }, true, signal).then(() => null, (error: unknown) => error);
      source = 'https://b.example'; release.resolve(); await lock;
      expect(await outcome).toMatchObject({ code: 'skillhub/context-stale' });
      expect(await readFile(file)).toEqual(before);
      expect(await ctx.skillhub.listFavorites(ctx.skillhub.getContext().sourceRevision, signal)).toEqual({ items: [] });
    } finally { release.resolve(); await fiber.dispose(); }
  }));

  test('latest favorite selection resolves one identity without pinning snapshots or exposing download authority', async () => {
    const paths: string[] = [];
    const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch(request) {
      const url = new URL(request.url); paths.push(request.method + ' ' + url.pathname + url.search);
      return Response.json({ data: { namespace: 'team', slug: 'writer', version: '2.0.0', versionId: 22, fingerprint: 'new-content', downloadUrl: 'https://storage.example/private' } });
    } });
    const ctx = new Context(), fiber = ctx.plugin(SkillHubService, { registryUrl: server.url.href }); await fiber;
    try {
      const revision = ctx.skillhub.getContext().sourceRevision, signal = new AbortController().signal;
      const value = await ctx.skillhub.resolveFavoriteVersion(revision, 'team', 'writer', signal);
      expect(value).toEqual({ namespace: 'team', slug: 'writer', version: '2.0.0' });
      expect(paths).toEqual(['GET /api/cli/v1/skills/team/writer/resolve']);
      await expect(ctx.skillhub.resolveFavoriteVersion(revision, '..', 'writer', signal)).rejects.toMatchObject({ code: 'skillhub/favorites-error', details: { reason: 'INVALID_INPUT' } });
      expect(paths).toHaveLength(1);
    } finally { await fiber.dispose(); await server.stop(true); }
  });

  test('late latest-version responses cannot enter a changed source', async () => {
    const reached = Promise.withResolvers<void>(), release = Promise.withResolvers<void>();
    const server = Bun.serve({ hostname: '127.0.0.1', port: 0, async fetch() { reached.resolve(); await release.promise; return Response.json({ data: { namespace: 'team', slug: 'writer', version: '2.0.0', versionId: 22, fingerprint: 'abc', downloadUrl: '/download' } }); } });
    const ctx = new Context(); let source = server.url.href;
    const fiber = ctx.plugin(ctx => { new SkillHubService(ctx, { get: () => ({ registryUrl: source, detailTabs: { versions: 'auto', securityAudit: 'auto', reviews: 'auto' } }) }); }); await fiber;
    try {
      const outcome = ctx.skillhub.resolveFavoriteVersion(ctx.skillhub.getContext().sourceRevision, 'team', 'writer', new AbortController().signal).then(() => null, (error: unknown) => error);
      await reached.promise; source = 'https://b.example'; release.resolve();
      expect(await outcome).toMatchObject({ code: 'skillhub/context-stale' });
    } finally { release.resolve(); await fiber.dispose(); await server.stop(true); }
  });
});
