// @vitest-environment jsdom

import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { act, fireEvent, render, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { InstallForAgentButton, buildAgentInstallPrompt } from './install-for-agent-button'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, values?: Record<string, string>) => key === 'skillDetail.installForAgent.prompt'
      ? `Connect with ${values?.guideUrl}; install ${values?.skill} version ${values?.version}.`
      : key,
  }),
}))

describe('install-for-agent-button', () => {
  const originalRuntimeConfig = window.__SKILLHUB_RUNTIME_CONFIG__

  const formatPrompt = (guideUrl: string, skill: string, version: string) => (
    `Connect with ${guideUrl}; install ${skill} version ${version}.`
  )

  afterEach(() => {
    vi.restoreAllMocks()
    window.__SKILLHUB_RUNTIME_CONFIG__ = originalRuntimeConfig
  })

  it('builds a prompt for a global skill using the instance guide', () => {
    expect(buildAgentInstallPrompt('global', 'my-skill', '1.2.3', 'https://skill.example.com', formatPrompt)).toBe(
      'Connect with https://skill.example.com/registry/skill.md; install @global/my-skill version 1.2.3.',
    )
  })

  it('keeps a sub-path base and namespace in the copied prompt', () => {
    expect(buildAgentInstallPrompt('team-alpha', 'my-skill', '2.0.0', 'https://skill.example.com/skillhub/', formatPrompt)).toBe(
      'Connect with https://skill.example.com/skillhub/registry/skill.md; install @team-alpha/my-skill version 2.0.0.',
    )
  })

  it('renders an accessible copy button', () => {
    const html = renderToStaticMarkup(createElement(InstallForAgentButton, {
      namespace: 'global',
      slug: 'my-skill',
      version: '1.2.3',
    }))

    expect(html).toContain('data-testid="install-for-agent-button"')
    expect(html).toContain('aria-label="skillDetail.installForAgent.button"')
    expect(html).toContain('skillDetail.installForAgent.button')
  })

  it('can be disabled when the selected skill version is not installable', () => {
    const html = renderToStaticMarkup(createElement(InstallForAgentButton, {
      namespace: 'global',
      slug: 'my-skill',
      version: '1.2.3',
      disabled: true,
    }))

    expect(html).toContain('disabled=""')
  })

  it('is disabled for a version that cannot be copied safely across shells', () => {
    const html = renderToStaticMarkup(createElement(InstallForAgentButton, {
      namespace: 'global',
      slug: 'my-skill',
      version: '1.0.0&echo INJECTED',
    }))

    expect(html).toContain('disabled=""')
  })

  it('copies the complete instance, coordinate, and version prompt', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(globalThis.navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    })
    window.__SKILLHUB_RUNTIME_CONFIG__ = { appBaseUrl: 'https://skill.example.com/skillhub' }

    const { getByTestId } = render(createElement(InstallForAgentButton, {
      namespace: 'team-alpha',
      slug: 'my-skill',
      version: '2.0.0',
    }))

    await act(async () => fireEvent.click(getByTestId('install-for-agent-button')))

    await waitFor(() => expect(writeText).toHaveBeenCalledWith(
      'Connect with https://skill.example.com/skillhub/registry/skill.md; install @team-alpha/my-skill version 2.0.0.',
    ))
  })
})
