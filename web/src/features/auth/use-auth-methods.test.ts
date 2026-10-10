import { describe, expect, it, vi } from 'vitest'
import { getAuthMethodsQueryOptions, useAuthMethods } from './use-auth-methods'

const getMethods = vi.hoisted(() => vi.fn())

vi.mock('@/api/client', () => ({ authApi: { getMethods } }))

describe('getAuthMethodsQueryOptions', () => {
  it('keeps login auth-method lookup local to the page and bypasses the global 401 redirect', () => {
    const options = getAuthMethodsQueryOptions('/dashboard')

    expect(options.queryKey).toEqual(['auth', 'methods', '/dashboard'])
    expect(options.retry).toBe(false)
    expect(options.meta).toEqual({ skipGlobalErrorHandler: true })
    expect(options.queryFn).toBeTypeOf('function')
  })

  it('does not invent provider buttons when the server is unavailable', async () => {
    getMethods.mockRejectedValueOnce(new Error('backend unavailable'))

    await expect(getAuthMethodsQueryOptions().queryFn()).rejects.toThrow('backend unavailable')
  })
})

describe('use-auth-methods module exports', () => {
  it('exports useAuthMethods hook', () => {
    expect(useAuthMethods).toBeTypeOf('function')
  })
})
