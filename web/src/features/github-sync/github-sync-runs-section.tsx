import { useTranslation } from 'react-i18next'
import type { GithubSyncRunQuery } from '@/api/client'
import { GithubSyncRunList } from './github-sync-run-list'
import { useGithubSyncRuns } from './use-github-sync-admin'

export function GithubSyncRunsSection({ visible, search, onChange }: {
  visible: boolean
  search: { runPage?: number; runKind?: GithubSyncRunQuery['kind']; runStatus?: GithubSyncRunQuery['status'] }
  onChange: (changes: { runPage?: number; runId?: string; runKind?: GithubSyncRunQuery['kind']; runStatus?: GithubSyncRunQuery['status'] }) => void
}) {
  const { t } = useTranslation()
  const page = search.runPage ?? 0
  const runs = useGithubSyncRuns(page, 20, visible, search.runKind, search.runStatus)
  return <div className="space-y-4">
    <div className="flex flex-wrap gap-3 rounded border p-3">
      <label className="text-sm">{t('githubSync.runKind')}<select data-analytics-id="github.run.kind" className="mt-1 block h-9 min-w-40 rounded border bg-background px-2" value={search.runKind ?? ''} onChange={(event) => onChange({ runKind: event.target.value as GithubSyncRunQuery['kind'] || undefined, runPage: 0 })}>
        <option value="">{t('githubSync.all')}</option><option value="DISCOVERY">{t('githubSync.codes.DISCOVERY')}</option><option value="REPOSITORY_SYNC">{t('githubSync.codes.REPOSITORY_SYNC')}</option>
      </select></label>
      <label className="text-sm">{t('githubSync.sourceState')}<select data-analytics-id="github.run.status" className="mt-1 block h-9 min-w-40 rounded border bg-background px-2" value={search.runStatus ?? ''} onChange={(event) => onChange({ runStatus: event.target.value as GithubSyncRunQuery['status'] || undefined, runPage: 0 })}>
        <option value="">{t('githubSync.all')}</option>{['QUEUED', 'LEASED', 'RETRY_WAIT', 'PARTIAL', 'ACQUISITION_COMPLETE', 'FAILED'].map((status) => <option value={status} key={status}>{t(`githubSync.codes.${status}`)}</option>)}
      </select></label>
    </div>
    <GithubSyncRunList data={runs.data} loading={runs.isLoading} error={runs.error}
      page={page} onPageChange={(runPage) => onChange({ runPage })} onSelectRun={(runId) => onChange({ runId })} />
  </div>
}
