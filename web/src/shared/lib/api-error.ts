import i18n from '@/i18n/config'
import { toast } from './toast'

export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public serverMessage?: string,
  ) {
    super(message)
    this.name = 'ApiError'
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
