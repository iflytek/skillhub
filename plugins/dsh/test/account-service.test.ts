import { expect, test } from 'bun:test';
import { Context } from '@deepseek-ai/cordis';
import SkillHubService from '@astron-team/skillhub-dsh';
import { createHash } from 'node:crypto';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { mkdirSync, mkdtempSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs';
import { AccountCredentials } from '../src/host/account-credentials.ts';
import type { SkillHubConfig } from '../src/host/types.ts';

const identity = { handle: 'fixture-user', displayName: '测试用户 🐈' };
const fixture = { token: 'fixture-account-token', identity };
const signal = new AbortController().signal;
const file = (profile: string, source: string) => join(profile, 'skillhub-accounts', createHash('sha256').update(source).digest('hex') + '.json');

async function environment() {
  const profile = mkdtempSync(join(tmpdir(), 'skillhub-service-'));
  const requests: { path: string; authorization: string | null }[] = [];
  let status = 200;
  let pendingWhoami: { started(): void; gate: Promise<void> } | undefined;
  const server = Bun.serve({ hostname: '127.0.0.1', port: 0, async fetch(request) {
    const path = new URL(request.url).pathname;
    requests.push({ path, authorization: request.headers.get('authorization') });
    if (path.endsWith('/whoami')) {
      if (pendingWhoami) { const pending = pendingWhoami; pendingWhoami = undefined; pending.started(); await pending.gate; }
      if (request.headers.get('authorization') !== 'Bearer ' + fixture.token) return new Response(null, { status: 401 });
      return status === 200 ? Response.json({ data: identity }) : new Response(null, { status });
    }
    return Response.json({ data: { items: [], total: 0, limit: 20 } });
  } });
  const source = server.url.href.replace(/\/$/u, '');
  let config: SkillHubConfig = { registryUrl: source, detailTabs: { versions: 'hide', reviews: 'hide', securityAudit: 'hide' } };
  const fibers: { dispose(): Promise<void> }[] = [];
  async function start() {
    const ctx = new Context();
    const provider = ctx.plugin(ctx => {
      ctx.provide('profileContext', { name: 'fixture', dir: profile, patchPath: join(profile, 'patch.yml'), installAnchor: profile,
        cwd: profile, home: profile, startedBundles: [], overlays: [], telemetryDisabledEnv: undefined });
    });
    await provider;
    fibers.push(provider);
    const fiber = ctx.plugin(ctx => { new SkillHubService(ctx, { get: () => config }); });
    await fiber;
    fibers.push(fiber);
    return ctx.skillhub;
  }
  return { profile, source, start, requests, setStatus(value: number) { status = value; },
    switchSource(value: string) { config = { ...config, registryUrl: value }; },
    stopService: () => server.stop(true),
    pauseWhoami() {
      let began!: () => void;
      let release!: () => void;
      const started = new Promise<void>(resolve => { began = resolve; });
      pendingWhoami = { started: began, gate: new Promise<void>(resolve => { release = resolve; }) };
      return { started, release };
    },
    async dispose() { for (const fiber of fibers.toReversed()) await fiber.dispose(); await server.stop(true); rmSync(profile, { recursive: true, force: true }); } };
}

test('a fresh Host verifies the saved account and an invalid replacement preserves it', async () => {
  const env = await environment();
  try {
    const first = await env.start();
    await first.connectAccount(first.getContext().sourceRevision, fixture.token, signal);
    const original = readFileSync(file(env.profile, env.source));
    const restored = await env.start();
    const revision = restored.getContext().sourceRevision;
    expect(await restored.accountIdentity(revision, signal)).toEqual(identity);
    await expect(restored.connectAccount(revision, 'invalid-fixture', signal)).rejects.toMatchObject({ details: { reason: 'AUTH_REQUIRED' } });
    expect(restored.getContext().sourceRevision).toBe(revision);
    expect(readFileSync(file(env.profile, env.source))).toEqual(original);
    expect(await restored.accountIdentity(revision, signal)).toEqual(identity);
  } finally { await env.dispose(); }
}, 30000);

test('a verified replacement that cannot be saved leaves the original session and record intact', async () => {
  const env = await environment();
  try {
    const api = await env.start();
    await api.connectAccount(api.getContext().sourceRevision, fixture.token, signal);
    const revision = api.getContext().sourceRevision;
    const original = readFileSync(file(env.profile, env.source));
    const directory = join(env.profile, 'skillhub-accounts');
    renameSync(directory, directory + '.preserved');
    writeFileSync(directory, 'temporarily unavailable fixture');
    await expect(api.connectAccount(revision, fixture.token, signal)).rejects.toMatchObject({ code: 'skillhub/account-storage-error', details: { reason: 'SAVE_FAILED' } });
    expect(api.getContext().sourceRevision).toBe(revision);
    expect(await api.accountIdentity(revision, signal)).toEqual(identity);
    rmSync(directory); renameSync(directory + '.preserved', directory);
    expect(readFileSync(file(env.profile, env.source))).toEqual(original);
    expect(new AccountCredentials(env.profile).get(env.source)).toEqual(fixture);
  } finally { await env.dispose(); }
}, 30000);

test('a connection failure keeps a restored record for a later attempt', async () => {
  const env = await environment();
  try {
    new AccountCredentials(env.profile).set(env.source, fixture);
    const api = await env.start();
    const revision = api.getContext().sourceRevision;
    await env.stopService();
    await expect(api.accountIdentity(revision, signal)).rejects.toMatchObject({ code: 'skillhub/registry-error', details: { reason: 'NETWORK_ERROR' } });
    expect(api.getContext().sourceRevision).toBe(revision);
    expect(new AccountCredentials(env.profile).get(env.source)).toEqual(fixture);
  } finally { await env.dispose(); }
}, 30000);

test('an invalid source edit cannot split the existing account from its authenticated registry', async () => {
  const env = await environment();
  try {
    const api = await env.start();
    await api.connectAccount(api.getContext().sourceRevision, fixture.token, signal);
    const revision = api.getContext().sourceRevision;
    env.switchSource('invalid source fixture');
    expect(() => api.getContext()).toThrow();
    env.switchSource(env.source);
    expect(api.getContext().sourceRevision).toBe(revision);
    expect(await api.accountIdentity(revision, signal)).toEqual(identity);
    await api.listSkills(revision, '', signal);
    expect(env.requests.at(-1)?.authorization).toBe('Bearer ' + fixture.token);
  } finally { await env.dispose(); }
}, 30000);

test.each(['source', 'cancel'] as const)('a %s change during identity verification cannot save a late account', async change => {
  const env = await environment();
  try {
    const api = await env.start();
    const revision = api.getContext().sourceRevision;
    const controller = new AbortController();
    const pending = env.pauseWhoami();
    const connection = api.connectAccount(revision, fixture.token, controller.signal);
    await pending.started;
    if (change === 'source') { env.switchSource(env.source + '/other'); api.getContext(); }
    else controller.abort();
    pending.release();
    await expect(connection).rejects.toBeDefined();
    expect(new AccountCredentials(env.profile).get(env.source)).toBeUndefined();
    expect(new AccountCredentials(env.profile).get(env.source + '/other')).toBeUndefined();
  } finally { await env.dispose(); }
}, 30000);

test.each([401, 403, 503])('restored identity handles HTTP %s without forgetting a temporary failure', async status => {
  const env = await environment();
  try {
    new AccountCredentials(env.profile).set(env.source, fixture);
    const api = await env.start();
    const revision = api.getContext().sourceRevision;
    env.setStatus(status);
    await expect(api.accountIdentity(revision, signal)).rejects.toMatchObject({ code: 'skillhub/registry-error', details: { status } });
    const current = api.getContext().sourceRevision;
    if (status === 401) {
      expect(current).not.toBe(revision);
      expect(await api.accountIdentity(current, signal)).toBeNull();
      expect(new AccountCredentials(env.profile).get(env.source)).toBeUndefined();
    } else {
      expect(current).toBe(revision);
      expect(new AccountCredentials(env.profile).get(env.source)).toEqual(fixture);
      env.setStatus(200);
      expect(await api.accountIdentity(current, signal)).toEqual(identity);
    }
  } finally { await env.dispose(); }
}, 30000);

test.each(['logout', 'expired'] as const)('%s cannot retain a live account when unlink fails, and cleanup can be retried', async action => {
  const env = await environment();
  try {
    const api = await env.start();
    await api.connectAccount(api.getContext().sourceRevision, fixture.token, signal);
    const revision = api.getContext().sourceRevision;
    const original = readFileSync(file(env.profile, env.source));
    rmSync(file(env.profile, env.source)); mkdirSync(file(env.profile, env.source));
    writeFileSync(join(file(env.profile, env.source), 'block-unlink'), 'fixture');
    if (action === 'logout') expect(() => api.disconnectAccount(revision)).toThrow();
    else {
      env.setStatus(401);
      await expect(api.accountIdentity(revision, signal)).rejects.toMatchObject({ code: 'skillhub/registry-error', details: { reason: 'AUTH_REQUIRED', status: 401 } });
    }
    const current = api.getContext();
    expect(current.sourceRevision).not.toBe(revision);
    expect(current.accountClearFailed).toBe(true);
    expect(await api.accountIdentity(current.sourceRevision, signal)).toBeNull();
    env.switchSource(env.source + '/other');
    expect(api.getContext().accountClearFailed).toBeUndefined();
    expect(() => api.disconnectAccount(current.sourceRevision)).toThrow();
    env.switchSource(env.source);
    const retry = api.getContext();
    expect(retry.accountClearFailed).toBe(true);
    rmSync(file(env.profile, env.source), { recursive: true });
    writeFileSync(file(env.profile, env.source), original, { mode: 0o600 });
    expect(await api.accountIdentity(retry.sourceRevision, signal)).toBeNull();
    expect(api.disconnectAccount(retry.sourceRevision)).toBe(true);
    expect(api.getContext().accountClearFailed).toBeUndefined();
    const next = await env.start();
    expect(await next.accountIdentity(next.getContext().sourceRevision, signal)).toBeNull();
  } finally { await env.dispose(); }
}, 30000);

test('temporary restore failure keeps browsing anonymous and recovers only through identity recheck', async () => {
  const env = await environment();
  try {
    writeFileSync(join(env.profile, 'skillhub-accounts'), 'temporarily unavailable fixture');
    const api = await env.start();
    const revision = api.getContext().sourceRevision;
    expect((await api.listSkills(revision, '', signal)).items).toEqual([]);
    expect(env.requests.at(-1)?.authorization).toBeNull();
    for (let attempt = 0; attempt < 2; attempt++) {
      await expect(api.accountIdentity(revision, signal)).rejects.toMatchObject({ code: 'skillhub/account-storage-error', details: { reason: 'RESTORE_FAILED' } });
      expect(api.getContext().sourceRevision).toBe(revision);
    }
    rmSync(join(env.profile, 'skillhub-accounts'));
    new AccountCredentials(env.profile).set(env.source, fixture);
    await expect(api.accountIdentity(revision, signal)).rejects.toMatchObject({ code: 'skillhub/context-stale' });
    const current = api.getContext().sourceRevision;
    expect(current).not.toBe(revision);
    expect(await api.accountIdentity(current, signal)).toEqual(identity);
    expect(env.requests.filter(x => x.path.endsWith('/whoami'))).toHaveLength(1);
  } finally { await env.dispose(); }
}, 30000);
