import { describe, expect, it } from 'vitest'
import en from './locales/en.json'
import ko from './locales/ko.json'
import ru from './locales/ru.json'
import zh from './locales/zh.json'

const dispositionCodes = [
  'NOT_CHECKED', 'NO_VALID_SKILL', 'VALIDATION_FAILED', 'SOURCE_UNREADABLE',
  'EXCLUDED', 'DISABLED', 'CONFLICT', 'REVIEW_PENDING', 'WAITING_CAPACITY',
] as const

describe('deep discovery drawer disposition', () => {
  it('describes every repository detail result in each locale', () => {
    for (const locale of [zh, en, ru, ko]) {
      expect(locale.deepDiscovery.processingStatus).toBeTruthy()
      for (const code of dispositionCodes) {
        expect(locale.deepDiscovery.reason[code]).toBeTruthy()
      }
    }
  })

  it('describes pending review as a submitted review rather than an import failure', () => {
    expect(zh.deepDiscovery.reason.REVIEW_PENDING).toContain('已提交审核')
    expect(zh.deepDiscovery.processingStatus).toBe('处理状态')
  })
})
