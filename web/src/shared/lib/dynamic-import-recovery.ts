const RELOAD_GUARD_KEY = 'skillhub:dynamic-import-reload'

function resolveErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message
  }
  return String(error ?? '')
}

export function isDynamicImportFetchError(error: unknown): boolean {
  const message = resolveErrorMessage(error)
  return (error instanceof Error && error.name === 'ChunkLoadError')
    || message.includes('Failed to fetch dynamically imported module')
    || message.includes('error loading dynamically imported module')
    || message.includes('Importing a module script failed')
    || message.includes('ChunkLoadError')
}

export function recoverFromDynamicImportError(error: unknown): boolean {
  if (typeof window === 'undefined' || !isDynamicImportFetchError(error)) {
    return false
  }

  let sessionStorage: Storage
  try {
    sessionStorage = window.sessionStorage
    if (sessionStorage.getItem(RELOAD_GUARD_KEY) === '1') {
      return false
    }
    sessionStorage.setItem(RELOAD_GUARD_KEY, '1')
  } catch {
    return false
  }

  window.location.reload()
  return true
}

export function clearDynamicImportReloadGuard(): void {
  if (typeof window === 'undefined') {
    return
  }
  try {
    window.sessionStorage.removeItem(RELOAD_GUARD_KEY)
  } catch {
    // Session storage can be unavailable in restricted browsing contexts.
  }
}
