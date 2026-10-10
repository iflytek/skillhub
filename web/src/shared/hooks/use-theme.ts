import { useCallback, useState } from 'react'
import {
  applyTheme,
  DEFAULT_THEME,
  readStoredTheme,
  saveTheme,
  type Theme,
} from '@/shared/lib/theme'

export function useTheme() {
  const [theme, setThemeState] = useState<Theme>(() => readStoredTheme() ?? DEFAULT_THEME)

  const setTheme = useCallback((nextTheme: Theme) => {
    applyTheme(nextTheme)
    saveTheme(nextTheme)
    setThemeState(nextTheme)
  }, [])

  const toggleTheme = useCallback(() => {
    setTheme(theme === 'light' ? 'dark' : 'light')
  }, [setTheme, theme])

  return { theme, setTheme, toggleTheme }
}
