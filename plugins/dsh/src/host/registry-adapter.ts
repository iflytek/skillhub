import { z } from 'zod';
import { retryAfterDeadline } from './retry-after.js';

const searchSchema = z.object({
  items: z.array(z.object({
    namespace: z.string().min(1), slug: z.string().min(1),
    latestVersion: z.string().min(1), summary: z.string().nullable(),
  })).max(20),
  total: z.number().int().nonnegative(), limit: z.number().int().positive().max(20),
}).refine(value => value.items.length <= value.limit && value.items.length <= value.total);
const resolveSchema = z.object({
  namespace: z.string().min(1), slug: z.string().min(1), version: z.string().min(1),
  versionId: z.number().int().positive(), fingerprint: z.string().min(1),
  downloadUrl: z.string().min(1),
});
const metadataSchema = z.object({
  id: z.number().int().positive(), namespace: z.string(), slug: z.string(),
  displayName: z.string().nullable(), summary: z.string().nullable(),
});
const versionSchema = z.object({
  version: z.string().min(1), changelog: z.string().nullable(),
  publishedAt: z.string().nullable(), downloadAvailable: z.boolean(),
});
const reviewSchema = z.object({
  id: z.number().int().positive(), displayName: z.string().nullish(),
  score: z.number().int().min(1).max(5), reviewText: z.string().nullish(), createdAt: z.string(),
});
function pageSchema<T>(item: z.ZodType<T>) {
  return z.object({ items: z.array(item).max(20), total: z.number().int().nonnegative(),
    page: z.number().int().nonnegative(), size: z.number().int().positive().max(20) })
    .refine(value => value.items.length <= value.size && value.items.length <= value.total);
}

export type SearchResult = z.infer<typeof searchSchema>;
export type ResolvedSkill = z.infer<typeof resolveSchema>;
export type RegistryErrorCode = 'INVALID_SOURCE' | 'INVALID_INPUT' | 'ANONYMOUS_DENIED'
  | 'NOT_FOUND' | 'RATE_LIMITED' | 'REGISTRY_UNAVAILABLE' | 'HTTP_ERROR'
  | 'INVALID_RESPONSE' | 'RESPONSE_TOO_LARGE' | 'CANCELLED' | 'NETWORK_ERROR';

export class RegistryError extends Error {
  constructor(
    readonly code: RegistryErrorCode,
    readonly retryable: boolean,
    readonly status?: number,
    readonly retryAfterSeconds?: number,
  ) {
    super(code);
    this.name = 'RegistryError';
  }
}

function segment(value: string): string {
  // WHATWG URLs normalize dot segments even when the dots are percent-encoded.
  // eslint-disable-next-line no-control-regex -- Deliberately reject control characters in untrusted coordinates.
  if (!value || value === '.' || value === '..' || /[\u0000-\u001f\u007f]/u.test(value)) {
    throw new RegistryError('INVALID_INPUT', false);
  }
  return encodeURIComponent(value);
}

/** Anonymous, read-only Registry transport; no CLI auth/configuration side effects. */
export class RegistryAdapter {
  readonly source: string;
  private retryAt = 0;

  constructor(source: string, private readonly fetchImpl: typeof fetch = fetch, private readonly timeoutMs = 30_000) {
    if (!Number.isSafeInteger(timeoutMs) || timeoutMs < 1) throw new RegistryError('INVALID_INPUT', false);
    let url: URL;
    try { url = new URL(source); }
    catch { throw new RegistryError('INVALID_SOURCE', false); }
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
      throw new RegistryError('INVALID_SOURCE', false);
    }
    this.source = url.href.replace(/\/+$/u, '');
  }

  async search(query = '', signal?: AbortSignal): Promise<SearchResult> {
    const params = new URLSearchParams({ q: query, limit: '20' });
    return this.json(`/api/cli/v1/skills/search?${params}`, searchSchema, signal);
  }

  async resolve(namespace: string, slug: string, version?: string, signal?: AbortSignal): Promise<ResolvedSkill> {
    const query = version === undefined ? '' : `?${new URLSearchParams({ version })}`;
    if (version === '') throw new RegistryError('INVALID_INPUT', false);
    const result = await this.json(
      `/api/cli/v1/skills/${segment(namespace)}/${segment(slug)}/resolve${query}`,
      resolveSchema, signal,
    );
    if (result.namespace !== namespace || result.slug !== slug || (version !== undefined && result.version !== version)) {
      throw new RegistryError('INVALID_RESPONSE', false);
    }
    return result;
  }

  async documentation(namespace: string, slug: string, version: string, signal?: AbortSignal): Promise<string> {
    return this.read(
      `/api/v1/skills/${segment(namespace)}/${segment(slug)}/versions/${segment(version)}/file?path=SKILL.md`,
      'text/plain', signal,
    );
  }

  async metadata(namespace: string, slug: string, signal?: AbortSignal) {
    const result = await this.json(`/api/v1/skills/${segment(namespace)}/${segment(slug)}`, metadataSchema, signal);
    if (result.namespace !== namespace || result.slug !== slug) throw new RegistryError('INVALID_RESPONSE', false);
    return result;
  }

  async versions(namespace: string, slug: string, page: number, size: number, signal?: AbortSignal) {
    return this.page(`/api/v1/skills/${segment(namespace)}/${segment(slug)}/versions`, versionSchema, page, size, signal);
  }

  async reviews(skillId: number, page: number, size: number, signal?: AbortSignal) {
    if (!Number.isSafeInteger(skillId) || skillId < 1) throw new RegistryError('INVALID_INPUT', false);
    const result = await this.page(`/api/v1/skills/${skillId}/reviews`, reviewSchema, page, size, signal);
    return { ...result, items: result.items.map(item => ({ ...item,
      displayName: item.displayName ?? '', reviewText: item.reviewText ?? '',
    })) };
  }

  private async page<T>(path: string, item: z.ZodType<T>, page: number, size: number, signal?: AbortSignal) {
    if (!Number.isSafeInteger(page) || page < 0 || !Number.isSafeInteger(size) || size < 1 || size > 20) {
      throw new RegistryError('INVALID_INPUT', false);
    }
    const result = await this.json(`${path}?${new URLSearchParams({ page: String(page), size: String(size) })}`, pageSchema(item), signal);
    if (result.page !== page || result.size !== size) throw new RegistryError('INVALID_RESPONSE', false);
    return result;
  }

  private async json<T>(path: string, schema: z.ZodType<T>, signal?: AbortSignal): Promise<T> {
    const body = await this.read(path, 'application/json', signal);
    try {
      return z.object({ data: schema }).parse(JSON.parse(body)).data;
    } catch {
      throw new RegistryError('INVALID_RESPONSE', false);
    }
  }

  private async read(path: string, accept: string, signal?: AbortSignal): Promise<string> {
    // Both JSON and SKILL.md are bounded; archive downloads use the install core.
    const maxBytes = 1024 * 1024;
    const deadline = new AbortController();
    const requestSignal = signal ? AbortSignal.any([signal, deadline.signal]) : deadline.signal;
    const timer = setTimeout(() => deadline.abort(), this.timeoutMs);
    try {
      requestSignal.throwIfAborted();
      const remaining = Math.ceil((this.retryAt - Date.now()) / 1000);
      if (remaining > 0) throw new RegistryError('RATE_LIMITED', true, 429, remaining);
      const response = await this.fetchImpl(`${this.source}${path}`, {
        method: 'GET', credentials: 'omit', redirect: 'error', signal: requestSignal,
        headers: { Accept: accept },
      });
      if (!response.ok) {
        const status = response.status;
        if (status === 429) {
          this.retryAt = Math.max(this.retryAt, retryAfterDeadline(response.headers.get('retry-after'), Date.now()) ?? 0);
        }
        await response.body?.cancel().catch(() => undefined);
        const code = status === 401 || status === 403 ? 'ANONYMOUS_DENIED'
          : status === 404 ? 'NOT_FOUND' : status === 429 ? 'RATE_LIMITED'
          : status >= 500 ? 'REGISTRY_UNAVAILABLE' : 'HTTP_ERROR';
        const seconds = status === 429 ? Math.ceil((this.retryAt - Date.now()) / 1000) : 0;
        throw new RegistryError(code, status === 429 || status >= 500, status, seconds > 0 ? seconds : undefined);
      }
      if (Number(response.headers.get('content-length')) > maxBytes) {
        await response.body?.cancel();
        throw new RegistryError('RESPONSE_TOO_LARGE', false);
      }
      if (!response.body) return '';
      const reader = response.body.getReader();
      const decoder = new TextDecoder('utf-8', { fatal: true });
      let bytes = 0;
      let content = '';
      try {
        for (;;) {
          requestSignal.throwIfAborted();
          const { done, value } = await reader.read();
          if (done) break;
          bytes += value.byteLength;
          if (bytes > maxBytes) throw new RegistryError('RESPONSE_TOO_LARGE', false);
          try { content += decoder.decode(value, { stream: true }); }
          catch { throw new RegistryError('INVALID_RESPONSE', false); }
        }
        try { return content + decoder.decode(); }
        catch { throw new RegistryError('INVALID_RESPONSE', false); }
      } finally {
        await reader.cancel().catch(() => undefined);
        reader.releaseLock();
      }
    } catch (error) {
      if (signal?.aborted) throw new RegistryError('CANCELLED', false);
      if (error instanceof RegistryError) throw error;
      throw new RegistryError('NETWORK_ERROR', true);
    } finally {
      clearTimeout(timer);
    }
  }
}
