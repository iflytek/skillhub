import { describe, expect, it } from 'vitest'
import en from './locales/en.json'
import zh from './locales/zh.json'

describe('skill detail lifecycle locales', () => {
  it('defines the unarchive label in both locales', () => {
    expect(zh.skillDetail.unarchiveSkill).toBe('恢复技能')
    expect(en.skillDetail.unarchiveSkill).toBe('Restore Skill')
  })

  it('defines package relative link missing messages in both locales', () => {
    expect(zh.skillDetail.packageLinkMissingTitle).toBe('文件未找到')
    expect(zh.skillDetail.packageLinkMissingDescription).toBe('该链接指向的文件不在当前技能版本中。')
    expect(en.skillDetail.packageLinkMissingTitle).toBe('File not found')
    expect(en.skillDetail.packageLinkMissingDescription).toBe('This link points to a file that is not included in the current skill version.')
  })
})

describe('GitHub upstream tag locales', () => {
  it('defines every upstream tag status in all locales', async () => {
    const ko = (await import('./locales/ko.json')).default
    const ru = (await import('./locales/ru.json')).default
    for (const locale of [en, zh, ko, ru]) {
      const keys = Object.keys(locale.skillDetail)
      for (const status of ['EXACT', 'NO_TAG', 'UNKNOWN', 'UNRECORDED']) {
        expect(keys).toContain(`githubUpstreamTag${status}`)
      }
      expect(keys.some(key => key.startsWith('githubUpstreamTagAFTER_TAG'))).toBe(true)
      expect(keys).toContain('githubUpstreamTagScope')
      expect(keys).toContain('githubDeclaredVersion')
      expect(keys).toContain('githubDeclaredVersionNone')
    }
  })

  it('pluralizes the distance with the real i18n configuration', async () => {
    const i18n = (await import('./config')).default
    const values = { tag: 'v1.3.2', commit: '4ae2317d' }
    expect(i18n.getFixedT('en')('skillDetail.githubUpstreamTagAFTER_TAG', { ...values, count: 1 }))
      .toBe('1 commit after v1.3.2 · 4ae2317d')
    expect(i18n.getFixedT('en')('skillDetail.githubUpstreamTagAFTER_TAG', { ...values, count: 3 }))
      .toBe('3 commits after v1.3.2 · 4ae2317d')
    expect(i18n.getFixedT('zh')('skillDetail.githubUpstreamTagAFTER_TAG', { ...values, count: 3 }))
      .toBe('v1.3.2 之后 3 个提交 · 4ae2317d')
    expect(i18n.getFixedT('ko')('skillDetail.githubUpstreamTagAFTER_TAG', { ...values, count: 3 }))
      .toBe('v1.3.2 이후 커밋 3개 · 4ae2317d')
    expect(i18n.getFixedT('ru')('skillDetail.githubUpstreamTagAFTER_TAG', { ...values, count: 5 }))
      .toBe('5 коммитов после v1.3.2 · 4ae2317d')
  })
})
