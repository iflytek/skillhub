import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { GithubDiscoveryEntry, GithubSearchRuleInput } from '@/api/client'
import { useGithubDiscoveryEntries, useGithubDiscoveryMutations, useGithubSearchPreview, useGithubSyncSchedulePolicy, useGithubSyncWorkerStatus } from './use-github-sync-admin'
import { githubSyncAdminApi } from '@/api/client'
import { toast } from '@/shared/lib/toast'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Button } from '@/shared/ui/button'
import { githubSyncStatusLabel } from './status-label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/shared/ui/dialog'

type Selector = { kind: 'KEYWORD' | 'TOPIC'; value: string; priority: 'CORE' | 'EXTENSION' }
const initialSelector: Selector = { kind: 'KEYWORD', value: '', priority: 'CORE' }
type DiscoveryMode = 'repository' | 'owner' | 'rule'

export function GithubDiscoveryConfig({ layout = 'default' }: { layout?: 'default' | 'other' }) {
  const { t, i18n } = useTranslation()
  const entries = useGithubDiscoveryEntries()
  const schedule = useGithubSyncSchedulePolicy()
  const workerStatus = useGithubSyncWorkerStatus()
  const mutations = useGithubDiscoveryMutations()
  const [repository, setRepository] = useState('')
  const [mode, setMode] = useState<DiscoveryMode | null>(null)
  const [owner, setOwner] = useState('')
  const [ownerHours, setOwnerHours] = useState<number | ''>('')
  const [ruleTitle, setRuleTitle] = useState('')
  const [editingRuleId, setEditingRuleId] = useState<string | null>(null)
  const [ruleHours, setRuleHours] = useState<number | ''>('')
  const [selectors, setSelectors] = useState<Selector[]>([initialSelector])
  const [previewId, setPreviewId] = useState<string | null>(null)
  const [previewSnapshot, setPreviewSnapshot] = useState<string | null>(null)
  const [previewPending, setPreviewPending] = useState(false)
  const shownEntries = layout === 'other' ? entries.data?.filter((entry) => entry.entryType !== 'CURATED_REPOSITORY') : entries.data

  const ruleBody = (): GithubSearchRuleInput => ({
    title: ruleTitle.trim(), targetHours: ruleHours === '' ? undefined : ruleHours,
    selectors: selectors.map(({ kind, value, priority }) => ({ kind, value: value.trim(), priority })),
  })
  const previewMatchesForm = previewId !== null && previewSnapshot === JSON.stringify(ruleBody())
  const preview = useGithubSearchPreview(previewMatchesForm ? previewId : null)

  const previewRule = async () => {
    if (workerStatus.data?.online !== true) {
      toast.error(t('githubSync.previewWorkerOffline'))
      return
    }
    const body = ruleBody()
    setPreviewPending(true)
    setPreviewId(null)
    try {
      const queued = await githubSyncAdminApi.previewSearchRule(body)
      setPreviewSnapshot(JSON.stringify(body))
      setPreviewId(queued.id)
    } catch (error) {
      toast.error(t('githubSync.previewFailed'), error instanceof Error ? error.message : '')
    } finally {
      setPreviewPending(false)
    }
  }

  const addRepository = async (event: FormEvent) => {
    event.preventDefault()
    const parts = repository.trim().replace(/^https:\/\/github\.com\//i, '').replace(/\.git\/?$/i, '').replace(/\/$/, '').split('/')
    if (parts.length !== 2 || !parts[0] || !parts[1]) {
      toast.error(t('githubSync.repositoryFormat'))
      return
    }
    try {
      await mutations.addRepository.mutateAsync({ ownerLogin: parts[0], repositoryName: parts[1] })
      setRepository('')
      setMode(null)
      toast.success(t('githubSync.saved'))
    } catch (error) {
      toast.error(t('githubSync.saveFailed'), error instanceof Error ? error.message : '')
    }
  }

  const addOwner = async (event: FormEvent) => {
    event.preventDefault()
    try {
      await mutations.addOwner.mutateAsync({ ownerLogin: owner.trim(),
        targetHours: ownerHours === '' ? undefined : ownerHours })
      setOwner('')
      setOwnerHours('')
      setMode(null)
      toast.success(t('githubSync.saved'))
    } catch (error) {
      toast.error(t('githubSync.saveFailed'), error instanceof Error ? error.message : '')
    }
  }

  const clearRuleForm = () => {
    setPreviewId(null)
    setPreviewSnapshot(null)
    setEditingRuleId(null)
    setRuleTitle('')
    setRuleHours('')
    setSelectors([initialSelector])
  }

  const openForm = (nextMode: DiscoveryMode) => {
    setRepository('')
    setOwner('')
    setOwnerHours('')
    clearRuleForm()
    setMode(nextMode)
  }

  const closeForm = () => {
    setMode(null)
    setRepository('')
    setOwner('')
    setOwnerHours('')
    clearRuleForm()
  }

  const editRule = (entry: GithubDiscoveryEntry) => {
    if (!entry.id || entry.entryType !== 'SEARCH_RULE') return
    setEditingRuleId(entry.id)
    setPreviewId(null)
    setPreviewSnapshot(null)
    setRuleTitle(entry.title ?? '')
    setRuleHours(entry.targetHours ?? '')
    setSelectors(entry.selectors?.map((selector) => ({
      kind: selector.kind as Selector['kind'],
      value: selector.value ?? '',
      priority: selector.priority as Selector['priority'],
    })) ?? [initialSelector])
    setMode('rule')
  }

  const saveRule = async (event: FormEvent) => {
    event.preventDefault()
    const body = ruleBody()
    try {
      if (editingRuleId) {
        await mutations.updateSearchRule.mutateAsync({ id: editingRuleId, body })
        toast.success(t('githubSync.saved'))
      } else {
        await mutations.addSearchRule.mutateAsync(body)
        toast.success(t('githubSync.ruleSavedDisabled'))
      }
      clearRuleForm()
      setMode(null)
    } catch (error) {
      toast.error(t('githubSync.saveFailed'), error instanceof Error ? error.message : '')
    }
  }

  const setEnabled = async (id: string, enabled: boolean) => {
    try {
      await mutations.setEnabled.mutateAsync({ id, enabled })
      toast.success(t('githubSync.saved'))
    } catch (error) {
      toast.error(t('githubSync.saveFailed'), error instanceof Error ? error.message : '')
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold">{t(layout === 'other' ? 'githubSync.otherAcquisition' : 'githubSync.discoveryTitle')}</h2>
          <p className="mt-1 text-sm text-muted-foreground">{t(layout === 'other' ? 'githubSync.otherAcquisitionHelp' : 'githubSync.discoveryIntro')}</p>
        </div>
        <div className="flex flex-wrap gap-2" role="group" aria-label={t('githubSync.discoveryMode')}>
          {layout === 'default' ? <Button size="sm" className="sync-action" onClick={() => openForm('repository')}>{t('githubSync.addRepository')}</Button> : null}
          <Button data-analytics-id="github.owner.create" size="sm" variant="outline" className="sync-action" onClick={() => openForm('owner')}>{t('githubSync.addOwner')}</Button>
          <Button data-analytics-id="github.rule.create" size="sm" variant="outline" className="sync-action" onClick={() => openForm('rule')}>{t('githubSync.addRule')}</Button>
        </div>
      </div>
      <Dialog open={mode !== null} onOpenChange={(open) => { if (!open) closeForm() }}>
        <DialogContent closeAnalyticsId={mode === 'owner' ? 'github.owner.cancel' : editingRuleId ? 'github.rule.edit.cancel' : 'github.rule.create.cancel'} className="github-sync-surface w-[min(calc(100vw-2rem),60rem)] rounded-md">
          <DialogHeader>
            <DialogTitle>{t(mode === 'repository' ? 'githubSync.addRepository' : mode === 'owner' ? 'githubSync.addOwner' : editingRuleId ? 'githubSync.editRule' : 'githubSync.addRule')}</DialogTitle>
            <DialogDescription>{t(mode === 'repository' ? 'githubSync.repositoryHelp' : mode === 'owner' ? 'githubSync.ownerHelp' : 'githubSync.ruleHelp')}</DialogDescription>
          </DialogHeader>
        {mode === 'repository' ? (
        <div>
          <form onSubmit={addRepository} className="space-y-3">
            <Input aria-label={t('githubSync.repository')} placeholder="owner/repository" value={repository} onChange={(event) => setRepository(event.target.value)} required />
            <Button type="submit" size="sm" className="sync-action" disabled={mutations.addRepository.isPending}>{t('githubSync.add')}</Button>
          </form>
        </div>
        ) : null}
        {mode === 'owner' ? (
        <div>
          <form data-analytics-id="github.owner.add" onSubmit={addOwner} className="space-y-3">
            <Input aria-label={t('githubSync.owner')} placeholder="owner" value={owner} onChange={(event) => setOwner(event.target.value)} required />
            <label className="block text-sm">{t('githubSync.targetHours')}
              <Input type="number" min={1} max={720} value={ownerHours}
                placeholder={String(schedule.data?.curatedOwnerHours ?? 24)}
                onChange={(event) => setOwnerHours(event.target.value === '' ? '' : Number(event.target.value))} />
              <span className="text-xs text-muted-foreground">{t('githubSync.targetHoursDefault')}</span>
            </label>
            <Button type="submit" size="sm" className="sync-action" disabled={mutations.addOwner.isPending}>{t('githubSync.add')}</Button>
          </form>
        </div>
        ) : null}
        {mode === 'rule' ? (
        <div>
          <div className="grid gap-6 lg:grid-cols-2">
          <form data-analytics-id={editingRuleId ? 'github.rule.edit.submit' : 'github.rule.create.submit'} onSubmit={saveRule} className="space-y-3">
            <Input aria-label={t('githubSync.ruleTitle')} placeholder={t('githubSync.ruleTitle')} value={ruleTitle} onChange={(event) => setRuleTitle(event.target.value)} required />
            <label className="block text-sm">{t('githubSync.targetHours')}
              <Input type="number" min={1} max={720} value={ruleHours}
                placeholder={String(schedule.data?.searchRuleHours ?? 72)}
                onChange={(event) => setRuleHours(event.target.value === '' ? '' : Number(event.target.value))} />
              <span className="text-xs text-muted-foreground">{t('githubSync.targetHoursDefault')}</span>
            </label>
            {selectors.map((selector, index) => (
              <div key={index} className="space-y-3 border-t pt-3" style={{ borderColor: 'var(--sync-line)' }}>
                <div className="grid gap-3 sm:grid-cols-2">
                  <label className="block text-sm">{t('githubSync.selectorKind')}
                    <select className="mt-1 w-full px-2 text-sm" value={selector.kind} onChange={(event) => setSelectors((current) => current.map((item, at) => at === index ? { ...item, kind: event.target.value as Selector['kind'] } : item))}>
                      <option data-analytics-id="github.selector.kind.keyword" value="KEYWORD">{t('githubSync.keyword')}</option><option data-analytics-id="github.selector.kind.topic" value="TOPIC">{t('githubSync.topic')}</option>
                    </select>
                  </label>
                  <label className="block text-sm">{t('githubSync.selectorPriority')}
                    <select className="mt-1 w-full px-2 text-sm" value={selector.priority} onChange={(event) => setSelectors((current) => current.map((item, at) => at === index ? { ...item, priority: event.target.value as Selector['priority'] } : item))}>
                      <option data-analytics-id="github.selector.priority.core" value="CORE">{t('githubSync.core')}</option><option data-analytics-id="github.selector.priority.extension" value="EXTENSION">{t('githubSync.extension')}</option>
                    </select>
                  </label>
                </div>
                <div className="flex flex-wrap items-end gap-2">
                  <label className="min-w-40 flex-1 text-sm">{t(selector.kind === 'TOPIC' ? 'githubSync.topic' : 'githubSync.keyword')}
                    <Input className="mt-1" value={selector.value} onChange={(event) => setSelectors((current) => current.map((item, at) => at === index ? { ...item, value: event.target.value } : item))} required />
                  </label>
                  {selectors.length > 1 ? <Button data-analytics-id="github.selector.remove" type="button" size="sm" className="sync-action" variant="outline" onClick={() => setSelectors((current) => current.filter((_, at) => at !== index))}>{t('githubSync.remove')}</Button> : null}
                </div>
              </div>
            ))}
            {selectors.length < 20 ? <Button data-analytics-id="github.selector.add" type="button" size="sm" className="sync-action" variant="outline" onClick={() => setSelectors((current) => [...current, initialSelector])}>{t('githubSync.addSelector')}</Button> : null}
            <div className="flex flex-wrap gap-2">
              <Button type="submit" size="sm" className="sync-action" disabled={mutations.addSearchRule.isPending || mutations.updateSearchRule.isPending}>{t(editingRuleId ? 'githubSync.save' : 'githubSync.add')}</Button>
              <Button data-analytics-id="github.rule.preview" type="button" size="sm" className="sync-action" variant="outline" disabled={previewPending || workerStatus.data?.online !== true || !ruleTitle.trim() || selectors.some((selector) => !selector.value.trim())} onClick={previewRule}>{t('githubSync.previewRule')}</Button>
              <Button data-analytics-id={editingRuleId ? 'github.rule.edit.cancel' : 'github.rule.create.cancel'} type="button" size="sm" className="sync-action" variant="outline" onClick={closeForm}>{t('githubSync.cancel')}</Button>
            </div>
          </form>
          <div className="sync-preview p-4" aria-live="polite">
          <h3 className="font-semibold">{t('githubSync.rulePreviewTitle')}</h3>
          <p className="mt-1 text-sm text-muted-foreground">{t('githubSync.previewCaveat')}</p>
          {previewId && !previewMatchesForm ? <p role="status" className="border-t pt-3 text-sm text-muted-foreground">{t('githubSync.previewStale')}</p> : null}
          {workerStatus.isLoading ? <p className="mt-4 text-sm text-muted-foreground">{t('githubSync.previewWorkerChecking')}</p> : null}
          {workerStatus.error ? <p role="alert" className="mt-4 text-sm" style={{ color: 'var(--sync-amber)' }}>{t('githubSync.previewWorkerUnknown')}</p> : null}
          {!workerStatus.isLoading && !workerStatus.error && workerStatus.data?.online === false ? <p role="status" className="mt-4 text-sm" style={{ color: 'var(--sync-amber)' }}>{t('githubSync.previewWorkerOffline')}</p> : null}
          {previewMatchesForm ? <div className="mt-4 space-y-2 border-t pt-3 text-sm">
            {preview.isLoading ? <p>{t('githubSync.loading')}</p> : null}
            {preview.error ? <p role="alert">{t('githubSync.previewFailed')}: {preview.error.message}</p> : null}
            {preview.data?.status === 'QUEUED' && workerStatus.data?.online === true ? <p role="status">{t('githubSync.previewQueued')}</p> : null}
            {preview.data?.status === 'LEASED' ? <p role="status">{t('githubSync.previewWaiting')}</p> : null}
            {preview.data?.status === 'RETRY_WAIT' ? <p role="status">{t('githubSync.previewDeferred')}{preview.data.retryAt ? ` · ${formatLocalDateTime(preview.data.retryAt, i18n.language)}` : ''}{preview.data.errorCode ? ` · ${preview.data.errorCode}` : ''}</p> : null}
            {preview.data?.status === 'FAILED' ? <p role="alert">{t('githubSync.previewFailed')}{preview.data.errorCode ? ` · ${preview.data.errorCode}` : ''}</p> : null}
            {preview.data?.status === 'ACQUISITION_COMPLETE' && !preview.data.result ? <p role="alert">{t('githubSync.previewMissingResult')}</p> : null}
            {preview.data?.result?.selectors.map((selector, index) => <div key={`${selector.kind}:${selector.value}:${index}`} className="border-b p-2" style={{ borderColor: 'var(--sync-line)' }}>
              <p>{selector.kind}: {selector.value} · {t('githubSync.previewEstimate')}: {selector.totalCount}</p>
              {selector.limitReached || selector.incompleteResults ? <p className="text-muted-foreground">{t('githubSync.previewLimited')}</p> : null}
              <p className="break-all text-muted-foreground">{selector.samples.map((sample) => `${sample.owner}/${sample.name}`).join(' · ')}</p>
            </div>)}
          </div> : null}
          {!previewId && workerStatus.data?.online === true ? <p className="mt-4 text-sm text-muted-foreground">{t('githubSync.previewPrompt')}</p> : null}
          </div>
          </div>
        </div>
        ) : null}
        </DialogContent>
      </Dialog>

      <Card className="sync-section space-y-3 p-4 sm:p-5 overflow-x-auto">
        <div>
          <h2 className="text-lg font-semibold">{t('githubSync.existingEntries')}</h2>
          <p className="text-sm text-muted-foreground">{t('githubSync.existingEntriesHint')}</p>
        </div>
        {entries.isLoading ? <p className="p-6 text-sm text-muted-foreground">{t('githubSync.loading')}</p> : null}
        {entries.error ? <p role="alert" className="p-6 text-sm text-destructive">{t('githubSync.loadFailed')}: {entries.error.message}</p> : null}
        {shownEntries?.length === 0 ? <p className="p-6 text-sm text-muted-foreground">{t('githubSync.empty')}</p> : null}
        {shownEntries?.length ? (
          <Table className="min-w-[760px] [&_td]:px-3 [&_th]:px-3">
            <TableHeader><TableRow>
              <TableHead>{t('githubSync.entryType')}</TableHead><TableHead>{t('githubSync.entryName')}</TableHead>
              <TableHead>{t('githubSync.ruleVersion')}</TableHead><TableHead>{t('githubSync.nextCheck')}</TableHead>
              <TableHead>{t('githubSync.sourceState')}</TableHead><TableHead>{t('githubSync.details')}</TableHead>
            </TableRow></TableHeader>
            <TableBody>{shownEntries.map((entry) => (
              <TableRow key={entry.id}>
                <TableCell>{githubSyncStatusLabel(t, entry.entryType)}</TableCell>
                <TableCell className="max-w-xs break-all">{entry.title || [entry.ownerLogin, entry.repositoryName].filter(Boolean).join('/')}</TableCell>
                <TableCell>{entry.ruleVersion ?? '—'}</TableCell>
                <TableCell>{formatLocalDateTime(entry.nextDueAt, i18n.language)}</TableCell>
                <TableCell><span className="sync-state" data-tone={entry.enabled ? 'teal' : 'amber'}>{entry.enabled ? t('githubSync.active') : t('githubSync.paused')}</span></TableCell>
                <TableCell className="space-x-2">
                  {entry.entryType === 'SEARCH_RULE' ? <Button data-analytics-id="github.rule.edit" size="sm" className="sync-action" variant="outline" onClick={() => editRule(entry)}>{t('githubSync.edit')}</Button> : null}
                  <Button data-analytics-id={entry.entryType === 'SEARCH_RULE' ? entry.enabled ? 'github.rule.disable' : 'github.rule.enable' : entry.enabled ? 'github.owner.disable' : 'github.owner.enable'} size="sm" className="sync-action" variant="outline" disabled={!entry.id || mutations.setEnabled.isPending} onClick={() => entry.id && setEnabled(entry.id, !entry.enabled)}>{entry.enabled ? t('githubSync.disable') : t('githubSync.enable')}</Button>
                </TableCell>
              </TableRow>
            ))}</TableBody>
          </Table>
        ) : null}
      </Card>
    </div>
  )
}
