import { useMemo } from 'react'
import { AlertTriangle, Boxes, CheckCircle2, Copy, Download } from 'lucide-react'
import { useNavigate, useParams, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { getBaseUrl, isPortableSkillVersion } from '@/features/skill/install-command'
import { SuiteMemberTable } from '@/features/suite/suite-member-table'
import { SuiteVersionLedger } from '@/features/suite/suite-version-ledger'
import { withoutDuplicateSuiteTitle } from '@/features/suite/suite-overview'
import { suiteStatusLabel, suiteVisibilityLabel } from '@/features/suite/suite-labels'
import { useSuiteLabels } from '@/shared/hooks/use-label-queries'
import { useSuiteDetail, useSuiteVersions, useSubmitSuite } from '@/shared/hooks/use-suite-queries'
import { NamespaceBadge } from '@/shared/components/namespace-badge'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { toast } from '@/shared/lib/toast'
import { cn } from '@/shared/lib/utils'
import { Button } from '@/shared/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'

export function SuiteDetailPage() {
  const { namespace, slug } = useParams({ from: '/suite/$namespace/$slug' })
  const search = useSearch({ from: '/suite/$namespace/$slug' })
  const navigate = useNavigate()
  const { t } = useTranslation()
  const { data: suite, isLoading, error } = useSuiteDetail(namespace, slug, search.version)
  const { data: versions = [] } = useSuiteVersions(namespace, slug)
  const { data: suiteLabels } = useSuiteLabels(namespace, slug, Boolean(suite))
  const submitMutation = useSubmitSuite()
  const registryUrl = useMemo(() => getBaseUrl(), [])

  if (isLoading) return <div className={APP_SHELL_PAGE_CLASS_NAME}><SkeletonList count={3} /></div>
  if (!suite || error) {
    return <div className={APP_SHELL_PAGE_CLASS_NAME}><p className="text-destructive">{t('suite.notFound')}</p></div>
  }

  const suitePath = `/suite/${encodeURIComponent(namespace)}/${encodeURIComponent(slug)}`
  const returnTo = `${suitePath}?version=${encodeURIComponent(suite.version)}`
  const entryMember = suite.members.find(member => member.entry)
  const command = isPortableSkillVersion(suite.version)
    ? `skillhub suite install @${suite.namespace}/${suite.slug} --version ${suite.version} --registry ${registryUrl}`
    : ''

  const copyCommand = async () => {
    if (!command) return
    await navigator.clipboard.writeText(command)
    toast.success(t('suite.commandCopied'))
  }

  const submit = async () => {
    try {
      await submitMutation.mutateAsync({
        suiteId: suite.id,
        versionId: suite.versionId,
        privatePublish: suite.visibility === 'PRIVATE',
      })
      toast.success(suite.visibility === 'PRIVATE' ? t('suite.published') : t('suite.submitted'))
    } catch (submitError) {
      toast.error(t('suite.actionFailed'), submitError instanceof Error ? submitError.message : '')
    }
  }

  return (
    <div className={cn(APP_SHELL_PAGE_CLASS_NAME, 'mx-auto max-w-7xl space-y-5')}>
      <header className="space-y-4">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0 max-w-3xl space-y-3">
            <div className="flex flex-wrap items-center gap-2">
              <NamespaceBadge type={suite.namespace === 'global' ? 'GLOBAL' : 'TEAM'} name={suite.namespace} />
              <span className="badge-soft badge-soft-blue inline-flex items-center gap-1.5">
                <Boxes className="h-3.5 w-3.5" aria-hidden="true" />
                {t('suite.resourceTypeSuite')}
              </span>
            </div>
            <div>
              <h1 className="break-words font-heading text-2xl font-bold text-foreground [overflow-wrap:anywhere]">
                {suite.displayName}
              </h1>
              <p className="mt-1 font-mono text-sm text-muted-foreground">@{suite.namespace}/{suite.slug}</p>
            </div>
            <p className="text-sm leading-6 text-muted-foreground">{suite.summary || t('suite.noSummary')}</p>
            {suiteLabels?.length ? (
              <div className="flex flex-wrap gap-2" aria-label={t('suite.assignedLabels')}>
                {suiteLabels.map(label => (
                  <span key={label.slug} className="rounded-md border border-indigo-500/20 bg-indigo-500/10 px-2 py-1 text-xs text-indigo-700 dark:text-indigo-300">
                    {label.displayName}
                  </span>
                ))}
              </div>
            ) : null}
          </div>

          <div className="flex flex-col gap-4 sm:flex-row sm:items-center lg:pt-10">
            <div className="min-w-32 border-l pl-5">
              <p className="text-xs text-muted-foreground">{t('suite.currentVersion')}</p>
              <p className="mt-1 font-mono text-lg font-semibold">v{suite.version}</p>
              <p className={cn(
                'mt-1 flex w-fit items-center gap-1.5 rounded-md border px-2 py-1 text-xs font-medium',
                suite.available
                  ? 'border-emerald-500/20 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300'
                  : 'border-amber-500/20 bg-amber-500/10 text-amber-700 dark:text-amber-300',
              )}>
                {suite.available
                  ? <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />
                  : <AlertTriangle className="h-3.5 w-3.5" aria-hidden="true" />}
                {suiteStatusLabel(t, suite.status)}
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              {command ? (
                <Button size="sm" className="gap-2" onClick={copyCommand}>
                  <Download className="h-4 w-4" aria-hidden="true" />
                  {t('suite.installSuite')}
                </Button>
              ) : null}
              {command ? (
                <Button size="sm" variant="soft" className="gap-2" onClick={copyCommand}>
                  <Copy className="h-4 w-4" aria-hidden="true" />
                  {t('suite.copyInstallCommand')}
                </Button>
              ) : null}
              {suite.allowedActions.includes('EDIT') ? (
                <Button size="sm" variant="outline" onClick={() => navigate({
                  to: `/dashboard/suites/${suite.namespace}/${encodeURIComponent(suite.slug)}/edit`,
                  search: { version: suite.version },
                })}>{t('suite.editDraft')}</Button>
              ) : null}
              {suite.allowedActions.includes('SUBMIT') || suite.allowedActions.includes('PUBLISH_PRIVATE') ? (
                <Button size="sm" disabled={submitMutation.isPending} onClick={submit}>
                  {suite.visibility === 'PRIVATE' ? t('suite.publishDirectly') : t('suite.submitReview')}
                </Button>
              ) : null}
            </div>
          </div>
        </div>

        {!suite.available && suite.status === 'PUBLISHED' ? (
          <div className="flex items-start gap-2.5 rounded-lg border border-amber-500/30 bg-amber-500/5 px-4 py-3 text-sm">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-700" aria-hidden="true" />
            <div>
              <p className="font-medium">{t('suite.degraded')}</p>
              <p className="mt-0.5 text-xs text-muted-foreground">{t('suite.degradedDescription')}</p>
            </div>
          </div>
        ) : null}
      </header>

      <Tabs defaultValue="overview">
        <TabsList>
          <TabsTrigger value="overview" className="py-2 text-xs">{t('suite.overviewTab')}</TabsTrigger>
          <TabsTrigger value="members" className="py-2 text-xs">{t('suite.membersTabShort', { count: suite.members.length })}</TabsTrigger>
          <TabsTrigger value="versions" className="py-2 text-xs">{t('suite.versionsTab', { count: versions.length })}</TabsTrigger>
        </TabsList>

        <TabsContent value="overview" className="mt-4">
          <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_17rem]">
            <article className="min-w-0">
              {suite.overview ? (
                <MarkdownRenderer content={withoutDuplicateSuiteTitle(suite.overview, suite.displayName)} />
              ) : (
                <div className="rounded-lg border border-dashed p-6 text-sm text-muted-foreground">
                  {suite.summary || t('suite.noOverview')}
                </div>
              )}
            </article>
            <aside className="space-y-5 border-l border-border/70 pl-5" aria-label={t('suite.overviewSupport')}>
              {entryMember ? (
                <section>
                  <h2 className="text-sm font-semibold">{t('suite.startWithEntry')}</h2>
                  <p className="mt-1 text-xs leading-5 text-muted-foreground">{t('suite.entrySnapshotDescription')}</p>
                  <div className="mt-3 border-y border-border/70 py-3">
                    <p className="truncate text-sm font-medium">
                      {entryMember.displayName || `@${entryMember.namespace}/${entryMember.slug}`}
                    </p>
                    <p className="mt-1 break-all font-mono text-xs text-muted-foreground">
                      @{entryMember.namespace}/{entryMember.slug}@{entryMember.version}
                    </p>
                  </div>
                  {entryMember.browsable && !entryMember.blockingReason && entryMember.skillId && entryMember.skillVersionId ? (
                    <Button size="sm" variant="soft" className="mt-3" onClick={() => navigate({
                      to: '/space/$namespace/$slug',
                      params: { namespace: entryMember.namespace, slug: entryMember.slug },
                      search: { returnTo, version: entryMember.version },
                    })}>
                      {t('suite.viewPinnedVersion')}
                    </Button>
                  ) : (
                    <p className="mt-3 text-xs text-amber-700">{t('suite.memberUnavailable')}</p>
                  )}
                </section>
              ) : null}
              <section className="border-t pt-5">
                <h2 className="text-sm font-semibold">{t('suite.thisVersionContains')}</h2>
                <dl className="mt-3 space-y-3 text-xs">
                  <div className="flex items-center justify-between gap-4">
                    <dt className="text-muted-foreground">{t('suite.memberCount')}</dt>
                    <dd className="font-medium">{suite.members.length}</dd>
                  </div>
                  <div className="flex items-center justify-between gap-4">
                    <dt className="text-muted-foreground">{t('suite.visibility')}</dt>
                    <dd className="font-medium">{suiteVisibilityLabel(t, suite.visibility)}</dd>
                  </div>
                </dl>
              </section>
            </aside>
          </div>
        </TabsContent>

        <TabsContent value="members" className="mt-4">
          <SuiteMemberTable suite={suite} returnTo={returnTo} />
        </TabsContent>

        <TabsContent value="versions" className="mt-4">
          {versions.length ? (
            <SuiteVersionLedger
              namespace={namespace}
              slug={slug}
              versions={versions}
              currentSuite={suite}
              returnTo={returnTo}
              onSelectVersion={(version) => navigate({ to: suitePath, search: { version } })}
            />
          ) : <p className="text-sm text-muted-foreground">{t('suite.noVersions')}</p>}
        </TabsContent>
      </Tabs>
    </div>
  )
}
