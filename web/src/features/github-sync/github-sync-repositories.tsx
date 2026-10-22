import { useEffect, useRef, useState, type FormEvent, type MouseEvent } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ExternalLink, RefreshCw } from 'lucide-react'
import type { GithubSyncRepositoryQuery } from '@/api/client'
import { Pagination } from '@/shared/components/pagination'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { GithubSyncSourceDetail } from './github-sync-source-detail'
import { useGithubSyncRepository, useGithubSyncRepositoryOrigins, useGithubSyncRepositoryPaths, useGithubSyncRepositories } from './use-github-sync-admin'

const PAGE_SIZE = 20
const ORIGIN_TYPES = ['CURATED_REPOSITORY', 'CURATED_OWNER', 'SEARCH_RULE', 'OFFICIAL_DISCOVERY'] as const
const STATES = ['REGISTERED', 'PENDING', 'VALIDATING', 'NO_VALID_SKILL', 'VALIDATION_FAILED', 'UNREACHABLE', 'BOUND_WITHOUT_SOURCE'] as const
const SOURCE_STATUSES = ['ACTIVE', 'PAUSED', 'UNREACHABLE'] as const

type Search = { repositoryId?: number; page?: number; q?: string; originType?: GithubSyncRepositoryQuery['originType']; state?: GithubSyncRepositoryQuery['state']; sourceStatus?: GithubSyncRepositoryQuery['sourceStatus']; asOf?: string; runId?: string }
type Change = (changes: Partial<Search>) => void

function SelectFilter({ label, value, options, onChange, analyticsId }: { label: string; value?: string; options: readonly string[]; onChange: (value?: string) => void; analyticsId?: string }) {
  const { t } = useTranslation()
  return <label className="text-sm">{label}<select data-analytics-id={analyticsId} className="mt-1 block h-9 w-full rounded border bg-background px-2" value={value ?? ''} onChange={(event) => onChange(event.target.value || undefined)}>
    <option value="">{t('githubSync.all')}</option>
    {options.map((option) => <option value={option} key={option}>{t(`githubSync.repositoryFilter.${option}`, { defaultValue: option })}</option>)}
  </select></label>
}

export function RepositoryDetail({ repositoryId, visible, onClose, onRun }: { repositoryId: number; visible: boolean; onClose: () => void; onRun: (id: string) => void }) {
  const { t, i18n } = useTranslation()
  const [originPage, setOriginPage] = useState(0)
  const [originAsOf, setOriginAsOf] = useState<string>()
  const [pathPage, setPathPage] = useState(0)
  const [pathAsOf, setPathAsOf] = useState<string>()
  const [sourceActions, setSourceActions] = useState(false)
  const navigatingAway = useRef(false)
  const detail = useGithubSyncRepository(repositoryId, visible)
  const origins = useGithubSyncRepositoryOrigins(repositoryId, originPage, originAsOf, visible)
  const paths = useGithubSyncRepositoryPaths(repositoryId, pathPage, pathAsOf, visible)
  const repository = detail.data?.repository
  const date = (value?: string | null) => formatLocalDateTime(value ?? undefined, i18n.language)
  const originPages = Math.ceil((origins.data?.total ?? 0) / PAGE_SIZE)
  const pathPages = Math.ceil((paths.data?.total ?? 0) / PAGE_SIZE)
  const markNavigation = (event: MouseEvent<HTMLAnchorElement>) => {
    if (!event.defaultPrevented && event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey) {
      navigatingAway.current = true
    }
  }

  return <Dialog open onOpenChange={(open) => { if (!open && !navigatingAway.current) onClose() }}>
    <DialogContent closeAnalyticsId="github.repository.close" className="github-sync-surface !left-auto !right-0 !top-0 !h-dvh !max-h-dvh !grid-rows-[auto_minmax(0,1fr)] !gap-0 !overflow-hidden w-[min(100vw,40rem)] !translate-x-0 !translate-y-0 !rounded-none p-0">
      <DialogHeader className="border-b px-5 py-4 text-left"><DialogTitle>{t('githubSync.repositoryDetail')}</DialogTitle><DialogDescription>{t('githubSync.repositoryDetailHelp')}</DialogDescription></DialogHeader>
      <div className="min-h-0 space-y-5 overflow-y-auto overscroll-contain px-5 py-5">
        {detail.isLoading ? <p className="text-sm text-muted-foreground">{t('githubSync.loading')}</p> : null}
        {detail.error ? <p role="alert" className="text-sm text-destructive">{t('githubSync.loadFailed')}: {detail.error.message}</p> : null}
        {repository ? <>
          <div className="space-y-2"><a data-analytics-id="github.repository.external" className="inline-flex max-w-full items-center gap-1 break-all text-lg font-semibold text-primary hover:underline" href={`https://github.com/${encodeURIComponent(repository.owner ?? '')}/${encodeURIComponent(repository.name ?? '')}`} target="_blank" rel="noopener noreferrer">{repository.owner}/{repository.name}<ExternalLink className="h-4 w-4 shrink-0" /></a>
            <p className="text-sm">{t(`githubSync.repositoryState.${repository.state}`, { defaultValue: repository.state ?? '—' })}{repository.sourceStatus ? ` · ${t(`githubSync.sourceStatus.${repository.sourceStatus}`, { defaultValue: repository.sourceStatus })}` : ''}</p>
            <p className="text-xs text-muted-foreground">{t('githubSync.firstSeen')}: {date(repository.firstSeenAt)} · ID {repository.repositoryId}</p>
          </div>
          <dl className="grid gap-3 rounded border p-3 text-sm sm:grid-cols-2">
            <div><dt className="text-xs text-muted-foreground">{t('deepDiscovery.lastChecked')}</dt><dd>{date(detail.data?.lastCheckedAt)}</dd></div>
            <div><dt className="text-xs text-muted-foreground">{t('githubSync.nextCheck')}</dt><dd>{date(detail.data?.nextCheckAt)}</dd></div>
            {detail.data?.lastErrorCode ? <div className="sm:col-span-2"><dt className="text-xs text-muted-foreground">{t('githubSync.failureReason')}</dt><dd>{t(`githubSync.codes.${detail.data.lastErrorCode}`, { defaultValue: detail.data.lastErrorCode })}</dd></div> : null}
          </dl>
          {detail.data?.latestRunId ? <Button data-analytics-id="github.run.open" size="sm" variant="outline" onClick={() => onRun(detail.data!.latestRunId!)}>{t('githubSync.viewRun')}</Button> : null}
          <section className="space-y-2 border-t pt-4"><h3 className="font-semibold">{t('githubSync.origins')}</h3>
            {origins.isLoading ? <p className="text-sm text-muted-foreground">{t('githubSync.loading')}</p> : null}
            {origins.error ? <p role="alert" className="text-sm text-destructive">{t('githubSync.loadFailed')}: {origins.error.message}</p> : null}
            {origins.data?.items?.length ? <div className="divide-y rounded border text-sm">{origins.data.items.map((origin, index) => <div key={`${origin.originType}:${origin.originKey}:${index}`} className="space-y-1 p-3"><p className="font-medium">{t(`githubSync.repositoryFilter.${origin.originType}`, { defaultValue: origin.originType ?? '—' })}</p><p className="break-all text-muted-foreground">{origin.originKey}</p><p className="text-xs text-muted-foreground">{t('githubSync.firstSeen')}: {date(origin.firstSeenAt)}{origin.lastSeenAt ? ` · ${t('deepDiscovery.lastDiscovered')}: ${date(origin.lastSeenAt)}` : ''}</p></div>)}</div> : !origins.isLoading && !origins.error ? <p className="text-sm text-muted-foreground">{t('githubSync.empty')}</p> : null}
            {originPages > 1 ? <Pagination analyticsId="github.provenance.paginate" page={originPage} totalPages={originPages} onPageChange={(next) => { setOriginAsOf(originAsOf ?? origins.data?.asOf); setOriginPage(next) }} /> : null}
          </section>
          <section className="space-y-2 border-t pt-4"><h3 className="font-semibold">{t('deepDiscovery.pathResults')} {paths.data ? `(${paths.data.total ?? 0})` : ''}</h3>
            {paths.isLoading ? <p className="text-sm text-muted-foreground">{t('githubSync.loading')}</p> : null}
            {paths.error ? <p role="alert" className="text-sm text-destructive">{t('githubSync.loadFailed')}: {paths.error.message}</p> : null}
            {paths.data?.items?.length ? <div className="divide-y rounded border text-sm">{paths.data.items.map((path, index) => <div key={`${path.skillPath}:${index}`} className="space-y-1 p-3"><p className="break-all font-medium">{path.skillPath}</p><p className="text-xs text-muted-foreground">{t(`deepDiscovery.pathOutcome.${path.outcome}`, { defaultValue: path.outcome ?? '—' })}{path.reasonCode ? ` · ${t(`githubSync.codes.${path.reasonCode}`, { defaultValue: path.reasonCode })}` : ''}</p>{path.reviewId ? <Link data-analytics-id="review.detail.open" to="/dashboard/reviews/$id" params={{ id: String(path.reviewId) }} onClickCapture={markNavigation} className="text-xs text-primary hover:underline">{t('deepDiscovery.viewReview')}</Link> : null}</div>)}</div> : !paths.isLoading && !paths.error ? <p className="text-sm text-muted-foreground">{t('deepDiscovery.noPaths')}</p> : null}
            {pathPages > 1 ? <Pagination analyticsId="github.path.paginate" page={pathPage} totalPages={pathPages} onPageChange={(next) => { setPathAsOf(pathAsOf ?? paths.data?.asOf); setPathPage(next) }} /> : null}
          </section>
          {repository.sourceId ? <section className="border-t pt-4"><Button data-analytics-id="github.source.actions" size="sm" variant="outline" aria-expanded={sourceActions} onClick={() => setSourceActions(!sourceActions)}>{t('githubSync.sourceActions')}</Button>{sourceActions && visible ? <div className="mt-4"><GithubSyncSourceDetail sourceId={repository.sourceId} status={repository.sourceStatus ?? undefined} visible={visible} /></div> : null}</section> : null}
          <Link data-analytics-id="review.queue.open" to="/dashboard/reviews" search={{ type: 'sync' }} onClickCapture={markNavigation} className="inline-block border-t pt-4 text-sm text-primary hover:underline">{t('githubSync.openReviewQueue')}</Link>
        </> : null}
      </div>
    </DialogContent>
  </Dialog>
}

export function GithubSyncRepositories({ visible, search, onChange }: { visible: boolean; search: Search; onChange: Change }) {
  const { t, i18n } = useTranslation()
  const [input, setInput] = useState(search.q ?? '')
  const [refreshVersion, setRefreshVersion] = useState(0)
  useEffect(() => setInput(search.q ?? ''), [search.q])
  const query: GithubSyncRepositoryQuery = {
    page: search.page ?? 0, size: PAGE_SIZE, q: search.q || undefined, asOf: search.asOf,
    originType: search.originType,
    state: search.state,
    sourceStatus: search.sourceStatus,
  }
  const repositories = useGithubSyncRepositories(query, visible, refreshVersion)
  const head = useGithubSyncRepositories({ ...query, page: 0, size: 1, asOf: undefined }, visible && Boolean(search.asOf), 0, 60_000)
  const firstSeen = head.data?.items?.[0]?.firstSeenAt
  const newResults = Boolean(firstSeen && search.asOf && Date.parse(firstSeen) > Date.parse(search.asOf))
  const pages = Math.max(1, Math.ceil((repositories.data?.total ?? 0) / PAGE_SIZE))

  useEffect(() => {
    if (!search.asOf && repositories.data?.asOf && visible) onChange({ asOf: repositories.data.asOf })
  }, [onChange, repositories.data?.asOf, search.asOf, visible])

  const changeFilter = (changes: Partial<Search>) => onChange({ ...changes, page: 0, asOf: undefined })
  const refresh = () => { onChange({ page: 0, asOf: undefined }); setRefreshVersion((value) => value + 1) }
  const submit = (event: FormEvent) => { event.preventDefault(); changeFilter({ q: input.trim() || undefined }) }

  return <div className="space-y-4">
    <div><h2 className="text-lg font-semibold">{t('githubSync.repositoriesTab')}</h2><p className="text-sm text-muted-foreground">{t('githubSync.repositoriesHelp')}</p></div>
    <form data-analytics-id="github.repository.search" onSubmit={submit} className="grid gap-3 rounded border p-3 sm:grid-cols-2 xl:grid-cols-[minmax(180px,1fr)_repeat(3,minmax(145px,180px))_auto]">
      <label className="text-sm">{t('githubSync.searchRepository')}<Input className="mt-1" value={input} onChange={(event) => setInput(event.target.value)} maxLength={120} placeholder="owner/repository" /></label>
      <SelectFilter analyticsId="github.repository.origin.filter" label={t('githubSync.discoveryMethod')} value={search.originType} options={ORIGIN_TYPES} onChange={(originType) => changeFilter({ originType: originType as GithubSyncRepositoryQuery['originType'] })} />
      <SelectFilter analyticsId="github.repository.state.filter" label={t('githubSync.repositoryStateLabel')} value={search.state} options={STATES} onChange={(state) => changeFilter({ state: state as GithubSyncRepositoryQuery['state'] })} />
      <SelectFilter analyticsId="github.repository.source.status.filter" label={t('githubSync.sourceState')} value={search.sourceStatus} options={SOURCE_STATUSES} onChange={(sourceStatus) => changeFilter({ sourceStatus: sourceStatus as GithubSyncRepositoryQuery['sourceStatus'] })} />
      <Button type="submit" size="sm" className="self-end">{t('githubSync.search')}</Button>
    </form>
    {newResults ? <div role="status" className="flex items-center justify-between rounded border border-primary/30 bg-primary/5 p-3 text-sm"><span>{t('githubSync.newRepositoryResults')}</span><Button data-analytics-id="github.repository.refresh" size="sm" variant="outline" onClick={refresh}><RefreshCw className="h-4 w-4" />{t('githubSync.refreshResults')}</Button></div> : null}
    <Card className="sync-section overflow-x-auto">
      {repositories.isLoading ? <p className="p-5 text-sm text-muted-foreground">{t('githubSync.loading')}</p> : null}
      {repositories.error ? <p role="alert" className="p-5 text-sm text-destructive">{t('githubSync.loadFailed')}: {repositories.error.message}</p> : null}
      {!repositories.isLoading && !repositories.error && !repositories.data?.items?.length ? <p className="p-5 text-sm text-muted-foreground">{t('githubSync.empty')}</p> : null}
      {repositories.data?.items?.length ? <Table className="min-w-[720px]"><TableHeader><TableRow><TableHead>{t('githubSync.repository')}</TableHead><TableHead>{t('githubSync.discoveryMethod')}</TableHead><TableHead>{t('githubSync.repositoryStateLabel')}</TableHead><TableHead>{t('githubSync.firstSeen')}</TableHead><TableHead>{t('githubSync.details')}</TableHead></TableRow></TableHeader><TableBody>{repositories.data.items.map((item) => <TableRow key={item.repositoryId}>
        <TableCell className="min-w-44 break-all font-medium">{item.owner}/{item.name}<span className="mt-1 block font-mono text-xs text-muted-foreground">#{item.repositoryId}</span></TableCell>
        <TableCell className="min-w-36"><div className="flex flex-wrap gap-1">{item.originTypes?.map((type) => <span className="rounded border px-2 py-0.5 text-xs" key={type}>{t(`githubSync.repositoryFilter.${type}`, { defaultValue: type })}</span>)}</div></TableCell>
        <TableCell><span className="sync-state" data-tone={item.state === 'REGISTERED' ? 'blue' : item.state === 'VALIDATION_FAILED' || item.state === 'UNREACHABLE' ? 'red' : 'amber'}>{t(`githubSync.repositoryState.${item.state}`, { defaultValue: item.state ?? '—' })}</span>{item.sourceStatus ? <span className="mt-1 block text-xs text-muted-foreground">{t(`githubSync.sourceStatus.${item.sourceStatus}`, { defaultValue: item.sourceStatus })}</span> : null}</TableCell>
        <TableCell>{formatLocalDateTime(item.firstSeenAt, i18n.language)}</TableCell>
        <TableCell><Button data-analytics-id="github.repository.open" size="sm" variant="outline" onClick={() => onChange({ repositoryId: item.repositoryId })}>{t('githubSync.details')}</Button></TableCell>
      </TableRow>)}</TableBody></Table> : null}
    </Card>
    {repositories.data && pages > 1 ? <Pagination analyticsId="github.repository.paginate" page={search.page ?? 0} totalPages={pages} onPageChange={(page) => onChange({ page, asOf: search.asOf ?? repositories.data?.asOf })} /> : null}
    {search.repositoryId ? <RepositoryDetail key={search.repositoryId} repositoryId={search.repositoryId} visible={visible} onClose={() => onChange({ repositoryId: undefined })} onRun={(runId) => onChange({ runId, repositoryId: undefined })} /> : null}
  </div>
}
