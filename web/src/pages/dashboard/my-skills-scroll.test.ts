import { describe, expect, it } from 'vitest'
import { rememberMySkillsScroll, takeMySkillsScroll } from './my-skills-scroll'

describe('My Skills scroll context', () => {
  it('restores only the matching identity and filters once', () => {
    rememberMySkillsScroll('user-a:page-2', 540)
    expect(takeMySkillsScroll('user-b:page-2')).toBeUndefined()
    expect(takeMySkillsScroll('user-a:page-1')).toBeUndefined()
    expect(takeMySkillsScroll('user-a:page-2')).toBe(540)
    expect(takeMySkillsScroll('user-a:page-2')).toBeUndefined()
  })

  it('keeps only the latest departure context', () => {
    rememberMySkillsScroll('user-a:page-1', 100)
    rememberMySkillsScroll('user-a:page-2', 600)
    expect(takeMySkillsScroll('user-a:page-1')).toBeUndefined()
    expect(takeMySkillsScroll('user-a:page-2')).toBe(600)
  })
})
