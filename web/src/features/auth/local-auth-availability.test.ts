import { describe, expect, it } from 'vitest'
import type { AuthMethod } from '@/api/types'
import { getLocalAuthAvailability } from './local-auth-availability'

function method(id: string, methodType: string): AuthMethod {
  return { id, methodType, provider: 'local', displayName: id, actionUrl: '/' }
}

describe('getLocalAuthAvailability', () => {
  it('keeps local flows available until the catalog has loaded', () => {
    expect(getLocalAuthAvailability(undefined)).toEqual({ login: true, registration: true })
  })

  it('keeps local flows available when the catalog request fails', () => {
    expect(getLocalAuthAvailability([], true)).toEqual({ login: true, registration: true })
  })

  it('follows the advertised local methods', () => {
    expect(getLocalAuthAvailability([
      method('local-password', 'PASSWORD'),
      method('local-registration', 'PASSWORD_REGISTRATION'),
    ])).toEqual({ login: true, registration: true })
    expect(getLocalAuthAvailability([method('local-password', 'PASSWORD')]))
      .toEqual({ login: true, registration: false })
    expect(getLocalAuthAvailability([method('oauth-oidc', 'OAUTH_REDIRECT')]))
      .toEqual({ login: false, registration: false })
  })
})
