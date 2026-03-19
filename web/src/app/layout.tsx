import { Suspense, useEffect, useState } from 'react'
import { Outlet, Link, useRouterState } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/use-auth'
import { LanguageSwitcher } from '@/shared/components/language-switcher'
import { UserMenu } from '@/shared/components/user-menu'
import { NotificationBell } from '@/features/notification/notification-bell'
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

  return (
    <div className="min-h-screen flex flex-col relative overflow-x-clip" style={{ background: 'var(--bg-page, hsl(var(--background)))' }}>
      {/* Decorative gradient orb */}
      <div
        className="absolute top-0 right-0 w-[600px] h-[500px] rounded-full opacity-90 pointer-events-none z-0"
        style={{
          background: 'radial-gradient(ellipse at 70% 20%, rgba(184,94,255,0.25) 0%, rgba(106,109,255,0.15) 40%, transparent 70%)',
          filter: 'blur(60px)',
        }}
      />

      {/* Header */}
      <header className={getAppHeaderClassName(isHeaderElevated)} style={{ borderColor: 'hsl(var(--border))' }}>
        <Link to="/" className="text-xl font-semibold tracking-tight text-brand-gradient">
          SkillHub
        </Link>

        <nav className="hidden md:flex items-center gap-8 text-[15px] font-normal" style={{ color: 'hsl(var(--text-secondary))' }}>
          {navItems.map((item) => {
            if (item.auth && !user) return null
            const active = isActive(item.to, item.exact)

            return (
              <Link
                key={item.to}
                to={item.to}
                className={
                  active
                    ? 'px-4 py-1.5 rounded-full bg-brand-gradient text-white shadow-sm'
                    : 'hover:opacity-80 transition-opacity duration-150'
                }
              >
                {item.label}
              </Link>
            )
          })}
        </nav>

        <div className="flex items-center gap-6 text-[15px] font-normal" style={{ color: 'hsl(var(--text-secondary))' }}>
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
      <footer className="relative z-10 border-t rounded-t-2xl mt-auto" style={{ background: '#F1F5F9', borderColor: 'hsl(var(--border))' }}>
        <div className="max-w-6xl mx-auto px-6 md:px-12 py-10">
          <div className="flex flex-col md:flex-row md:items-start md:justify-between gap-10 md:gap-12">
            <div className="flex-shrink-0">
              <div className="flex items-center gap-2 mb-3">
                <div className="w-9 h-9 rounded-lg flex items-center justify-center text-white text-sm font-bold shadow-sm bg-brand-gradient">
                  S
                </div>
                <span className="text-lg font-bold text-brand-gradient">SkillHub</span>
              </div>
              <p className="text-sm max-w-xs" style={{ color: 'hsl(var(--text-secondary))' }}>
                {t('layout.footerDescription')}
              </p>
            </div>
            <div className="flex flex-wrap gap-12 md:gap-16">
              <div>
                <h4 className="text-sm font-semibold mb-3" style={{ color: 'hsl(var(--foreground))' }}>
                  {t('nav.home')}
                </h4>
                <ul className="space-y-2 text-sm">
                  <li>
                    <Link to="/" className="hover:opacity-80 transition-opacity" style={{ color: 'hsl(var(--text-secondary))' }}>
                      {t('nav.home')}
                    </Link>
                  </li>
                  <li>
                    <Link
                      to="/search"
                      search={{ q: '', sort: 'relevance', page: 0, starredOnly: false }}
                      className="hover:opacity-80 transition-opacity"
                      style={{ color: 'hsl(var(--text-secondary))' }}
                    >
                      {t('nav.search')}
                    </Link>
                  </li>
                  <li>
                    <Link to="/dashboard" className="hover:opacity-80 transition-opacity" style={{ color: 'hsl(var(--text-secondary))' }}>
                      {t('nav.dashboard')}
                    </Link>
                  </li>
                </ul>
              </div>
              <div>
                <h4 className="text-sm font-semibold mb-3" style={{ color: 'hsl(var(--foreground))' }}>
                  {t('footer.resources')}
                </h4>
                <ul className="space-y-2 text-sm">
                  <li>
                    <a href="#" className="hover:opacity-80 transition-opacity" style={{ color: 'hsl(var(--text-secondary))' }}>
                      {t('footer.docs')}
                    </a>
                  </li>
                  <li>
                    <a href="#" className="hover:opacity-80 transition-opacity" style={{ color: 'hsl(var(--text-secondary))' }}>
                      {t('footer.api')}
                    </a>
                  </li>
                  <li>
                    <a href="#" className="hover:opacity-80 transition-opacity" style={{ color: 'hsl(var(--text-secondary))' }}>
                      {t('footer.community')}
                    </a>
                  </li>
                </ul>
              </div>
            </div>
          </div>
          <div
            className="mt-10 pt-6 border-t flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 text-xs"
            style={{ borderColor: 'hsl(var(--border))', color: 'hsl(var(--muted-foreground))' }}
          >
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
