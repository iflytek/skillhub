export type TokenExpirationMode = 'never' | '7d' | '30d' | '90d' | 'custom'

export function resolveTokenExpiresAt(mode: TokenExpirationMode, customExpiresAt?: string) {
  if (mode === 'never') {
    return undefined
  }

  if (mode === 'custom') {
    return customExpiresAt || undefined
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
  return toLocalDateTimeInputValue(next)
}

export function toLocalDateTimeInputValue(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hours}:${minutes}`
}
