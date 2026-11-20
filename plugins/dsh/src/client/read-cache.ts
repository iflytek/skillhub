/** Read-only views may reuse data; write authorization is always checked separately. */
export const readCacheOptions = {
  staleTime: 60_000,
  refetchOnWindowFocus: false,
  refetchOnReconnect: false,
} as const;

/** Detail readers store their source in a JSON-encoded cache identity. */
export function queryHasSource(key: readonly unknown[], source: string): boolean {
  return key.some(part => {
    if (part === source) return true;
    if (typeof part !== 'string' || !part.startsWith('[')) return false;
    try {
      const identity: unknown = JSON.parse(part);
      return Array.isArray(identity) && identity.includes(source);
    } catch { return false; }
  });
}
