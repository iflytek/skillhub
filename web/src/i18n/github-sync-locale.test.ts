import { describe, expect, it } from 'vitest'
import en from './locales/en.json'
import ko from './locales/ko.json'
import ru from './locales/ru.json'
import zh from './locales/zh.json'

describe('GitHub sync status locales', () => {
  it('keeps the status and failure code keys aligned across locales', () => {
    const expected = Object.keys(zh.githubSync.codes).sort()
    expect(Object.keys(en.githubSync.codes).sort()).toEqual(expected)
    expect(Object.keys(ru.githubSync.codes).sort()).toEqual(expected)
    expect(Object.keys(ko.githubSync.codes).sort()).toEqual(expected)
  })

  it('describes known acquisition and archive failures for administrators', () => {
    for (const code of [
      'TREE_INCOMPLETE', 'SUBMODULE', 'SYMLINK', 'PACKAGE_TOO_LARGE',
      'MISSING_SKILL_MD', 'PACKAGE_ACQUISITION_FAILED', 'PACKAGE_DELIVERY_FAILED',
      'PACKAGE_STRUCTURE_INVALID', 'LICENSE_EVIDENCE_INVALID', 'NO_VALID_SKILL',
    ] as const) {
      expect(zh.githubSync.codes[code]).toBeTruthy()
      expect(zh.githubSync.codes[code]).not.toBe(code)
      expect(en.githubSync.codes[code]).toBeTruthy()
      expect(ru.githubSync.codes[code]).toBeTruthy()
      expect(ko.githubSync.codes[code]).toBeTruthy()
    }
  })
})
