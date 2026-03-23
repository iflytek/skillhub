import { ApiError } from '@/api/client'

export function shouldFallbackVisibleLabelsError(error: unknown) {
  return error instanceof ApiError
}
