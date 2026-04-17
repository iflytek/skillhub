// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
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

async function importLayoutWithGate(released: boolean) {
  vi.resetModules()
  vi.doMock('@/shared/theme/theme-release', () => ({
    THEME_TOGGLE_RELEASED: released,
  }))
  const module = await import('./layout')
  return module.Layout
}

describe('Layout', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('exports a named Layout component function', async () => {
    const Layout = await importLayoutWithGate(false)
    expect(typeof Layout).toBe('function')
    expect(Layout.name).toBe('Layout')
  })

  it('keeps Layout export stable for theme wiring integration', async () => {
    const Layout = await importLayoutWithGate(false)
    expect(Layout).toBeDefined()
    expect(typeof Layout).toBe('function')
  })

  it('hides theme controls when THEME_TOGGLE_RELEASED is false', async () => {
    const GatedLayout = await importLayoutWithGate(false)
    render(createElement(GatedLayout))

    expect(screen.queryByRole('button', { name: /theme mode:/i })).toBeNull()
  })

  it('shows tri-state icon switch when THEME_TOGGLE_RELEASED is true', async () => {
    const GatedLayout = await importLayoutWithGate(true)
    render(createElement(GatedLayout))

    const toggle = screen.getByRole('button', { name: /theme mode: system/i })
    expect(toggle).toBeTruthy()

    fireEvent.click(toggle)
    expect(screen.getByRole('button', { name: /theme mode: light/i })).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: /theme mode: light/i }))
    expect(screen.getByRole('button', { name: /theme mode: dark/i })).toBeTruthy()
  })

  it('does not render legacy decorative orb class and keeps semantic shell tokens', async () => {
    const Layout = await importLayoutWithGate(false)
    const { container } = render(createElement(Layout))

    expect(container.querySelector('.bg-brand-gradient.opacity-20.blur-3xl')).toBeNull()
    expect(container.querySelector('[class*="bg-background"]')).toBeTruthy()
    expect(container.querySelector('[class*="text-foreground"]')).toBeTruthy()
    expect(container.querySelector('[class*="border-border"]')).toBeTruthy()
  })
})
