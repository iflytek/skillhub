// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react'
import { createElement } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'

// Layout is a component-only file with no exported pure functions or constants.
// We verify that the named export exists for the router to consume.

vi.mock('@tanstack/react-router', () => ({
  Outlet: () => null,
  Link: ({ children }: { children: unknown }) => children,
  useRouterState: () => ({ pathname: '/', resolvedPathname: '/' }),
}))

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

vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => ({
    user: null,
    isLoading: false,
  }),
}))

vi.mock('@/shared/components/language-switcher', () => ({
  LanguageSwitcher: () => null,
}))

vi.mock('@/shared/components/user-menu', () => ({
  UserMenu: () => null,
}))

vi.mock('./layout-header-style', () => ({
  getAppHeaderClassName: () => 'header-class',
}))

vi.mock('./layout-main-content', () => ({
  resolveAppMainContentPathname: (p: string) => p,
  getAppMainContentLayout: () => ({
    mainClassName: 'main-class',
    contentClassName: 'content-class',
  }),
}))

import { Layout } from './layout'

describe('Layout', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
    vi.resetModules()
  })

  it('exports a named Layout component function', () => {
    expect(typeof Layout).toBe('function')
    expect(Layout.name).toBe('Layout')
  })

  it('keeps Layout export stable for theme wiring integration', () => {
    expect(Layout).toBeDefined()
    expect(typeof Layout).toBe('function')
  })

  it('hides theme controls when THEME_TOGGLE_RELEASED is false', async () => {
    vi.doMock('@/shared/theme/theme-release', () => ({
      THEME_TOGGLE_RELEASED: false,
    }))

    const { Layout: GatedLayout } = await import('./layout')
    render(createElement(GatedLayout))

    expect(screen.queryByText('Light')).toBeNull()
    expect(screen.queryByText('Dark')).toBeNull()
    expect(screen.queryByText('System')).toBeNull()
  })

  it('shows Light/Dark/System options when THEME_TOGGLE_RELEASED is true', async () => {
    vi.doMock('@/shared/theme/theme-release', () => ({
      THEME_TOGGLE_RELEASED: true,
    }))

    const { Layout: GatedLayout } = await import('./layout')
    render(createElement(GatedLayout))

    expect(screen.getByText('Light')).toBeTruthy()
    expect(screen.getByText('Dark')).toBeTruthy()
    expect(screen.getByText('System')).toBeTruthy()
  })

  it('does not render legacy decorative orb class and keeps semantic shell tokens', () => {
    const { container } = render(createElement(Layout))

    expect(container.querySelector('.bg-brand-gradient.opacity-20.blur-3xl')).toBeNull()
    expect(container.querySelector('[class*="bg-background"]')).toBeTruthy()
    expect(container.querySelector('[class*="text-foreground"]')).toBeTruthy()
    expect(container.querySelector('[class*="border-border"]')).toBeTruthy()
  })
})
