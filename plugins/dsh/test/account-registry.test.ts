import { expect, test } from 'bun:test';
import { zipSync, strToU8 } from 'fflate';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { AccountRegistry, uploadPackage } from '../src/host/account-registry.ts';
import { AccountCredentials } from '../src/host/account-credentials.ts';
import { RegistryAdapter } from '../src/host/registry-adapter.ts';

const signal = new AbortController().signal;
const archive = Buffer.from(zipSync({ 'SKILL.md': strToU8('---\nname: Demo Skill\n---\n') })).toString('base64');
const target = { slug: 'demo-skill', existing: null };
const skill = { id: 7, namespace: 'design', slug: target.slug, displayName: 'Demo', status: 'ACTIVE', visibility: 'PRIVATE' };

function registry(handler: (url: URL, req: RequestInit) => Response | Promise<Response>) {
  const transport = Object.assign(async (input: string | Request | URL, init?: RequestInit) => {
    const url = new URL(String(input));
    expect(url.origin).toBe('https://registry.example');
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer local-fixture');
    expect(init?.redirect).toBe('error');
    return handler(url, init!);
  }, { preconnect: fetch.preconnect });
  return new AccountRegistry(new RegistryAdapter('https://registry.example/prefix', transport, 1000, 'local-fixture'));
}
const data = (value: unknown) => Response.json({ data: value });

test('upload uses one shared CLI publish request and status failures never resubmit it', async () => {
  let posts = 0;
  const api = registry((url, req) => {
    if (url.pathname.endsWith('/me/skills')) {
      expect(url.searchParams.get('namespace')).toBe('design');
      expect(url.searchParams.get('filter')).toBe('all');
      expect(url.searchParams.get('q')).toBe(target.slug);
      return data({ items: [], page: 0, size: 20, total: 0 });
    }
    if (req.method === 'POST') {
      expect(url.pathname).toBe('/prefix/api/cli/v1/skills/design/publish');
      expect((req.body as FormData).get('visibility')).toBe('NAMESPACE_ONLY');
      expect((req.body as FormData).get('file')).toBeInstanceOf(Blob);
      posts++;
      return data({ namespace: 'design', slug: target.slug, version: '1.0', visibility: 'NAMESPACE_ONLY', versionId: 14 });
    }
    expect(url.pathname).toBe('/prefix/api/v1/me/skills/versions/14');
    return new Response(null, { status: 403 });
  });
  const result = await api.publish('design', archive, 'NAMESPACE_ONLY', target, signal);
  expect(result.versionId).toBe(14);
  await expect(api.status(14, result.version, signal)).rejects.toMatchObject({ code: 'ACCESS_DENIED' });
  expect(posts).toBe(1);
});

test('a legacy publish response is a successful receipt without a version ID', async () => {
  const legacy = { namespace: 'design', slug: target.slug, version: '1.0', visibility: 'PRIVATE' } as const;
  const api = registry((_url, req) => req.method === 'POST'
    ? data(legacy) : data({ items: [], page: 0, size: 20, total: 0 }));
  expect(await api.publish('design', archive, 'PRIVATE', target, signal)).toEqual(legacy);
});

test('upload status reads only the acknowledged version and rejects identity mismatches', async () => {
  let response = { id: 14, version: '1.0', status: 'UPLOADED' };
  const api = registry((url, req) => {
    expect(url.pathname).toBe('/prefix/api/v1/me/skills/versions/14');
    expect(req.method).toBeUndefined();
    return data(response);
  });
  expect(await api.status(14, '1.0', signal)).toEqual(response);
  response = { ...response, id: 15 };
  await expect(api.status(14, '1.0', signal)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' });
  response = { ...response, id: 14, version: '2.0' };
  await expect(api.status(14, '1.0', signal)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' });
  await expect(api.status(0, '1.0', signal)).rejects.toMatchObject({ code: 'INVALID_INPUT' });
});

test('an owned row retains the exact published version ID used when opening a detail', async () => {
  const row = { ...skill, publishedVersion: { id: 14, version: '1.0' } };
  const api = registry(() => data({ items: [row], total: 1, page: 0, size: 20 }));
  const page = await api.owned('design', 0, '', signal);
  expect(page.items[0]?.publishedVersion).toEqual({ id: 14, version: '1.0' });
});

test('upload stops before writing when the target changed or matches on multiple pages', async () => {
  let pages = 0;
  let total = 1;
  const api = registry((url, req) => {
    expect(req.method).toBeUndefined();
    pages++;
    return data({ items: [skill], page: Number(url.searchParams.get('page')), size: 20, total });
  });
  await expect(api.publish('design', archive, 'PRIVATE', target, signal)).rejects.toMatchObject({ code: 'TARGET_CHANGED' });
  total = 21;
  await expect(api.target('design', target.slug, signal)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' });
  expect(pages).toBe(3);
  expect(() => uploadPackage('not a ZIP')).toThrow();
});

test('member changes use the selected user ID and propagate an expired credential', async () => {
  const api = registry((url, req) => {
    if (req.method === 'POST') {
      expect(url.pathname).toBe('/prefix/api/v1/namespaces/team/members');
      expect(JSON.parse(String(req.body))).toEqual({ userId: 'user-1', role: 'MEMBER' });
      return data({ userId: 'user-1', role: 'MEMBER', displayName: null });
    }
    return new Response(null, { status: 401 });
  });
  expect(await api.changeMember('team', { action: 'add', userId: 'user-1', role: 'MEMBER' }, signal)).toBe(true);
  await expect(api.members('team', 0, signal)).rejects.toMatchObject({ code: 'AUTH_REQUIRED' });
});

test('member reads preserve the safe server error code for business guidance', async () => {
  const api = registry(() => Response.json(
    { code: 403, errorCode: 'error.namespace.member.listForbidden' },
    { status: 403 },
  ));
  await expect(api.members('global', 0, signal)).rejects.toMatchObject({
    code: 'ACCESS_DENIED', status: 403, errorCode: 'error.namespace.member.listForbidden',
  });
});

test('member reads bound chunked 403 diagnostics and preserve the permission error', async () => {
  let cancelled = false;
  const body = new ReadableStream({
    start(controller) { controller.enqueue(new Uint8Array(64 * 1024 + 1)); },
    cancel() { cancelled = true; },
  });
  const api = registry(() => new Response(body, { status: 403 }));
  await expect(api.members('team', 0, signal)).rejects.toMatchObject({ code: 'ACCESS_DENIED', status: 403 });
  expect(cancelled).toBe(true);
});

test('member reads distinguish a missing bearer scope from a team role denial', async () => {
  const path = '/prefix/api/v1/namespaces/team/members';
  const headers = { 'WWW-Authenticate': 'Bearer error="insufficient_scope", scope="namespace:read"' };
  const missing = registry(url => {
    expect(url.pathname).toBe(path);
    return new Response(null, { status: 403, headers });
  });
  await expect(missing.members('team', 0, signal)).rejects.toMatchObject({
    code: 'TOKEN_SCOPE_REQUIRED', status: 403, requiredScope: 'namespace:read',
  });
  const denied = registry(() => new Response(null, { status: 403 }));
  await expect(denied.members('team', 0, signal)).rejects.toMatchObject({ code: 'ACCESS_DENIED', status: 403 });
  const member = { userId: 'user-1', displayName: null, role: 'MEMBER' };
  const allowed = registry(() => data({ items: [member], total: 1, page: 0, size: 20 }));
  await expect(allowed.members('team', 0, signal)).resolves.toMatchObject({ items: [member] });
});

test('credentials stay bound to one source and profile, and logout forgets them', () => {
  const home = mkdtempSync(join(tmpdir(), 'skillhub-account-'));
  const a = 'https://example.org/a';
  const b = 'https://example.org/b';
  const value = { token: 'test-value', identity: { handle: 'id', displayName: 'Name' } };
  try {
    const credentials = new AccountCredentials(home);
    credentials.set(a, value);
    expect(credentials.get(a)).toEqual(value);
    expect(credentials.get(b)).toBeUndefined();
    expect(new AccountCredentials(home).get(a)).toEqual(value);
    credentials.set(a);
    expect(credentials.get(a)).toBeUndefined();
    expect(new AccountCredentials(home).get(a)).toBeUndefined();
  } finally { rmSync(home, { recursive: true, force: true }); }
}, 30000);

test.each([
  ['lowercase', { 'skill.md': strToU8('---\nname: demo-skill\n---\n') }],
  ['leading whitespace', { 'SKILL.md': strToU8('\n\n---\nname: demo-skill\n---\n') }],
  ['auxiliary manifest', { 'SKILL.md': strToU8('---\nname: demo-skill\n---\n'), 'references/SKILL.md': strToU8('Reference documentation') }],
  ['wrapped lowercase', { 'package/skill.md': strToU8('---\nname: demo-skill\n---\n') }],
])('accepts the shared server manifest rules for %s', (_name, files) => {
  expect(uploadPackage(Buffer.from(zipSync(files)).toString('base64')).slug).toBe('demo-skill');
});

test('ambiguous root manifests remain rejected', () => {
  const bytes = zipSync({ 'SKILL.md': strToU8('---\nname: first\n---\n'), 'skill.md': strToU8('---\nname: second\n---\n') });
  expect(() => uploadPackage(Buffer.from(bytes).toString('base64'))).toThrow();
});

test('two profiles sharing a home keep their accounts and logout independent', () => {
  const home = mkdtempSync(join(tmpdir(), 'skillhub-profiles-'));
  const source = 'https://registry.example';
  const first = join(home, 'profiles', 'first');
  const second = join(home, 'profiles', 'second');
  const alice = { token: 'alice-fixture', identity: { handle: 'alice', displayName: 'Alice' } };
  const bob = { token: 'bob-fixture', identity: { handle: 'bob', displayName: 'Bob' } };
  try {
    const a = new AccountCredentials(first);
    const b = new AccountCredentials(second);
    a.set(source, alice);
    expect(b.get(source)).toBeUndefined();
    b.set(source, bob);
    expect(a.get(source)).toEqual(alice);
    b.set(source);
    expect(a.get(source)).toEqual(alice);
    expect(b.get(source)).toBeUndefined();
    expect(new AccountCredentials(first).get(source)).toEqual(alice);
    expect(new AccountCredentials(second).get(source)).toBeUndefined();
    const transient = new AccountCredentials();
    transient.set(source, bob);
    expect(new AccountCredentials().get(source)).toBeUndefined();
  } finally { rmSync(home, { recursive: true, force: true }); }
}, 60000);
