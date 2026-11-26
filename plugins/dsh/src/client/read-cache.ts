import type { QueryClient } from '@tanstack/react-query';

/** Read-only views may reuse data; write authorization is always checked separately. */
export const readCacheOptions = {
  staleTime: 60_000,
  refetchOnWindowFocus: false,
  refetchOnReconnect: false,
} as const;

/** Detail readers store their source in a JSON-encoded cache identity. */
function detailIdentity(part: unknown): unknown[] | undefined {
  if (typeof part !== 'string' || !part.startsWith('[')) return;
  try {
    const parsed: unknown = JSON.parse(part);
    return Array.isArray(parsed) ? parsed : undefined;
  } catch { return undefined; }
}

export function queryHasSource(key: readonly unknown[], source: string): boolean {
  return key.some(part => part === source || detailIdentity(part)?.includes(source));
}

/** Refresh the Registry views affected by publishing or deleting a skill. Local installs keep their own lifecycle. */
export function invalidateRegistrySkill(client: QueryClient, revision: string, namespace: string, slug: string): void {
  for (const key of [
    ['account-owned', revision, namespace], ['account-team', revision, namespace],
    ['skillhub-catalog', revision], ['skillhub-detail', revision, namespace, slug],
  ]) void client.invalidateQueries({ queryKey: key });

  void client.invalidateQueries({ queryKey: ['skillhub-module'], predicate: ({ queryKey }) => {
    if (queryKey[2] !== 'versions') return false;
    const identity = detailIdentity(queryKey[1]);
    return identity?.[1] === revision && identity[2] === namespace && identity[3] === slug;
  } });
}
