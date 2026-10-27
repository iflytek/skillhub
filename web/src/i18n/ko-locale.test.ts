import { createInstance } from 'i18next'
import { describe, expect, it } from 'vitest'
import en from './locales/en.json'
import ko from './locales/ko.json'

function flatten(value: unknown, prefix = ''): Record<string, string> {
  if (typeof value === 'string') return { [prefix]: value }
  return Object.fromEntries(Object.entries(value as Record<string, unknown>).flatMap(([key, child]) =>
    Object.entries(flatten(child, prefix ? `${prefix}.${key}` : key)),
  ))
}

const pluralSuffix = /_(zero|one|two|few|many|other)$/
const english = flatten(en)
const korean = flatten(ko)
const normalizedKeys = (keys: string[]) => [...new Set(keys.map((key) => key.replace(pluralSuffix, '_plural')))].sort()
const placeholders = (text: string) => [...text.matchAll(/\{\{[^}]+\}\}/g)].map(([match]) => match).sort()
const tags = (text: string) => [...text.matchAll(/<\/?[\w]+\s*\/?\s*>/g)].map(([match]) => match).sort()

describe('Korean locale', () => {
  it('covers every English key, allowing language-specific plural categories', () => {
    expect(normalizedKeys(Object.keys(korean))).toEqual(normalizedKeys(Object.keys(english)))
  })

  it('preserves interpolation variables and markup without empty translations', () => {
    for (const [key, value] of Object.entries(korean)) {
      const reference = english[key] ?? english[key.replace(pluralSuffix, '_other')]
      expect(reference, key).toBeDefined()
      expect(placeholders(value), key).toEqual(placeholders(reference))
      expect(tags(value), key).toEqual(tags(reference))
      // The existing pagination suffix is deliberately empty in English.
      if (reference.trim()) expect(value.trim(), key).not.toBe('')
    }
  })

  it('keeps executable installation commands and registry placeholders intact', () => {
    expect(ko.landing.quickStart.cli.command).toBe(en.landing.quickStart.cli.command)
    expect(ko.landing.quickStart.human.command).toBe(en.landing.quickStart.human.command)
    expect(ko.landing.quickStart.human.commandTemplate).toBe(en.landing.quickStart.human.commandTemplate)
  })

  it('uses Korean plural rules for zero, one, and multiple reviews without English fallback', async () => {
    const instance = createInstance()
    await instance.init({ lng: 'ko-KR', fallbackLng: 'en', resources: { en: { translation: en }, ko: { translation: ko } } })
    for (const count of [0, 1, 2, 21, 100]) {
      const result = instance.t('skillReviews.count', { count, returnDetails: true })
      expect(result.res, String(count)).toBe(`리뷰 ${count}개`)
      expect(result.usedLng).toBe('ko')
    }
  })
})
