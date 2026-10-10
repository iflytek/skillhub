export type Theme = 'light' | 'dark'

export const THEME_STORAGE_KEY = 'skillhub-theme'
export const DEFAULT_THEME: Theme = 'light'

interface ThemeStorage {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
}

export function isTheme(value: unknown): value is Theme {
  return value === 'light' || value === 'dark'
}

export function readStoredTheme(storage: ThemeStorage | undefined = getBrowserStorage()): Theme | null {
  if (!storage) return null

  try {
    const value = storage.getItem(THEME_STORAGE_KEY)
    return isTheme(value) ? value : null
  } catch {
    return null
  }
}

export function applyTheme(theme: Theme, root: HTMLElement = document.documentElement) {
  root.classList.toggle('dark', theme === 'dark')
  root.dataset.theme = theme
  root.style.colorScheme = theme
}

export function initializeTheme(root: HTMLElement = document.documentElement): Theme {
  const theme = readStoredTheme() ?? DEFAULT_THEME
  applyTheme(theme, root)
  return theme
}

export function saveTheme(theme: Theme, storage: ThemeStorage | undefined = getBrowserStorage()) {
  if (!storage) return

  try {
    storage.setItem(THEME_STORAGE_KEY, theme)
  } catch {
    // Storage can be unavailable in private browsing or hardened browser contexts.
  }
}

function getBrowserStorage(): Storage | undefined {
  if (typeof window === 'undefined') return undefined

  try {
    return window.localStorage
  } catch {
    return undefined
  }
}
