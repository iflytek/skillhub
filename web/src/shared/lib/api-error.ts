import i18n from '@/i18n/config'
import { toast } from './toast'

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

export function handleApiError(error: unknown): void {
  if (!(error instanceof ApiError)) {
    toast.error(i18n.t('apiError.unknown'))
    return
  }

  const { status } = error

  if (status === 401) {
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
