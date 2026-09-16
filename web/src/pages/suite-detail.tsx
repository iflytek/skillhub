import { useMemo } from 'react'
import { Link, useNavigate, useParams, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { AlertTriangle, ArrowUpRight, Boxes, CheckCircle2, Copy, Terminal, Wrench } from 'lucide-react'
import { useSuiteDetail, useSuiteVersions, useSubmitSuite } from '@/shared/hooks/use-suite-queries'
import { suiteBlockingReasonLabel, suiteStatusLabel, suiteVisibilityLabel } from '@/features/suite/suite-labels'
import { SuiteManagementActions } from '@/features/suite/suite-management-actions'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { getBaseUrl, isPortableSkillVersion } from '@/features/skill/install-command'
import { Card } from '@/shared/ui/card'
import { Button, buttonVariants } from '@/shared/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { NamespaceBadge } from '@/shared/components/namespace-badge'
import { toast } from '@/shared/lib/toast'
import { cn } from '@/shared/lib/utils'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'

export function SuiteDetailPage() {
  const { namespace, slug } = useParams({ from: '/suite/$namespace/$slug' })
  const { t } = useTranslation()
  const search = useSearch({ from: '/suite/$namespace/$slug' })
  const navigate = useNavigate()
  const { data: suite, isLoading, error } = useSuiteDetail(namespace, slug, search.version)
  const { data: versions } = useSuiteVersions(namespace, slug)
  const submitMutation = useSubmitSuite()
  const registryUrl = useMemo(() => getBaseUrl(), [])
  const command = useMemo(
    () => suite && isPortableSkillVersion(suite.version)
      ? `skillhub suite install @${suite.namespace}/${suite.slug} --version ${suite.version} --registry ${registryUrl}`
      : '',
    [registryUrl, suite],
  )
  const suitePath = `/suite/${encodeURIComponent(namespace)}/${encodeURIComponent(slug)}`
  const returnTo = search.version
    ? `${suitePath}?version=${encodeURIComponent(search.version)}`
    : suitePath

  if (isLoading) return <div className={APP_SHELL_PAGE_CLASS_NAME}><SkeletonList count={3} /></div>
  if (!suite || error) {
    return <div className={APP_SHELL_PAGE_CLASS_NAME}><p className="text-destructive">{t('suite.notFound')}</p></div>
  }

  const handleSubmit = async () => {
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

  const hasPrimaryActions = suite.allowedActions.includes('EDIT')
    || suite.allowedActions.includes('SUBMIT')
    || suite.allowedActions.includes('PUBLISH_PRIVATE')
  const entryMember = suite.members.find(member => member.entry)

  return (
    <div className={cn(APP_SHELL_PAGE_CLASS_NAME, 'mx-auto max-w-6xl')}>
      <div className="flex flex-col gap-8 lg:flex-row">
        <div className="min-w-0 flex-1 space-y-8">
          <div className="space-y-3">
            <div className="flex flex-wrap items-center gap-3">
              <NamespaceBadge type={suite.namespace === 'global' ? 'GLOBAL' : 'TEAM'} name={suite.namespace} />
              <span className="badge-soft badge-soft-blue inline-flex items-center gap-1.5">
                <Boxes className="h-3.5 w-3.5" aria-hidden="true" />
                {t('suite.resourceTypeSuite')}
              </span>
            </div>
            <h1 className="text-balance break-words text-4xl font-bold font-heading text-foreground [overflow-wrap:anywhere]">
              {suite.displayName}
            </h1>
            <p className="font-mono text-sm text-muted-foreground">@{suite.namespace}/{suite.slug}</p>
            <p className="text-lg leading-relaxed text-muted-foreground">
              {suite.summary || t('suite.noSummary')}
            </p>
          </div>

          {!suite.available ? (
            <Card className="border-amber-500/30 bg-amber-500/5 p-4">
              <div className="flex items-start gap-3">
                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" aria-hidden="true" />
                <div>
                  <p className="font-medium text-foreground">{t('suite.degraded')}</p>
                  <p className="mt-1 text-sm text-muted-foreground">{t('suite.degradedDescription')}</p>
                </div>
              </div>
            </Card>
          ) : null}

          <Tabs defaultValue="overview">
            <TabsList>
              <TabsTrigger value="overview">{t('suite.overviewTab')}</TabsTrigger>
              <TabsTrigger value="members">{t('suite.membersTab', { count: suite.members.length })}</TabsTrigger>
            </TabsList>

            <TabsContent value="overview" className="mt-6">
              <Card className="overflow-hidden">
                <div className="p-6 sm:p-8">
                  {suite.overview ? (
                    <MarkdownRenderer content={suite.overview} />
                  ) : (
                    <p className="text-sm leading-7 text-muted-foreground">
                      {suite.summary || t('suite.noOverview')}
                    </p>
                  )}
                </div>

                {entryMember ? (
                  <section className="border-t border-border/60 bg-secondary/30 p-6 sm:p-8">
                    <div className="flex flex-col gap-5 sm:flex-row sm:items-center sm:justify-between">
                      <div className="min-w-0">
                        <h2 className="font-heading text-lg font-semibold text-foreground">
                          {t('suite.startWithEntry')}
                        </h2>
                        <p className="mt-1 max-w-2xl text-sm leading-6 text-muted-foreground">
                          {t('suite.startWithEntryDescription', { count: suite.members.length })}
                        </p>
                      </div>
                      {entryMember.browsable && !entryMember.blockingReason
                        && entryMember.skillId && entryMember.skillVersionId ? (
                          <Link
                            to="/space/$namespace/$slug"
                            params={{ namespace: entryMember.namespace, slug: entryMember.slug }}
                            search={{ returnTo }}
                            className={cn(buttonVariants({ variant: 'outline', size: 'sm' }), 'shrink-0 gap-1.5')}
                          >
                            {t('suite.viewEntrySkill')}
                            <ArrowUpRight className="h-3.5 w-3.5" aria-hidden="true" />
                          </Link>
                        ) : null}
                    </div>

                    <div className="mt-5 flex flex-col gap-2 sm:flex-row sm:items-center sm:gap-4">
                      <p className="flex min-w-0 items-center gap-2 font-semibold text-foreground">
                        <Wrench className="h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
                        <span className="truncate">
                          {entryMember.displayName || `@${entryMember.namespace}/${entryMember.slug}`}
                        </span>
                      </p>
                      <p className="break-all font-mono text-xs text-muted-foreground">
                        @{entryMember.namespace}/{entryMember.slug}@{entryMember.version}
                      </p>
                      {entryMember.blockingReason ? (
                        <span className="text-xs font-medium text-destructive">
                          {suiteBlockingReasonLabel(t, entryMember.blockingReason)}
                        </span>
                      ) : null}
                    </div>
                  </section>
                ) : null}
              </Card>
            </TabsContent>

            <TabsContent value="members" className="mt-6">
              <div className="grid gap-4 md:grid-cols-2">
                {suite.members.map((member) => {
                  const memberName = member.displayName || `@${member.namespace}/${member.slug}`
                  const content = (
                    <Card className="h-full p-5 transition-colors hover:border-primary/40">
                      <div className="flex items-start justify-between gap-4">
                        <div className="min-w-0">
                          <p className="flex items-center gap-2 font-semibold">
                            <Wrench className="h-4 w-4 shrink-0 text-primary" />
                            <span className="truncate">{memberName}</span>
                          </p>
                          <p className="mt-1 truncate text-xs text-muted-foreground">
                            @{member.namespace}/{member.slug}
                          </p>
                        </div>
                        {member.browsable && !member.blockingReason && member.skillId && member.skillVersionId ? (
                          <ArrowUpRight className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
                        ) : null}
                      </div>
                      {member.summary ? (
                        <p className="mt-4 line-clamp-3 text-sm leading-6 text-muted-foreground">{member.summary}</p>
                      ) : null}
                      <div className="mt-5 flex flex-wrap items-center gap-2 text-xs">
                        <span className="rounded-full bg-secondary px-2.5 py-1 font-mono">
                          {t('suite.pinnedVersion', { version: member.version })}
                        </span>
                        {member.entry ? (
                          <span className="rounded-full bg-primary/10 px-2.5 py-1 font-medium text-primary">
                            {t('suite.entrySkill')}
                          </span>
                        ) : null}
                        <span className={member.blockingReason ? 'text-destructive' : 'text-emerald-600'}>
                          {member.blockingReason
                            ? suiteBlockingReasonLabel(t, member.blockingReason)
                            : t('suite.available')}
                        </span>
                      </div>
                    </Card>
                  )

                  return member.browsable && !member.blockingReason && member.skillId && member.skillVersionId ? (
                    <Link
                      key={`${member.namespace}/${member.slug}@${member.version}`}
                      to="/space/$namespace/$slug"
                      params={{ namespace: member.namespace, slug: member.slug }}
                      search={{ returnTo }}
                      aria-label={t('suite.viewMember', { name: memberName })}
                      className="rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70 focus-visible:ring-offset-2"
                    >
                      {content}
                    </Link>
                  ) : (
                    <div key={`${member.namespace}/${member.slug}@${member.version}`}>{content}</div>
                  )
                })}
              </div>
            </TabsContent>
          </Tabs>
        </div>

        <aside
          className="w-full flex-shrink-0 space-y-5 lg:w-80"
          aria-label={t('suite.detailsSidebar')}
        >
          {hasPrimaryActions ? (
            <Card className="space-y-3 p-5">
              {suite.allowedActions.includes('EDIT') ? (
                <Button className="w-full" variant="outline" onClick={() => navigate({
                  to: `/dashboard/suites/${suite.namespace}/${encodeURIComponent(suite.slug)}/edit`,
                  search: { version: suite.version },
                })}>{t('suite.editDraft')}</Button>
              ) : null}
              {suite.allowedActions.includes('SUBMIT') || suite.allowedActions.includes('PUBLISH_PRIVATE') ? (
                <Button className="w-full" disabled={submitMutation.isPending} onClick={handleSubmit}>
                  {suite.visibility === 'PRIVATE' ? t('suite.publishDirectly') : t('suite.submitReview')}
                </Button>
              ) : null}
            </Card>
          ) : null}

          <Card className="space-y-5 p-5">
            <div className="flex items-center justify-between gap-4">
              <span className="text-sm text-muted-foreground">{t('suite.version')}</span>
              <span className="break-all text-right font-mono font-semibold text-foreground">v{suite.version}</span>
            </div>
            <div className="h-px bg-border/40" />
            <div className="flex items-center justify-between gap-4">
              <span className="text-sm text-muted-foreground">{t('suite.status')}</span>
              <span className="text-right font-semibold text-foreground">{suiteStatusLabel(t, suite.status)}</span>
            </div>
            <div className="h-px bg-border/40" />
            <div className="flex items-center justify-between gap-4">
              <span className="text-sm text-muted-foreground">{t('suite.visibility')}</span>
              <span className="text-right font-semibold text-foreground">{suiteVisibilityLabel(t, suite.visibility)}</span>
            </div>
            <div className="h-px bg-border/40" />
            <div className="flex items-center justify-between gap-4">
              <span className="text-sm text-muted-foreground">{t('suite.installStatus')}</span>
              <span className={cn(
                'flex items-center gap-1 text-right font-semibold',
                suite.available ? 'text-emerald-600' : 'text-destructive',
              )}>
                {suite.available
                  ? <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
                  : <AlertTriangle className="h-4 w-4" aria-hidden="true" />}
                {suite.available ? t('suite.available') : t('suite.degraded')}
              </span>
            </div>
          </Card>

          <Card className="space-y-4 p-5">
            <div className="flex items-center gap-2">
              <Terminal className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
              <span className="text-sm font-semibold font-heading text-foreground">{t('suite.installCommand')}</span>
            </div>
            {command ? (
              <div className="flex min-w-0 items-center gap-2 rounded-lg bg-secondary p-3">
                <code className="min-w-0 flex-1 overflow-x-auto text-sm">{command}</code>
                <Button
                  variant="outline"
                  size="sm"
                  aria-label={t('suite.copyInstallCommand')}
                  onClick={async () => {
                    await navigator.clipboard.writeText(command)
                    toast.success(t('suite.commandCopied'))
                  }}
                ><Copy className="h-4 w-4" /></Button>
              </div>
            ) : (
              <p role="alert" className="text-sm text-destructive">
                {t('skillDetail.installCommandUnsafeVersion')}
              </p>
            )}
          </Card>

          {versions?.length ? (
            <Card className="p-5">
              <h2 className="text-sm font-semibold font-heading text-foreground">{t('suite.versionHistory')}</h2>
              <div className="mt-4 flex flex-wrap gap-2">
                {versions.map((item) => (
                  <Button
                    key={item.id}
                    variant={item.version === suite.version ? 'default' : 'outline'}
                    size="sm"
                    onClick={() => navigate({
                      to: `/suite/${namespace}/${encodeURIComponent(slug)}`,
                      search: { version: item.version },
                    })}
                  >v{item.version} · {suiteStatusLabel(t, item.status)}</Button>
                ))}
              </div>
            </Card>
          ) : null}

          <SuiteManagementActions suite={suite} />
        </aside>
      </div>
    </div>
  )
}
