import { Suspense, useEffect, useState } from 'react'
import { Outlet, Link, useRouterState } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/use-auth'
import { LanguageSwitcher } from '@/shared/components/language-switcher'
import { Button } from '@/shared/ui/button'
import { UserMenu } from '@/shared/components/user-menu'
import { NotificationBell } from '@/features/notification/notification-bell'
import { Laptop, Moon, Sun } from 'lucide-react'
import { THEME_TOGGLE_RELEASED } from '@/shared/theme/theme-release'
import {
  applyThemeMode,
  getStoredThemePreference,
  resolveThemeMode,
  setThemePreference as persistAndApplyThemePreference,
  type ThemePreference,
} from '@/shared/theme/theme-preference'
import { getAppHeaderClassName } from './layout-header-style'
import { getAppMainContentLayout, resolveAppMainContentPathname } from './layout-main-content'

/**
 * Application shell shared by all routed pages.
 *
 * It owns the global header, footer, language switcher, auth-aware navigation, and suspense
 * fallback used while lazy route modules are loading.
 */
export function Layout() {
  const { t } = useTranslation()
  const { pathname, resolvedPathname } = useRouterState({
    select: (s) => ({
      pathname: s.location.pathname,
      resolvedPathname: s.resolvedLocation?.pathname,
    }),
  })
  const { user, isLoading } = useAuth()
  const [isHeaderElevated, setIsHeaderElevated] = useState(false)
  const [themePreference, setThemePreference] = useState<ThemePreference>(() => getStoredThemePreference())
  const contentLayoutPathname = resolveAppMainContentPathname(pathname, resolvedPathname)
  const mainContentLayout = getAppMainContentLayout(contentLayoutPathname)

  useEffect(() => {
    const updateHeaderElevation = () => {
      setIsHeaderElevated(window.scrollY > 0)
    }

    updateHeaderElevation()
    window.addEventListener('scroll', updateHeaderElevation, { passive: true })

    return () => {
      window.removeEventListener('scroll', updateHeaderElevation)
    }
  }, [])

  useEffect(() => {
    if (themePreference !== 'system' || typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return
    }

    const mediaQueryList = window.matchMedia('(prefers-color-scheme: dark)')
    const syncMode = (matches: boolean) => {
      const nextMode = resolveThemeMode('system', matches)
      applyThemeMode(nextMode)
      document.documentElement.dataset.themeMode = nextMode
    }

    syncMode(mediaQueryList.matches)

    const handleChange = (event: MediaQueryListEvent) => {
      syncMode(event.matches)
    }

    if (typeof mediaQueryList.addEventListener === 'function') {
      mediaQueryList.addEventListener('change', handleChange)
      return () => {
        mediaQueryList.removeEventListener('change', handleChange)
      }
    }

    mediaQueryList.addListener(handleChange)
    return () => {
      mediaQueryList.removeListener(handleChange)
    }
  }, [themePreference])

  const navItems: Array<{
    label: string
    to: string
    exact?: boolean
    auth?: boolean
  }> = [
    { label: t('nav.landing'), to: '/', exact: true },
    { label: t('nav.publish'), to: '/dashboard/publish', auth: true },
    { label: t('nav.search'), to: '/search' },
    { label: t('nav.dashboard'), to: '/dashboard', auth: true },
    { label: t('nav.mySkills'), to: '/dashboard/skills', auth: true },
  ]

  const isActive = (to: string, exact?: boolean) => {
    if (exact) return pathname === to
    // Keep matching strict so parent dashboard paths do not highlight unrelated child links.
    return pathname === to
  }

  const themeControlMeta: Record<ThemePreference, { label: string; icon: typeof Sun; next: ThemePreference }> = {
    light: { label: 'Light', icon: Sun, next: 'dark' },
    dark: { label: 'Dark', icon: Moon, next: 'system' },
    system: { label: 'System', icon: Laptop, next: 'light' },
  }

  const handleThemePreferenceChange = (nextPreference: ThemePreference) => {
    setThemePreference(nextPreference)
    persistAndApplyThemePreference(nextPreference)
  }

  const currentThemeControl = themeControlMeta[themePreference]
  const nextThemeControl = themeControlMeta[currentThemeControl.next]
  const ThemeIcon = currentThemeControl.icon

  return (
    <div className="relative flex min-h-screen flex-col overflow-x-clip bg-background text-foreground">
      {/* Header */}
      <header className={getAppHeaderClassName(isHeaderElevated)}>
        <Link to="/" className="text-xl font-semibold tracking-tight text-foreground">
          SkillHub
        </Link>

        <nav className="hidden items-center gap-8 text-sm font-normal text-muted-foreground md:flex">
          {navItems.map((item) => {
            if (item.auth && !user) return null
            const active = isActive(item.to, item.exact)

            return (
              <Link
                key={item.to}
                to={item.to}
                className={
                  active
                    ? 'rounded-full bg-primary px-4 py-1.5 text-primary-foreground shadow-sm'
                    : 'hover:opacity-80 transition-opacity duration-150'
                }
              >
                {item.label}
              </Link>
            )
          })}
        </nav>

        <div className="flex items-center gap-6 text-sm font-normal text-muted-foreground">
          {/* THEME_TOGGLE_RELEASED controls UX visibility only, never security/policy behavior. */}
          {THEME_TOGGLE_RELEASED ? (
            <Button
              variant="outline"
              size="icon"
              className="h-9 w-9 rounded-full border-border/80 bg-card text-foreground"
              onClick={() => handleThemePreferenceChange(currentThemeControl.next)}
              aria-label={`Theme mode: ${currentThemeControl.label}. Click to switch to ${nextThemeControl.label}.`}
              title={`Theme: ${currentThemeControl.label} -> ${nextThemeControl.label}`}
            >
              <ThemeIcon className="h-4 w-4" aria-hidden />
              <span className="sr-only">
                Theme mode: {currentThemeControl.label}. Click to switch to {nextThemeControl.label}.
              </span>
            </Button>
          ) : null}
          <LanguageSwitcher />
          {user && <NotificationBell />}
          {isLoading ? null : user ? (
            <UserMenu user={user} />
          ) : (
            <Link
              to="/login"
              search={{ returnTo: '' }}
              className="hover:opacity-80 transition-opacity"
            >
              {t('nav.login')}
            </Link>
          )}
        </div>
      </header>

      {/* Main content */}
      <main className={mainContentLayout.mainClassName}>
        <Suspense
          fallback={
            <div className="space-y-4 animate-fade-up">
              <div className="h-10 w-48 animate-shimmer rounded-lg" />
              <div className="h-5 w-72 animate-shimmer rounded-md" />
              <div className="h-64 animate-shimmer rounded-xl" />
            </div>
          }
        >
          <div className={mainContentLayout.contentClassName}>
            <Outlet />
          </div>
        </Suspense>
      </main>

      {/* Footer */}
      <footer className="relative z-10 mt-auto rounded-t-2xl border-t border-border bg-secondary/70">
        <div className="max-w-6xl mx-auto px-6 md:px-12 py-10">
          <div className="flex flex-col md:flex-row md:items-start md:justify-between gap-10 md:gap-12">
            <div className="flex-shrink-0">
              <div className="flex items-center gap-2 mb-3">
                <div className="w-9 h-9 rounded-lg flex items-center justify-center bg-primary text-sm font-bold text-primary-foreground shadow-sm">
                  S
                </div>
                <span className="text-lg font-bold text-foreground">SkillHub</span>
              </div>
              <p className="max-w-xs text-sm text-muted-foreground">
                {t('layout.footerDescription')}
              </p>
            </div>
            <div className="flex flex-wrap gap-12 md:gap-16">
              <div>
                <h4 className="mb-3 text-sm font-semibold text-foreground">
                  {t('nav.home')}
                </h4>
                <ul className="space-y-2 text-sm">
                  <li>
                    <Link to="/" className="text-muted-foreground transition-opacity hover:opacity-80">
                      {t('nav.home')}
                    </Link>
                  </li>
                  <li>
                    <Link
                      to="/search"
                      search={{ q: '', sort: 'relevance', page: 0, starredOnly: false }}
                      className="text-muted-foreground transition-opacity hover:opacity-80"
                    >
                      {t('nav.search')}
                    </Link>
                  </li>
                  <li>
                    <Link to="/dashboard" className="text-muted-foreground transition-opacity hover:opacity-80">
                      {t('nav.dashboard')}
                    </Link>
                  </li>
                </ul>
              </div>
              <div>
                <h4 className="mb-3 text-sm font-semibold text-foreground">
                  {t('footer.resources')}
                </h4>
                <ul className="space-y-2 text-sm">
                  <li>
                    <a href="#" className="text-muted-foreground transition-opacity hover:opacity-80">
                      {t('footer.docs')}
                    </a>
                  </li>
                  <li>
                    <a href="#" className="text-muted-foreground transition-opacity hover:opacity-80">
                      {t('footer.api')}
                    </a>
                  </li>
                  <li>
                    <a href="#" className="text-muted-foreground transition-opacity hover:opacity-80">
                      {t('footer.community')}
                    </a>
                  </li>
                </ul>
              </div>
            </div>
          </div>
          <div className="mt-10 flex flex-col gap-4 border-t border-border pt-6 text-xs text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
            <span>{t('footer.copyright')}</span>
            <div className="flex items-center gap-2">
              <Link to="/privacy" className="hover:opacity-80 transition-opacity">
                {t('footer.privacy')}
              </Link>
              <span>|</span>
              <Link to="/terms" className="hover:opacity-80 transition-opacity">
                {t('footer.terms')}
              </Link>
            </div>
          </div>
        </div>
      </footer>
    </div>
  )
}
