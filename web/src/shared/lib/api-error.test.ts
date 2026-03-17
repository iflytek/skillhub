import { beforeEach, describe, expect, it, vi } from 'vitest'

const errorSpy = vi.fn()

vi.mock('./toast', () => ({
  toast: {
    error: errorSpy,
  },
}))

describe('ApiError', () => {
  beforeEach(() => {
    errorSpy.mockReset()
  })

  it('keeps the provided server message key', async () => {
    const { ApiError } = await import('./api-error')

    const error = new ApiError('apiError.unknown', 400, 'server message', 'error.server.key')

    expect(error.serverMessageKey).toBe('error.server.key')
  })
})

describe('handleApiError', () => {
  beforeEach(() => {
    errorSpy.mockReset()
    vi.stubGlobal('window', { location: { href: '' } })
  })

  it('redirects to login for unauthorized api errors', async () => {
    const { ApiError, handleApiError } = await import('./api-error')

    handleApiError(new ApiError('apiError.unauthorized', 401))

    expect(errorSpy).toHaveBeenCalled()
    expect(window.location.href).toBe('/login')
  })

  it('falls back to the server message for non-standard api errors', async () => {
    const { ApiError, handleApiError } = await import('./api-error')

    handleApiError(new ApiError('apiError.unknown', 422, 'Server said no'))

    expect(errorSpy).toHaveBeenLastCalledWith('Server said no')
  })
})
