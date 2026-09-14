import { useEffect, useState, type ReactNode } from 'react'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { AlertTriangle, Boxes, CheckCircle2, ChevronRight, CircleDot, Clock3, Square } from 'lucide-react'
import type { SkillSuiteBundleOperationSummary } from '@/api/types'
import { useMySuiteBundleOperations, useMySuites } from '@/shared/hooks/use-suite-queries'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { suiteStatusLabel } from '@/features/suite/suite-labels'

const PAGE_SIZE = 12
const NEEDS_ATTENTION = new Set(['BLOCKED_RETRYABLE', 'REPREVIEW_REQUIRED'])
const IN_PROGRESS = new Set(['RUNNING', 'WAITING_FOR_MEMBERS'])

export function MySuitesPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const search = useSearch({ from: '/dashboard/suites' })
  const activeTab = search.tab ?? 'suites'
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [taskPage, setTaskPage] = useState(0)
  const { data, isLoading } = useMySuites(query.trim(), page, PAGE_SIZE, activeTab === 'suites')
  const { data: operations, isLoading: isLoadingOperations } = useMySuiteBundleOperations(
    taskPage,
    PAGE_SIZE,
    activeTab === 'publishing',
  )
  const taskTotalPages = Math.max(1, Math.ceil((operations?.total ?? 0) / PAGE_SIZE))

  useEffect(() => {
    if (taskPage >= taskTotalPages) setTaskPage(taskTotalPages - 1)
  }, [taskPage, taskTotalPages])

  const groups = {
    attention: operations?.items.filter(operation => NEEDS_ATTENTION.has(operation.status)) ?? [],
    progress: operations?.items.filter(operation => IN_PROGRESS.has(operation.status)) ?? [],
    recent: operations?.items.filter(operation =>
      !NEEDS_ATTENTION.has(operation.status) && !IN_PROGRESS.has(operation.status)) ?? [],
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <DashboardPageHeader
        title={t('suite.myTitle')}
        subtitle={t('suite.myDescription')}
        actions={<Button onClick={() => navigate({ to: '/dashboard/suites/new' })}>{t('suite.create')}</Button>}
      />
      <Tabs
        value={activeTab}
        onValueChange={(tab) => navigate({
          to: '/dashboard/suites',
          search: { tab: tab === 'publishing' ? 'publishing' : undefined },
          replace: true,
        })}
        className="space-y-6"
      >
        <TabsList>
          <TabsTrigger value="suites">{t('suite.tabs.suites')}</TabsTrigger>
          <TabsTrigger value="publishing">{t('suite.tabs.publishing')}</TabsTrigger>
        </TabsList>

        <TabsContent value="suites" className="space-y-6">
          <Input
            className="max-w-xl"
            value={query}
            placeholder={t('suite.searchPlaceholder')}
            onChange={(event) => {
              setQuery(event.target.value)
              setPage(0)
            }}
          />
          {isLoading ? (
            <div className="h-40 animate-shimmer rounded-xl" />
          ) : data?.items.length ? (
            <>
              <div className="grid gap-4 md:grid-cols-2">
                {data.items.map((suite) => (
                  <Card key={suite.id} className="p-5">
                    <div className="flex items-start justify-between gap-4">
                      <div className="min-w-0">
                        <p className="flex items-center gap-2 font-semibold"><Boxes className="h-4 w-4" />{suite.displayName}</p>
                        <p className="mt-1 truncate font-mono text-xs text-muted-foreground">@{suite.namespace}/{suite.slug}@{suite.version}</p>
                      </div>
                      <span className="rounded-full bg-secondary px-2 py-1 text-xs">{suiteStatusLabel(t, suite.versionStatus)}</span>
                    </div>
                    <p className="mt-3 line-clamp-2 min-h-10 text-sm text-muted-foreground">{suite.summary || t('suite.noSummary')}</p>
                    <div className="mt-4 flex gap-2">
                      <Button variant="outline" size="sm" onClick={() => navigate({
                        to: `/suite/${suite.namespace}/${encodeURIComponent(suite.slug)}`,
                        search: { version: suite.version },
                      })}>{t('suite.view')}</Button>
                    </div>
                  </Card>
                ))}
              </div>
              <Pagination page={page} totalPages={Math.max(1, Math.ceil(data.total / data.size))} onPageChange={setPage} />
            </>
          ) : (
            <EmptyState title={t('suite.myEmpty')} description={t('suite.myEmptyDescription')} />
          )}
        </TabsContent>

        <TabsContent value="publishing" className="space-y-7">
          <div>
            <h2 className="text-lg font-semibold">{t('suite.bundle.taskListTitle')}</h2>
            <p className="mt-1 text-sm text-muted-foreground">{t('suite.bundle.taskListDescription')}</p>
          </div>
          {isLoadingOperations ? (
            <div className="h-40 animate-shimmer rounded-xl" />
          ) : operations?.total ? (
            <>
              <TaskSection
                title={t('suite.bundle.groups.attention')}
                description={t('suite.bundle.groups.attentionDescription')}
                operations={groups.attention}
                emptyLabel={t('suite.bundle.groups.attentionEmpty')}
                icon={<AlertTriangle className="h-4 w-4 text-amber-600" />}
                locale={i18n.language}
                onOpen={(operationId) => navigate({ to: `/dashboard/suites/publishing/${operationId}` })}
              />
              <TaskSection
                title={t('suite.bundle.groups.progress')}
                description={t('suite.bundle.groups.progressDescription')}
                operations={groups.progress}
                emptyLabel={t('suite.bundle.groups.progressEmpty')}
                icon={<Clock3 className="h-4 w-4 text-primary" />}
                locale={i18n.language}
                onOpen={(operationId) => navigate({ to: `/dashboard/suites/publishing/${operationId}` })}
              />
              <TaskSection
                title={t('suite.bundle.groups.recent')}
                description={t('suite.bundle.groups.recentDescription')}
                operations={groups.recent}
                emptyLabel={t('suite.bundle.groups.recentEmpty')}
                icon={<CheckCircle2 className="h-4 w-4 text-emerald-600" />}
                locale={i18n.language}
                onOpen={(operationId) => navigate({ to: `/dashboard/suites/publishing/${operationId}` })}
              />
              <Pagination page={taskPage} totalPages={taskTotalPages} onPageChange={setTaskPage} />
            </>
          ) : (
            <EmptyState title={t('suite.bundle.taskEmpty')} description={t('suite.bundle.taskEmptyDescription')} />
          )}
        </TabsContent>
      </Tabs>
    </div>
  )
}

function TaskSection({ title, description, operations, emptyLabel, icon, locale, onOpen }: {
  title: string
  description: string
  operations: SkillSuiteBundleOperationSummary[]
  emptyLabel: string
  icon: ReactNode
  locale: string
  onOpen: (operationId: string) => void
}) {
  const { t } = useTranslation()
  return (
    <section className="space-y-3">
      <div className="flex items-center gap-2">
        {icon}
        <div>
          <h3 className="text-sm font-semibold">{title}</h3>
          <p className="text-xs text-muted-foreground">{description}</p>
        </div>
      </div>
      {operations.length ? (
        <Card className="divide-y overflow-hidden">
          {operations.map((operation) => (
            <button
              key={operation.operationId}
              type="button"
              className="flex w-full flex-col gap-3 px-5 py-4 text-left transition-colors hover:bg-muted/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring sm:flex-row sm:items-center sm:justify-between"
              onClick={() => onOpen(operation.operationId)}
            >
              <div className="flex min-w-0 items-start gap-3">
                <OperationStatusIcon status={operation.status} />
                <div className="min-w-0">
                  <p className="truncate font-mono text-sm font-medium">{operation.targetCoordinate}@{operation.targetVersion}</p>
                  <p className="mt-1 text-xs text-muted-foreground">{t(`suite.bundle.status.${operation.status}`)}</p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {t('suite.bundle.memberProgress', {
                      completed: operation.completedMembers,
                      total: operation.totalMembers,
                      waiting: operation.waitingMembers,
                    })}
                  </p>
                </div>
              </div>
              <div className="flex shrink-0 items-center gap-3 pl-8 text-xs text-muted-foreground sm:pl-0">
                <span>{formatLocalDateTime(operation.updatedAt, locale)}</span>
                <ChevronRight className="h-4 w-4" aria-hidden="true" />
              </div>
            </button>
          ))}
        </Card>
      ) : (
        <p className="rounded-lg border border-dashed px-4 py-5 text-sm text-muted-foreground">{emptyLabel}</p>
      )}
    </section>
  )
}

function OperationStatusIcon({ status }: { status: SkillSuiteBundleOperationSummary['status'] }) {
  if (status === 'BLOCKED_RETRYABLE' || status === 'REPREVIEW_REQUIRED') {
    return <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" aria-hidden="true" />
  }
  if (status === 'CANCELLED') {
    return <Square className="mt-0.5 h-5 w-5 shrink-0 text-muted-foreground" aria-hidden="true" />
  }
  if (status === 'SUITE_DRAFT_CREATED') {
    return <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-emerald-600" aria-hidden="true" />
  }
  return <CircleDot className="mt-0.5 h-5 w-5 shrink-0 text-primary" aria-hidden="true" />
}
