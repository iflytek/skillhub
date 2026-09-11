import { describe, expect, mock, test } from 'bun:test'
import { AuthService } from '../../../src/services/auth-service'

describe('AuthService device flow', () => {
  test('polls pending authorization, validates the token, then persists credentials', async () => {
    const setRegistry = mock(async () => {})
    const setToken = mock(async () => {})
    const sleep = mock(async () => {})
    const pollDeviceToken = mock()
      .mockResolvedValueOnce({ error: 'authorization_pending' })
      .mockResolvedValueOnce({ accessToken: 'oauth-secret', tokenType: 'Bearer' })
    const clientFactory = mock((_registry: string, token?: string) => token
      ? { whoami: async () => ({ handle: 'oauth-user', displayName: 'OAuth User' }) }
      : {
          requestDeviceCode: async () => ({
            deviceCode: 'device-secret',
            userCode: 'ABCD-2345',
            verificationUri: '/device',
            expiresIn: 60,
            interval: 2
          }),
          pollDeviceToken
        })

    const service = new AuthService(
      { setRegistry } as never,
      { setToken } as never,
      { clientFactory: clientFactory as never, sleep, now: () => 0 }
    )
    const onVerification = mock(async () => {})

    const result = await service.loginWithDeviceFlow('https://skillhub.example.com', onVerification)

    expect(result).toEqual({ handle: 'oauth-user' })
    expect(onVerification).toHaveBeenCalledWith({
      userCode: 'ABCD-2345',
      verificationUri: 'https://skillhub.example.com/device',
      expiresIn: 60
    })
    expect(sleep).toHaveBeenCalledTimes(2)
    expect(sleep).toHaveBeenCalledWith(2_000)
    expect(setRegistry).toHaveBeenCalledWith('https://skillhub.example.com')
    expect(setToken).toHaveBeenCalledWith('https://skillhub.example.com', 'oauth-secret')
  })

  test('stops at expiry without persisting credentials', async () => {
    const setRegistry = mock(async () => {})
    const setToken = mock(async () => {})
    let now = 0
    const service = new AuthService(
      { setRegistry } as never,
      { setToken } as never,
      {
        clientFactory: (() => ({
          requestDeviceCode: async () => ({
            deviceCode: 'device-secret',
            userCode: 'ABCD-2345',
            verificationUri: '/device',
            expiresIn: 1,
            interval: 1
          }),
          pollDeviceToken: async () => ({ error: 'authorization_pending' })
        })) as never,
        sleep: async (milliseconds: number) => { now += milliseconds },
        now: () => now
      }
    )

    await expect(service.loginWithDeviceFlow('https://skillhub.example.com', async () => {}))
      .rejects.toMatchObject({ message: 'device authorization expired', exitCode: 2 })
    expect(setRegistry).not.toHaveBeenCalled()
    expect(setToken).not.toHaveBeenCalled()
  })

  test('stops when the authorization server denies the request', async () => {
    const setToken = mock(async () => {})
    const service = new AuthService(
      { setRegistry: mock(async () => {}) } as never,
      { setToken } as never,
      {
        clientFactory: (() => ({
          requestDeviceCode: async () => ({
            deviceCode: 'device-secret',
            userCode: 'ABCD-2345',
            verificationUri: '/device',
            expiresIn: 60,
            interval: 1
          }),
          pollDeviceToken: async () => ({ error: 'access_denied' })
        })) as never,
        sleep: async () => {},
        now: () => 0
      }
    )

    await expect(service.loginWithDeviceFlow('https://skillhub.example.com', async () => {}))
      .rejects.toMatchObject({ message: 'device authorization failed: access_denied', exitCode: 2 })
    expect(setToken).not.toHaveBeenCalled()
  })

  test('rejects a non-positive polling interval from the registry', async () => {
    const service = new AuthService(
      { setRegistry: mock(async () => {}) } as never,
      { setToken: mock(async () => {}) } as never,
      {
        clientFactory: (() => ({
          requestDeviceCode: async () => ({
            deviceCode: 'device-secret',
            userCode: 'ABCD-2345',
            verificationUri: '/device',
            expiresIn: 60,
            interval: 0
          })
        })) as never
      }
    )

    await expect(service.loginWithDeviceFlow('https://skillhub.example.com', async () => {}))
      .rejects.toMatchObject({ message: 'registry returned invalid device authorization data', exitCode: 2 })
  })

  test('rejects a non-HTTP verification URL from the registry', async () => {
    const service = new AuthService(
      { setRegistry: mock(async () => {}) } as never,
      { setToken: mock(async () => {}) } as never,
      {
        clientFactory: (() => ({
          requestDeviceCode: async () => ({
            deviceCode: 'device-secret',
            userCode: 'ABCD-2345',
            verificationUri: 'javascript:alert(1)',
            expiresIn: 60,
            interval: 1
          })
        })) as never
      }
    )

    await expect(service.loginWithDeviceFlow('https://skillhub.example.com', async () => {}))
      .rejects.toMatchObject({ message: 'registry returned invalid device verification URL', exitCode: 2 })
  })
})
