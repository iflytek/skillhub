import { z } from 'zod';
import { parseDocument } from 'yaml';
import { unzipSync } from 'fflate';
import { RegistryAdapter, RegistryError } from './registry-adapter.js';
import type { MemberMutation, UploadTarget, Visibility } from './account-types.js';

const text = z.string().min(1);
const id = z.number().int().positive().safe();
const vis = z.enum(['PRIVATE', 'NAMESPACE_ONLY', 'PUBLIC']);
const role = z.enum(['OWNER', 'ADMIN', 'MEMBER']);
const identity = z.object({ handle: text, displayName: z.string().nullable().transform(v => v ?? '') });
const namespace = z.object({ id, slug: text, displayName: text, type: text, status: text, currentUserRole: role });
const version = z.object({ id, version: text });
const owned = z.object({ id, slug: text, namespace: text, displayName: z.string().nullable().transform(v => v ?? ''), visibility: vis, status: text, publishedVersion: version.nullable().optional() });
const member = z.object({ userId: text, displayName: z.string().nullable(), role });
const candidate = z.object({ userId: text, displayName: z.string().nullable(), email: z.string().nullable() });
const receipt = z.object({ namespace: text, slug: text, version: text, visibility: vis });
const segment = (s: string) => {
  if (!s || s === '.' || s === '..' || s.length > 256 || /[\\/]/u.test(s)) throw new RegistryError('INVALID_INPUT', false);
  return encodeURIComponent(s);
};
function page<T>(schema: z.ZodType<T>, number: number) {
  if (!Number.isSafeInteger(number) || number < 0) throw new RegistryError('INVALID_INPUT', false);
  return z.object({ items: z.array(schema).max(20), total: z.number().int().nonnegative(), page: z.literal(number), size: z.literal(20) });
}

export function uploadPackage(base64: string) {
  if (!base64 || base64.length > 11_184_812) throw new RegistryError('INVALID_INPUT', false);
  const bytes = Buffer.from(base64, 'base64');
  if (bytes.length > 8 * 1024 * 1024 || bytes.toString('base64') !== base64) throw new RegistryError('INVALID_INPUT', false);
  try {
    const manifests: string[] = [];
    unzipSync(bytes, { filter: file => {
      if (/^(?:[^/]+\/)?skill\.md$/iu.test(file.name)) manifests.push(file.name);
      return false;
    } });
    const roots = manifests.filter(name => !name.includes('/'));
    const candidates = roots.length ? roots : manifests;
    if (candidates.length !== 1) throw new Error();
    const found = unzipSync(bytes, { filter: file => {
      if (file.name !== candidates[0]) return false;
      if (file.originalSize > 256 * 1024) throw new Error();
      return true;
    } });
    const entry = Object.values(found)[0];
    if (!entry) throw new Error();
    const front = /^---\r?\n([\s\S]*?)\r?\n---(?:\r?\n|$)/u.exec(new TextDecoder('utf-8', { fatal: true }).decode(entry).trim());
    if (!front) throw new Error();
    const doc = parseDocument(front[1]!, { uniqueKeys: true });
    if (doc.errors.length) throw new Error();
    const name = doc.get('name');
    if (typeof name !== 'string') throw new Error();
    const slug = name.trim().toLowerCase().replace(/[^\p{L}\p{N}\p{So}]+/gu, '-').replace(/^-|-$/gu, '');
    if (slug.length < 2 || slug.length > 64) throw new Error();
    return { bytes, slug };
  } catch { throw new RegistryError('INVALID_INPUT', false); }
}

/** Fixed authenticated endpoints on the configured Registry. The caller owns the credential lifecycle. */
export class AccountRegistry {
  constructor(private readonly registry: RegistryAdapter) {}
  whoami(signal: AbortSignal) { return this.registry.accountJson('/api/cli/v1/auth/whoami', identity, signal); }
  namespaces(signal: AbortSignal) { return this.registry.accountJson('/api/v1/me/namespaces', z.array(namespace), signal); }
  owned(ns: string, number: number, search: string, signal: AbortSignal) {
    const params = new URLSearchParams({ namespace: ns, filter: 'all', q: search, page: String(number), size: '20' });
    return this.registry.accountJson('/api/v1/me/skills?' + params, page(owned, number), signal);
  }
  team(ns: string, cursor: string, signal: AbortSignal) {
    const params = new URLSearchParams({ limit: '20', ...(cursor ? { cursor } : {}) });
    return this.registry.accountJson('/api/cli/v1/namespaces/' + segment(ns) + '/skills?' + params, z.object({
      items: z.array(z.object({ namespace: text, slug: text, version: text, versionId: id })).max(20), nextCursor: z.string().nullish().transform(v => v ?? null),
    }), signal);
  }
  members(ns: string, number: number, signal: AbortSignal) {
    return this.registry.accountJson('/api/v1/namespaces/' + segment(ns) + '/members?page=' + number + '&size=20', page(member, number), signal);
  }
  candidates(ns: string, search: string, signal: AbortSignal) {
    return this.registry.accountJson('/api/v1/namespaces/' + segment(ns) + '/member-candidates?' + new URLSearchParams({ search, size: '10' }), z.array(candidate).max(10), signal);
  }
  async changeMember(ns: string, mutation: MemberMutation, signal: AbortSignal) {
    const action = mutation.action;
    if (!['add', 'role', 'remove'].includes(action) || (action !== 'remove' && !['MEMBER','ADMIN'].includes(mutation.role))) throw new RegistryError('INVALID_INPUT', false);
    const root = '/api/v1/namespaces/' + segment(ns) + '/members';
    const user = segment(mutation.userId);
    if (action === 'remove') await this.registry.accountJson(root + '/' + user, z.unknown(), signal, { method: 'DELETE' });
    else {
      const response = await this.registry.accountJson(action === 'add' ? root : root + '/' + user + '/role', member, signal,
        { method: action === 'add' ? 'POST' : 'PUT', body: JSON.stringify({ userId: mutation.userId, role: mutation.role }), headers: { 'Content-Type': 'application/json' } });
      if (response.userId !== mutation.userId || response.role !== mutation.role) throw new RegistryError('INVALID_RESPONSE', false);
    }
    return true;
  }
  async deleteOwned(ns: string, skillId: number, slug: string, signal: AbortSignal) {
    if (!id.safeParse(skillId).success) throw new RegistryError('INVALID_INPUT', false);
    const result = await this.registry.accountJson('/api/v1/skills/id/' + skillId,
      z.object({ skillId: id, namespace: text, slug: text, deleted: z.boolean() }), signal, { method: 'DELETE' });
    if (result.skillId !== skillId || ns !== result.namespace || result.slug !== slug || !result.deleted) throw new RegistryError('INVALID_RESPONSE', false);
    return true;
  }
  async target(ns: string, slug: string, signal: AbortSignal): Promise<UploadTarget> {
    let number = 0;
    let existing: UploadTarget['existing'] = null;
    for (;;) {
      const result = await this.owned(ns, number++, slug, signal);
      const exact = result.items.filter(item => item.namespace === ns && item.slug === slug);
      if (exact.length > 1 || (existing && exact.length)) throw new RegistryError('INVALID_RESPONSE', false);
      if (exact[0]) existing = { id: exact[0].id, visibility: exact[0].visibility };
      if (number * result.size >= result.total) return { slug, existing };
      if (!result.items.length) throw new RegistryError('INVALID_RESPONSE', false);
    }
  }
  async publish(ns: string, archive: string, visibility: Visibility, expected: UploadTarget, signal: AbortSignal) {
    const { bytes, slug } = uploadPackage(archive);
    const current = await this.target(ns, slug, signal);
    if (!vis.safeParse(visibility).success || expected.slug !== slug || current.existing?.id !== expected.existing?.id
      || current.existing?.visibility !== expected.existing?.visibility) throw new RegistryError('TARGET_CHANGED', false);
    const body = new FormData();
    body.append('file', new Blob([new Uint8Array(bytes)], { type: 'application/zip' }), 'skill.zip');
    body.append('visibility', visibility);
    const result = await this.registry.accountJson('/api/cli/v1/skills/' + segment(ns) + '/publish', receipt, signal, { method: 'POST', body });
    if (result.namespace !== ns || result.slug !== slug || result.visibility !== visibility) throw new RegistryError('INVALID_RESPONSE', false);
    return result;
  }
  status(ns: string, slug: string, versionId: string, signal: AbortSignal) {
    return this.registry.accountJson('/api/v1/skills/' + [ns,slug].map(segment).join('/') + '/versions/' + segment(versionId),
      z.object({ id, version: z.literal(versionId), status: text }), signal);
  }
}
