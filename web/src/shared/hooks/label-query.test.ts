import { describe, expect, it } from 'vitest'
import { ApiError } from '@/api/client'
import { shouldFallbackVisibleLabelsError } from './label-query'

describe('shouldFallbackVisibleLabelsError', () => {
  it('falls back to an empty label list for API failures', () => {
    expect(shouldFallbackVisibleLabelsError(new ApiError('需要先登录', 401))).toBe(true)
    expect(shouldFallbackVisibleLabelsError(new ApiError('server error', 500))).toBe(true)
  })

  it('does not swallow non-API failures', () => {
    expect(shouldFallbackVisibleLabelsError(new Error('boom'))).toBe(false)
  })
})
