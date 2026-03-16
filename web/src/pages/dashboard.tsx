import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/use-auth'
import { useMySkills } from '@/shared/hooks/use-skill-queries'
import { TokenList } from '@/features/token/token-list'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { limitPreviewItems } from './dashboard-preview'

const DASHBOARD_PREVIEW_LIMIT = 4

export function DashboardPage() {
  const { t } = useTranslation()
  const { user, hasRole } = useAuth()
  const governanceVisible = hasRole('SKILL_ADMIN') || hasRole('SUPER_ADMIN')
  const { data: skills, isLoading: isLoadingSkills } = useMySkills()
  const skillPreview = limitPreviewItems(skills ?? [], DASHBOARD_PREVIEW_LIMIT)

  return (
    <div className="space-y-8 animate-fade-up">
      <div>
        <h1 className="text-4xl font-bold font-heading text-foreground">{t('dashboard.title')}</h1>
        <p className="text-muted-foreground mt-2 text-lg">
          {t('dashboard.subtitle')}
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t('dashboard.userInfo')}</CardTitle>
          <CardDescription>{t('dashboard.userInfoDesc')}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="flex items-center gap-5">
            {user?.avatarUrl && (
              <img
                src={user.avatarUrl}
                alt={user.displayName}
                className="h-20 w-20 rounded-2xl border-2 border-border/60 shadow-card"
              />
            )}
            <div className="space-y-1.5">
              <div className="text-xl font-semibold font-heading">{user?.displayName}</div>
              <div className="text-sm text-muted-foreground">{user?.email}</div>
              <div className="text-xs text-muted-foreground flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-emerald-500" />
                {t('dashboard.loginVia', { provider: user?.oauthProvider })}
              </div>
            </div>
          </div>
          {user?.platformRoles && user.platformRoles.length > 0 && (
            <div className="space-y-3">
              <div className="text-sm font-medium font-heading">{t('dashboard.platformRoles')}</div>
              <div className="flex flex-wrap gap-2">
                {user.platformRoles.map((role: string) => (
                  <span
                    key={role}
                    className="inline-flex items-center rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary border border-primary/20"
                  >
                    {role}
                  </span>
                ))}
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      <div className={`grid grid-cols-1 gap-4 ${governanceVisible ? 'md:grid-cols-5' : 'md:grid-cols-4'}`}>
        <Card className="p-5">
          <div className="text-sm text-muted-foreground">{t('dashboard.starsAndRatings')}</div>
          <Link to="/dashboard/stars" className="mt-2 inline-block font-semibold text-primary hover:underline">
            {t('dashboard.viewStars')}
          </Link>
        </Card>
        <Card className="p-5">
          <div className="text-sm text-muted-foreground">{t('dashboard.mySkillsTitle')}</div>
          <Link to="/dashboard/skills" className="mt-2 inline-block font-semibold text-primary hover:underline">
            {t('dashboard.openMySkills')}
          </Link>
        </Card>
        <Card className="p-5">
          <div className="text-sm text-muted-foreground">{t('dashboard.credentials')}</div>
          <Link to="/dashboard/tokens" className="mt-2 inline-block font-semibold text-primary hover:underline">
            {t('dashboard.openTokens')}
          </Link>
        </Card>
        <Card className="p-5">
          <div className="text-sm text-muted-foreground">{t('dashboard.governanceTitle')}</div>
          <Link to="/dashboard/governance" className="mt-2 inline-block font-semibold text-primary hover:underline">
            {t('dashboard.viewGovernance')}
          </Link>
        </Card>
        {governanceVisible ? (
          <Card className="p-5">
            <div className="text-sm text-muted-foreground">{t('dashboard.reportsTitle')}</div>
            <Link to="/dashboard/reports" className="mt-2 inline-block font-semibold text-primary hover:underline">
              {t('dashboard.viewReports')}
            </Link>
          </Card>
        ) : null}
      </div>

      <div className="grid grid-cols-1 gap-8 xl:grid-cols-2 xl:items-start">
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-xl font-semibold">{t('mySkills.title')}</h2>
            <Link to="/dashboard/skills" className="text-sm font-semibold text-primary hover:underline">
              {t('dashboard.openMySkills')}
            </Link>
          </div>
          <p className="text-sm text-muted-foreground">{t('dashboard.mySkillsPreviewDescription')}</p>
          <Card>
            <CardContent className="p-4">
              {isLoadingSkills ? (
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  {Array.from({ length: DASHBOARD_PREVIEW_LIMIT }).map((_, index) => (
                    <div key={index} className="h-20 animate-shimmer rounded-lg" />
                  ))}
                </div>
              ) : skillPreview.items.length > 0 ? (
                <div className="space-y-3">
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                    {skillPreview.items.map((skill) => (
                      <Link
                        key={skill.id}
                        to="/space/$namespace/$slug"
                        params={{ namespace: skill.namespace, slug: skill.slug }}
                        className="rounded-lg border border-border/60 px-3 py-3 transition-colors hover:bg-accent/40"
                      >
                        <div className="truncate text-sm font-medium">{skill.displayName}</div>
                        <div className="mt-1 truncate text-xs text-muted-foreground">@{skill.namespace}</div>
                        {skill.latestVersion ? (
                          <div className="mt-2 inline-flex rounded-full bg-secondary px-2 py-1 text-xs text-muted-foreground">
                            v{skill.latestVersion}
                          </div>
                        ) : null}
                      </Link>
                    ))}
                  </div>
                  {skillPreview.hasMore ? (
                    <div className="inline-flex rounded-full bg-secondary px-3 py-1 text-xs text-muted-foreground">
                      {t('dashboard.previewMore')}
                    </div>
                  ) : null}
                </div>
              ) : (
                <div className="text-sm text-muted-foreground">{t('dashboard.mySkillsPreviewEmpty')}</div>
              )}
            </CardContent>
          </Card>
        </div>

        <div className="space-y-4">
          <TokenList />
        </div>
      </div>
    </div>
  )
}
