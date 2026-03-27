import i18n from '@/i18n/config'
import { toast } from './toast'

const ACCOUNT_DISABLED_REASON = 'accountDisabled'

function resolveLocalizedMessage(message?: string): string | undefined {
  if (!message) {
    return undefined
  }

  return i18n.exists(message) ? i18n.t(message) : message
}

export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public serverMessage?: string,
    public serverMessageKey?: string,
  ) {
    super(resolveLocalizedMessage(message) || message)
    this.name = 'ApiError'
    this.serverMessage = resolveLocalizedMessage(serverMessage) || serverMessage
    this.serverMessageKey = serverMessageKey
  }
}

function isAccountDisabledError(error: ApiError): boolean {
  const accountDisabledMessages = [
    i18n.t('apiError.auth.accountDisabled'),
    i18n.getFixedT('en')('apiError.auth.accountDisabled'),
    i18n.getFixedT('zh')('apiError.auth.accountDisabled'),
  ]
  const normalizedServerMessage = (error.serverMessage ?? '').toLowerCase()
  const normalizedMessage = error.message.toLowerCase()

  return error.serverMessageKey === 'error.auth.local.accountDisabled'
    || error.serverMessage === 'error.auth.local.accountDisabled'
    || accountDisabledMessages.includes(error.serverMessage ?? '')
    || accountDisabledMessages.includes(error.message)
    || normalizedServerMessage.includes('disabled')
    || normalizedMessage.includes('disabled')
    || (error.serverMessage ?? '').includes('禁琨')
    || error.message.includes('禁用')
}

export function handleApiError(error: unknown): void {
  if (!(error instanceof ApiError)) {
    toast.error(i18n.t('apiError.unknown'))
    return
  }

  const { status } = error

  // Network error (status 0) - fetch failed without receiving a response
  if (status === 0) {
    toast.error(i18n.t('apiError.networkError'))
    return
  }

  if (status === 401) {
    if (isAccountDisabledError(error)) {
      window.location.href = `/login?reason=${ACCOUNT_DISABLED_REASON}`
      return
    }
    toast.error(i18n.t('apiError.unauthorized'))
    window.location.href = '/login'
    return
  }

  if (status === 403) {
    toast.error(i18n.t('apiError.forbidden'))
    return
  }

  if (status === 404) {
    toast.error(i18n.t('apiError.notFound'))
    return
  }

  if (status >= 500) {
    toast.error(i18n.t('apiError.serverError'))
    return
  }

  // For other errors, show the server message if available
  toast.error(error.serverMessage || i18n.t('apiError.unknown'))
}
