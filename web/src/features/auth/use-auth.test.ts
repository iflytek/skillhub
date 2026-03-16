import { describe, expect, it } from 'vitest'
import { getAuthQueryOptions } from './use-auth'

describe('getAuthQueryOptions', () => {
  it('keeps auth state fresh so role changes are picked up promptly', () => {
    const options = getAuthQueryOptions(true)

    expect(options.queryKey).toEqual(['auth', 'me'])
    expect(options.staleTime).toBe(0)
    expect(options.refetchOnWindowFocus).toBe(true)
    expect(options.refetchOnReconnect).toBe(true)
    expect(options.refetchInterval).toBe(60_000)
    expect(options.enabled).toBe(true)
  })
})
