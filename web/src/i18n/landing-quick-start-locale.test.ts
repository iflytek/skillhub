import { describe, expect, it } from 'vitest'
import skillGuideTemplate from '../docs/skill.md.template?raw'
import en from './locales/en.json'
import ru from './locales/ru.json'
import zh from './locales/zh.json'

describe('landing quick start locales', () => {
  it('uses localized agent setup prompts for chinese, english, and russian', () => {
    expect(zh.landing.quickStart.agent.command).toBe('请根据 https://www.example.com/registry/skill.md 接入 SkillHub')
    expect(en.landing.quickStart.agent.command).toBe('Connect SkillHub using https://www.example.com/registry/skill.md')
    expect(ru.landing.quickStart.agent.command).toBe('Подключите SkillHub по инструкции https://www.example.com/registry/skill.md')
  })

  it('provides command templates with url placeholder for dynamic rendering', () => {
    expect(zh.landing.quickStart.agent.commandTemplate).toBe('请根据 {{url}} 接入 SkillHub')
    expect(en.landing.quickStart.agent.commandTemplate).toBe('Connect SkillHub using {{url}}')
    expect(ru.landing.quickStart.agent.commandTemplate).toBe('Подключите SkillHub по инструкции {{url}}')
    expect(zh.landing.quickStart.human.commandTemplate).toContain('--registry {{url}}')
    expect(en.landing.quickStart.human.commandTemplate).toContain('--registry {{url}}')
    expect(ru.landing.quickStart.human.commandTemplate).toContain('--registry {{url}}')
  })

  it('keeps exact skill installs on the selected registry', () => {
    for (const prompt of [
      zh.skillDetail.installForAgent.prompt,
      en.skillDetail.installForAgent.prompt,
      ru.skillDetail.installForAgent.prompt,
    ]) {
      expect(prompt).toContain('{{guideUrl}}')
      expect(prompt).toContain('{{skill}}')
      expect(prompt).toContain('{{version}}')
      expect(prompt).not.toContain('fallback')
      expect(prompt).not.toContain('备用公共')
      expect(prompt).not.toMatch(/若无法安装|If installation fails|Если установка не удалась/)
      expect(prompt).not.toMatch(/不要改用其他来源|do not use another source|не используйте другой источник/)
    }
  })

  it('keeps the native CLI guide bound to the selected registry', () => {
    expect(skillGuideTemplate).toContain('name: skillhub-cli')
    expect(skillGuideTemplate).toContain('version: 2.0.2')
    expect(skillGuideTemplate).toContain('npm install --global @astron-team/skillhub')
    expect(skillGuideTemplate).not.toContain('@astron-team/skillhub@0.1.12')
    expect(skillGuideTemplate).toContain('the `registry` field in `~/.skillhub/config.json`')
    expect(skillGuideTemplate).toContain('`https://skill.xfyun.cn`')
    expect(skillGuideTemplate).not.toContain('${SKILLHUB_PUBLIC_BASE_URL}')
    expect(skillGuideTemplate).toContain('separately confirms removal of that exact identified launcher')
    expect(skillGuideTemplate).toContain('Never unlink an executable directly')
    expect(skillGuideTemplate).toContain('do not run the global installation or update yet')
    expect(skillGuideTemplate).toContain('resolved package metadata proves')
    expect(skillGuideTemplate).toContain('even when it prints `SkillHub CLI <version>`')
    expect(skillGuideTemplate).toContain('does not authorize removing another `skillhub` launcher')
    expect(skillGuideTemplate).toContain('Treat `<registry>` below as a value to replace')
    expect(skillGuideTemplate).toContain([
      'skillhub install @global/skillhub-cli \\',
      '  --scope user',
    ].join('\n'))
    expect(skillGuideTemplate).toContain('PowerShell 7')
    expect(skillGuideTemplate).toContain('do not search for or substitute a similarly named package')
    expect(skillGuideTemplate).toContain('ask before querying another registry')
  })

  it('exposes CLI install command in both locales', () => {
    expect(zh.landing.quickStart.tabs.cli).toBe('CLI')
    expect(zh.landing.quickStart.cli.command).toBe('npm i -g @astron-team/skillhub')
    expect(zh.landing.quickStart.cli.description).toBe('安装 SkillHub CLI 到本地，后续可运行 skillhub install 安装技能')
    expect(en.landing.quickStart.tabs.cli).toBe('CLI')
    expect(en.landing.quickStart.cli.command).toBe('npm i -g @astron-team/skillhub')
    expect(en.landing.quickStart.cli.description).toBe('Install the SkillHub CLI locally to run skillhub install for skills.')
  })
})
