import { describe, expect, it } from 'vitest'
import en from './locales/en.json'
import ru from './locales/ru.json'
import zh from './locales/zh.json'

describe('suite locales', () => {
  it('keeps Suite keys aligned in every supported locale', () => {
    const expected = Object.keys(zh.suite).sort()
    expect(Object.keys(en.suite).sort()).toEqual(expected)
    expect(Object.keys(ru.suite).sort()).toEqual(expected)
    expect(en.nav.suites).toBeTruthy()
    expect(ru.nav.suites).toBeTruthy()
    expect(zh.nav.suites).toBeTruthy()
  })
})
