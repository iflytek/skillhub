import { expect, test } from 'bun:test';
import { createHash, randomUUID } from 'node:crypto';
import { mkdir, mkdtemp, readFile, realpath, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { zipSync, strToU8 } from 'fflate';
import { registryFetch } from '../scripts/core-registry-transport.ts';
import type { NativeCore, NativeRuntime } from '../src/host/generated/native-contract.js';

test('DSH download redirects stop carrying credentials after leaving Registry API paths', async () => {
  const observed: Array<string | null> = [];
  const urls = ['https://registry.example/prefix/api/download', 'https://registry.example/storage/file', 'https://registry.example/prefix/api/final'];
  const fetchImpl = Object.assign(async (input: string | URL | Request, init?: RequestInit) => {
    const index = observed.length;
    expect(String(input)).toBe(urls[index]);
    observed.push(new Headers(init?.headers).get('Authorization'));
    expect(init?.redirect).toBe('manual');
    return index < 2 ? new Response(null, { status: 302, headers: { Location: urls[index + 1]! } }) : new Response('package');
  }, { preconnect: fetch.preconnect });
  const response = await registryFetch('https://registry.example/prefix', 'fixture-token', fetchImpl)(urls[0]!);
  expect(await response.text()).toBe('package');
  expect(observed).toEqual(['Bearer fixture-token', null, null]);
});

test('a CDN denial cannot replay a successful authenticated Registry redirect', async () => {
  const registry = 'https://registry.example/prefix';
  const seen: Array<string | null> = [];
  const fetchImpl = Object.assign(async (input: string | URL | Request, init?: RequestInit) => {
    seen.push(new Headers(init?.headers).get('Authorization'));
    return new URL(String(input)).origin === 'https://registry.example'
      ? new Response(null, { status: 302, headers: { Location: 'https://cdn.example/file' } })
      : new Response('cdn denial', { status: 403 });
  }, { preconnect: fetch.preconnect });
  const result = await registryFetch(registry, 'fixture-token', fetchImpl)(registry + '/api/v1/skills/team/demo/versions/1.0/download');
  expect(result.status).toBe(403);
  expect(await result.text()).toBe('cdn denial');
  expect(seen).toEqual(['Bearer fixture-token', null]);
});

test('resolve, archive and other Registry requests preserve authenticated denials', async () => {
  const registry = 'https://registry.example/prefix';
  const observed: Array<string | null> = [];
  const fetchImpl = Object.assign(async (_input: string | URL | Request, init?: RequestInit) => {
    const bearer = new Headers(init?.headers).get('Authorization');
    observed.push(bearer);
    return bearer ? new Response('authorization denied', { status: 403 }) : new Response('public response');
  }, { preconnect: fetch.preconnect });
  const request = registryFetch(registry, 'fixture-token', fetchImpl);
  for (const [path, method] of [
    ['/api/cli/v1/skills/team/demo/resolve', 'GET'],
    ['/api/v1/skills/team/demo/versions/1.0/download', 'GET'],
    ['/api/cli/v1/auth/whoami', 'GET'],
    ['/api/cli/v1/skills/team/demo/resolve', 'POST'],
  ] as const) {
    const result = await request(registry + path, { method });
    expect(result.status).toBe(403);
    expect(await result.text()).toBe('authorization denied');
  }
  expect(observed).toEqual(Array(4).fill('Bearer fixture-token'));
});

test('the packaged DSH adapter authenticates concurrent Host installations independently', async () => {
  const content = '# Private fixture';
  const fingerprint = 'sha256:' + createHash('sha256').update('SKILL.md:' + createHash('sha256').update(content).digest('hex') + '\n').digest('hex');
  const archive = zipSync({ 'SKILL.md': strToU8(content) });
  const tokens = new Set<string>();
  const server = Bun.serve({ hostname: '127.0.0.1', port: 0, async fetch(request) {
    const auth = request.headers.get('Authorization');
    if (auth !== 'Bearer alice' && auth !== 'Bearer bob') return new Response(null, { status: 401 });
    tokens.add(auth);
    await new Promise(resolve => setTimeout(resolve, 5));
    if (new URL(request.url).pathname.endsWith('/resolve')) return Response.json({ data: {
      namespace: 'team', slug: 'fixture', version: '1.0', versionId: 7, fingerprint,
      downloadUrl: '/api/cli/v1/skills/team/fixture/versions/1.0/download',
    } });
    return new Response(archive);
  } });
  const home = await realpath(await mkdtemp(join(tmpdir(), 'dsh-private-core-')));
  try {
    const core = await import(new URL('../lib/local-core.js', import.meta.url).href) as {
      openNativeRuntime(options: Parameters<NativeCore['openNativeRuntime']>[0] & { getRegistryToken(registry: string): string | undefined }): Promise<NativeRuntime>;
    };
    await Promise.all(['alice', 'bob'].map(async name => {
      const profile = join(home, name);
      const root = join(profile, 'skills');
      await mkdir(root, { recursive: true });
      const registry = server.url.href.replace(/\/$/u, '');
      const runtime = await core.openNativeRuntime({ stateDirectory: join(profile, 'operations'), inventoryHome: profile,
        getRegistryToken: source => source === registry ? name : undefined });
      const operation = await runtime.acceptInstall({ hostInstanceId: runtime.hostInstanceId, ownerId: name,
        requestKey: randomUUID(), mode: 'new', parameters: { slug: 'fixture' } }, async () => ({
        namespace: 'team', slug: 'fixture', version: '1.0', targetLabel: 'Fixture', targetPaths: [join(root, 'fixture')],
        context: { sourceIdentity: registry, sourceLabel: 'Fixture', sourceRevision: randomUUID(), hostLabel: 'DSH', initiatedFrom: 'plaza' },
      }));
      expect(operation).toBeDefined();
      const result = await runtime.executeInstall({ operationId: operation!.operationId, ownerId: name, registry,
        namespace: 'team', slug: 'fixture', version: '1.0', versionId: 7, fingerprint, root, scope: 'user',
        authorize: async () => {}, validateStaged: async () => {} });
      expect(result.outcome).toBe('committed');
      expect(await readFile(join(root, 'fixture', 'SKILL.md'), 'utf8')).toBe(content);
    }));
    expect(tokens).toEqual(new Set(['Bearer alice', 'Bearer bob']));
  } finally { await server.stop(true); await rm(home, { recursive: true, force: true }); }
});
