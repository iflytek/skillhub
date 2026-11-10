// @vitest-environment jsdom

import { createElement } from 'react'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { defaultSitePresentation } from '@/shared/hooks/use-site-presentation'
import type { SitePresentationSettings } from '@/api/site-presentation'
import * as mod from './landing-quick-start'

const state = vi.hoisted((): { settings: SitePresentationSettings | null } => ({ settings: null }))
vi.mock('@/shared/hooks/use-site-presentation', async (original) => ({ ...await original<typeof import('@/shared/hooks/use-site-presentation')>(), useSitePresentation: () => ({ settings: state.settings ?? defaultSitePresentation() }) }))
vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      i18n: { resolvedLanguage: 'en' },
      t: (key: string, values?: Record<string, string>) => values?.url
        ? `${key} ${values.url}`
        : key,
    }),
  }
})

/**
 * LandingQuickStartSection is a React component that renders a tabbed quick-start
 * section with agent/human tabs and copy-to-clipboard commands.
 * All logic depends on React state and i18next hooks.
 * There are no exported pure helpers or constants to test here.
 *
 * We verify the module shape so downstream consumers break fast
 * if the export contract changes.
 */
describe('landing-quick-start module exports', () => {
  const originalRuntimeConfig = window.__SKILLHUB_RUNTIME_CONFIG__

  afterEach(() => {
    cleanup()
    state.settings = null
    vi.restoreAllMocks()
    window.__SKILLHUB_RUNTIME_CONFIG__ = originalRuntimeConfig
  })

  it('exports the LandingQuickStartSection component', () => {
    expect(mod.LandingQuickStartSection).toBeTypeOf('function')
  })

  it('uses available modes and custom presentation copy without changing functional commands', () => {
    state.settings = defaultSitePresentation()
    state.settings.sections.quickStart.items = { agent: false, web: false }
    state.settings.sections.quickStart.texts = { title: { en: 'Team access' }, cliTitle: { en: 'Use our CLI' } }
    render(createElement(mod.LandingQuickStartSection))
    expect(screen.getByRole('heading', { name: 'Team access' })).toBeTruthy()
    expect(screen.getByRole('button', { name: /Use our CLI/ }).getAttribute('aria-pressed')).toBe('true')
    expect(screen.queryByText('landing.experience.quickStart.modes.agent.title')).toBeNull()
    expect(screen.queryByText('landing.experience.quickStart.modes.web.title')).toBeNull()
    expect(screen.getByText('npx -y @astron-team/skillhub@0.1.12 --version')).toBeTruthy()
  })

  it('uses the self-hosted Registry URL in displayed and copied CLI commands', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(globalThis.navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    })
    window.__SKILLHUB_RUNTIME_CONFIG__ = {
      appBaseUrl: 'https://registry.internal.example/skills',
    }

    render(createElement(mod.LandingQuickStartSection))
    const cliLabel = screen.getByText('landing.experience.quickStart.modes.cli.title')
    await act(async () => fireEvent.click(cliLabel.closest('button')!))

    expect(screen.getAllByText('https://registry.internal.example/skills')).toHaveLength(2)
    expect(screen.getByText('registry: https://registry.internal.example/skills')).toBeTruthy()

    const copyButtons = screen.getAllByRole('button', {
      name: 'landing.experience.quickStart.copy',
    })
    await act(async () => fireEvent.click(copyButtons[1]))
    await act(async () => fireEvent.click(copyButtons[2]))

    await waitFor(() => {
      expect(writeText).toHaveBeenNthCalledWith(
        1,
        'npx -y @astron-team/skillhub@0.1.12 search weather --registry https://registry.internal.example/skills --limit 5',
      )
      expect(writeText).toHaveBeenNthCalledWith(
        2,
        'npx -y @astron-team/skillhub@0.1.12 install @global/weather --dir ./skills --registry https://registry.internal.example/skills',
      )
    })
  })
})
