import { expect, test } from 'bun:test';
import { Context, symbols } from '@deepseek-ai/cordis';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import SkillHubService from '../src/host/index.js';
import { installationContextError, installationContextReason, installationContextReasons } from '../src/host/installation-context.js';

// Run the real Host preacceptance path resolver. Change one captured boundary while
// its filesystem await is in flight, rather than replacing the guard under test.
for (const phase of ['list', 'target'] as const) for (const reason of ['AGENT', 'CWD', 'SOURCE', 'FS', 'POLICY', 'RUNTIME', 'DETAIL', 'stable-proxy'] as const) {
  test(`installation target resolution (${phase}) diagnoses ${reason} without weakening identity checks`, async () => {
    const directory = await mkdtemp(join(tmpdir(), 'skillhub-context-'));
    const ctx = new Context();
    let policy: unknown;
    let mutate = () => {};
    let calls = 0;
    const originalFs = {
      processPathFromHostPath: (path: string) => path,
      processPath: (path: string) => path,
      resolve: async (path: string) => { if (++calls === 1) mutate(); await Promise.resolve(); return path; },
    };
    let fs: object = originalFs;
    const agent = { session: { id: 'owner', header: { cwd: directory } }, ctx: { get: (key: string) => key === 'fs' ? fs : { resolve: () => policy } } };
    let currentAgent = agent;
    const remove = ctx.provide('agents', { get: () => currentAgent } as never);
    const fiber = ctx.plugin(SkillHubService, { registryUrl: 'https://registry.example' });
    await fiber;
    const runtime = { getStatus: () => ({ state: 'ready', hostInstanceId: 'host' }) };
    Reflect.set(ctx.skillhub, 'localRuntime', runtime);
    const revision = ctx.skillhub.getContext().sourceRevision;
    const details: Map<string, unknown> = Reflect.get(ctx.skillhub, 'details');
    details.set('detail', { sourceRevision: revision, fingerprint: 'fixed' });
    const list = Reflect.get(ctx.skillhub, 'installationTargetsFor').bind(ctx.skillhub);
    const available = phase === 'target' ? await list('owner', 'detail', new AbortController().signal) : undefined;
    calls = 0;
    mutate = () => {
      if (reason === 'AGENT') currentAgent = { ...agent };
      if (reason === 'CWD') agent.session.header.cwd = join(directory, 'other');
      if (reason === 'SOURCE') Reflect.set(ctx.skillhub, 'sourceRevision', 'changed');
      if (reason === 'FS') fs = { ...originalFs };
      if (reason === 'POLICY') policy = { mode: 'danger-full-access' };
      if (reason === 'RUNTIME') Reflect.set(ctx.skillhub, 'localRuntime', { ...runtime });
      if (reason === 'DETAIL') details.delete('detail');
      // Cordis can return fresh proxies of the same official underlying service.
      // This supported original-symbol identity already existed before this fix.
      if (reason === 'stable-proxy') fs = { ...originalFs, [symbols.original]: originalFs };
    };
    try {
      const resolve = async () => phase === 'list' ? list('owner', 'detail', new AbortController().signal)
        : { targets: [await ctx.skillhub.resolveInstallationTarget('owner', 'detail', available.targets[0].targetToken, new AbortController().signal)] };
      if (reason === 'stable-proxy') expect((await resolve()).targets).toHaveLength(phase === 'list' ? 2 : 1);
      else {
        let caught: unknown;
        try { await resolve(); } catch (error) { caught = error; }
        expect(installationContextReason(caught)).toBe(reason);
        expect(caught).toHaveProperty('code', 'skillhub/context-stale');
        expect(JSON.stringify(caught)).not.toContain(directory);
      }
    } finally { await fiber.dispose(); await remove(); await rm(directory, { recursive: true, force: true }); }
  });
}

test('context diagnostics accept only tagged errors and the core host mismatch code', () => {
  for (const reason of installationContextReasons) expect(installationContextReason(installationContextError(reason))).toBe(reason);
  expect(installationContextReason({ code: 'HOST_MISMATCH', message: '/private/token' })).toBe('HOST_MISMATCH');
  for (const error of [undefined, '/private/token', new Error('/private/token'), { reason: 'FS', code: 'skillhub/context-stale' }]) {
    expect(installationContextReason(error)).toBeUndefined();
  }
});
