import { describe, expect, mock, test } from 'bun:test'
import { AuthService } from '../../../src/services/auth-service'
import { CliError } from '../../../src/shared/errors'
import { EXIT } from '../../../src/shared/constants'

const REGISTRY = 'https://skillhub.example.com'

function deviceFlowClient() {
  return {
    requestDeviceCode: async () => ({
      deviceCode: 'device-secret',
      userCode: 'ABCD-2345',
      verificationUri: '/device',
      expiresIn: 60,
      interval: 1
    }),
    pollDeviceToken: async () => ({ accessToken: 'fresh-token', tokenType: 'Bearer' })
  }
}

describe('AuthService stored credential login', () => {
  test('keeps the stored credential when whoami accepts it', async () => {
    const setRegistry = mock(async () => {})
    const setToken = mock(async () => {})
    const onCredentialRejected = mock(() => {})
    const clientFactory = mock((_registry: string, token?: string) => {
      if (!token) throw new Error('device flow must not start for an accepted credential')
      return { whoami: async () => ({ handle: 'stored-user', displayName: 'Stored User' }) }
    })
    const service = new AuthService(
      { setRegistry } as never,
      { setToken } as never,
      { clientFactory: clientFactory as never }
    )

    const result = await service.loginWithStoredCredential(REGISTRY, 'stored-token', async () => {
      throw new Error('device flow must not start for an accepted credential')
    }, onCredentialRejected)

    expect(result).toEqual({ handle: 'stored-user' })
    expect(onCredentialRejected).not.toHaveBeenCalled()
    expect(setRegistry).toHaveBeenCalledWith(REGISTRY)
    expect(setToken).toHaveBeenCalledWith(REGISTRY, 'stored-token')
  })

  test('starts device flow when the server rejects the stored credential', async () => {
    const setRegistry = mock(async () => {})
    const setToken = mock(async () => {})
    const clientFactory = mock((_registry: string, token?: string) => token === 'stored-token'
      ? { whoami: async () => { throw new CliError('authentication failed', EXIT.auth, {}, 401) } }
      : token
        ? { whoami: async () => ({ handle: 'recovered-user', displayName: 'Recovered User' }) }
        : deviceFlowClient())
    const order: string[] = []
    const onCredentialRejected = mock(() => { order.push('rejected') })
    const onVerification = mock(async () => { order.push('verification') })
    const service = new AuthService(
      { setRegistry } as never,
      { setToken } as never,
      { clientFactory: clientFactory as never, sleep: async () => {}, now: () => 0 }
    )

    const result = await service.loginWithStoredCredential(
      REGISTRY,
      'stored-token',
      onVerification,
      onCredentialRejected
    )

    expect(result).toEqual({ handle: 'recovered-user' })
    expect(order).toEqual(['rejected', 'verification'])
    expect(onVerification).toHaveBeenCalledWith({
      userCode: 'ABCD-2345',
      verificationUri: REGISTRY + '/device',
      expiresIn: 60
    })
    expect(setRegistry).toHaveBeenCalledWith(REGISTRY)
    expect(setToken).toHaveBeenCalledWith(REGISTRY, 'fresh-token')
  })

  test.each([403, undefined])('does not replace a credential for HTTP %s or an unclassified auth failure', async status => {
    const setToken = mock(async () => {})
    const onVerification = mock(async () => {})
    const error = new CliError('access denied', EXIT.auth, {}, status)
    const clientFactory = mock(() => ({ whoami: async () => { throw error } }))
    const service = new AuthService(
      { setRegistry: mock(async () => {}) } as never,
      { setToken } as never,
      { clientFactory: clientFactory as never }
    )
    await expect(service.loginWithStoredCredential(REGISTRY, 'stored-token', onVerification)).rejects.toBe(error)
    expect(clientFactory).toHaveBeenCalledTimes(1)
    expect(onVerification).not.toHaveBeenCalled()
    expect(setToken).not.toHaveBeenCalled()
  })

  test('propagates a network failure without starting device flow', async () => {
    const setToken = mock(async () => {})
    const onCredentialRejected = mock(() => {})
    const onVerification = mock(async () => {})
    const service = new AuthService(
      { setRegistry: mock(async () => {}) } as never,
      { setToken } as never,
      {
        clientFactory: (() => ({
          whoami: async () => { throw new CliError('registry unreachable', EXIT.network) }
        })) as never
      }
    )

    await expect(service.loginWithStoredCredential(REGISTRY, 'stored-token', onVerification, onCredentialRejected))
      .rejects.toMatchObject({ message: 'registry unreachable', exitCode: EXIT.network })
    expect(onCredentialRejected).not.toHaveBeenCalled()
    expect(onVerification).not.toHaveBeenCalled()
    expect(setToken).not.toHaveBeenCalled()
  })

  test('propagates a non-authentication whoami failure without starting device flow', async () => {
    const setToken = mock(async () => {})
    const onCredentialRejected = mock(() => {})
    const service = new AuthService(
      { setRegistry: mock(async () => {}) } as never,
      { setToken } as never,
      {
        clientFactory: (() => ({
          whoami: async () => { throw new CliError('registry returned 500', EXIT.generic) }
        })) as never
      }
    )

    await expect(service.loginWithStoredCredential(REGISTRY, 'stored-token', async () => {}, onCredentialRejected))
      .rejects.toMatchObject({ message: 'registry returned 500', exitCode: EXIT.generic })
    expect(onCredentialRejected).not.toHaveBeenCalled()
    expect(setToken).not.toHaveBeenCalled()
  })

  test('keeps the stored credential when the fallback device flow fails', async () => {
    const setToken = mock(async () => {})
    const deleteToken = mock(async () => {})
    const onCredentialRejected = mock(() => {})
    const clientFactory = mock((_registry: string, token?: string) => token
      ? { whoami: async () => { throw new CliError('authentication failed', EXIT.auth, {}, 401) } }
      : { requestDeviceCode: async () => { throw new CliError('registry unreachable', EXIT.network) } })
    const service = new AuthService(
      { setRegistry: mock(async () => {}) } as never,
      { setToken, deleteToken } as never,
      { clientFactory: clientFactory as never }
    )

    await expect(service.loginWithStoredCredential(REGISTRY, 'stored-token', async () => {}, onCredentialRejected))
      .rejects.toMatchObject({ message: 'registry unreachable', exitCode: EXIT.network })
    expect(onCredentialRejected).toHaveBeenCalledTimes(1)
    expect(setToken).not.toHaveBeenCalled()
    expect(deleteToken).not.toHaveBeenCalled()
  })
})
