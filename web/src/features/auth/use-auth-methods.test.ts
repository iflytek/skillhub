import { describe, expect, it } from 'vitest'
import { getAuthMethodsQueryOptions, useAuthMethods } from './use-auth-methods'

describe('getAuthMethodsQueryOptions', () => {
  it('keeps login auth-method lookup local to the page and bypasses the global 401 redirect', () => {
    const options = getAuthMethodsQueryOptions('/dashboard')

    expect(options.queryKey).toEqual(['auth', 'methods', '/dashboard'])
    expect(options.retry).toBe(false)
    expect(options.meta).toEqual({ skipGlobalErrorHandler: true })
    expect(options.queryFn).toBeTypeOf('function')
  })
})

describe('use-auth-methods module exports', () => {
  it('exports useAuthMethods hook', () => {
    expect(useAuthMethods).toBeTypeOf('function')
  })
})
