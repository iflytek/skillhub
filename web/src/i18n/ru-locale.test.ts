import { describe, expect, it } from 'vitest'
import en from './locales/en.json'
import ru from './locales/ru.json'

function leafKeys(value: unknown, prefix = ''): string[] {
  if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
    return Object.entries(value as Record<string, unknown>).flatMap(([key, child]) =>
      leafKeys(child, prefix ? `${prefix}.${key}` : key),
    )
  }
  return [prefix]
}

function placeholders(text: string): string[] {
  return [...text.matchAll(/\{\{[^}]+\}\}/g)].map((match) => match[0]).sort()
}

describe('russian locale', () => {
  it('mirrors the english key tree', () => {
    expect(leafKeys(ru).sort()).toEqual(leafKeys(en).sort())
  })

  it('preserves interpolation placeholders', () => {
    const enMap = Object.fromEntries(leafKeys(en).map((key) => {
      const parts = key.split('.')
      let cursor: unknown = en
      for (const part of parts) {
        cursor = (cursor as Record<string, unknown>)[part]
      }
      return [key, String(cursor)]
    }))
    const mismatches: string[] = []
    for (const key of leafKeys(ru)) {
      const parts = key.split('.')
      let cursor: unknown = ru
      for (const part of parts) {
        cursor = (cursor as Record<string, unknown>)[part]
      }
      if (placeholders(String(cursor)).join() !== placeholders(enMap[key] ?? '').join()) {
        mismatches.push(key)
      }
    }
    expect(mismatches).toEqual([])
  })

  it('translates core navigation labels', () => {
    expect(ru.nav.home).not.toBe(en.nav.home)
    expect(ru.nav.home.length).toBeGreaterThan(0)
    expect(ru.login.title.length).toBeGreaterThan(0)
  })
})
