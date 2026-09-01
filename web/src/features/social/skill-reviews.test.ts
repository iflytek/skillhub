import { describe, expect, it } from 'vitest'
import en from '@/i18n/locales/en.json'
import ru from '@/i18n/locales/ru.json'
import zh from '@/i18n/locales/zh.json'
import * as componentModule from './skill-reviews'
import * as hookModule from './use-skill-reviews'

describe('skill reviews feature contract', () => {
  it('exports the detail-page component and review hooks', () => {
    expect(componentModule.SkillReviews).toBeTypeOf('function')
    expect(hookModule.useSkillReviews).toBeTypeOf('function')
    expect(hookModule.useMySkillReview).toBeTypeOf('function')
    expect(hookModule.useUpsertSkillReview).toBeTypeOf('function')
    expect(hookModule.useClearSkillReview).toBeTypeOf('function')
    expect(hookModule.useModerateSkillReview).toBeTypeOf('function')
  })

  it('keeps the review interaction copy available in every supported locale', () => {
    for (const locale of [en, zh, ru]) {
      expect(locale.skillReviews.title).toBeTruthy()
      expect(locale.skillReviews.write).toBeTruthy()
      expect(locale.skillReviews.empty).toBeTruthy()
      expect(locale.skillReviews.hide).toBeTruthy()
      expect(locale.skillReviews.restore).toBeTruthy()
    }
  })
})
