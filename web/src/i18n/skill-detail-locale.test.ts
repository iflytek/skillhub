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
