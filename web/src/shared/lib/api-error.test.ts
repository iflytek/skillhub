import { beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '@/i18n/config'

const errorSpy = vi.fn()

vi.mock('./toast', () => ({
  toast: {
    error: errorSpy,
  },
}))

describe('ApiError', () => {
  beforeEach(async () => {
    errorSpy.mockReset()
    await i18n.changeLanguage('zh')
  })

  it('keeps the provided server message key', async () => {
    const { ApiError } = await import('./api-error')

    const error = new ApiError('apiError.unknown', 400, 'server message', 'error.server.key')

    expect(error.serverMessageKey).toBe('error.server.key')
  })
})

describe('handleApiError', () => {
  beforeEach(async () => {
    errorSpy.mockReset()
    await i18n.changeLanguage('zh')
    vi.stubGlobal('window', { location: { href: '' } })
  })

  it('redirects to login for unauthorized api errors', async () => {
    const { ApiError, handleApiError } = await import('./api-error')

    handleApiError(new ApiError('apiError.unauthorized', 401))

    expect(errorSpy).toHaveBeenCalled()
    expect(window.location.href).toBe('/login')
  })

  it('preserves disabled-account reason when redirecting to login', async () => {
    const { ApiError, handleApiError } = await import('./api-error')

    handleApiError(new ApiError('This account has been disabled', 401, 'This account has been disabled'))

    expect(errorSpy).not.toHaveBeenCalled()
    expect(window.location.href).toBe('/login?reason=accountDisabled')
  })

  it('falls back to the server message for non-standard api errors', async () => {
    const { ApiError, handleApiError } = await import('./api-error')

    handleApiError(new ApiError('apiError.unknown', 422, 'Server said no'))

    expect(errorSpy).toHaveBeenLastCalledWith('Server said no')
  })

  it('shows the server message for 403 errors when present', async () => {
    const { ApiError, handleApiError } = await import('./api-error')

    handleApiError(new ApiError('apiError.forbidden', 403, '账号已被禁用，请联系管理员'))

    expect(errorSpy).toHaveBeenLastCalledWith('账号已被禁用，请联系管理员')
  })

  it('shows the server message for 5xx errors when present', async () => {
    const { ApiError, handleApiError } = await import('./api-error')

    handleApiError(new ApiError('apiError.serverError', 503, '目录服务器暂时不可用，请稍后重试'))

    expect(errorSpy).toHaveBeenLastCalledWith('目录服务器暂时不可用，请稍后重试')
  })

  it('falls back to generic text for 403/5xx without a server message', async () => {
    const { ApiError, handleApiError } = await import('./api-error')

    handleApiError(new ApiError('apiError.forbidden', 403))

    expect(errorSpy).toHaveBeenLastCalledWith(i18n.t('apiError.forbidden'))
  })

  it('shows network error message when status is 0 (network disconnected)', async () => {
    const { ApiError, handleApiError } = await import('./api-error')

    handleApiError(new ApiError('Network error', 0))

    expect(errorSpy).toHaveBeenLastCalledWith('网络连接失败，请检查网络')
  })

  it('shows network error message when status is 0 with timeout', async () => {
    const { ApiError, handleApiError } = await import('./api-error')

    handleApiError(new ApiError('error.request.timeout', 0))

    expect(errorSpy).toHaveBeenLastCalledWith('网络连接失败，请检查网络')
  })
})
