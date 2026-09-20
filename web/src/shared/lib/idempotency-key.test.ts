/** @vitest-environment node */

import { afterEach, describe, expect, it, vi } from 'vitest'
import { newIdempotencyKey } from './idempotency-key'

describe('newIdempotencyKey', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('uses crypto.randomUUID when it is available', () => {
    vi.stubGlobal('crypto', { randomUUID: () => 'request-1' })

    expect(newIdempotencyKey()).toBe('request-1')
  })

  it('uses crypto.getRandomValues when randomUUID is unavailable', () => {
    vi.stubGlobal('crypto', {
      getRandomValues: (bytes: Uint8Array) => {
        bytes.fill(1)
        return bytes
      },
    })

    expect(newIdempotencyKey()).toBe('01010101010101010101010101010101')
  })

  it('falls back to Math.random when Web Crypto is unavailable', () => {
    vi.stubGlobal('crypto', undefined)
    vi.spyOn(Math, 'random').mockReturnValue(0)

    expect(newIdempotencyKey()).toBe('00000000000000000000000000000000')
  })
})
