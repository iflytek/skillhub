import { Suspense, useEffect, useRef, useState } from 'react'
import { Outlet, Link, useRouterState } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Menu, X } from 'lucide-react'
import { useAuth } from '@/features/auth/use-auth'
import { BrandMark } from '@/shared/components/brand-mark'
import { LanguageSwitcher } from '@/shared/components/language-switcher'
import { ThemeToggle } from '@/shared/components/theme-toggle'
import { UserMenu } from '@/shared/components/user-menu'
import { NotificationBell } from '@/features/notification/notification-bell'
import { dismissOpenOverlays } from '@/shared/lib/dismiss-open-overlays'
import { syncDocumentLanguage } from '@/shared/lib/document-language'
import { DashboardSidebar, SIDEBAR_GROUPS } from '@/pages/dashboard'
import { canViewGovernanceCenter } from '@/shared/lib/governance-access'
import { getAppHeaderClassName } from './layout-header-style'
import { getAppMainContentLayout, resolveAppMainContentPathname } from './layout-main-content'

const FOOTER_LINK_CLASS_NAME = 'group relative inline-flex py-0.5 transition-colors duration-150 hover:text-foreground focus-visible:outline-none focus-visible:text-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-secondary after:absolute after:inset-x-0 after:-bottom-0.5 after:h-px after:origin-left after:scale-x-0 after:bg-foreground/60 after:transition-transform after:duration-200 hover:after:scale-x-100 motion-reduce:after:transition-none'

/**
 * Application shell shared by all routed pages.
 *
 * It owns the global header, footer, language switcher, auth-aware navigation, and suspense
 * fallback used while lazy route modules are loading.
 */
export function Layout() {
  const { t, i18n } = useTranslation()
  const { pathname, resolvedPathname } = useRouterState({
    select: (s) => ({
      pathname: s.location.pathname,
      resolvedPathname: s.resolvedLocation?.pathname,
    }),
  })
  const { user, isLoading } = useAuth()
  const [isHeaderElevated, setIsHeaderElevated] = useState(false)
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const previousPathnameRef = useRef(pathname)
  const contentLayoutPathname = resolveAppMainContentPathname(pathname, resolvedPathname)
  const mainContentLayout = getAppMainContentLayout(contentLayoutPathname)
  const isDashboardSubRoute = pathname !== '/dashboard' && pathname.startsWith('/dashboard')
  const showSidebar = (isDashboardSubRoute && pathname !== '/dashboard/publish') || pathname.startsWith('/settings/')
  const governanceVisible = canViewGovernanceCenter(user?.platformRoles)
  const filteredDashboardGroups = SIDEBAR_GROUPS
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => (
        (!item.admin || governanceVisible)
        && (!item.passwordCapability || user?.canChangePassword === true)
      )),
    }))
    .filter((group) => group.items.length > 0)

  useEffect(() => {
    syncDocumentLanguage(i18n.resolvedLanguage ?? i18n.language)
  }, [i18n.language, i18n.resolvedLanguage])

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

  // Pathname-only: search debounce on /search must not dismiss overlays mid-typing.
  useEffect(() => {
    if (previousPathnameRef.current === pathname) {
      return
    }
    previousPathnameRef.current = pathname
    dismissOpenOverlays()
  }, [pathname])

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
    // 「控制台」按钮：仅在 /dashboard 主页或 /settings/* 时高亮
    if (to === '/dashboard') {
      return pathname === '/dashboard' || pathname.startsWith('/settings/')
    }
    // Keep matching strict so parent dashboard paths do not highlight unrelated child links.
    return pathname === to
  }

  return (
    <div className="min-h-screen flex flex-col relative" style={{ background: 'var(--bg-page, hsl(var(--background)))' }}>
      {/* Clip only the decorative layer so in-tree Select/Dropdown are not cropped. */}
      <div className="pointer-events-none absolute inset-0 z-0 overflow-x-clip" aria-hidden>
        <div
          className="absolute top-0 right-0 w-[600px] h-[500px] rounded-full opacity-90"
          style={{
            background: 'radial-gradient(ellipse at 70% 20%, hsl(var(--glow-accent) / 0.12) 0%, hsl(var(--glow-primary) / 0.07) 40%, transparent 70%)',
            filter: 'blur(60px)',
          }}
        />
      </div>

      {/* Header */}
      <header className={getAppHeaderClassName(isHeaderElevated)} style={{ borderColor: 'hsl(var(--border))' }}>
        <Link to="/" className="text-xl font-semibold tracking-tight flex-shrink-0" style={{ color: 'hsl(var(--foreground))' }}>
          SkillHub
        </Link>

        {/* Desktop nav — lg+ only */}
        <nav className="hidden lg:flex items-center gap-5 text-[15px] font-normal" style={{ color: 'hsl(var(--text-secondary))' }}>
          {navItems.map((item) => {
            if (item.auth && !user) return null
            const active = isActive(item.to, item.exact)

            return (
              <Link
                key={item.to}
                to={item.to}
                className={
                  active
                    ? 'px-4 py-1.5 rounded-full text-sm font-medium bg-foreground text-background shadow-[0_1px_2px_0_rgb(0_0_0/0.12)]'
                    : 'px-4 py-1.5 rounded-full text-sm font-medium hover:opacity-90 transition-opacity duration-150'
                }
                style={active ? undefined : { color: 'hsl(var(--foreground) / 0.65)' }}
              >
                {item.label}
              </Link>
            )
          })}
        </nav>

        <div className="flex items-center gap-1 sm:gap-2 flex-shrink-0" style={{ color: 'hsl(var(--text-secondary))' }}>
          {/* Hamburger — visible below lg */}
          <button
            type="button"
            className="lg:hidden inline-flex items-center justify-center rounded-lg p-2 hover:bg-accent transition-colors"
            onClick={() => setMobileMenuOpen((prev) => !prev)}
            aria-expanded={mobileMenuOpen}
            aria-label={t(mobileMenuOpen ? 'layout.closeNavigation' : 'layout.openNavigation')}
          >
            {mobileMenuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>
          <ThemeToggle />
          <LanguageSwitcher />
          {user && <NotificationBell />}
          {isLoading ? null : user ? (
            <UserMenu user={user} />
          ) : (
            <Link
              to="/login"
              className="hover:opacity-80 transition-opacity"
            >
              {t('nav.login')}
            </Link>
          )}
        </div>
      </header>

      {/* Mobile nav dropdown */}
      {mobileMenuOpen ? (
        <div className="lg:hidden sticky top-[52px] z-40 border-b border-border bg-background/95 backdrop-blur-xl">
          <nav className="flex flex-col px-4 py-3 gap-1">
            {navItems.map((item) => {
              if (item.auth && !user) return null
              const active = isActive(item.to, item.exact)
              return (
                <Link
                  key={item.to}
                  to={item.to}
                  className={`px-4 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                    active ? 'bg-accent text-foreground' : 'text-muted-foreground hover:bg-accent hover:text-foreground'
                  }`}
                  onClick={() => setMobileMenuOpen(false)}
                >
                  {item.label}
                </Link>
              )
            })}
          </nav>
        </div>
      ) : null}

      {/* Main content */}
      <main className={mainContentLayout.mainClassName}>
        <Suspense
          fallback={
            <div className="space-y-3 animate-fade-up">
              <div className="h-8 w-36 animate-shimmer rounded-md" />
              <div className="h-4 w-56 animate-shimmer rounded-md" />
              <div className="h-48 animate-shimmer rounded-lg" />
            </div>
          }
        >
          <div className={mainContentLayout.contentClassName}>
          {showSidebar ? (
            <div className="flex flex-col lg:flex-row gap-6">
              <DashboardSidebar groups={filteredDashboardGroups} user={user} t={t} pathname={pathname} />
              <div className="flex-1 min-w-0">
                <Outlet />
              </div>
            </div>
          ) : (
            <Outlet />
          )}
        </div>
        </Suspense>
      </main>

      {/* Footer */}
      <footer className="relative z-10 mt-auto border-t bg-secondary/70" style={{ borderColor: 'hsl(var(--border))' }}>
        <div className="mx-auto max-w-6xl px-6 py-12 md:px-12 md:py-16">
          <div className="grid grid-cols-2 gap-8 md:grid-cols-5">
            <div className="col-span-2 md:col-span-1">
              <div className="mb-4 flex items-center gap-2.5">
                <BrandMark className="h-8 w-8 rounded-lg bg-background ring-1 ring-border/70" />
                <span className="font-semibold text-foreground">SkillHub</span>
              </div>
              <p className="text-sm text-muted-foreground">{t('layout.footerDescription')}</p>
            </div>

            <div>
              <h4 className="mb-4 text-sm font-semibold text-foreground">{t('footer.product')}</h4>
              <ul className="space-y-2.5 text-sm text-muted-foreground">
                <li><Link to="/search" search={{ q: '', sort: 'relevance', page: 0, starredOnly: false }} className={FOOTER_LINK_CLASS_NAME}>{t('footer.marketplace')}</Link></li>
                <li><Link to="/dashboard/publish" className={FOOTER_LINK_CLASS_NAME}>{t('footer.publish')}</Link></li>
                <li><Link to="/dashboard" className={FOOTER_LINK_CLASS_NAME}>{t('nav.dashboard')}</Link></li>
              </ul>
            </div>

            <div>
              <h4 className="mb-4 text-sm font-semibold text-foreground">{t('footer.developers')}</h4>
              <ul className="space-y-2.5 text-sm text-muted-foreground">
                <li><a href="https://github.com/iflytek/skillhub/tree/main/docs/skillhub" target="_blank" rel="noreferrer" className={FOOTER_LINK_CLASS_NAME}>{t('footer.docs')}</a></li>
                <li><a href="https://www.npmjs.com/package/@astron-team/skillhub" target="_blank" rel="noreferrer" className={FOOTER_LINK_CLASS_NAME}>CLI</a></li>
                <li><a href="https://github.com/iflytek/skillhub" target="_blank" rel="noreferrer" className={FOOTER_LINK_CLASS_NAME}>GitHub</a></li>
              </ul>
            </div>

            <div>
              <h4 className="mb-4 text-sm font-semibold text-foreground">{t('footer.project')}</h4>
              <ul className="space-y-2.5 text-sm text-muted-foreground">
                <li><a href="https://github.com/iflytek/skillhub/blob/main/LICENSE" target="_blank" rel="noreferrer" className={FOOTER_LINK_CLASS_NAME}>License</a></li>
                <li><a href="https://github.com/iflytek/skillhub/blob/main/CONTRIBUTING.md" target="_blank" rel="noreferrer" className={FOOTER_LINK_CLASS_NAME}>Contributing</a></li>
                <li><a href="https://github.com/iflytek/skillhub/releases" target="_blank" rel="noreferrer" className={FOOTER_LINK_CLASS_NAME}>Changelog</a></li>
                <li><a href="https://github.com/iflytek/skillhub/blob/main/CODE_OF_CONDUCT.md" target="_blank" rel="noreferrer" className={FOOTER_LINK_CLASS_NAME}>{t('footer.codeOfConduct')}</a></li>
              </ul>
            </div>

            <div>
              <h4 className="mb-4 text-sm font-semibold text-foreground">{t('footer.resources')}</h4>
              <ul className="space-y-2.5 text-sm text-muted-foreground">
                <li><a href="https://github.com/iflytek/skillhub/discussions" target="_blank" rel="noreferrer" className={FOOTER_LINK_CLASS_NAME}>{t('footer.community')}</a></li>
                <li><Link to="/privacy" className={FOOTER_LINK_CLASS_NAME}>{t('footer.privacy')}</Link></li>
                <li><Link to="/terms" className={FOOTER_LINK_CLASS_NAME}>{t('footer.terms')}</Link></li>
              </ul>
            </div>
          </div>

          <div className="mt-10 border-t border-border/70 pt-7 text-xs text-muted-foreground">
            <span>{t('footer.copyright')}</span>
          </div>
        </div>
      </footer>
    </div>
  )
}
