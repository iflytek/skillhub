/** Read-only views may reuse data; write authorization is always checked separately. */
export const readCacheOptions = {
  staleTime: 60_000,
  refetchOnWindowFocus: false,
  refetchOnReconnect: false,
} as const;
