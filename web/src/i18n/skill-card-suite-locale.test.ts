import { describe, expect, it } from 'vitest'
import en from './locales/en.json'
import ru from './locales/ru.json'
import zh from './locales/zh.json'

describe('Skill card Suite membership locales', () => {
  it('defines the passive membership label and explanation in every supported locale', () => {
    expect(zh.skillCard.relatedSuites).toBe('关联套件')
    for (const locale of [zh, en, ru]) {
      expect(locale.skillCard.relatedSuites).toBeTruthy()
      expect(locale.skillCard.relatedSuitesHint).toBeTruthy()
    }
  })
})
