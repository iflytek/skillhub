import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const usePersonalNamespaceSettingsMock = vi.fn()

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { language: 'en' },
    }),
  }
})

vi.mock('@/shared/lib/toast', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

vi.mock('@/features/admin/use-personal-namespace-settings', () => ({
  usePersonalNamespaceSettings: () => usePersonalNamespaceSettingsMock(),
  useUpdatePersonalNamespaceSettings: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

import { AdminSettingsPage, previewSlug, renderTemplate } from './settings'

describe('previewSlug', () => {
  it('lowercases and hyphenates the rendered template', () => {
    expect(previewSlug('${username}')).toBe('li-wei')
  })

  it('shows that underscores become hyphens', () => {
    expect(previewSlug('${username}_space')).toBe('li-wei-space')
  })

  it('collapses repeated separators and trims the edges', () => {
    expect(previewSlug('--${username}...space--')).toBe('li-wei-space')
  })

  it('keeps an unknown placeholder visible instead of dropping it', () => {
    expect(renderTemplate('${nickname}')).toBe('${nickname}')
  })

  it('renders the email prefix placeholder', () => {
    expect(previewSlug('${email_prefix}')).toBe('li-wei')
  })
})

describe('AdminSettingsPage', () => {
  beforeEach(() => {
    usePersonalNamespaceSettingsMock.mockReturnValue({
      data: {
        enabled: true,
        slugTemplate: '${username}',
        displayNameTemplate: '${username}',
        supportedPlaceholders: ['username', 'email_prefix', 'user_id'],
      },
      isLoading: false,
    })
  })

  it('renders the personal namespace section', () => {
    const html = renderToStaticMarkup(<AdminSettingsPage />)

    expect(html).toContain('adminSettings.personalNamespaceTitle')
    expect(html).toContain('adminSettings.slugTemplateLabel')
  })

  it('shows a loading state while the settings are fetched', () => {
    usePersonalNamespaceSettingsMock.mockReturnValue({ data: undefined, isLoading: true })

    const html = renderToStaticMarkup(<AdminSettingsPage />)

    expect(html).toContain('adminSettings.loading')
  })
})
