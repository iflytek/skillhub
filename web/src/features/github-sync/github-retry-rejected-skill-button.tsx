import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { githubSyncAdminApi, githubTeamSourcesApi } from '@/api/client'
import type { GithubSyncSkillPath } from '@/api/client'
import { Button } from '@/shared/ui/button'

export function GithubRetryRejectedSkillButton({ sourceId, path, namespaceSlug }: {
  sourceId: number
  path: GithubSyncSkillPath
  namespaceSlug?: string
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [feedback, setFeedback] = useState('')
  const skillPath = path.skillPath ?? ''
  const retry = useMutation({
    mutationFn: () => namespaceSlug
      ? githubTeamSourcesApi.retryRejectedSkill(namespaceSlug, sourceId, skillPath)
      : githubSyncAdminApi.retryRejectedSkill(sourceId, skillPath),
    onSuccess: () => {
      setFeedback(t('githubSync.retryRejectedScheduled'))
      void queryClient.invalidateQueries({ queryKey: ['github-sync'] })
    },
    onError: (error) => setFeedback(`${t('githubSync.retryRejectedFailed')}: ${error instanceof Error ? error.message : ''}`),
  })

  if (!skillPath || (path.latestBindingIngestionStatus !== 'REJECTED' && !path.retryRequested)) return null
  return <div className="space-y-1">
    <Button data-analytics-id="github.rejected.retry" size="sm" className="sync-action" variant="outline" disabled={!!path.retryInProgress || !!path.ignoredByCurrentPolicy || retry.isPending}
      onClick={() => { setFeedback(''); retry.mutate() }}>
      {path.retryInProgress ? t('githubSync.retryRejectedPending') : t('githubSync.retryRejected')}
    </Button>
    {feedback ? <p role="status" className="text-xs text-muted-foreground">{feedback}</p> : null}
  </div>
}
