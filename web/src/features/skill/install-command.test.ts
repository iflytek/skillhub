import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  InstallCommand,
  buildInstallCommand,
  buildInstallTarget,
  buildSkillhubCoordinate,
  buildSkillhubInstallCommand,
  getBaseUrl,
  isPortableSkillVersion,
} from './install-command'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}))

describe('install-command', () => {
  const originalWindow = globalThis.window

  function setMockWindow(appBaseUrl?: string) {
    const location = {
      protocol: 'https:',
      host: 'fallback.example.com',
    } satisfies Pick<Location, 'protocol' | 'host'>

    Object.defineProperty(globalThis, 'window', {
      configurable: true,
      writable: true,
      value: {
        __SKILLHUB_RUNTIME_CONFIG__: {
          appBaseUrl,
        },
        location,
      } satisfies {
        location: Pick<Location, 'protocol' | 'host'>
      } & {
        __SKILLHUB_RUNTIME_CONFIG__: {
          appBaseUrl?: string
        }
      },
    })
  }

  afterEach(() => {
    if (originalWindow) {
      Object.defineProperty(globalThis, 'window', {
        configurable: true,
        writable: true,
        value: originalWindow,
      })
      return
    }
    Reflect.deleteProperty(globalThis, 'window')
  })

  it('uses the plain slug for the global namespace', () => {
    expect(buildInstallTarget('global', 'my-skill')).toBe('my-skill')
    expect(buildInstallCommand('global', 'my-skill', 'https://skill.xfyun.cn')).toBe(
      'npx clawhub install my-skill --registry https://skill.xfyun.cn',
    )
  })

  it('prefixes non-global namespaces in the install target', () => {
    expect(buildInstallTarget('team-alpha', 'my-skill')).toBe('team-alpha--my-skill')
    expect(buildInstallCommand('team-alpha', 'my-skill', 'https://skill.xfyun.cn')).toBe(
      'npx clawhub install team-alpha--my-skill --registry https://skill.xfyun.cn',
    )
  })

  it('builds a one-line SkillHub npx command for the global namespace', () => {
    expect(buildSkillhubCoordinate('global', 'my-skill')).toBe('@global/my-skill')
    expect(buildSkillhubInstallCommand('global', 'my-skill', 'https://skill.xfyun.cn', '1.2.3')).toBe(
      'npx @astron-team/skillhub@latest install @global/my-skill --version 1.2.3 --registry https://skill.xfyun.cn',
    )
  })

  it('builds a one-line SkillHub npx command with namespace for team skills', () => {
    expect(buildSkillhubCoordinate('team-alpha', 'my-skill')).toBe('@team-alpha/my-skill')
    expect(buildSkillhubInstallCommand('team-alpha', 'my-skill', 'https://skill.xfyun.cn')).toBe(
      'npx @astron-team/skillhub@latest install @team-alpha/my-skill --registry https://skill.xfyun.cn',
    )
  })

  it('does not generate a cross-shell command for an unsafe version token', () => {
    expect(isPortableSkillVersion('20260818.075232')).toBe(true)
    expect(isPortableSkillVersion('1.0.0-beta+build.1')).toBe(true)
    expect(isPortableSkillVersion('1.0.0&echo INJECTED')).toBe(false)
    expect(buildSkillhubInstallCommand(
      'global',
      'my-skill',
      'https://skill.xfyun.cn',
      '1.0.0&echo INJECTED',
    )).toBe('')
  })

  it('uses the runtime app base url when available', () => {
    setMockWindow('https://app.example.com')

    expect(getBaseUrl()).toBe('https://app.example.com')
  })

  it('falls back to the browser origin when the app base url is missing', () => {
    setMockWindow()
    expect(getBaseUrl()).toBe('https://fallback.example.com')
  })

  it('falls back to browser origin when app base url is localhost', () => {
    setMockWindow('http://localhost')
    expect(getBaseUrl()).toBe('https://fallback.example.com')
  })

  it('falls back to browser origin when app base url contains localhost', () => {
    setMockWindow('http://localhost:8080')
    expect(getBaseUrl()).toBe('https://fallback.example.com')
  })

  it('renders the install command in a more compact code block', () => {
    setMockWindow('http://localhost:3000')

    const html = renderToStaticMarkup(createElement(InstallCommand, { namespace: 'global', slug: 'meeting-minutes-generator' }))

    expect(html).toContain('px-4 py-3')
    expect(html).toContain('leading-relaxed')
    expect(html).toContain('break-all')
  })

  it('renders install method tabs with only a short active underline', () => {
    setMockWindow('https://app.example.com')

    const html = renderToStaticMarkup(createElement(InstallCommand, {
      namespace: 'global',
      slug: 'meeting-minutes-generator',
    }))

    expect(html).toContain('after:w-6')
    expect(html).toContain('after:h-0.5')
    expect(html).not.toContain('rounded-lg border bg-background/80 p-1')
    expect(html).not.toContain('flex-1 rounded-md')
  })

  it('renders SkillHub CLI as the default install method', () => {
    setMockWindow('https://app.example.com')

    const html = renderToStaticMarkup(createElement(InstallCommand, {
      namespace: 'team-alpha',
      slug: 'meeting-minutes-generator',
      version: '2.0.0',
    }))

    expect(html).toContain('skillDetail.installMethodClawhub')
    expect(html).toContain('skillDetail.installMethodSkillhub')
    expect(html).toContain('aria-selected="true"')
    expect(html).toContain('npx @astron-team/skillhub@latest install @team-alpha/meeting-minutes-generator --version 2.0.0 --registry https://app.example.com')
    expect(html).not.toContain('npx clawhub install team-alpha--meeting-minutes-generator --registry https://app.example.com')
  })
})
