import { Link, useNavigate, useParams, useSearch } from '@tanstack/react-router'
import { AlertTriangle, CheckCircle2, ChevronRight, Clock3, Eye, Info } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { SkillSuite, SkillSuiteBundleOperationSummary } from '@/api/types'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { SuiteLabelPanel } from '@/features/skill/skill-label-panel'
import { SuiteManagementActions } from '@/features/suite/suite-management-actions'
import { SuiteMemberTable } from '@/features/suite/suite-member-table'
import { SuiteVersionLedger } from '@/features/suite/suite-version-ledger'
import { suiteBundleProblemKind } from '@/features/suite/suite-bundle-problem'
import { SuiteWorkspaceHeader } from '@/features/suite/suite-workspace-header'
import { withoutDuplicateSuiteTitle } from '@/features/suite/suite-overview'
import { suiteStatusLabel, suiteVisibilityLabel } from '@/features/suite/suite-labels'
import { useAuth } from '@/features/auth/use-auth'
import { useSuiteLabels } from '@/shared/hooks/use-label-queries'
import {
  useMySuiteBundleOperations,
  useSuiteDetail,
  useSuiteVersions,
  useSubmitSuite,
} from '@/shared/hooks/use-suite-queries'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { toast } from '@/shared/lib/toast'
import { cn } from '@/shared/lib/utils'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'

type ManagementTab = 'overview' | 'members' | 'versions' | 'publishing'

export function SuiteManagementPage() {
  const { namespace, slug } = useParams({ from: '/dashboard/suites/$namespace/$slug' })
  const search = useSearch({ from: '/dashboard/suites/$namespace/$slug' })
  const navigate = useNavigate()
  const { t, i18n } = useTranslation()
  const { hasRole } = useAuth()
  const activeTab: ManagementTab = search.tab ?? 'overview'
  const { data: suite, isLoading, error } = useSuiteDetail(namespace, slug, search.version)
  const { data: versions = [] } = useSuiteVersions(namespace, slug, Boolean(suite))
  const { data: labels } = useSuiteLabels(namespace, slug, Boolean(suite))
  const { data: operations } = useMySuiteBundleOperations(0, 50, Boolean(suite))
  const submitMutation = useSubmitSuite()
  const coordinate = `@${namespace}/${slug}`
  const suiteOperations = (operations?.items ?? [])
    .filter(operation => operation.targetCoordinate === coordinate)

  if (isLoading) return <div className="h-56 animate-shimmer rounded-lg" />
  if (!suite || error) {
    return <Card className="p-6 text-center text-sm text-destructive">{t('suite.notFound')}</Card>
  }

  const canManageLabels = hasRole('SUPER_ADMIN') || suite.allowedActions.some(action =>
    ['EDIT', 'CREATE_VERSION', 'HIDE', 'RESTORE', 'ARCHIVE', 'UNARCHIVE'].includes(action))
  const editPath = suite.allowedActions.includes('CREATE_VERSION')
    ? `/dashboard/suites/${suite.namespace}/${encodeURIComponent(suite.slug)}/new-version`
    : `/dashboard/suites/${suite.namespace}/${encodeURIComponent(suite.slug)}/edit`
  const editSearch = suite.allowedActions.includes('CREATE_VERSION')
    ? { sourceVersion: suite.version }
    : { version: suite.version }
  const canEdit = suite.allowedActions.includes('CREATE_VERSION') || suite.allowedActions.includes('EDIT')
  const returnTo = `/dashboard/suites/${namespace}/${encodeURIComponent(slug)}?version=${encodeURIComponent(suite.version)}&tab=members`
  const entryReady = suite.members.some(member => member.entry && !member.blockingReason)
  const entryMember = suite.members.find(member => member.entry)
  const availableMembers = suite.members.filter(member => !member.blockingReason).length
  const membersReady = suite.members.length > 0 && suite.members.every(member => !member.blockingReason)
  const readiness = [
    { label: t('suite.summaryLabel'), ready: Boolean(suite.summary?.trim()) },
    { label: t('suite.overviewTab'), ready: Boolean(suite.overview?.trim()) },
    { label: t('suite.entrySkill'), ready: entryReady },
    { label: t('suite.management.memberVersions'), ready: membersReady },
  ]
  const readyToPublish = readiness.every(item => item.ready)
  const canSubmit = suite.allowedActions.includes('SUBMIT') || suite.allowedActions.includes('PUBLISH_PRIVATE')
  const bannerAttention = suite.status === 'YANKED' || !membersReady
  const bannerNeutral = suite.status === 'DRAFT' || suite.status === 'PENDING_REVIEW'

  const navigateToEditor = () => navigate({ to: editPath, search: editSearch })
  const submit = async (target = suite) => {
    try {
      await submitMutation.mutateAsync({
        suiteId: target.id,
        versionId: target.versionId,
        privatePublish: target.visibility === 'PRIVATE',
      })
      toast.success(target.visibility === 'PRIVATE' ? t('suite.published') : t('suite.submitted'))
    } catch (submitError) {
      toast.error(t('suite.actionFailed'), submitError instanceof Error ? submitError.message : '')
    }
  }

  const primaryAction = canEdit ? (
    <Button size="sm" onClick={navigateToEditor}>
      {suite.allowedActions.includes('CREATE_VERSION') ? t('suite.createVersion') : t('suite.editDraft')}
    </Button>
  ) : null

  return (
    <div className="space-y-4 animate-fade-up">
      <SuiteWorkspaceHeader
        title={suite.displayName}
        eyebrow={coordinate}
        backLabel={t('suite.management.backToSuites')}
        onBack={() => navigate({ to: '/dashboard/suites' })}
        actions={(
          <div className="flex items-center gap-2">
            {primaryAction}
            <Button size="sm" variant="soft" className="gap-1.5" onClick={() => navigate({
              to: `/suite/${namespace}/${encodeURIComponent(slug)}`,
              search: { version: suite.version },
            })}>
              <Eye className="h-3.5 w-3.5" aria-hidden="true" />
              {t('suite.management.previewMarket')}
            </Button>
          </div>
        )}
      />

      <div className={cn(
        'flex items-start gap-2 rounded-md border px-3 py-2 text-xs',
        bannerAttention
          ? 'border-amber-500/30 bg-amber-500/5'
          : bannerNeutral
            ? 'border-blue-500/20 bg-blue-500/5'
            : 'border-emerald-500/25 bg-emerald-500/5',
      )}>
        {bannerAttention
          ? <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-700" aria-hidden="true" />
          : bannerNeutral
            ? <Info className="mt-0.5 h-4 w-4 shrink-0 text-blue-700 dark:text-blue-300" aria-hidden="true" />
            : <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-700" aria-hidden="true" />}
        <div className="min-w-0">
          <p className="font-medium leading-5">
            v{suite.version} · {suiteStatusLabel(t, suite.status)} · {suiteVisibilityLabel(t, suite.visibility)} · {t('suite.management.memberCountValue', { count: suite.members.length })}
          </p>
          <p className="text-[11px] leading-4 text-muted-foreground">
            {suite.status === 'YANKED'
              ? t('suite.management.yankedDescription')
              : !membersReady
                ? t('suite.degradedDescription')
                : suite.status === 'DRAFT'
                  ? t('suite.management.draftDescription')
                  : suite.status === 'PENDING_REVIEW'
                    ? t('suite.management.reviewDescription')
                    : t('suite.management.availableDescription')}
          </p>
        </div>
      </div>

      <Tabs
        value={activeTab}
        onValueChange={(value) => navigate({
          to: `/dashboard/suites/${namespace}/${encodeURIComponent(slug)}`,
          search: {
            version: suite.version,
            tab: value === 'overview' ? undefined : value as Exclude<ManagementTab, 'overview'>,
          },
          replace: true,
        })}
        className="space-y-3"
      >
        <TabsList>
          <TabsTrigger value="overview" className="py-2 text-xs">{t('suite.management.tabs.overview')}</TabsTrigger>
          <TabsTrigger value="members" className="py-2 text-xs">{t('suite.management.tabs.members', { count: suite.members.length })}</TabsTrigger>
          <TabsTrigger value="versions" className="py-2 text-xs">{t('suite.management.tabs.versions', { count: versions.length })}</TabsTrigger>
          <TabsTrigger value="publishing" className="py-2 text-xs">{t('suite.management.tabs.publishing', { count: suiteOperations.length })}</TabsTrigger>
        </TabsList>

        <TabsContent value="overview">
          <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_15rem]">
            <div className="space-y-3">
              {canSubmit ? <Card className="overflow-hidden rounded-lg">
                <div className="flex flex-col gap-2 border-b px-3 py-2.5 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <h2 className="text-xs font-semibold">{t('suite.management.publishChecklist')}</h2>
                    <p className="mt-0.5 text-[11px] text-muted-foreground">{t('suite.management.publishChecklistDescription')}</p>
                  </div>
                  <Button size="sm" className="h-7 px-2.5 text-[11px]" disabled={!readyToPublish || submitMutation.isPending} onClick={() => submit()}>
                      {suite.visibility === 'PRIVATE' ? t('suite.publishDirectly') : t('suite.submitReview')}
                  </Button>
                </div>
                <div className="grid gap-x-4 px-3 sm:grid-cols-2">
                  {readiness.map(item => (
                    <div key={item.label} className="flex items-center justify-between gap-3 border-b border-border/60 py-2 text-xs last:border-b-0 sm:[&:nth-last-child(-n+2)]:border-b-0">
                      <span className="flex items-center gap-2">
                        {item.ready
                          ? <CheckCircle2 className="h-3.5 w-3.5 text-emerald-700" aria-hidden="true" />
                          : <AlertTriangle className="h-3.5 w-3.5 text-amber-700" aria-hidden="true" />}
                        {item.label}
                      </span>
                      <span className={cn(
                        'rounded-md border px-1.5 py-0.5 text-[11px] font-medium',
                        item.ready
                          ? 'border-emerald-500/20 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300'
                          : 'border-amber-500/20 bg-amber-500/10 text-amber-700 dark:text-amber-300',
                      )}>
                        {t(item.ready ? 'suite.management.ready' : 'suite.management.incomplete')}
                      </span>
                    </div>
                  ))}
                </div>
              </Card> : null}

              <Card className="overflow-hidden rounded-lg">
                <div className="flex items-center justify-between gap-3 border-b px-3 py-2.5">
                  <div>
                    <h2 className="text-xs font-semibold">{t('suite.management.marketContent')}</h2>
                    <p className="mt-0.5 text-[11px] text-muted-foreground">{t('suite.management.marketContentDescription')}</p>
                  </div>
                  {suite.allowedActions.includes('EDIT') ? (
                    <Button size="sm" variant="soft" className="h-6 px-2 text-[11px]" onClick={navigateToEditor}>{t('suite.management.editContent')}</Button>
                  ) : null}
                </div>
                <div className="space-y-3 px-3 py-3">
                  <div>
                    <p className="text-[11px] font-medium text-muted-foreground">{t('suite.summaryLabel')}</p>
                    <p className="mt-0.5 text-xs leading-5">{suite.summary || t('suite.noSummary')}</p>
                  </div>
                  <div className="border-t pt-3">
                    <p className="text-[11px] font-medium text-muted-foreground">{t('suite.overviewTab')}</p>
                    {suite.overview ? (
                      <div className="mt-1 max-h-24 overflow-hidden text-xs leading-5 [&_h1]:text-sm [&_h2]:text-xs [&_p]:my-0.5">
                        <MarkdownRenderer content={withoutDuplicateSuiteTitle(suite.overview, suite.displayName)} />
                      </div>
                    ) : <p className="mt-0.5 text-xs text-muted-foreground">{t('suite.noOverview')}</p>}
                  </div>
                </div>
              </Card>

              <Card className="overflow-hidden rounded-lg">
                <div className="flex items-center justify-between gap-3 border-b px-3 py-2.5">
                  <div>
                    <h2 className="text-xs font-semibold">{t('suite.management.membersTitle', { count: suite.members.length })}</h2>
                    <p className="mt-0.5 text-[11px] text-muted-foreground">{t('suite.management.membersSnapshotDescription')}</p>
                  </div>
                  {canEdit ? (
                    <Button size="sm" variant="soft" className="h-6 px-2 text-[11px]" onClick={navigateToEditor}>{t('suite.management.editMembers')}</Button>
                  ) : null}
                </div>
                <div className="grid gap-3 px-3 py-2.5 text-xs sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
                  <div className="min-w-0">
                    <p className="text-[11px] text-muted-foreground">{t('suite.entrySkill')}</p>
                    <p className="mt-0.5 truncate font-medium">
                      {entryMember?.displayName || (entryMember ? `@${entryMember.namespace}/${entryMember.slug}` : t('suite.entryRequired'))}
                    </p>
                  </div>
                  <p className={cn(
                    'w-fit rounded-md border px-1.5 py-0.5 text-[11px] font-medium',
                    membersReady
                      ? 'border-emerald-500/20 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300'
                      : 'border-amber-500/20 bg-amber-500/10 text-amber-700 dark:text-amber-300',
                  )}>
                    {t('suite.availableMemberCount', { available: availableMembers, total: suite.members.length })}
                  </p>
                </div>
              </Card>

            </div>

            <div className="space-y-3">
              <SuiteLabelPanel
                namespace={suite.namespace}
                slug={suite.slug}
                initialLabels={labels ?? []}
                canManage={canManageLabels}
                isSuperAdmin={hasRole('SUPER_ADMIN')}
                compact
              />
              <SuiteManagementActions suite={suite} compact />
            </div>
          </div>
        </TabsContent>

        <TabsContent value="members">
          <SuiteMemberTable
            suite={suite}
            returnTo={returnTo}
            management
            onEdit={canEdit ? navigateToEditor : undefined}
          />
        </TabsContent>

        <TabsContent value="versions">
          {versions.length ? (
            <SuiteVersionLedger
              namespace={namespace}
              slug={slug}
              versions={versions}
              currentSuite={suite}
              returnTo={`/dashboard/suites/${namespace}/${encodeURIComponent(slug)}?version=${encodeURIComponent(suite.version)}&tab=versions`}
              management
              onSelectVersion={(version) => navigate({
                to: `/dashboard/suites/${namespace}/${encodeURIComponent(slug)}`,
                search: { version, tab: 'versions' },
              })}
              onEditVersion={(target) => navigate({
                to: `/dashboard/suites/${target.namespace}/${encodeURIComponent(target.slug)}/edit`,
                search: { version: target.version },
              })}
              onSubmitVersion={submit}
            />
          ) : <p className="text-sm text-muted-foreground">{t('suite.noVersions')}</p>}
        </TabsContent>

        <TabsContent value="publishing">
          <Card className="overflow-hidden">
            <div className="border-b px-4 py-3">
              <h2 className="text-sm font-semibold">{t('suite.bundle.taskListTitle')}</h2>
              <p className="mt-1 text-xs text-muted-foreground">{t('suite.management.publishingDescription')}</p>
            </div>
            <OperationRows operations={suiteOperations} locale={i18n.language} suite={suite} />
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}

function OperationRows({ operations, locale, suite, compact = false }: {
  operations: SkillSuiteBundleOperationSummary[]
  locale: string
  suite: SkillSuite
  compact?: boolean
}) {
  const { t } = useTranslation()
  if (operations.length === 0) {
    return <p className="px-4 py-7 text-center text-sm text-muted-foreground">{t('suite.bundle.taskEmpty')}</p>
  }
  return (
    <div className="divide-y divide-border/70">
      {operations.map((operation) => {
        const problemKind = suiteBundleProblemKind(operation.status, operation.failureCode)
        return (
          <Link
            key={operation.operationId}
            to="/dashboard/suites/publishing/$operationId"
            params={{ operationId: operation.operationId }}
            search={{
              suiteNamespace: suite.namespace,
              suiteSlug: suite.slug,
              suiteVersion: suite.version,
            }}
            className={cn(
              'grid gap-2 px-4 py-3 text-sm transition-colors hover:bg-secondary/30',
              !compact && 'sm:grid-cols-[minmax(0,1fr)_auto_auto] sm:items-center',
            )}
          >
            <div className="min-w-0">
              <p className="truncate text-sm font-medium">
                {problemKind
                  ? t(`suite.bundle.problem.${problemKind}.title`)
                  : t(`suite.bundle.statusTitle.${operation.status}`)}
              </p>
              <p className="mt-1 truncate text-xs text-muted-foreground">
                {t('suite.bundle.memberProgress', {
                  completed: operation.completedMembers,
                  total: operation.totalMembers,
                  waiting: operation.waitingMembers,
                })}
              </p>
            </div>
            {!compact ? (
              <span className={cn(
                'w-fit rounded-md border px-2 py-1 text-xs font-medium',
                problemKind
                  ? 'border-amber-500/20 bg-amber-500/10 text-amber-700 dark:text-amber-300'
                  : operation.status === 'SUITE_DRAFT_CREATED'
                    ? 'border-emerald-500/20 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300'
                    : 'border-blue-500/20 bg-blue-500/10 text-blue-700 dark:text-blue-300',
              )}>
                {t(`suite.bundle.statusLabel.${operation.status}`)}
              </span>
            ) : null}
            <span className="flex items-center gap-1 text-xs text-muted-foreground">
              <Clock3 className="h-3.5 w-3.5" aria-hidden="true" />
              {formatLocalDateTime(operation.updatedAt, locale)}
              <ChevronRight className="h-3.5 w-3.5" aria-hidden="true" />
            </span>
          </Link>
        )
      })}
    </div>
  )
}
