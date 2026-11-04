import { lstat, mkdir, open, realpath } from 'node:fs/promises';
import { constants } from 'node:fs';
import { join } from 'node:path';
import { resolveDshHome } from '@deepseek-ai/dsh-home-paths';
import { z } from 'zod';
import { RegistryAdapter } from './registry-adapter.js';
import type { FavoriteList, FavoriteSkill } from './types.js';

export const favoriteLimits = { items: 500, bytes: 8 * 1024 * 1024 } as const;
const coordinate = z.string().min(1).max(256).refine(value => value !== '.' && value !== '..'
  // eslint-disable-next-line no-control-regex -- Coordinates cannot contain transport control characters.
  && !/[\u0000-\u001f\u007f]/u.test(value));
const favoriteSchema = z.object({
  namespace: coordinate, slug: coordinate,
  displayName: z.string().max(256).optional(), summary: z.string().max(1024).optional(),
}).strict();
const storedFavoriteSchema = favoriteSchema.extend({ registry: z.string().min(1).max(2048) });
const documentSchema = z.object({ version: z.literal(1), items: z.array(storedFavoriteSchema).max(favoriteLimits.items) }).strict();
type StoredFavorite = z.infer<typeof storedFavoriteSchema>;
type Document = z.infer<typeof documentSchema>;
type Reason = 'INVALID_INPUT' | 'UNAVAILABLE' | 'INVALID_STORE' | 'LIMIT_REACHED';
export class FavoritesError extends Error {
  constructor(readonly reason: Reason) { super(reason); this.name = 'FavoritesError'; }
}
class ContextChanged extends Error {
  constructor(readonly original: unknown) { super('Favorites context changed'); }
}
function missing(error: unknown): boolean {
  return error !== null && typeof error === 'object' && 'code' in error && error.code === 'ENOENT';
}
function sourceIdentity(source: string, reason: Reason): string {
  try {
    const normalized = new RegistryAdapter(source).source;
    if (normalized.length > 2048) throw new Error('Too long');
    return normalized;
  } catch { throw new FavoritesError(reason); }
}
export function validateFavoriteSkill(input: unknown): FavoriteSkill {
  const parsed = favoriteSchema.safeParse(input);
  if (!parsed.success) throw new FavoritesError('INVALID_INPUT');
  return parsed.data;
}
function key(item: FavoriteSkill): string { return JSON.stringify([item.namespace, item.slug]); }
function project(items: StoredFavorite[], registry: string): FavoriteList {
  return { items: items.filter(item => item.registry === registry).map(({ namespace, slug, displayName, summary }) => ({
    namespace, slug, ...(displayName === undefined ? {} : { displayName }), ...(summary === undefined ? {} : { summary }),
  })) };
}

/** User preferences only: no inventory, skill directory, or Registry mutation. */
export class FavoritesStore {
  constructor(private readonly home?: string) {}

  async list(registry: string, assertCurrent: () => void = () => {}): Promise<FavoriteList> {
    const source = sourceIdentity(registry, 'INVALID_INPUT');
    const file = await this.location(false);
    const document = file ? await this.read(file) : { version: 1, items: [] };
    assertCurrent();
    return project(document.items, source);
  }

  async set(registry: string, input: FavoriteSkill, favorite: boolean, assertCurrent: () => void = () => {}): Promise<FavoriteList> {
    const source = sourceIdentity(registry, 'INVALID_INPUT');
    const parsed = favoriteSchema.safeParse(input);
    if (!parsed.success || typeof favorite !== 'boolean') throw new FavoritesError('INVALID_INPUT');
    const check = () => { try { assertCurrent(); } catch (error) { throw new ContextChanged(error); } };
    try {
      check();
      const file = await this.location(true);
      if (!file) throw new FavoritesError('UNAVAILABLE');
      // Import lazily: an unavailable optional preference dependency must not disable inventory operations.
      const { withFileLock, writeFileAtomic } = await import('@deepseek-ai/dsh-atomic-write');
      return await withFileLock(file, async () => {
        check();
        if (await this.location(false) !== file) throw new FavoritesError('INVALID_STORE');
        const document = await this.read(file);
        const identity = key(parsed.data);
        const existing = document.items.findIndex(item => item.registry === source && key(item) === identity);
        let items = document.items;
        if (favorite) {
          if (existing < 0 && items.length >= favoriteLimits.items) throw new FavoritesError('LIMIT_REACHED');
          const next = { registry: source, ...parsed.data };
          items = existing < 0 ? [next, ...items] : items.map((item, index) => index === existing ? next : item);
        } else if (existing >= 0) items = items.filter((_, index) => index !== existing);
        const content = JSON.stringify({ version: 1, items }) + '\n';
        if (Buffer.byteLength(content) > favoriteLimits.bytes) throw new FavoritesError('LIMIT_REACHED');
        check();
        if (JSON.stringify(document.items) !== JSON.stringify(items)) await writeFileAtomic(file, content, { mode: 0o600, dirMode: 0o700 });
        return project(items, source);
      }, { waitMs: 2000 });
    } catch (error) {
      if (error instanceof ContextChanged) throw error.original;
      if (error instanceof FavoritesError) throw error;
      throw new FavoritesError('UNAVAILABLE');
    }
  }

  private async location(create: boolean): Promise<string | undefined> {
    try {
      const home = this.home ?? resolveDshHome();
      if (create) await mkdir(home, { recursive: true, mode: 0o700 });
      const canonical = await realpath(home);
      if (!(await lstat(canonical)).isDirectory()) throw new FavoritesError('INVALID_STORE');
      const directory = join(canonical, 'skillhub');
      if (create) {
        try { await mkdir(directory, { mode: 0o700 }); }
        catch (error) { if (!(error && typeof error === 'object' && 'code' in error && error.code === 'EEXIST')) throw error; }
      }
      const stat = await lstat(directory);
      if (!stat.isDirectory() || stat.isSymbolicLink()) throw new FavoritesError('INVALID_STORE');
      return join(directory, 'favorites.json');
    } catch (error) {
      if (!create && missing(error)) return undefined;
      if (error instanceof FavoritesError) throw error;
      throw new FavoritesError('UNAVAILABLE');
    }
  }

  private async read(file: string): Promise<Document> {
    try {
      const before = await lstat(file);
      if (!before.isFile() || before.isSymbolicLink() || before.size > favoriteLimits.bytes) throw new FavoritesError('INVALID_STORE');
      const handle = await open(file, constants.O_RDONLY | constants.O_NOFOLLOW);
      try {
        const opened = await handle.stat();
        if (!opened.isFile() || opened.dev !== before.dev || opened.ino !== before.ino || opened.size > favoriteLimits.bytes) throw new FavoritesError('INVALID_STORE');
        const bytes = await handle.readFile();
        if (bytes.length > favoriteLimits.bytes) throw new FavoritesError('INVALID_STORE');
        let raw: unknown;
        try { raw = JSON.parse(bytes.toString('utf8')); } catch { throw new FavoritesError('INVALID_STORE'); }
        const result = documentSchema.safeParse(raw);
        if (!result.success) throw new FavoritesError('INVALID_STORE');
        const seen = new Set<string>();
        for (const item of result.data.items) {
          if (sourceIdentity(item.registry, 'INVALID_STORE') !== item.registry) throw new FavoritesError('INVALID_STORE');
          const identity = JSON.stringify([item.registry, item.namespace, item.slug]);
          if (seen.has(identity)) throw new FavoritesError('INVALID_STORE');
          seen.add(identity);
        }
        return result.data;
      } finally { await handle.close(); }
    } catch (error) {
      if (missing(error)) return { version: 1, items: [] };
      if (error instanceof FavoritesError) throw error;
      throw new FavoritesError('UNAVAILABLE');
    }
  }
}
