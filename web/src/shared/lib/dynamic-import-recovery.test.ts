import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  clearDynamicImportReloadGuard,
  isDynamicImportFetchError,
  recoverFromDynamicImportError,
} from './dynamic-import-recovery'

const values = new Map<string, string>()
const reload = vi.fn()
const sessionStorage = {
  get length() {
    return values.size
  },
  clear: vi.fn(() => values.clear()),
  getItem: vi.fn((key: string) => values.get(key) ?? null),
  key: vi.fn((index: number) => Array.from(values.keys())[index] ?? null),
  removeItem: vi.fn((key: string) => values.delete(key)),
  setItem: vi.fn((key: string, value: string) => values.set(key, value)),
} satisfies Storage

describe('dynamic import recovery', () => {
  beforeEach(() => {
    values.clear()
    reload.mockClear()
    vi.stubGlobal('window', {
      location: { reload },
      sessionStorage,
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it.each([
    'Failed to fetch dynamically imported module: /assets/login.js',
    'error loading dynamically imported module: /assets/login.js',
    'Importing a module script failed',
    'ChunkLoadError: Loading chunk 42 failed',
  ])('recognizes a stale dynamic import error: %s', (message) => {
    expect(isDynamicImportFetchError(new Error(message))).toBe(true)
  })

  it('ignores unrelated errors', () => {
    expect(isDynamicImportFetchError(new Error('Request failed with status 500'))).toBe(false)
  })

  it('recognizes errors whose name is ChunkLoadError', () => {
    const error = new Error('Loading chunk 42 failed')
    error.name = 'ChunkLoadError'

    expect(isDynamicImportFetchError(error)).toBe(true)
  })

  it('reloads only once while the recovery guard is active', () => {
    const error = new Error('Failed to fetch dynamically imported module')

    expect(recoverFromDynamicImportError(error)).toBe(true)
    expect(recoverFromDynamicImportError(error)).toBe(false)
    expect(recoverFromDynamicImportError(error)).toBe(false)
    expect(reload).toHaveBeenCalledTimes(1)
  })

  it('allows recovery again after a dynamic import succeeds', () => {
    const error = new Error('Failed to fetch dynamically imported module')

    expect(recoverFromDynamicImportError(error)).toBe(true)
    clearDynamicImportReloadGuard()
    expect(recoverFromDynamicImportError(error)).toBe(true)
    expect(reload).toHaveBeenCalledTimes(2)
  })

  it('does not mask the original import error when session storage is unavailable', () => {
    vi.stubGlobal('window', {
      location: { reload },
      get sessionStorage() {
        throw new DOMException('Access denied', 'SecurityError')
      },
    })

    const error = new Error('Failed to fetch dynamically imported module')

    expect(recoverFromDynamicImportError(error)).toBe(false)
    expect(() => clearDynamicImportReloadGuard()).not.toThrow()
    expect(reload).not.toHaveBeenCalled()
  })
})
