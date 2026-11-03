import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { githubSyncAdminApi, githubTeamSourcesApi } from '@/api/client'
import { Button } from '@/shared/ui/button'

export function GithubSourceRescanButton({ sourceId, status, namespaceSlug }: {
  sourceId: number
  status?: string
  namespaceSlug?: string
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [feedback, setFeedback] = useState('')
  const rescan = useMutation({
    mutationFn: () => namespaceSlug
      ? githubTeamSourcesApi.rescan(namespaceSlug, sourceId)
      : githubSyncAdminApi.rescan(sourceId),
    onSuccess: (result) => {
      setFeedback(t(result.status === 'MERGED'
        ? result.taskStatus === 'RETRY_WAIT' ? 'githubSync.rescanRetryWait' : 'githubSync.rescanMerged'
        : 'githubSync.rescanScheduled'))
      void queryClient.invalidateQueries({ queryKey: ['github-sync', namespaceSlug ? 'team' : 'sources'] })
      void queryClient.invalidateQueries({ queryKey: ['github-sync', 'runs'] })
    },
    onError: (error) => setFeedback(`${t('githubSync.rescanFailed')}: ${error instanceof Error ? error.message : ''}`),
  })

  return <div className="space-y-1">
    <Button data-analytics-id="github.source.rescan" size="sm" className="sync-action" variant="outline" disabled={status === 'PAUSED' || rescan.isPending}
      onClick={() => { setFeedback(''); rescan.mutate() }}>
      {t('githubSync.rescan')}
    </Button>
    {status === 'PAUSED' ? <p className="text-xs text-muted-foreground">{t('githubSync.rescanPaused')}</p> : null}
    {feedback ? <p role="status" className="text-xs text-muted-foreground">{feedback}</p> : null}
  </div>
}
