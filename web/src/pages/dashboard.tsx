import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { tokenApi } from '@/api/client'
import { useAuth } from '@/features/auth/use-auth'
import { useMySkills } from '@/shared/hooks/use-skill-queries'
import { TokenList } from '@/features/token/token-list'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { limitPreviewItems } from './dashboard-preview'

const DASHBOARD_PREVIEW_LIMIT = 3

export function DashboardPage() {
  const { t } = useTranslation()
  const { user, hasRole } = useAuth()
  const governanceVisible = hasRole('SKILL_ADMIN') || hasRole('SUPER_ADMIN')
  const { data: skills, isLoading: isLoadingSkills } = useMySkills()
  const { data: tokenPage, isLoading: isLoadingTokens } = useQuery({
    queryKey: ['tokens', 'dashboard-preview'],
    queryFn: () => tokenApi.getTokens({ page: 0, size: DASHBOARD_PREVIEW_LIMIT + 1 }),
  })
  const skillPreview = limitPreviewItems(skills ?? [], DASHBOARD_PREVIEW_LIMIT)
  const tokenPreview = limitPreviewItems(tokenPage?.items ?? [], DASHBOARD_PREVIEW_LIMIT)

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

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>{t('mySkills.title')}</CardTitle>
            <CardDescription>{t('dashboard.mySkillsPreviewDescription')}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {isLoadingSkills ? (
              <div className="space-y-2">
                {Array.from({ length: DASHBOARD_PREVIEW_LIMIT }).map((_, index) => (
                  <div key={index} className="h-10 animate-shimmer rounded-lg" />
                ))}
              </div>
            ) : skillPreview.items.length > 0 ? (
              <>
                {skillPreview.items.map((skill) => (
                  <Link
                    key={skill.id}
                    to="/space/$namespace/$slug"
                    params={{ namespace: skill.namespace, slug: skill.slug }}
                    className="flex items-center justify-between rounded-lg border border-border/60 px-3 py-2 transition-colors hover:bg-accent/40"
                  >
                    <div className="min-w-0">
                      <div className="truncate text-sm font-medium">{skill.displayName}</div>
                      <div className="truncate text-xs text-muted-foreground">@{skill.namespace}</div>
                    </div>
                    {skill.latestVersion ? (
                      <span className="ml-3 shrink-0 rounded-full bg-secondary px-2 py-1 text-xs text-muted-foreground">
                        v{skill.latestVersion}
                      </span>
                    ) : null}
                  </Link>
                ))}
                {skillPreview.hasMore ? (
                  <div className="inline-flex rounded-full bg-secondary px-3 py-1 text-xs text-muted-foreground">
                    {t('dashboard.previewMore', { count: skillPreview.remainingCount })}
                  </div>
                ) : null}
              </>
            ) : (
              <div className="text-sm text-muted-foreground">{t('dashboard.mySkillsPreviewEmpty')}</div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t('token.title')}</CardTitle>
            <CardDescription>{t('dashboard.tokensPreviewDescription')}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {isLoadingTokens ? (
              <div className="space-y-2">
                {Array.from({ length: DASHBOARD_PREVIEW_LIMIT }).map((_, index) => (
                  <div key={index} className="h-10 animate-shimmer rounded-lg" />
                ))}
              </div>
            ) : tokenPreview.items.length > 0 ? (
              <>
                {tokenPreview.items.map((token) => (
                  <div
                    key={token.id}
                    className="flex items-center justify-between rounded-lg border border-border/60 px-3 py-2"
                  >
                    <div className="min-w-0">
                      <div className="truncate text-sm font-medium">{token.name}</div>
                      <div className="truncate text-xs text-muted-foreground">{token.tokenPrefix}...</div>
                    </div>
                  </div>
                ))}
                {tokenPreview.hasMore ? (
                  <div className="inline-flex rounded-full bg-secondary px-3 py-1 text-xs text-muted-foreground">
                    {t('dashboard.previewMore', { count: tokenPreview.remainingCount })}
                  </div>
                ) : null}
              </>
            ) : (
              <div className="text-sm text-muted-foreground">{t('dashboard.tokensPreviewEmpty')}</div>
            )}
          </CardContent>
        </Card>
      </div>

      <TokenList />
    </div>
  )
}
