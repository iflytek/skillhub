/** @vitest-environment jsdom */

import { beforeEach, describe, expect, it } from 'vitest'
import {
  applyTheme,
  DEFAULT_THEME,
  initializeTheme,
  readStoredTheme,
  saveTheme,
  THEME_STORAGE_KEY,
} from './theme'

describe('theme preference', () => {
  beforeEach(() => {
    window.localStorage.clear()
    document.documentElement.classList.remove('dark')
    document.documentElement.removeAttribute('data-theme')
    document.documentElement.style.colorScheme = ''
  })

  it('uses light when the browser has no saved preference', () => {
    expect(initializeTheme()).toBe(DEFAULT_THEME)
    expect(document.documentElement.classList.contains('dark')).toBe(false)
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(document.documentElement.style.colorScheme).toBe('light')
  })

  it('restores a valid browser-local preference before render', () => {
    window.localStorage.setItem(THEME_STORAGE_KEY, 'dark')

    expect(initializeTheme()).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(document.documentElement.dataset.theme).toBe('dark')
  })

  it('ignores invalid and inaccessible storage values', () => {
    expect(readStoredTheme({ getItem: () => 'system', setItem: () => undefined })).toBeNull()
    expect(readStoredTheme({
      getItem: () => {
        throw new Error('blocked')
      },
      setItem: () => undefined,
    })).toBeNull()
  })

  it('applies and saves only the selected frontend theme', () => {
    applyTheme('dark')
    saveTheme('dark')

    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark')
  })

  it('ignores storage write failures', () => {
    expect(() => saveTheme('dark', {
      getItem: () => null,
      setItem: () => {
        throw new Error('blocked')
      },
    })).not.toThrow()
  })
})
