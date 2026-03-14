import { useTranslation } from 'react-i18next'
import { useParams, useNavigate, useRouterState } from '@tanstack/react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft } from 'lucide-react'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { FileTree } from '@/features/skill/file-tree'
import { InstallCommand } from '@/features/skill/install-command'
import { RatingInput } from '@/features/social/rating-input'
import { StarButton } from '@/features/social/star-button'
import { useAuth } from '@/features/auth/use-auth'
import { adminApi } from '@/api/client'
import { NamespaceBadge } from '@/shared/components/namespace-badge'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/shared/ui/tabs'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import {
  useSkillDetail,
  useSkillVersions,
  useSkillFiles,
  useSkillReadme,
} from '@/shared/hooks/use-skill-queries'

export function SkillDetailPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const location = useRouterState({ select: (s) => s.location })
  const queryClient = useQueryClient()
  const { namespace, slug } = useParams({ from: '/space/$namespace/$slug' })
  const { user, hasRole } = useAuth()

  const { data: skill, isLoading: isLoadingSkill, error: skillError } = useSkillDetail(namespace, slug)
  const { data: versions } = useSkillVersions(namespace, slug)
  const latestVersion = versions?.[0]
  const { data: files } = useSkillFiles(namespace, slug, latestVersion?.version)
  const { data: readme } = useSkillReadme(namespace, slug, latestVersion?.version)
  const governanceVisible = hasRole('SKILL_ADMIN') || hasRole('SUPER_ADMIN')

  const refreshSkill = () => {
    queryClient.invalidateQueries({ queryKey: ['skills', namespace, slug] })
    queryClient.invalidateQueries({ queryKey: ['skills', namespace, slug, 'versions'] })
    queryClient.invalidateQueries({ queryKey: ['skills'] })
  }

  const hideMutation = useMutation({
    mutationFn: () => adminApi.hideSkill(skill!.id),
    onSuccess: refreshSkill,
  })

  const unhideMutation = useMutation({
    mutationFn: () => adminApi.unhideSkill(skill!.id),
    onSuccess: refreshSkill,
  })

  const yankMutation = useMutation({
    mutationFn: () => adminApi.yankVersion(latestVersion!.id),
    onSuccess: refreshSkill,
  })

  const handleDownload = () => {
    if (!user) {
      requireLogin()
      return
    }
    if (!latestVersion) {
      return
    }
    const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
    const downloadUrl = `/api/v1/skills/${cleanNamespace}/${slug}/versions/${latestVersion.version}/download`
    window.open(downloadUrl, '_blank')
  }

  const requireLogin = () => {
    navigate({
      to: '/login',
      search: {
        returnTo: `${location.pathname}${location.searchStr}${location.hash}`,
      },
    })
  }

  const handleBack = () => {
    if (window.history.length > 1) {
      window.history.back()
      return
    }
    navigate({ to: '/search', search: { q: '', sort: 'relevance', page: 0 } })
  }

  if (isLoadingSkill) {
    return (
      <div className="space-y-6 animate-fade-up">
        <div className="h-10 w-64 animate-shimmer rounded-lg" />
        <div className="h-5 w-96 animate-shimmer rounded-md" />
        <div className="h-64 animate-shimmer rounded-xl" />
      </div>
    )
  }

  if (skillError) {
    const isForbidden = skillError instanceof Error && skillError.message.includes('403')

    if (isForbidden && !user) {
      return (
        <div className="text-center py-20 animate-fade-up">
          <h2 className="text-2xl font-bold font-heading mb-2">{t('skillDetail.loginRequired')}</h2>
          <p className="text-muted-foreground mb-6">{t('skillDetail.loginRequiredDesc')}</p>
          <Button onClick={requireLogin}>{t('common.login')}</Button>
        </div>
      )
    }

    return (
      <div className="text-center py-20 animate-fade-up">
        <h2 className="text-2xl font-bold font-heading mb-2">{t('skillDetail.accessDenied')}</h2>
        <p className="text-muted-foreground">{t('skillDetail.accessDeniedDesc')}</p>
      </div>
    )
  }

  if (!skill) {
    return (
      <div className="text-center py-20 animate-fade-up">
        <h2 className="text-2xl font-bold font-heading mb-2">{t('skillDetail.notFound')}</h2>
        <p className="text-muted-foreground">{t('skillDetail.notFoundDesc')}</p>
      </div>
    )
  }

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 animate-fade-up">
      {/* Main Content */}
      <div className="lg:col-span-2 space-y-8">
        <div className="space-y-3">
          <Button
            variant="ghost"
            size="sm"
            className="gap-2 px-0 text-muted-foreground hover:text-foreground"
            onClick={handleBack}
          >
            <ArrowLeft className="h-4 w-4" />
            {t('skillDetail.back')}
          </Button>
          <div className="flex items-center gap-3 mb-1">
            <NamespaceBadge type="GLOBAL" name={namespace} />
          </div>
          <h1 className="text-4xl font-bold font-heading text-foreground">{skill.displayName}</h1>
          {skill.summary && (
            <p className="text-lg text-muted-foreground leading-relaxed">{skill.summary}</p>
          )}
        </div>

        <Tabs defaultValue="readme">
          <TabsList>
            <TabsTrigger value="readme">{t('skillDetail.tabReadme')}</TabsTrigger>
            <TabsTrigger value="files">{t('skillDetail.tabFiles')}</TabsTrigger>
            <TabsTrigger value="versions">{t('skillDetail.tabVersions')}</TabsTrigger>
          </TabsList>

          <TabsContent value="readme" className="mt-6">
            {readme ? (
              <Card className="p-8">
                <MarkdownRenderer content={readme} />
              </Card>
            ) : (
              <Card className="p-8 text-muted-foreground text-center">
                {t('skillDetail.noReadme')}
              </Card>
            )}
          </TabsContent>

          <TabsContent value="files" className="mt-6">
            {files && files.length > 0 ? (
              <FileTree files={files} />
            ) : (
              <Card className="p-8 text-muted-foreground text-center">
                {t('skillDetail.noFiles')}
              </Card>
            )}
          </TabsContent>

          <TabsContent value="versions" className="mt-6">
            <Card className="p-6">
              {versions && versions.length > 0 ? (
                <div className="space-y-0 divide-y divide-border/40">
                  {versions.map((version) => (
                    <div key={version.id} className="py-5 first:pt-0 last:pb-0">
                      <div className="flex items-center justify-between mb-2">
                        <span className="font-semibold font-heading text-foreground flex items-center gap-2">
                          <span className="px-2.5 py-0.5 rounded-full bg-primary/10 text-primary text-sm font-mono">
                            v{version.version}
                          </span>
                        </span>
                        <span className="text-sm text-muted-foreground">
                          {new Date(version.publishedAt).toLocaleDateString(i18n.language)}
                        </span>
                      </div>
                      {version.changelog && (
                        <p className="text-sm text-muted-foreground leading-relaxed">{version.changelog}</p>
                      )}
                      <div className="text-xs text-muted-foreground mt-2 flex items-center gap-3">
                        <span>{t('skillDetail.fileCount', { count: version.fileCount })}</span>
                        <span className="w-1 h-1 rounded-full bg-muted-foreground/40" />
                        <span>{(version.totalSize / 1024).toFixed(1)} KB</span>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="text-muted-foreground text-center py-8">{t('skillDetail.noVersions')}</div>
              )}
            </Card>
          </TabsContent>
        </Tabs>
      </div>

      {/* Sidebar */}
      <div className="space-y-5">
        <Card className="p-5 space-y-5">
          <div className="flex items-center justify-between">
            <div className="text-sm text-muted-foreground">{t('skillDetail.version')}</div>
            <div className="font-semibold font-mono text-foreground">
              {skill.latestVersion ? `v${skill.latestVersion}` : '—'}
            </div>
          </div>

          <div className="h-px bg-border/40" />

          <div className="flex items-center justify-between">
            <div className="text-sm text-muted-foreground">{t('skillDetail.downloads')}</div>
            <div className="font-semibold text-foreground">{skill.downloadCount.toLocaleString()}</div>
          </div>

          <div className="h-px bg-border/40" />

          <div className="flex items-center justify-between">
            <div className="text-sm text-muted-foreground">{t('skillDetail.rating')}</div>
            <div className="font-semibold text-foreground">
              {skill.ratingCount > 0 && skill.ratingAvg !== undefined ? `${skill.ratingAvg.toFixed(1)} / 5` : t('skillDetail.ratingNone')}
            </div>
          </div>

          <div className="h-px bg-border/40" />

          <div className="flex items-center justify-between">
            <div className="text-sm text-muted-foreground">{t('skillDetail.namespaceLabel')}</div>
            <NamespaceBadge type="GLOBAL" name={namespace} />
          </div>

          <div className="h-px bg-border/40" />

          <div className="space-y-3">
            <StarButton skillId={skill.id} starCount={skill.starCount} onRequireLogin={requireLogin} />
            <RatingInput skillId={skill.id} onRequireLogin={requireLogin} />
            {!user && (
              <p className="text-xs text-muted-foreground">{t('skillDetail.loginToRate')}</p>
            )}
          </div>
        </Card>

        {skill.latestVersion && (
          <Card className="p-5 space-y-4">
            <div className="text-sm font-semibold font-heading text-foreground">{t('skillDetail.install')}</div>
            <InstallCommand
              namespace={namespace}
              slug={slug}
              version={skill.latestVersion}
            />
          </Card>
        )}

        <Button
          className="w-full"
          variant="outline"
          size="lg"
          onClick={handleDownload}
          disabled={!latestVersion}
        >
          <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10" />
          </svg>
          {t('skillDetail.download')}
        </Button>

        {governanceVisible && (
          <Card className="p-5 space-y-3">
            <div className="text-sm font-semibold font-heading text-foreground">{t('skillDetail.governance')}</div>
            <div className="flex flex-col gap-3">
              {!skill.hidden ? (
                <Button variant="outline" onClick={() => hideMutation.mutate()} disabled={hideMutation.isPending}>
                  {hideMutation.isPending ? t('skillDetail.processing') : t('skillDetail.hideSkill')}
                </Button>
              ) : (
                <Button variant="outline" onClick={() => unhideMutation.mutate()} disabled={unhideMutation.isPending}>
                  {unhideMutation.isPending ? t('skillDetail.processing') : t('skillDetail.unhideSkill')}
                </Button>
              )}
              {latestVersion && (
                <Button variant="destructive" onClick={() => yankMutation.mutate()} disabled={yankMutation.isPending}>
                  {yankMutation.isPending ? t('skillDetail.processing') : t('skillDetail.yankVersion')}
                </Button>
              )}
            </div>
          </Card>
        )}
      </div>
    </div>
  )
}
