import { describe, expect, it, vi } from 'vitest'
import { resolveTokenExpiresAt, toLocalDateTimeInputValue } from './token-expiration'

describe('resolveTokenExpiresAt', () => {
  it('returns undefined for never-expiring tokens', () => {
    expect(resolveTokenExpiresAt('never')).toBeUndefined()
  })

  it('returns the provided custom expiration value', () => {
    expect(resolveTokenExpiresAt('custom', '2026-03-17T12:30')).toBe('2026-03-17T12:30')
  })

  it('calculates future expiration values for preset durations', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-03-17T12:34:56'))

    expect(resolveTokenExpiresAt('7d')).toBe('2026-03-24T12:34')
    expect(resolveTokenExpiresAt('30d')).toBe('2026-04-16T12:34')
    expect(resolveTokenExpiresAt('90d')).toBe('2026-06-15T12:34')

    vi.useRealTimers()
  })
})

describe('toLocalDateTimeInputValue', () => {
  it('formats date values as yyyy-MM-ddTHH:mm', () => {
    expect(toLocalDateTimeInputValue(new Date('2026-03-17T05:06:07'))).toBe('2026-03-17T05:06')
  })
})
