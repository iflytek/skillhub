import { toLocalDateTimeInputValue } from '@/shared/lib/date-time'

export type TokenExpirationMode = 'never' | '7d' | '30d' | '90d' | 'custom'

export function resolveTokenExpiresAt(mode: TokenExpirationMode, customExpiresAt?: string) {
  if (mode === 'never') {
    return undefined
  }

  if (mode === 'custom') {
    return customExpiresAt ? new Date(customExpiresAt).toISOString() : undefined
  }

  const next = new Date()
  if (mode === '7d') {
    next.setDate(next.getDate() + 7)
  } else if (mode === '30d') {
    next.setDate(next.getDate() + 30)
  } else if (mode === '90d') {
    next.setDate(next.getDate() + 90)
  }
  next.setSeconds(0, 0)
  return next.toISOString()
}

export { toLocalDateTimeInputValue }
