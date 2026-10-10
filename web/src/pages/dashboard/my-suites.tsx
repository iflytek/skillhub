import { useEffect, useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Boxes, ChevronRight, Search, X } from 'lucide-react'
import type { MySkillSuiteWorkspaceItem } from '@/api/types'
import { useMySuiteWorkspace } from '@/shared/hooks/use-suite-queries'
import { SuiteWorkspaceHeader } from '@/features/suite/suite-workspace-header'
import { suiteStatusLabel } from '@/features/suite/suite-labels'
import { suiteBundleProblemKind } from '@/features/suite/suite-bundle-problem'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { cn } from '@/shared/lib/utils'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'

const PAGE_SIZE = 12
const FILTERS = ['ALL', 'ATTENTION', 'DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'OTHER'] as const

export function MySuitesPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const [filters, setFilters] = useState({ query: '', state: 'ALL', page: 0 })
  const [queryInput, setQueryInput] = useState('')
  const workspace = useMySuiteWorkspace(filters.query, filters.state, filters.page, PAGE_SIZE)
  const data = workspace.data
  const stale = workspace.isPlaceholderData
  const update = (next: Partial<typeof filters>) => setFilters(current => ({ ...current, page: 0, ...next }))

  useEffect(() => {
    if (!data || stale) return
    const lastPage = Math.max(0, Math.ceil(data.total / PAGE_SIZE) - 1)
    if (filters.page > lastPage) setFilters(current => ({ ...current, page: lastPage }))
  }, [data, stale, filters.page])

  function viewSuite(item: MySkillSuiteWorkspaceItem, tab?: 'publishing') {
    void navigate({
      to: `/dashboard/suites/${item.namespace}/${encodeURIComponent(item.slug)}`,
      search: tab ? { version: item.suiteVersion, tab } : { version: item.suiteVersion },
    })
  }
  function open(item: MySkillSuiteWorkspaceItem) {
    if (item.suiteId) {
      viewSuite(item, item.operationId ? 'publishing' : undefined)
      return
    }
    if (item.operationId) void navigate({ to: `/dashboard/suites/publishing/${encodeURIComponent(item.operationId)}` })
    else viewSuite(item)
  }

  return (
    <div className="space-y-4 animate-fade-up">
      <SuiteWorkspaceHeader title={t('suite.myTitle')} description={t('suite.workspace.description')}
        actions={<Button size="sm" onClick={() => navigate({ to: '/dashboard/suites/new' })}>{t('suite.create')}</Button>} />
      <div className="flex flex-wrap items-center gap-2">
        <form className="flex w-full items-center gap-2 sm:w-auto" onSubmit={event => { event.preventDefault(); update({ query: queryInput.trim() }) }}>
        <div className="relative min-w-0 flex-1 sm:w-72">
          <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
          <Input className="h-8 pl-8 pr-8 text-xs" value={queryInput} maxLength={200}
            aria-label={t('suite.workspace.searchLabel')} placeholder={t('suite.workspace.searchPlaceholder')}
            onChange={event => setQueryInput(event.target.value)} />
          {queryInput && <button type="button" aria-label={t('suite.workspace.clearSearch')}
            className="absolute right-1 top-1/2 flex h-6 w-6 -translate-y-1/2 items-center justify-center rounded-sm text-muted-foreground hover:bg-secondary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            onClick={() => setQueryInput('')}><X className="h-3.5 w-3.5" aria-hidden="true" /></button>}
        </div>
        <Button type="submit" size="sm" variant="outline" className="h-8 text-xs">{t('nav.search')}</Button>
        </form>
        <Select value={filters.state} onValueChange={state => update({ state })}>
          <SelectTrigger className="h-8 w-32 text-xs" aria-label={t('suite.workspace.stateFilter')}><SelectValue /></SelectTrigger>
          <SelectContent>{FILTERS.map(state => <SelectItem key={state} value={state}>{t(`suite.workspace.filter.${state}`)}</SelectItem>)}</SelectContent>
        </Select>
        {data && <button type="button" aria-pressed={filters.state === 'ATTENTION'}
          onClick={() => update({ state: filters.state === 'ATTENTION' ? 'ALL' : 'ATTENTION' })}
          className={cn('inline-flex h-7 items-center gap-1.5 rounded-md px-2 text-xs focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
            data.attentionCount ? 'bg-amber-500/10 text-amber-800 dark:text-amber-300' : 'text-muted-foreground', filters.state === 'ATTENTION' && 'ring-1 ring-amber-500/30')}>
          {t('suite.workspace.attention')}{' '}<span className="font-medium tabular-nums">{data.attentionCount}</span>
        </button>}
      </div>
      {workspace.isLoading ? <div className="h-64 animate-shimmer rounded-lg" />
        : workspace.isError ? <div role="alert" className="rounded-lg border p-4 text-xs text-destructive">
          {t('suite.workspace.error')}<Button variant="ghost" size="sm" onClick={() => workspace.refetch()}>{t('suite.workspace.reload')}</Button>
        </div> : data?.items.length ? <>
          <div className="overflow-hidden rounded-lg border" aria-busy={stale || workspace.isFetching}>
            <div className="hidden grid-cols-[minmax(0,1fr)_5rem_10rem_9rem_8rem] gap-3 border-b bg-muted/30 px-3 py-2 text-[11px] text-muted-foreground lg:grid">
              {['suite', 'version', 'state', 'updated', 'action'].map(column => <span key={column} className={column === 'action' ? 'text-right' : ''}>{t(`suite.workspace.columns.${column}`)}</span>)}
            </div>
            <div className={cn('divide-y', stale && 'opacity-60')}>
              {data.items.map(item => {
                const problem = !item.suiteId && item.operationStatus === 'SUITE_DRAFT_CREATED'
                  ? 'draftMissing'
                  : suiteBundleProblemKind(item.operationStatus, item.failureCode)
                return <div key={`${item.namespace}/${item.slug}`}
                  className={cn('grid gap-2 px-3 py-3 sm:grid-cols-[minmax(0,1fr)_auto] lg:grid-cols-[minmax(0,1fr)_5rem_12rem_9rem_8rem] lg:items-center lg:gap-3', !item.suiteId && 'bg-blue-500/[0.025]')}>
                  <div className="min-w-0">
                    <button type="button" disabled={stale} onClick={() => item.suiteId ? viewSuite(item) : open(item)}
                      className="flex max-w-full items-center gap-2 text-left text-xs font-semibold hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
                      <Boxes className="h-3.5 w-3.5 shrink-0 text-muted-foreground" aria-hidden="true" /><span className="truncate">{item.displayName}</span>
                    </button>
                    <p className="mt-1 truncate pl-5 font-mono text-[11px] text-muted-foreground">@{item.namespace}/{item.slug}</p>
                    {item.summary && <p className="mt-0.5 truncate pl-5 text-[11px] text-muted-foreground">{item.summary}</p>}
                  </div>
                  <span className="pl-5 font-mono text-[11px] sm:pl-0">v{item.version}</span>
                  <div className="min-w-0 pl-5 sm:pl-0">
                    <span className={cn('inline-flex rounded-md border px-1.5 py-0.5 text-[11px] font-medium',
                      item.state === 'ATTENTION' || item.state === 'REJECTED' ? 'border-amber-500/20 bg-amber-500/10 text-amber-800 dark:text-amber-300'
                        : item.state === 'PUBLISHED' ? 'border-emerald-500/20 bg-emerald-500/10 text-emerald-800 dark:text-emerald-300'
                          : item.state === 'PREPARING' || item.state === 'PENDING_REVIEW' ? 'border-blue-500/20 bg-blue-500/10 text-blue-800 dark:text-blue-300' : 'border-border bg-secondary text-muted-foreground')}>
                      {item.operationStatus ? t(`suite.bundle.statusLabel.${item.operationStatus}`) : suiteStatusLabel(t, item.state)}
                    </span>
                    {problem && <p className="mt-1 truncate text-[11px] text-foreground/75">{t(`suite.bundle.problem.${problem}.title`)}</p>}
                    {item.operationStatus && <p className="mt-0.5 truncate text-[11px] text-muted-foreground">{t(`suite.bundle.taskHint.${item.operationStatus}`)}</p>}
                  </div>
                  <span className="pl-5 text-[11px] text-muted-foreground sm:pl-0">{formatLocalDateTime(item.updatedAt, i18n.language)}</span>
                  <Button variant="ghost" size="sm" disabled={stale} onClick={() => open(item)} className="h-7 justify-self-end px-2 text-[11px] sm:col-span-2 lg:col-span-1">
                    {item.operationStatus && !item.suiteId ? t(`suite.bundle.taskAction.${item.operationStatus}`) : t('suite.view')}
                    <ChevronRight className="ml-1 h-3 w-3" aria-hidden="true" />
                  </Button>
                </div>
              })}
            </div>
          </div>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="text-[11px] text-muted-foreground">{t('suite.workspace.total', { count: data.total, size: PAGE_SIZE })}</span>
            <Pagination page={filters.page} totalPages={Math.max(1, Math.ceil(data.total / PAGE_SIZE))} onPageChange={page => update({ page })} />
          </div>
        </> : <EmptyState title={t(filters.query || filters.state !== 'ALL' ? 'suite.workspace.noMatches' : 'suite.myEmpty')} />}
    </div>
  )
}
