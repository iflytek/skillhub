import { Suspense } from 'react'
import { Outlet, Link, useRouterState } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/use-auth'
import { LandingPage } from '@/pages/landing'
import { LanguageSwitcher } from '@/shared/components/language-switcher'
import { UserMenu } from '@/shared/components/user-menu'

export function Layout() {
  const { t } = useTranslation()
  const pathname = useRouterState({ select: (s) => s.location.pathname })
  const isLanding = pathname === '/'
  const { user, isLoading } = useAuth(!isLanding)

  if (isLanding) {
    return <LandingPage />
  }

  return (
    <div className="min-h-screen bg-background bg-dots relative">
      <div className="glow-orb-primary" style={{ top: '-10%', right: '10%' }} />
      <div className="glow-orb-accent" style={{ bottom: '20%', left: '-5%' }} />

      <header className="sticky top-0 z-50 glass-strong border-b border-border/40">
        <div className="container mx-auto flex h-16 items-center justify-between px-4 lg:px-8">
          <div className="flex items-center gap-8">
            <Link to="/" className="flex items-center gap-2 group">
              <img src="/skillhub-logo-dark.svg" alt="SkillHub" className="w-12 h-12" />
              <span className="text-xl font-bold font-heading text-foreground group-hover:text-primary transition-colors">
                SkillHub
              </span>
            </Link>

            <nav className="hidden md:flex items-center gap-6">
              <Link
                to="/search"
                search={{ q: '', sort: 'relevance', page: 0, starredOnly: false }}
                className="text-sm font-medium text-muted-foreground hover:text-foreground transition-colors"
                activeProps={{ className: 'text-primary' }}
              >
                {t('nav.explore')}
              </Link>
            </nav>
          </div>

          <nav className="flex items-center gap-6">
            <LanguageSwitcher />
            {isLoading ? null : user ? (
              <UserMenu user={user} />
            ) : (
              <Link
                to="/login"
                search={{ returnTo: '' }}
                className="text-sm font-medium text-muted-foreground hover:text-foreground transition-colors"
                activeProps={{ className: 'text-primary' }}
              >
                {t('nav.login')}
              </Link>
            )}
          </nav>
        </div>
      </header>

      <main className="container mx-auto px-4 lg:px-8 py-12 relative z-10">
        <Suspense
          fallback={
            <div className="space-y-4 animate-fade-up">
              <div className="h-10 w-48 animate-shimmer rounded-lg" />
              <div className="h-5 w-72 animate-shimmer rounded-md" />
              <div className="h-64 animate-shimmer rounded-xl" />
            </div>
          }
        >
          <Outlet />
        </Suspense>
      </main>

      <footer className="relative z-10 border-t border-border/40 bg-card/30 backdrop-blur-sm mt-24">
        <div className="container mx-auto px-4 lg:px-8 py-12">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-8 mb-8">
            <div className="col-span-1 md:col-span-2">
              <div className="flex items-center gap-2 mb-4">
                <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-primary to-primary/70 flex items-center justify-center shadow-glow">
                  <span className="text-primary-foreground font-bold text-sm">S</span>
                </div>
                <span className="text-xl font-bold font-heading text-foreground">SkillHub</span>
              </div>
              <p className="text-sm text-muted-foreground max-w-sm">
                {t('layout.footerDescription')}
              </p>
            </div>

            <div>
              <h3 className="text-sm font-semibold font-heading text-foreground mb-3">{t('nav.home')}</h3>
              <ul className="space-y-2">
                <li>
                  <Link to="/" className="text-sm text-muted-foreground hover:text-primary transition-colors">
                    {t('nav.home')}
                  </Link>
                </li>
                <li>
                  <Link
                    to="/search"
                    search={{ q: '', sort: 'relevance', page: 0, starredOnly: false }}
                    className="text-sm text-muted-foreground hover:text-primary transition-colors"
                  >
                    {t('nav.search')}
                  </Link>
                </li>
                <li>
                  <Link to="/dashboard" className="text-sm text-muted-foreground hover:text-primary transition-colors">
                    {t('nav.dashboard')}
                  </Link>
                </li>
              </ul>
            </div>

            <div>
              <h3 className="text-sm font-semibold font-heading text-foreground mb-3">{t('footer.resources')}</h3>
              <ul className="space-y-2">
                <li>
                  <a href="#" className="text-sm text-muted-foreground hover:text-primary transition-colors">
                    {t('footer.docs')}
                  </a>
                </li>
                <li>
                  <a href="#" className="text-sm text-muted-foreground hover:text-primary transition-colors">
                    {t('footer.api')}
                  </a>
                </li>
                <li>
                  <a href="#" className="text-sm text-muted-foreground hover:text-primary transition-colors">
                    {t('footer.community')}
                  </a>
                </li>
              </ul>
            </div>
          </div>

          <div className="pt-6 border-t border-border/40 flex flex-col md:flex-row items-center justify-between gap-4">
            <p className="text-xs text-muted-foreground">
              {t('footer.copyright')}
            </p>
            <div className="flex items-center gap-4">
              <Link to="/privacy" className="text-xs text-muted-foreground hover:text-primary transition-colors">
                {t('footer.privacy')}
              </Link>
              <Link to="/terms" className="text-xs text-muted-foreground hover:text-primary transition-colors">
                {t('footer.terms')}
              </Link>
            </div>
          </div>
        </div>
      </footer>
    </div>
  )
}
