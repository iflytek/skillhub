import { describe, expect, test } from 'bun:test';
import { Context } from '@deepseek-ai/cordis';
import SkillHubService from '@astron-team/skillhub-dsh';
import type { SkillHubConfig } from '../src/host/types.ts';

describe('optional detail modules', () => {
  for (const mode of ['auto', 'show', 'hide'] as const) {
    test.each([200, 403, 404, 503])(`${mode} gates optional requests with HTTP %s`, async status => {
      const requests: string[] = [];
      const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch(request) {
        const url = new URL(request.url);
        requests.push(url.pathname + url.search);
        if (url.pathname.endsWith('/versions') || url.pathname.endsWith('/reviews')) {
          if (status !== 200) return new Response('private diagnostics', { status });
          return Response.json({ data: { items: [], total: 0,
            page: Number(url.searchParams.get('page')), size: Number(url.searchParams.get('size')) } });
        }
        if (url.pathname.endsWith('/resolve')) return Response.json({ data: {
          namespace: 'team', slug: 'writer', version: '1', versionId: 7, fingerprint: 'f', downloadUrl: '/archive',
        } });
        if (url.pathname.endsWith('/file')) return new Response('# Writer');
        return Response.json({ data: { id: 42, namespace: 'team', slug: 'writer', displayName: 'Writer', summary: '' } });
      } });
      const ctx = new Context();
      let config: SkillHubConfig = { registryUrl: server.url.href,
        detailTabs: { versions: mode, reviews: mode, securityAudit: mode } };
      const fiber = ctx.plugin(ctx => { new SkillHubService(ctx, { get: () => config }); });
      await fiber;
      try {
        const signal = new AbortController().signal;
        const detail = await ctx.skillhub.getSkillDetail(ctx.skillhub.getContext().sourceRevision,
          'team', 'writer', '1', signal);
        const availability = mode === 'hide' ? 'hidden' : mode === 'show' ? 'unknown'
          : status === 200 ? 'available' : status === 403 ? 'anonymous-denied' : 'unknown';
        expect(detail.tabs).toEqual({ versions: availability, reviews: availability,
          securityAudit: mode === 'hide' ? 'hidden' : 'anonymous-denied' });
        expect(requests.filter(path => path.includes('size='))).toEqual(mode === 'auto' ? [
          '/api/v1/skills/team/writer/versions?page=0&size=1',
          '/api/v1/skills/42/reviews?page=0&size=1',
        ] : []);
        for (const read of [() => ctx.skillhub.listVersions(detail.detailToken, 0, signal),
          () => ctx.skillhub.listReviews(detail.detailToken, 0, signal)]) {
          if (mode === 'hide') await expect(read()).rejects.toMatchObject({ code: 'skillhub/context-stale' });
          else if (status === 200) expect(await read()).toMatchObject({ items: [], total: 0, size: 20 });
          else await expect(read()).rejects.toMatchObject({ code: 'skillhub/registry-error', details: { status } });
        }
        expect(requests.filter(path => path.includes('size=20'))).toHaveLength(mode === 'hide' ? 0 : 2);
        expect(requests.some(path => path.includes('security-audit'))).toBe(false);
        // Configuration changes must gate even previously issued detail references before I/O.
        config = { ...config, detailTabs: { versions: 'hide', reviews: 'hide', securityAudit: 'hide' } };
        const count = requests.length;
        await expect(ctx.skillhub.listVersions(detail.detailToken, 0, signal)).rejects.toMatchObject({ code: 'skillhub/context-stale' });
        await expect(ctx.skillhub.listReviews(detail.detailToken, 0, signal)).rejects.toMatchObject({ code: 'skillhub/context-stale' });
        expect(requests).toHaveLength(count);
      } finally { await fiber.dispose(); await server.stop(true); }
    });
  }

  test('a transient failed probe can become an available empty module on explicit detail retry', async () => {
    let available = false;
    let probes = 0;
    const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch(request) {
      const url = new URL(request.url);
      if (url.pathname.endsWith('/reviews')) {
        probes++;
        return available ? Response.json({ data: { items: [], total: 0, page: 0, size: 1 } })
          : new Response('temporarily unavailable', { status: 503 });
      }
      if (url.pathname.endsWith('/resolve')) return Response.json({ data: {
        namespace: 'team', slug: 'writer', version: '1', versionId: 7, fingerprint: 'f', downloadUrl: '/archive',
      } });
      if (url.pathname.endsWith('/file')) return new Response('# Writer');
      return Response.json({ data: { id: 42, namespace: 'team', slug: 'writer', displayName: 'Writer', summary: '' } });
    } });
    const ctx = new Context();
    const fiber = ctx.plugin(SkillHubService, { registryUrl: server.url.href,
      detailTabs: { versions: 'hide', reviews: 'auto', securityAudit: 'hide' } });
    await fiber;
    try {
      const read = () => ctx.skillhub.getSkillDetail(ctx.skillhub.getContext().sourceRevision,
        'team', 'writer', '1', new AbortController().signal);
      expect((await read()).tabs.reviews).toBe('unknown');
      available = true;
      expect((await read()).tabs.reviews).toBe('available');
      expect((await read()).tabs.reviews).toBe('available');
      expect(probes).toBe(2);
    } finally { await fiber.dispose(); await server.stop(true); }
  });

  test.each([[403, 'anonymous-denied'], [404, 'unknown'], [503, 'unknown']] as const)(
    'auto preserves the meaning of HTTP %s without declaring unsupported', async (status, expected) => {
      let probes = 0;
      const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch(request) {
        const path = new URL(request.url).pathname;
        if (path.endsWith('/versions')) { probes++; return new Response('private', { status }); }
        if (path.endsWith('/resolve')) return Response.json({ data: { namespace:'team',slug:'writer',version:'1',versionId:7,fingerprint:'f',downloadUrl:'/archive' } });
        if (path.endsWith('/file')) return new Response('# Writer');
        return Response.json({ data: { id:42,namespace:'team',slug:'writer',displayName:'Writer',summary:'' } });
      } });
      const ctx = new Context();
      const fiber = ctx.plugin(SkillHubService, { registryUrl:server.url.href,
        detailTabs:{ versions:'auto',reviews:'hide',securityAudit:'auto' } });
      await fiber;
      try {
        for (let i = 0; i < 2; i++) {
          const detail = await ctx.skillhub.getSkillDetail(ctx.skillhub.getContext().sourceRevision,
            'team','writer','1',new AbortController().signal);
          expect(detail.tabs.versions).toBe(expected);
        }
        expect(probes).toBe(status === 403 ? 1 : 2);
      } finally { await fiber.dispose(); await server.stop(true); }
    },
  );

  test('auto probes only first rows, shares cached capability and binds lazy reviews to skill ID', async () => {
    const paths: string[] = [];
    const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch(request) {
      const u = new URL(request.url); paths.push(u.pathname + u.search);
      expect(request.headers.get('authorization')).toBeNull();
      let data: unknown;
      if (u.pathname.endsWith('/resolve')) data = {namespace:'team',slug:'writer',version:'1',versionId:7,fingerprint:'f',downloadUrl:'/archive'};
      else if (u.pathname.endsWith('/file')) return new Response('# Writer');
      else if (u.pathname.endsWith('/versions') || u.pathname.endsWith('/reviews')) data = {
        items: [], total:0,page:Number(u.searchParams.get('page')),size:Number(u.searchParams.get('size')),
      };
      else data = {id:42,namespace:'team',slug:'writer',displayName:'Writer',summary:'Example',ownerId:'private',canManageLifecycle:true};
      return Response.json({data});
    }});
    const ctx = new Context();
    let config: SkillHubConfig = {registryUrl:server.url.href,detailTabs:{versions:'auto',reviews:'auto',securityAudit:'show'}};
    const fiber = ctx.plugin(ctx => { new SkillHubService(ctx,{get:()=>config}); }); await fiber;
    const signal = new AbortController().signal;
    try {
      const revision=ctx.skillhub.getContext().sourceRevision;
      const detail=await ctx.skillhub.getSkillDetail(revision,'team','writer','1',signal);
      expect(detail.tabs).toEqual({versions:'available',reviews:'available',securityAudit:'anonymous-denied'});
      expect(detail).not.toHaveProperty('ownerId');
      expect(paths.filter(path=>path.includes('size='))).toEqual([
        '/api/v1/skills/team/writer/versions?page=0&size=1', '/api/v1/skills/42/reviews?page=0&size=1',
      ]);
      await ctx.skillhub.getSkillDetail(revision,'team','writer','1',signal);
      expect(paths.filter(path=>path.includes('size=1'))).toHaveLength(2);
      await ctx.skillhub.listReviews(detail.detailToken,1,signal);
      expect(paths.at(-1)).toBe('/api/v1/skills/42/reviews?page=1&size=20');
      expect(paths.some(path=>path.includes('security-audit'))).toBe(false);
      config={...config,detailTabs:{versions:'hide',reviews:'hide',securityAudit:'hide'}};
      const before=paths.length;
      const hidden=await ctx.skillhub.getSkillDetail(revision,'team','writer','1',signal);
      expect(hidden.tabs).toEqual({versions:'hidden',reviews:'hidden',securityAudit:'hidden'});
      expect(paths.slice(before).some(path=>path.includes('size='))).toBe(false);
      await expect(ctx.skillhub.listReviews(detail.detailToken,0,signal)).rejects.toMatchObject({code:'skillhub/context-stale'});
      config={...config,registryUrl:''};
      await expect(ctx.skillhub.listVersions(detail.detailToken,0,signal)).rejects.toMatchObject({code:'skillhub/context-stale'});
    } finally { await fiber.dispose(); await server.stop(true); }
  });
});
