import { Suspense, useEffect, useRef, useState } from 'react'
import { Outlet, Link, useRouterState } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Menu, X } from 'lucide-react'
import { useAuth } from '@/features/auth/use-auth'
import { useSitePresentation } from '@/shared/hooks/use-site-presentation'
import { presentationItemVisible, presentationLinkUrl } from '@/shared/lib/presentation-content'
import { presentationText } from '@/shared/hooks/use-site-presentation'
import { BrandMark } from '@/shared/components/brand-mark'
import { LanguageSwitcher } from '@/shared/components/language-switcher'
import { ThemeToggle } from '@/shared/components/theme-toggle'
import { UserMenu } from '@/shared/components/user-menu'
import { NotificationBell } from '@/features/notification/notification-bell'
import { dismissOpenOverlays } from '@/shared/lib/dismiss-open-overlays'
import { syncDocumentLanguage } from '@/shared/lib/document-language'
import { DashboardSidebar, SIDEBAR_GROUPS } from '@/pages/dashboard'
import { canViewGovernanceCenter } from '@/shared/lib/governance-access'
import { useMyNamespaces } from '@/shared/hooks/use-namespace-queries'
import { canAccessReviewCenter } from '@/features/review/review-paths'
import { APP_HEADER_CONTENT_CLASS_NAME, getAppHeaderClassName } from './layout-header-style'
import { APP_FRAME_CLASS_NAME } from '@/shared/lib/layout-frame'
import { AUTH_ENTRY_PATHS, SPLIT_AUTH_PATHS, getAppMainContentLayout, resolveAppMainContentPathname } from './layout-main-content'

const FOOTER_LINK_CLASS_NAME = 'group relative inline-flex py-0.5 transition-colors duration-150 hover:text-foreground focus-visible:outline-none focus-visible:text-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-secondary after:absolute after:inset-x-0 after:-bottom-0.5 after:h-px after:origin-left after:scale-x-0 after:bg-foreground/60 after:transition-transform after:duration-200 hover:after:scale-x-100 motion-reduce:after:transition-none'

/**
 * Application shell shared by all routed pages.
 *
 * It owns the global header, footer, language switcher, auth-aware navigation, and suspense
 * fallback used while lazy route modules are loading.
 */
export function Layout() {
  const { settings: presentation } = useSitePresentation()
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
  const isAuthEntryRoute = AUTH_ENTRY_PATHS.includes(contentLayoutPathname as typeof AUTH_ENTRY_PATHS[number])
  const isSplitAuthRoute = SPLIT_AUTH_PATHS.includes(contentLayoutPathname as typeof SPLIT_AUTH_PATHS[number])
  const showSidebar = mainContentLayout.sidebarRowClassName !== undefined
  const governanceVisible = canViewGovernanceCenter(user?.platformRoles)
  const { data: managedNamespaces } = useMyNamespaces(Boolean(user) && showSidebar)
  const reviewVisible = canAccessReviewCenter(user?.platformRoles, managedNamespaces)
  const filteredDashboardGroups = SIDEBAR_GROUPS
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => (
        (!item.admin || governanceVisible)
        && (!item.review || reviewVisible)
        && (!item.superAdmin || user?.platformRoles?.includes('SUPER_ADMIN'))
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
    analyticsId?: string
    exact?: boolean
    auth?: boolean
  }> = [
    { label: t('nav.landing'), to: '/', analyticsId: 'navigation.home.open', exact: true },
    { label: t('nav.publish'), to: '/dashboard/publish', analyticsId: 'skill.publish.open', auth: true },
    { label: t('nav.marketplace'), to: '/search', analyticsId: 'navigation.skills.open' },
    { label: t('nav.githubSkills'), to: '/github', analyticsId: 'navigation.github.open' },
    { label: t('nav.suites'), to: '/suites', analyticsId: 'navigation.suites.open' },
    { label: t('nav.dashboard'), to: '/dashboard', analyticsId: 'navigation.dashboard.open', auth: true },
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
      <header
        className={isSplitAuthRoute ? 'absolute right-5 top-4 z-50 flex items-center sm:right-10' : getAppHeaderClassName(isHeaderElevated)}
        style={isSplitAuthRoute ? undefined : { borderColor: 'hsl(var(--border))' }}
      >
        <div className={isSplitAuthRoute ? 'flex items-center' : APP_HEADER_CONTENT_CLASS_NAME}>
        {!isSplitAuthRoute ? (
          <Link data-analytics-id="navigation.home.open" to="/" className="flex min-w-0 max-w-[min(40vw,18rem)] items-center gap-2 text-xl font-semibold tracking-tight" style={{ color: 'hsl(var(--foreground))' }}>
            {presentation.logoUrl ? <BrandMark className="h-8 w-8 shrink-0" /> : null}
            <span className="truncate" title={presentation.siteName ?? 'SkillHub'}>{presentation.siteName ?? 'SkillHub'}</span>
          </Link>
        ) : null}

        {/* Desktop nav — xl+ leaves room for localized labels and account controls. */}
        {!isAuthEntryRoute ? (
          <nav className="hidden xl:flex items-center gap-5 text-[15px] font-normal" style={{ color: 'hsl(var(--text-secondary))' }}>
            {navItems.map((item) => {
              if (item.auth && !user) return null
              const active = isActive(item.to, item.exact)

              return (
                <Link data-analytics-id={item.analyticsId}
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
        ) : null}

        <div className="flex items-center gap-1 sm:gap-2 flex-shrink-0" style={{ color: 'hsl(var(--text-secondary))' }}>
          {/* Hamburger — visible below xl */}
          {!isAuthEntryRoute ? (
            <button data-analytics-id="navigation.menu.toggle"
              type="button"
              className="xl:hidden inline-flex items-center justify-center rounded-lg p-2 hover:bg-accent transition-colors"
              onClick={() => setMobileMenuOpen((prev) => !prev)}
              aria-expanded={mobileMenuOpen}
              aria-label={t(mobileMenuOpen ? 'layout.closeNavigation' : 'layout.openNavigation')}
            >
              {mobileMenuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
            </button>
          ) : null}
          <ThemeToggle />
          <LanguageSwitcher />
          {user && <NotificationBell />}
          {isLoading ? null : user ? (
            <UserMenu user={user} />
          ) : !isAuthEntryRoute ? (
            <Link data-analytics-id="auth.login.open"
              to="/login"
              className="hover:opacity-80 transition-opacity"
            >
              {t('nav.login')}
            </Link>
          ) : null}
        </div>
        </div>
      </header>

      {/* Mobile nav dropdown */}
      {mobileMenuOpen && !isAuthEntryRoute ? (
        <div className="xl:hidden sticky top-[57px] z-40 border-b border-border bg-background/95 backdrop-blur-xl">
          <nav className={`${APP_FRAME_CLASS_NAME} flex flex-col gap-1 py-3`}>
            {navItems.map((item) => {
              if (item.auth && !user) return null
              const active = isActive(item.to, item.exact)
              return (
                <Link data-analytics-id={item.analyticsId}
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
            <div className={`${APP_FRAME_CLASS_NAME} space-y-3 py-8 animate-fade-up`}>
              <div className="h-8 w-36 animate-shimmer rounded-md" />
              <div className="h-4 w-56 animate-shimmer rounded-md" />
              <div className="h-48 animate-shimmer rounded-lg" />
            </div>
          }
        >
          <div className={mainContentLayout.contentClassName}>
          {showSidebar ? (
            <div className={mainContentLayout.sidebarRowClassName ?? "flex flex-col lg:flex-row gap-6"}>
              <DashboardSidebar groups={filteredDashboardGroups} user={user} t={t} pathname={contentLayoutPathname} />
              <div className={mainContentLayout.sidebarContentClassName ?? "flex-1 min-w-0"}>
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
      {!isAuthEntryRoute && presentation.sections.footer.enabled ? (
      <footer className="relative z-10 mt-auto border-t bg-secondary/70 [overflow-wrap:anywhere]" style={{ borderColor: 'hsl(var(--border))' }}>
        <div className={`${APP_FRAME_CLASS_NAME} py-12 md:py-16`}>
          <div className={`grid grid-cols-2 gap-8 ${['md:grid-cols-1', 'md:grid-cols-2', 'md:grid-cols-3', 'md:grid-cols-4', 'md:grid-cols-5'][['product', 'developers', 'project', 'resources'].filter(item => presentationItemVisible(presentation, 'footer', item)).length]}`}>
            <div className="col-span-2 md:col-span-1">
              <div className="mb-4 flex items-center gap-2.5">
                <BrandMark className="h-8 w-8 rounded-lg bg-background ring-1 ring-border/70" />
                <span className="min-w-0 font-semibold text-foreground [overflow-wrap:anywhere]">{presentation.siteName ?? 'SkillHub'}</span>
              </div>
              <p className="text-sm text-muted-foreground">{presentationText(presentation, 'footer', 'description', i18n.resolvedLanguage, t)}</p>
            </div>

            {presentationItemVisible(presentation, 'footer', 'product') && <div>
              <h4 className="mb-4 text-sm font-semibold text-foreground">{presentationText(presentation, 'footer', 'productTitle', i18n.resolvedLanguage, t)}</h4>
              <ul className="space-y-2.5 text-sm text-muted-foreground">
                <li><Link data-analytics-id="navigation.skills.open" to="/search" search={{ q: '', sort: 'relevance', page: 0, starredOnly: false }} className={FOOTER_LINK_CLASS_NAME}>{presentationText(presentation, 'footer', 'marketLabel', i18n.resolvedLanguage, t)}</Link></li>
                <li><Link data-analytics-id="skill.publish.open" to="/dashboard/publish" className={FOOTER_LINK_CLASS_NAME}>{presentationText(presentation, 'footer', 'publishLabel', i18n.resolvedLanguage, t)}</Link></li>
                <li><Link data-analytics-id="navigation.dashboard.open" to="/dashboard" className={FOOTER_LINK_CLASS_NAME}>{presentationText(presentation, 'footer', 'consoleLabel', i18n.resolvedLanguage, t)}</Link></li>
              </ul>
            </div>}

            {presentationItemVisible(presentation, 'footer', 'developers') && <div>
              <h4 className="mb-4 text-sm font-semibold text-foreground">{presentationText(presentation, 'footer', 'developersTitle', i18n.resolvedLanguage, t)}</h4>
              <ul className="space-y-2.5 text-sm text-muted-foreground">
                <li><a data-analytics-id="navigation.documentation.open" href={presentationLinkUrl(presentation, 'documentation', 'https://github.com/iflytek/skillhub/tree/main/docs/skillhub')} target="_blank" rel="noreferrer" className={FOOTER_LINK_CLASS_NAME}>{presentationText(presentation, 'footer', 'documentationLabel', i18n.resolvedLanguage, t)}</a></li>
                <li><a data-analytics-id="navigation.cli.open" href={presentationLinkUrl(presentation, 'cliPackage')} target="_blank" rel="noreferrer" className={FOOTER_LINK_CLASS_NAME}>{presentationText(presentation, 'footer', 'cliPackageLabel', i18n.resolvedLanguage, t)}</a></li>
                <li><a data-analytics-id="navigation.repository.open" href={presentationLinkUrl(presentation, 'repository')} target="_blank" rel="noreferrer" className={FOOTER_LINK_CLASS_NAME}>{presentationText(presentation, 'footer', 'repositoryLabel', i18n.resolvedLanguage, t)}</a></li>
              </ul>
            </div>}

            {presentationItemVisible(presentation, 'footer', 'project') && <div>
              <h4 className="mb-4 text-sm font-semibold text-foreground">{presentationText(presentation, 'footer', 'projectTitle', i18n.resolvedLanguage, t)}</h4>
              <ul className="space-y-2.5 text-sm text-muted-foreground">
                <li><a data-analytics-id="navigation.license.open" href={presentationLinkUrl(presentation, 'license')} target="_blank" rel="noreferrer" className={FOOTER_LINK_CLASS_NAME}>{presentationText(presentation, 'footer', 'licenseLabel', i18n.resolvedLanguage, t)}</a></li>
                <li><a data-analytics-id="navigation.contributing.open" href={presentationLinkUrl(presentation, 'contributing')} target="_blank" rel="noreferrer" className={FOOTER_LINK_CLASS_NAME}>{presentationText(presentation, 'footer', 'contributingLabel', i18n.resolvedLanguage, t)}</a></li>
                <li><a data-analytics-id="navigation.changelog.open" href={presentationLinkUrl(presentation, 'changelog')} target="_blank" rel="noreferrer" className={FOOTER_LINK_CLASS_NAME}>{presentationText(presentation, 'footer', 'changelogLabel', i18n.resolvedLanguage, t)}</a></li>
                <li><a data-analytics-id="navigation.conduct.open" href={presentationLinkUrl(presentation, 'codeOfConduct')} target="_blank" rel="noreferrer" className={FOOTER_LINK_CLASS_NAME}>{presentationText(presentation, 'footer', 'codeOfConductLabel', i18n.resolvedLanguage, t)}</a></li>
              </ul>
            </div>}

            {presentationItemVisible(presentation, 'footer', 'resources') && <div>
              <h4 className="mb-4 text-sm font-semibold text-foreground">{presentationText(presentation, 'footer', 'resourcesTitle', i18n.resolvedLanguage, t)}</h4>
              <ul className="space-y-2.5 text-sm text-muted-foreground">
                <li><a data-analytics-id="navigation.community.open" href={presentationLinkUrl(presentation, 'community')} target="_blank" rel="noreferrer" className={FOOTER_LINK_CLASS_NAME}>{presentationText(presentation, 'footer', 'communityLabel', i18n.resolvedLanguage, t)}</a></li>
                <li><Link data-analytics-id="navigation.privacy.open" to="/privacy" className={FOOTER_LINK_CLASS_NAME}>{presentationText(presentation, 'footer', 'privacyLabel', i18n.resolvedLanguage, t)}</Link></li>
                <li><Link data-analytics-id="navigation.terms.open" to="/terms" className={FOOTER_LINK_CLASS_NAME}>{presentationText(presentation, 'footer', 'termsLabel', i18n.resolvedLanguage, t)}</Link></li>
              </ul>
            </div>}
          </div>

          <div className="mt-10 border-t border-border/70 pt-7 text-xs text-muted-foreground">
            <span>{presentationText(presentation, 'footer', 'copyright', i18n.resolvedLanguage, t)}</span>
          </div>
        </div>
      </footer>
      ) : null}
    </div>
  )
}
