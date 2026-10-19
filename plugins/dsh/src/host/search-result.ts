import { z } from 'zod';
import type { SearchInstallationStatus } from './types.js';
export interface ConversationSearchResult {
  schemaVersion: 1; kind: 'skillhub-search'; query: string; sourceRevision: string;
  sourceLabel: string; createdAt: string; total: number; returned: number; limit: number;
  items: { namespace: string; slug: string; latestVersion: string; summary: string; selectionRef: string; installation?: SearchInstallationStatus }[];
}

const source = z.string().max(2048).refine(value => {
  try {
    const url = new URL(value);
    return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password && !url.search && !url.hash;
  } catch { return false; }
});
const schema = z.object({
  schemaVersion: z.literal(1), kind: z.literal('skillhub-search'),
  query: z.string().max(1000), sourceRevision: z.string().uuid(), sourceLabel: source,
  createdAt: z.string().datetime(), total: z.number().int().nonnegative(),
  returned: z.number().int().nonnegative().max(20), limit: z.number().int().positive().max(20),
  items: z.array(z.object({
    namespace: z.string().min(1), slug: z.string().min(1), latestVersion: z.string().min(1),
    summary: z.string(), selectionRef: z.string().uuid(),
    installation: z.object({
      checkedAt: z.string().datetime(), complete: z.boolean(),
      unavailable: z.array(z.enum(['user', 'project'])).max(2),
      records: z.array(z.object({
        scope: z.enum(['user', 'project']), version: z.string().min(1),
        state: z.enum(['present', 'missing', 'metadata-invalid', 'mismatch', 'unreadable']),
      }).strict()),
    }).strict().optional(),
  }).strict()).max(20),
}).strict().refine(value => value.items.length === value.returned
  && value.returned <= value.limit && value.returned <= value.total);

/** Persisted presentation metadata is untrusted and must not supply arbitrary actions. */
export function parseSearchResult(value: unknown): ConversationSearchResult | undefined {
  const result = schema.safeParse(value);
  return result.success ? result.data : undefined;
}
