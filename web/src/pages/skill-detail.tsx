import { useState } from 'react'
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
import { adminApi, WEB_API_PREFIX } from '@/api/client'
import { useSubmitSkillReport } from '@/features/report/use-skill-reports'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { formatCompactCount } from '@/shared/lib/number-format'
import { NamespaceBadge } from '@/shared/components/namespace-badge'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/shared/ui/tabs'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Textarea } from '@/shared/ui/textarea'
import { toast } from '@/shared/lib/toast'
import {
  useSkillDetail,
  useSkillVersions,
  useSkillFiles,
  useSkillReadme,
  useArchiveSkill,
  useDeleteSkillVersion,
  useUnarchiveSkill,
} from '@/shared/hooks/use-skill-queries'

export function SkillDetailPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const location = useRouterState({ select: (s) => s.location })
  const queryClient = useQueryClient()
  const [reportDialogOpen, setReportDialogOpen] = useState(false)
  const [reportReason, setReportReason] = useState('')
  const [reportDetails, setReportDetails] = useState('')
  const [archiveConfirmOpen, setArchiveConfirmOpen] = useState(false)
  const [unarchiveConfirmOpen, setUnarchiveConfirmOpen] = useState(false)
  const [deleteVersionTarget, setDeleteVersionTarget] = useState<string | null>(null)
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
  const archiveMutation = useArchiveSkill()
  const unarchiveMutation = useUnarchiveSkill()
  const deleteVersionMutation = useDeleteSkillVersion()
  const reportMutation = useSubmitSkillReport(namespace, slug)

  const handleDownload = () => {
    if (!user) {
      requireLogin()
      return
    }
    if (!latestVersion) {
      return
    }
    const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
    const downloadUrl = `${WEB_API_PREFIX}/skills/${cleanNamespace}/${slug}/versions/${latestVersion.version}/download`
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

  const handleOpenReport = () => {
    if (!user) {
      requireLogin()
      return
    }
    setReportDialogOpen(true)
  }

  const handleSubmitReport = async () => {
    if (!reportReason.trim()) {
      toast.error(t('skillDetail.reportReasonRequired'))
      return
    }

    try {
      await reportMutation.mutateAsync({
        reason: reportReason.trim(),
        details: reportDetails.trim() || undefined,
      })
      setReportDialogOpen(false)
      setReportReason('')
      setReportDetails('')
      toast.success(t('skillDetail.reportSuccessTitle'), t('skillDetail.reportSuccessDescription'))
    } catch (error) {
      toast.error(t('skillDetail.reportErrorTitle'), error instanceof Error ? error.message : '')
    }
  }

  const handleBack = () => {
    if (window.history.length > 1) {
      window.history.back()
      return
    }
    navigate({ to: '/search', search: { q: '', sort: 'relevance', page: 0, starredOnly: false } })
  }

  const resolveSkillStatusLabel = (status?: string) => {
    if (status === 'ARCHIVED') {
      return t('skillDetail.statusArchived')
    }
    if (status === 'ACTIVE') {
      return t('skillDetail.statusActive')
    }
    if (status === 'HIDDEN') {
      return t('skillDetail.statusHidden')
    }
    return status ?? ''
  }

  const canDeleteVersion = (status?: string) => status === 'DRAFT' || status === 'REJECTED'

  const handleArchive = async () => {
    try {
      await archiveMutation.mutateAsync({ namespace, slug })
      toast.success(
        t('skillDetail.archiveSuccessTitle'),
        t('skillDetail.archiveSuccessDescription', { skill: skill?.displayName ?? slug }),
      )
      setArchiveConfirmOpen(false)
    } catch (error) {
      toast.error(t('skillDetail.archiveErrorTitle'), error instanceof Error ? error.message : '')
      throw error
    }
  }

  const handleUnarchive = async () => {
    try {
      await unarchiveMutation.mutateAsync({ namespace, slug })
      toast.success(
        t('skillDetail.unarchiveSuccessTitle'),
        t('skillDetail.unarchiveSuccessDescription', { skill: skill?.displayName ?? slug }),
      )
      setUnarchiveConfirmOpen(false)
    } catch (error) {
      toast.error(t('skillDetail.unarchiveErrorTitle'), error instanceof Error ? error.message : '')
      throw error
    }
  }

  const handleDeleteVersion = async () => {
    if (!deleteVersionTarget) {
      return
    }
    try {
      await deleteVersionMutation.mutateAsync({ namespace, slug, version: deleteVersionTarget })
      toast.success(
        t('skillDetail.deleteVersionSuccessTitle'),
        t('skillDetail.deleteVersionSuccessDescription', { version: deleteVersionTarget }),
      )
      setDeleteVersionTarget(null)
    } catch (error) {
      toast.error(t('skillDetail.deleteVersionErrorTitle'), error instanceof Error ? error.message : '')
      throw error
    }
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
            {skill.status && (
              <span className="rounded-full border border-border/60 bg-secondary/40 px-2.5 py-0.5 text-xs text-muted-foreground">
                {resolveSkillStatusLabel(skill.status)}
              </span>
            )}
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
                          {version.status && (
                            <span className="rounded-full border border-border/60 bg-secondary/40 px-2.5 py-0.5 text-xs text-muted-foreground">
                              {version.status}
                            </span>
                          )}
                        </span>
                        <div className="flex items-center gap-3">
                          <span className="text-sm text-muted-foreground">
                            {formatLocalDateTime(version.publishedAt, i18n.language)}
                          </span>
                          {skill.canManageLifecycle && canDeleteVersion(version.status) && (
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => setDeleteVersionTarget(version.version)}
                            >
                              {t('skillDetail.deleteVersion')}
                            </Button>
                          )}
                        </div>
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
            <div className="font-semibold text-foreground">{formatCompactCount(skill.downloadCount)}</div>
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
            <Button variant="outline" className="w-full" onClick={handleOpenReport} disabled={reportMutation.isPending}>
              {reportMutation.isPending ? t('skillDetail.processing') : t('skillDetail.reportSkill')}
            </Button>
            {!user && (
              <p className="text-xs text-muted-foreground">{t('skillDetail.loginToRate')}</p>
            )}
          </div>
        </Card>

        {skill.latestVersion && (
          <Card className="p-5 space-y-4">
            <div className="text-sm font-semibold font-heading text-foreground">{t('skillDetail.install')}</div>
            {skill.status === 'ARCHIVED' && (
              <p className="text-sm text-muted-foreground">{t('skillDetail.archivedInstallHint')}</p>
            )}
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
          disabled={!latestVersion || skill.status === 'ARCHIVED'}
        >
          <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10" />
          </svg>
          {t('skillDetail.download')}
        </Button>

        {skill.canManageLifecycle && (
          <Card className="p-5 space-y-3">
            <div className="text-sm font-semibold font-heading text-foreground">{t('skillDetail.lifecycle')}</div>
            <p className="text-sm text-muted-foreground">
              {skill.status === 'ARCHIVED'
                ? t('skillDetail.archivedPublishHint')
                : t('skillDetail.lifecycleHint')}
            </p>
            {skill.status === 'ARCHIVED' ? (
              <Button variant="outline" onClick={() => setUnarchiveConfirmOpen(true)} disabled={unarchiveMutation.isPending}>
                {unarchiveMutation.isPending ? t('skillDetail.processing') : t('skillDetail.unarchiveSkill')}
              </Button>
            ) : (
              <Button variant="outline" onClick={() => setArchiveConfirmOpen(true)} disabled={archiveMutation.isPending}>
                {archiveMutation.isPending ? t('skillDetail.processing') : t('skillDetail.archiveSkill')}
              </Button>
            )}
          </Card>
        )}

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

      <Dialog open={reportDialogOpen} onOpenChange={setReportDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('skillDetail.reportDialogTitle')}</DialogTitle>
            <DialogDescription>{t('skillDetail.reportDialogDescription')}</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <Input
              value={reportReason}
              onChange={(event) => setReportReason(event.target.value)}
              placeholder={t('skillDetail.reportReasonPlaceholder')}
              maxLength={200}
            />
            <Textarea
              value={reportDetails}
              onChange={(event) => setReportDetails(event.target.value)}
              placeholder={t('skillDetail.reportDetailsPlaceholder')}
              rows={5}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setReportDialogOpen(false)}>
              {t('dialog.cancel')}
            </Button>
            <Button onClick={handleSubmitReport} disabled={reportMutation.isPending}>
              {reportMutation.isPending ? t('skillDetail.processing') : t('skillDetail.submitReport')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={archiveConfirmOpen}
        onOpenChange={setArchiveConfirmOpen}
        title={t('skillDetail.archiveConfirmTitle')}
        description={t('skillDetail.archiveConfirmDescription', { skill: skill.displayName })}
        confirmText={t('skillDetail.archiveSkill')}
        onConfirm={handleArchive}
      />

      <ConfirmDialog
        open={unarchiveConfirmOpen}
        onOpenChange={setUnarchiveConfirmOpen}
        title={t('skillDetail.unarchiveConfirmTitle')}
        description={t('skillDetail.unarchiveConfirmDescription', { skill: skill.displayName })}
        confirmText={t('skillDetail.unarchiveSkill')}
        onConfirm={handleUnarchive}
      />

      <ConfirmDialog
        open={!!deleteVersionTarget}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteVersionTarget(null)
          }
        }}
        title={t('skillDetail.deleteVersionConfirmTitle')}
        description={deleteVersionTarget ? t('skillDetail.deleteVersionConfirmDescription', { version: deleteVersionTarget }) : ''}
        confirmText={t('skillDetail.deleteVersion')}
        variant="destructive"
        onConfirm={handleDeleteVersion}
      />
    </div>
  )
}
