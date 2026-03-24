import { describe, expect, it } from 'vitest'
import en from './locales/en.json'
import zh from './locales/zh.json'

describe('landing quick start locales', () => {
  it('uses localized agent setup prompts for chinese and english', () => {
    expect(zh.landing.quickStart.agent.command).toBe('阅读 https://www.example.com/registry/skill.md，并按照说明完成 SkillHub Skills Registry 的配置')
    expect(en.landing.quickStart.agent.command).toBe('Read https://www.example.com/registry/skill.md and follow the instructions to setup SkillHub Skills Registry')
  })
})
